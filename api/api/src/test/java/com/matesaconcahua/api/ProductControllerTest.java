package com.matesaconcahua.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private String adminToken;
    private String userToken;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // Create admin user
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setName("Admin");
        admin.setRole(User.Role.admin);
        userRepository.save(admin);
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getEmail(), admin.getRole().name());

        // Create regular user
        User regular = new User();
        regular.setEmail("user@test.com");
        regular.setPassword(passwordEncoder.encode("user123"));
        regular.setName("User");
        regular.setRole(User.Role.user);
        userRepository.save(regular);
        userToken = jwtUtil.generateToken(regular.getId(), regular.getEmail(), regular.getRole().name());

        // Create test product
        testProduct = new Product();
        testProduct.setName("Mate Calabaza");
        testProduct.setDescription("Mate tradicional");
        testProduct.setPrice(new BigDecimal("2500.00"));
        testProduct.setCategory(Product.Category.mates);
        testProduct.setStock(10);
        testProduct = productRepository.save(testProduct);
    }

    // ── GET /api/products ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 and product list for GET /api/products")
    void getAll_returnsProductList() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products").isArray())
            .andExpect(jsonPath("$.products", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("should return product list without authentication")
    void getAll_publicEndpoint_noAuth() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk());
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 and product when product exists")
    void getOne_existingProduct_returns200() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.name").value("Mate Calabaza"))
            .andExpect(jsonPath("$.product.price").value(2500.00));
    }

    @Test
    @DisplayName("should return 404 when product does not exist")
    void getOne_nonExistentProduct_returns404() throws Exception {
        mockMvc.perform(get("/api/products/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }

    // ── POST /api/products ────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 201 when admin creates product")
    void create_asAdmin_returns201() throws Exception {
        var body = Map.of(
            "name", "Bombilla Imperial",
            "description", "Bombilla de alta calidad",
            "price", 1200,
            "category", "bombillas",
            "stock", 5
        );

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.product.name").value("Bombilla Imperial"));
    }

    @Test
    @DisplayName("should return 403 when regular user tries to create product")
    void create_asUser_returns403() throws Exception {
        var body = Map.of("name", "Product", "price", 100, "category", "mates", "stock", 1);

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated user tries to create product")
    void create_unauthenticated_returns401() throws Exception {
        var body = Map.of("name", "Product", "price", 100, "category", "mates", "stock", 1);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 when admin updates product")
    void update_asAdmin_returns200() throws Exception {
        var body = Map.of(
            "name", "Mate Actualizado",
            "description", "Nueva descripción",
            "price", 3000,
            "category", "mates",
            "stock", 20
        );

        mockMvc.perform(put("/api/products/" + testProduct.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.name").value("Mate Actualizado"));
    }

    @Test
    @DisplayName("should return 403 when regular user tries to update product")
    void update_asUser_returns403() throws Exception {
        var body = Map.of("name", "Updated", "price", 100, "category", "mates", "stock", 1);

        mockMvc.perform(put("/api/products/" + testProduct.getId())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 404 when admin updates non-existent product")
    void update_nonExistentProduct_returns404() throws Exception {
        var body = Map.of("name", "X", "price", 100, "category", "mates", "stock", 1);

        mockMvc.perform(put("/api/products/99999")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isNotFound());
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 when admin deletes product")
    void delete_asAdmin_returns200() throws Exception {
        mockMvc.perform(delete("/api/products/" + testProduct.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("should return 403 when regular user tries to delete product")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/products/" + testProduct.getId())
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 404 when admin deletes non-existent product")
    void delete_nonExistentProduct_returns404() throws Exception {
        mockMvc.perform(delete("/api/products/99999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }
}
