package com.matesaconcahua.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matesaconcahua.api.entity.Cart;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.repository.CartRepository;
import com.matesaconcahua.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ObjectMapper   objectMapper;

    @GetMapping
    public ResponseEntity<?> getCart(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        String itemsJson = cart != null ? cart.getItems() : "[]";
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> items = objectMapper.readValue(itemsJson, java.util.List.class);
            return ResponseEntity.ok(Map.of("cart", items));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("cart", new java.util.ArrayList<>()));
        }
    }

    @PostMapping
    public ResponseEntity<?> saveCart(@RequestBody Map<String, Object> body, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));

        try {
            Object itemsObj  = body.get("items");
            String itemsJson = itemsObj != null
                    ? objectMapper.writeValueAsString(itemsObj)
                    : "[]";

            Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
                Cart c = new Cart();
                c.setUser(user);
                return c;
            });
            cart.setItems(itemsJson);
            cartRepository.save(cart);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al guardar el carrito"));
        }
    }
}
