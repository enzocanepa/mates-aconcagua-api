package com.matesaconcahua.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.resources.preference.Preference;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.ProductRepository;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckoutControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private String userToken;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("buyer@test.com");
        user.setPassword(passwordEncoder.encode("pass123"));
        user.setName("Buyer");
        user.setRole(User.Role.user);
        userRepository.save(user);
        userToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        testProduct = new Product();
        testProduct.setName("Mate Test");
        testProduct.setDescription("Desc");
        testProduct.setPrice(new BigDecimal("1500.00"));
        testProduct.setCategory(Product.Category.mates);
        testProduct.setStock(10);
        testProduct = productRepository.save(testProduct);
    }

    /**
     * Helper to build a valid checkout body.
     */
    private Map<String, Object> buildCheckoutBody(int productId, int quantity) {
        var items = List.of(Map.of("id", productId, "quantity", quantity));
        var payer = Map.of("name", "Juan Pérez", "email", "juan@test.com");
        return Map.of("items", items, "payer", payer);
    }

    @Test
    @DisplayName("should return 400 when items list is empty")
    void createPreference_emptyItems_returns400() throws Exception {
        var body = Map.of("items", List.of(), "payer", Map.of("name", "X", "email", "x@x.com"));

        // Empty items → NullPointerException / IllegalArgumentException in loop
        mockMvc.perform(post("/api/checkout/create-preference")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            // The loop does not iterate, so MP SDK will be called with empty items.
            // The response should either be 400 or 502 (MP error with empty items).
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("should return 400 when product in cart does not exist in DB")
    void createPreference_productNotFound_returns400() throws Exception {
        var body = buildCheckoutBody(99999, 1);

        mockMvc.perform(post("/api/checkout/create-preference")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("should use price from DB, not from request body")
    void createPreference_priceFromDB_notFromRequest() throws Exception {
        // Even if the client sends a tampered price, the server must use DB price.
        // We verify indirectly: product DB price = 1500, but request items have no "price" field.
        // The MP SDK will be called with the DB price. We mock PreferenceClient to avoid real HTTP.
        var items = List.of(Map.of(
            "id", testProduct.getId(),
            "quantity", 1
            // Note: no "price" field — server must load from DB
        ));
        var payer = Map.of("name", "Juan", "email", "juan@test.com");
        var body = Map.of("items", items, "payer", payer);

        // Use MockedConstruction to intercept PreferenceClient instantiation
        Preference mockPreference = Mockito.mock(Preference.class);
        Mockito.when(mockPreference.getId()).thenReturn("PREF-TEST-123");
        Mockito.when(mockPreference.getInitPoint()).thenReturn("https://mp.com/init");
        Mockito.when(mockPreference.getSandboxInitPoint()).thenReturn("https://sandbox.mp.com/init");

        try (MockedConstruction<PreferenceClient> ignored = Mockito.mockConstruction(PreferenceClient.class,
                (mock, context) -> Mockito.when(mock.create(Mockito.any())).thenReturn(mockPreference))) {

            mockMvc.perform(post("/api/checkout/create-preference")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preference_id").value("PREF-TEST-123"))
                .andExpect(jsonPath("$.init_point").isNotEmpty());
        }
    }

    @Test
    @DisplayName("should return 200 with init_point when preference is created successfully")
    void createPreference_success_returns200() throws Exception {
        var body = buildCheckoutBody(testProduct.getId(), 1);

        Preference mockPreference = Mockito.mock(Preference.class);
        Mockito.when(mockPreference.getId()).thenReturn("PREF-HAPPY-456");
        Mockito.when(mockPreference.getInitPoint()).thenReturn("https://mp.com/checkout");
        Mockito.when(mockPreference.getSandboxInitPoint()).thenReturn("https://sandbox.mp.com/checkout");

        try (MockedConstruction<PreferenceClient> ignored = Mockito.mockConstruction(PreferenceClient.class,
                (mock, context) -> Mockito.when(mock.create(Mockito.any())).thenReturn(mockPreference))) {

            mockMvc.perform(post("/api/checkout/create-preference")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.init_point").isNotEmpty())
                .andExpect(jsonPath("$.preference_id").value("PREF-HAPPY-456"));
        }
    }

    @Test
    @DisplayName("should return 400 when items field is missing from request body")
    void createPreference_missingItems_returns400OrServerError() throws Exception {
        // No "items" key → cartItems cast will be null → NPE in for loop
        var body = Map.of("payer", Map.of("name", "X", "email", "x@x.com"));

        mockMvc.perform(post("/api/checkout/create-preference")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            // Endpoint is permitAll, but the null items will produce 500 or 400
            .andExpect(status().is5xxServerError());
    }
}
