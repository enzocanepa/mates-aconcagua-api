package com.matesaconcahua.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final RestClient restClient;

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${mail.from}")
    private String from;

    public MailService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .requestFactory(requestFactory)
                .build();
    }

    @Async
    public void sendResetCode(String to, String code) {
        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("name", "Mates Aconcagua", "email", from),
                            "to", List.of(Map.of("email", to)),
                            "subject", "Código para recuperar tu contraseña",
                            "textContent", """
                                    Recibimos una solicitud para restablecer tu contraseña.

                                    Tu código de verificación es: %s

                                    Este código vence en 15 minutos. Si no solicitaste este cambio, ignorá este mensaje.
                                    """.formatted(code)
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error al enviar email de recuperación a {}: {}", to, e.getMessage());
        }
    }
}
