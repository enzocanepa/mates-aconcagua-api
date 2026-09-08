package com.matesaconcahua.api.service;

import com.matesaconcahua.api.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class N8nNotificationService {

    private static final Logger log = LoggerFactory.getLogger(N8nNotificationService.class);

    private final RestClient restClient;

    @Value("${n8n.compra-exitosa-url:}")
    private String compraExitosaUrl;

    @Value("${n8n.compra-fallida-url:}")
    private String compraFallidaUrl;

    @Value("${n8n.webhook-secret:}")
    private String webhookSecret;

    public N8nNotificationService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Async
    public void notificarCompraExitosa(Order order) {
        enviar(compraExitosaUrl, Map.of(
                "email", order.getUser().getEmail(),
                "nombre", order.getUser().getName(),
                "order_id", order.getId(),
                "total", order.getTotal(),
                "metodo_pago", "Mercado Pago"
        ));
    }

    @Async
    public void notificarCompraFallida(Order order, String motivo) {
        enviar(compraFallidaUrl, Map.of(
                "email", order.getUser().getEmail(),
                "nombre", order.getUser().getName(),
                "motivo", motivo
        ));
    }

    private void enviar(String url, Map<String, Object> body) {
        if (url == null || url.isBlank()) return;
        try {
            restClient.post()
                    .uri(url)
                    .header("x-webhook-secret", webhookSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error notificando a n8n ({}): {}", url, e.getMessage());
        }
    }
}
