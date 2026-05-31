package com.matesaconcahua.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matesaconcahua.api.entity.Order;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.OrderRepository;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private String adminToken;
    private String userToken;
    private String userId;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // Admin user
        User admin = new User();
        admin.setEmail("admin@order.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setName("Admin");
        admin.setRole(User.Role.admin);
        userRepository.save(admin);
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getEmail(), admin.getRole().name());

        // Regular user
        User regular = new User();
        regular.setEmail("user@order.com");
        regular.setPassword(passwordEncoder.encode("user123"));
        regular.setName("Usuario Test");
        regular.setRole(User.Role.user);
        userRepository.save(regular);
        userId = regular.getId();
        userToken = jwtUtil.generateToken(regular.getId(), regular.getEmail(), regular.getRole().name());

        // Test product
        testProduct = new Product();
        testProduct.setName("Mate Test");
        testProduct.setDescription("Desc");
        testProduct.setPrice(new BigDecimal("1500.00"));
        testProduct.setCategory(Product.Category.mates);
        testProduct.setStock(100);
        testProduct = productRepository.save(testProduct);
    }

    // ── GET /api/orders (my orders) ───────────────────────────────────────────

    @Test
    @DisplayName("should return 200 and empty list when user has no orders")
    void getMyOrders_noOrders_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").isArray())
            .andExpect(jsonPath("$.orders", hasSize(0)));
    }

    @Test
    @DisplayName("should return 401 when unauthenticated user requests own orders")
    void getMyOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized());
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 201 when authenticated user creates order")
    void createOrder_authenticatedUser_returns201() throws Exception {
        var cartItems = List.of(Map.of(
            "id", testProduct.getId(),
            "name", "Mate Test",
            "price", 1500.0,
            "quantity", 2
        ));
        var body = Map.of("cart", cartItems, "total", 3000.0);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.id").isNotEmpty())
            .andExpect(jsonPath("$.order.status").value("pending"));
    }

    @Test
    @DisplayName("should return 400 when stock is insufficient")
    void createOrder_insufficientStock_returns400() throws Exception {
        // Product has 100 stock, try to order 200
        var cartItems = List.of(Map.of(
            "id", testProduct.getId(),
            "name", "Mate Test",
            "price", 1500.0,
            "quantity", 200
        ));
        var body = Map.of("cart", cartItems, "total", 300000.0);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsStringIgnoringCase("stock")));
    }

    @Test
    @DisplayName("should return 404 when cart contains non-existent product")
    void createOrder_nonExistentProduct_returns404() throws Exception {
        var cartItems = List.of(Map.of(
            "id", 99999,
            "name", "Producto Inexistente",
            "price", 100.0,
            "quantity", 1
        ));
        var body = Map.of("cart", cartItems, "total", 100.0);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated user creates order")
    void createOrder_unauthenticated_returns401() throws Exception {
        var body = Map.of("cart", List.of(), "total", 0.0);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    // ── GET /api/orders/admin ─────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 and all orders when admin requests admin orders")
    void getAllOrders_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/orders/admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").isArray());
    }

    @Test
    @DisplayName("should return 403 when regular user requests admin orders")
    void getAllOrders_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/orders/admin")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated user requests admin orders")
    void getAllOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/admin"))
            .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/orders/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 when admin updates order status to completed")
    void updateStatus_asAdmin_returns200() throws Exception {
        // First create an order
        var cartItems = List.of(Map.of(
            "id", testProduct.getId(),
            "name", "Mate Test",
            "price", 1500.0,
            "quantity", 1
        ));
        var createBody = Map.of("cart", cartItems, "total", 1500.0);

        var createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody)))
            .andExpect(status().isCreated())
            .andReturn();

        String json = createResult.getResponse().getContentAsString();
        String orderId = objectMapper.readTree(json).path("order").path("id").asText();

        // Now update status
        var patchBody = Map.of("status", "completed");
        mockMvc.perform(patch("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("completed"));
    }

    @Test
    @DisplayName("should return 400 when admin sends invalid order status")
    void updateStatus_invalidStatus_returns400() throws Exception {
        var cartItems = List.of(Map.of(
            "id", testProduct.getId(), "name", "X", "price", 1500.0, "quantity", 1
        ));
        var createBody = Map.of("cart", cartItems, "total", 1500.0);
        var createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody)))
            .andReturn();

        String json = createResult.getResponse().getContentAsString();
        String orderId = objectMapper.readTree(json).path("order").path("id").asText();

        var patchBody = Map.of("status", "INVALID_STATUS");
        mockMvc.perform(patch("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 403 when regular user tries to update order status")
    void updateStatus_asUser_returns403() throws Exception {
        mockMvc.perform(patch("/api/orders/some-id")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "completed"))))
            .andExpect(status().isForbidden());
    }
}
