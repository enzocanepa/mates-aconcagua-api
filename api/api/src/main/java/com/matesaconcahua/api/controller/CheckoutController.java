package com.matesaconcahua.api.controller;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.*;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @PostMapping("/create-preference")
    public ResponseEntity<?> createPreference(@RequestBody Map<String, Object> body,
                                               Authentication auth) {
        try {
            MercadoPagoConfig.setAccessToken(mpAccessToken);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cartItems = (List<Map<String, Object>>) body.get("items");

            @SuppressWarnings("unchecked")
            Map<String, String> payer = (Map<String, String>) body.get("payer");

            @SuppressWarnings("unchecked")
            Map<String, String> shipping = (Map<String, String>) body.get("shipping");

            String baseUrl = (String) body.getOrDefault("baseUrl", "http://localhost:5173");

            // Armar items de la preferencia
            List<PreferenceItemRequest> items = new ArrayList<>();
            for (Map<String, Object> ci : cartItems) {
                double price    = ((Number) ci.get("price")).doubleValue();
                int    quantity = ((Number) ci.get("quantity")).intValue();

                items.add(PreferenceItemRequest.builder()
                        .id(String.valueOf(ci.get("id")))
                        .title(String.valueOf(ci.get("name")))
                        .description(ci.getOrDefault("description", "").toString())
                        .pictureUrl(ci.getOrDefault("image", "").toString())
                        .categoryId(ci.getOrDefault("category", "").toString())
                        .quantity(quantity)
                        .unitPrice(BigDecimal.valueOf(price))
                        .currencyId("ARS")
                        .build());
            }

            // Payer
            PreferencePayerRequest payerRequest = PreferencePayerRequest.builder()
                    .name(payer.getOrDefault("name", ""))
                    .email(payer.getOrDefault("email", ""))
                    .build();

            // Back URLs
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(baseUrl + "/checkout/exito")
                    .failure(baseUrl + "/checkout/error")
                    .pending(baseUrl + "/checkout/pendiente")
                    .build();

            // Preferencia completa
            PreferenceRequest request = PreferenceRequest.builder()
                    .items(items)
                    .payer(payerRequest)
                    .backUrls(backUrls)
                    .externalReference(UUID.randomUUID().toString())
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference   = client.create(request);

            // TEST- credentials → sandbox, APP_USR- credentials → production
            boolean isSandbox = mpAccessToken.startsWith("TEST-");
            String initPoint = (isSandbox && preference.getSandboxInitPoint() != null)
                    ? preference.getSandboxInitPoint()
                    : preference.getInitPoint();

            return ResponseEntity.ok(Map.of(
                    "init_point",   initPoint,
                    "preference_id", preference.getId()
            ));

        } catch (com.mercadopago.exceptions.MPApiException e) {
            System.err.println("MP API error status: " + e.getStatusCode());
            System.err.println("MP API error body: "   + e.getApiResponse().getContent());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "MercadoPago: " + e.getApiResponse().getContent()));
        } catch (Exception e) {
            System.err.println("MercadoPago error: " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al crear la preferencia: " + e.getMessage()));
        }
    }
}
