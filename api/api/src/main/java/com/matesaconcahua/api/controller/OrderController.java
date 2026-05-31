package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<?> getMyOrders(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("orders", orderService.findByUser(userId)));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, Authentication auth) {
        // C-03: validar campos requeridos antes de castear
        if (body.get("cart") == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Falta el campo: cart"));

        String userId = (String) auth.getPrincipal();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cartItems = (List<Map<String, Object>>) body.get("cart");

        if (cartItems.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "El carrito no puede estar vacío"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("order", orderService.create(userId, cartItems)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllOrders() {
        return ResponseEntity.ok(Map.of("orders", orderService.findAll()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                          @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("order", orderService.updateStatus(id, body.get("status"))));
    }
}
