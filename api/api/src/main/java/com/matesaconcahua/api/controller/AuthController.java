package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.dto.auth.AuthResponse;
import com.matesaconcahua.api.dto.auth.LoginRequest;
import com.matesaconcahua.api.dto.auth.SignupRequest;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.exception.BusinessException;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.security.JwtUtil;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil         jwtUtil;
    private final JavaMailSender  mailSender;

    @Value("${mail.from}")
    private String mailFrom;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.email()))
            throw new BusinessException("Ya existe una cuenta con ese email", HttpStatus.CONFLICT);

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(User.Role.user);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(token, user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Email o contraseña incorrectos", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword()))
            throw new BusinessException("Email o contraseña incorrectos", HttpStatus.UNAUTHORIZED);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(toResponse(token, user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "El email es requerido"));

        // Siempre responde 200 para no revelar si el email está registrado
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            user.setResetCode(code);
            user.setResetCodeExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            sendResetEmail(user.getEmail(), user.getName(), code);
        });

        return ResponseEntity.ok(Map.of("message", "Si el email está registrado, recibirás un código en tu casilla"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email       = body.get("email");
        String code        = body.get("code");
        String newPassword = body.get("newPassword");

        if (email == null || code == null || newPassword == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Todos los campos son requeridos"));
        if (newPassword.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));

        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);

        if (user == null
                || user.getResetCode() == null
                || !user.getResetCode().equals(code.trim())
                || user.getResetCodeExpiry() == null
                || user.getResetCodeExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código inválido o expirado"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetCode(null);
        user.setResetCodeExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    private void sendResetEmail(String to, String name, String code) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Tu código de recuperación — Mates Aconcagua");
            helper.setText(buildResetEmailHtml(name, code), true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Error enviando email de reset a {}: {}", to, e.getMessage());
        }
    }

    private String buildResetEmailHtml(String name, String code) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f6f4ec;font-family:'Helvetica Neue',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f6f4ec;padding:40px 0;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">
                    <tr>
                      <td style="background:#4a5f2f;padding:28px 32px;text-align:center;">
                        <p style="margin:0;font-size:22px;font-weight:700;color:#fff;letter-spacing:0.5px;">Mates Aconcagua 🧉</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:36px 40px 28px;">
                        <p style="margin:0 0 8px;font-size:17px;color:#22261d;font-weight:600;">Hola, %s</p>
                        <p style="margin:0 0 28px;font-size:15px;color:#6c7062;line-height:1.6;">
                          Recibimos una solicitud para restablecer la contraseña de tu cuenta.
                          Usá el siguiente código en la página de recuperación:
                        </p>
                        <div style="background:#f6f4ec;border-radius:12px;padding:20px;text-align:center;margin-bottom:28px;">
                          <p style="margin:0;font-size:38px;font-weight:800;letter-spacing:10px;color:#4a5f2f;">%s</p>
                        </div>
                        <p style="margin:0 0 8px;font-size:13px;color:#9a9d90;text-align:center;">
                          Este código expira en <strong>15 minutos</strong>.
                        </p>
                        <p style="margin:16px 0 0;font-size:13px;color:#9a9d90;text-align:center;">
                          Si no solicitaste este cambio, podés ignorar este email.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f6f4ec;padding:16px 32px;text-align:center;">
                        <p style="margin:0;font-size:12px;color:#9a9d90;">Mates Aconcagua · Mendoza, Argentina</p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name != null ? name : "usuario", code);
    }

    private AuthResponse toResponse(String token, User user) {
        return new AuthResponse(token,
                new AuthResponse.UserDto(user.getId(), user.getEmail(), user.getName(), user.getRole().name()));
    }
}
