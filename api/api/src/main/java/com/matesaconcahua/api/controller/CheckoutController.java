package com.matesaconcahua.api.controller;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.*;
import com.mercadopago.resources.preference.Preference;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.repository.ProductRepository;
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

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @Value("${app.base-url}")
    private String appBaseUrl;

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

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(items)
                    .payer(payerRequest)
                    .backUrls(backUrls)
                    .externalReference(UUID.randomUUID().toString())
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference   = client.create(request);

            boolean isSandbox = mpAccessToken.startsWith("TEST-");
            String initPoint = (isSandbox && preference.getSandboxInitPoint() != null)
                    ? preference.getSandboxInitPoint()
                    : preference.getInitPoint();

            return ResponseEntity.ok(Map.of(
                    "init_point",    initPoint,
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
}
