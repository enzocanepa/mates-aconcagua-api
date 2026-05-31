package com.matesaconcahua.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.UserRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String SIGNUP_URL = "/api/auth/signup";
    private static final String LOGIN_URL  = "/api/auth/login";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("existing@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setName("Existing User");
        user.setRole(User.Role.user);
        userRepository.save(user);
    }

    // ── Signup ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 201 and token when signup is successful")
    void signup_happyPath() throws Exception {
        var body = Map.of("name", "Juan Pérez", "email", "juan@test.com", "password", "secret123");

        mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("juan@test.com"))
            .andExpect(jsonPath("$.user.role").value("user"))
            .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("should return 409 when email is already registered")
    void signup_duplicateEmail_returns409() throws Exception {
        var body = Map.of("name", "Duplicate", "email", "existing@test.com", "password", "pass123");

        mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value(containsString("email")));
    }

    @Test
    @DisplayName("should return 400 when name is missing")
    void signup_missingName_returns400() throws Exception {
        var body = Map.of("email", "new@test.com", "password", "pass123");

        mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("should return 400 when email format is invalid")
    void signup_invalidEmail_returns400() throws Exception {
        var body = Map.of("name", "Juan", "email", "not-an-email", "password", "pass123");

        mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 when password is shorter than 6 characters")
    void signup_shortPassword_returns400() throws Exception {
        var body = Map.of("name", "Juan", "email", "juan2@test.com", "password", "abc");

        mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 and token when credentials are valid")
    void login_validCredentials_returns200() throws Exception {
        var body = Map.of("email", "existing@test.com", "password", "password123");

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("existing@test.com"));
    }

    @Test
    @DisplayName("should return 401 when password is wrong")
    void login_wrongPassword_returns401() throws Exception {
        var body = Map.of("email", "existing@test.com", "password", "wrongpassword");

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("should return 401 when user does not exist")
    void login_nonExistentUser_returns401() throws Exception {
        var body = Map.of("email", "ghost@test.com", "password", "pass123");

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("should return 400 when login body is empty")
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should not return password in auth response")
    void login_doesNotExposePassword() throws Exception {
        var body = Map.of("email", "existing@test.com", "password", "password123");

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.password").doesNotExist());
    }
}
