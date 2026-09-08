package com.matesaconcahua.api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    public void sendResetCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Código para recuperar tu contraseña");
            message.setText("""
                    Recibimos una solicitud para restablecer tu contraseña.

                    Tu código de verificación es: %s

                    Este código vence en 15 minutos. Si no solicitaste este cambio, ignorá este mensaje.
                    """.formatted(code));
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error al enviar email de recuperación a {}: {}", to, e.getMessage());
        }
    }
}
