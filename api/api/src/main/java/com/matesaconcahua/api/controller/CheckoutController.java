package com.matesaconcahua.api.controller;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.*;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.matesaconcahua.api.entity.Order;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.repository.OrderRepository;
import com.matesaconcahua.api.repository.ProductRepository;
import com.matesaconcahua.api.service.N8nNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final N8nNotificationService n8nNotificationService;

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    @PostMapping("/create-preference")
    public ResponseEntity<?> createPreference(@RequestBody Map<String, Object> body,
                                               Authentication auth) {
        try {
            MercadoPagoConfig.setAccessToken(mpAccessToken);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cartItems = (List<Map<String, Object>>) body.get("items");

            if (cartItems == null || cartItems.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El carrito no puede estar vacío"));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> payer = (Map<String, String>) body.get("payer");

            // Validate prices from DB — never trust client-sent prices
            List<PreferenceItemRequest> items = new ArrayList<>();
            for (Map<String, Object> ci : cartItems) {
                int productId = ((Number) ci.get("id")).intValue();
                int quantity  = ((Number) ci.get("quantity")).intValue();

                if (quantity <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a cero"));
                }

                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Producto no encontrado: " + productId));

                items.add(PreferenceItemRequest.builder()
                        .id(String.valueOf(product.getId()))
                        .title(product.getName())
                        .description(product.getDescription() != null ? product.getDescription() : "")
                        .pictureUrl(product.getImage() != null ? product.getImage() : "")
                        .categoryId(product.getCategory().name())
                        .quantity(quantity)
                        .unitPrice(product.getPrice())
                        .currencyId("ARS")
                        .build());
            }

            PreferencePayerRequest payerRequest = PreferencePayerRequest.builder()
                    .name(payer.getOrDefault("name", ""))
                    .email(payer.getOrDefault("email", ""))
                    .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(appBaseUrl + "/checkout/exito")
                    .failure(appBaseUrl + "/checkout/error")
                    .pending(appBaseUrl + "/checkout/pendiente")
                    .build();

            String externalReference = UUID.randomUUID().toString();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(items)
                    .payer(payerRequest)
                    .backUrls(backUrls)
                    .externalReference(externalReference)
                    // Temporalmente deshabilitado para descartar que la validación de MP sobre
                    // esta URL esté activando controles antifraude más estrictos en el checkout.
                    // .notificationUrl(apiBaseUrl + "/api/checkout/webhook")
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference   = client.create(request);

            // MP unificó el esquema: lo que define si el entorno es de prueba son las
            // credenciales (TEST- vs APP_USR-), no el dominio. sandbox_init_point/el dominio
            // sandbox.mercadopago.com.ar quedaron legacy y dan 404/loops de redirect.
            return ResponseEntity.ok(Map.of(
                    "init_point",    preference.getInitPoint(),
                    "preference_id", preference.getId()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (com.mercadopago.exceptions.MPApiException e) {
            log.error("MP API error {}: {}", e.getStatusCode(), e.getApiResponse().getContent());
            return ResponseEntity.status(502).body(Map.of("error", "Error al procesar el pago. Intentá nuevamente."));
        } catch (Exception e) {
            log.error("Checkout error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Error interno. Intentá nuevamente."));
        }
    }

    // Notificación server-to-server de Mercado Pago: fuente de verdad del estado del
    // pago, independiente de que el navegador del cliente vuelva a cargar la página.
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request) {
        try {
            String type = request.getParameter("type") != null
                    ? request.getParameter("type")
                    : request.getParameter("topic");
            String paymentId = request.getParameter("data.id") != null
                    ? request.getParameter("data.id")
                    : request.getParameter("id");

            if (!"payment".equals(type) || paymentId == null)
                return ResponseEntity.ok().build();

            MercadoPagoConfig.setAccessToken(mpAccessToken);
            Payment payment = new PaymentClient().get(Long.valueOf(paymentId));

            String externalReference = payment.getExternalReference();
            if (externalReference == null) return ResponseEntity.ok().build();

            Order order = orderRepository.findByExternalReference(externalReference).orElse(null);
            if (order == null) {
                log.warn("Webhook de MP: no se encontró orden para externalReference {}", externalReference);
                return ResponseEntity.ok().build();
            }

            Order.Status previousStatus = order.getStatus();
            Order.Status newStatus = mapMpStatus(payment.getStatus());

            if (newStatus == previousStatus) return ResponseEntity.ok().build(); // ya procesado, evita duplicados

            order.setStatus(newStatus);
            order.setPaymentId(String.valueOf(payment.getId()));
            orderRepository.save(order);

            if (newStatus == Order.Status.completed)
                n8nNotificationService.notificarCompraExitosa(order);
            else if (newStatus == Order.Status.cancelled)
                n8nNotificationService.notificarCompraFallida(order, "Pago rechazado por Mercado Pago");

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error procesando webhook de Mercado Pago: {}", e.getMessage(), e);
            return ResponseEntity.ok().build(); // 200 siempre para que MP no reintente indefinidamente
        }
    }

    private Order.Status mapMpStatus(String mpStatus) {
        return switch (mpStatus) {
            case "approved" -> Order.Status.completed;
            case "rejected", "cancelled" -> Order.Status.cancelled;
            default -> Order.Status.pending; // pending, in_process, authorized, etc.
        };
    }
}
