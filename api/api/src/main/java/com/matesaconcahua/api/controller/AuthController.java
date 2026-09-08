package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.dto.auth.AuthResponse;
import com.matesaconcahua.api.dto.auth.ForgotPasswordRequest;
import com.matesaconcahua.api.dto.auth.LoginRequest;
import com.matesaconcahua.api.dto.auth.ResetPasswordRequest;
import com.matesaconcahua.api.dto.auth.SignupRequest;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.exception.BusinessException;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.security.JwtUtil;
import com.matesaconcahua.api.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final SecureRandom RESET_CODE_RANDOM = new SecureRandom();

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil         jwtUtil;
    private final MailService     mailService;

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
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String code = String.format("%06d", RESET_CODE_RANDOM.nextInt(1_000_000));
            user.setResetCode(code);
            user.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            mailService.sendResetCode(user.getEmail(), code);
        });
        // Siempre 200, sin revelar si el email existe o no
        return ResponseEntity.ok(Map.of("message", "Si el email existe, te enviamos un código de verificación"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Código inválido o expirado", HttpStatus.BAD_REQUEST));

        boolean codeValid = user.getResetCode() != null
                && user.getResetCode().equals(request.code())
                && user.getResetCodeExpiresAt() != null
                && user.getResetCodeExpiresAt().isAfter(LocalDateTime.now());

        if (!codeValid)
            throw new BusinessException("Código inválido o expirado", HttpStatus.BAD_REQUEST);

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    private AuthResponse toResponse(String token, User user) {
        return new AuthResponse(token,
                new AuthResponse.UserDto(user.getId(), user.getEmail(), user.getName(), user.getRole().name()));
    }
}
