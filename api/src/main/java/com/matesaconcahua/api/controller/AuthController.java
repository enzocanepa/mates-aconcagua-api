package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");
        String name     = body.get("name");

        if (userRepository.existsByEmail(email))
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese email"));

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(User.Role.user);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(Map.of("token", token, "user", toMap(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword()))
            return ResponseEntity.status(401).body(Map.of("error", "Email o contraseña incorrectos"));

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(Map.of("token", token, "user", toMap(user)));
    }

    private Map<String, Object> toMap(User user) {
        return Map.of(
            "id",    user.getId(),
            "email", user.getEmail(),
            "name",  user.getName() != null ? user.getName() : "",
            "role",  user.getRole().name()
        );
    }
}
