package com.matesaconcahua.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private String userToken;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("user@sec.com");
        user.setPassword(passwordEncoder.encode("pass123"));
        user.setName("SecUser");
        user.setRole(User.Role.user);
        userRepository.save(user);
        userToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    // ── Unauthenticated access to protected endpoints ─────────────────────────

    @Test
    @DisplayName("should return 401 for GET /api/orders without authentication")
    void getOrders_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 for GET /api/cart without authentication")
    void getCart_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 for POST /api/cart without authentication")
    void postCart_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 for POST /api/orders without authentication")
    void postOrders_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cart", java.util.List.of(), "total", 0))))
            .andExpect(status().isUnauthorized());
    }

    // ── User accessing admin endpoints ────────────────────────────────────────

    @Test
    @DisplayName("should return 403 for GET /api/orders/admin when user is not admin")
    void adminOrders_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/orders/admin")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 403 for POST /api/products when user is not admin")
    void createProduct_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("name", "X", "price", 100, "category", "mates", "stock", 1))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 403 for DELETE /api/products/{id} when user is not admin")
    void deleteProduct_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/products/1")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 403 for PATCH /api/orders/{id} when user is not admin")
    void patchOrderStatus_asUser_returns403() throws Exception {
        mockMvc.perform(patch("/api/orders/some-order-id")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "completed"))))
            .andExpect(status().isForbidden());
    }

    // ── Invalid / malformed JWT tokens ────────────────────────────────────────

    @Test
    @DisplayName("should return 401 when JWT token is completely invalid")
    void invalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 when JWT signature is wrong")
    void wrongSignatureJwt_returns401() throws Exception {
        // A JWT signed with a different secret
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9"
            + ".eyJzdWIiOiJ1c2VyLWlkIiwicm9sZSI6InVzZXIifQ"
            + ".WRONG_SIGNATURE_HERE";
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + fakeToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 when Authorization header is Bearer with empty token")
    void emptyBearerToken_returns401() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized());
    }

    // ── Public endpoints should be accessible ─────────────────────────────────

    @Test
    @DisplayName("should return 200 for GET /api/products without authentication")
    void getProducts_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 for POST /api/auth/signup without authentication")
    void signup_noAuth_returns201() throws Exception {
        var body = Map.of("name", "Nuevo", "email", "nuevo@sec.com", "password", "pass123");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("should return 200 for GET /actuator/health without authentication")
    void actuatorHealth_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    // ── Valid authenticated access ────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 for GET /api/cart with valid JWT")
    void getCart_withValidJwt_returns200() throws Exception {
        mockMvc.perform(get("/api/cart")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk());
    }
}
