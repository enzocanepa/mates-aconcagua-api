package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.entity.*;
import com.matesaconcahua.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository    orderRepository;
    private final UserRepository     userRepository;
    private final ProductRepository  productRepository;

    @GetMapping
    public ResponseEntity<?> getMyOrders(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(Map.of("orders", orders));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Usuario no encontrado"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cartItems = (List<Map<String, Object>>) body.get("cart");
        double totalRaw = ((Number) body.get("total")).doubleValue();

        // Verificar stock antes de crear la orden
        for (Map<String, Object> ci : cartItems) {
            Integer productId = ((Number) ci.get("id")).intValue();
            int qty = ((Number) ci.get("quantity")).intValue();
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Producto no encontrado: " + productId));
            if (product.getStock() != null && product.getStock() < qty)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Stock insuficiente para: " + product.getName()));
        }

        Order order = new Order();
        order.setUser(user);
        order.setTotal(BigDecimal.valueOf(totalRaw));
        order.setStatus(Order.Status.completed);

        List<OrderItem> items = new ArrayList<>();
        for (Map<String, Object> ci : cartItems) {
            Integer productId = ((Number) ci.get("id")).intValue();
            int qty = ((Number) ci.get("quantity")).intValue();

            Product product = productRepository.findById(productId).orElseThrow();

            // Descontar stock
            if (product.getStock() != null)
                product.setStock(product.getStock() - qty);
            productRepository.save(product);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(qty);
            item.setUnitPrice(BigDecimal.valueOf(((Number) ci.get("price")).doubleValue()));
            items.add(item);
        }

        order.setItems(items);
        Order saved = orderRepository.save(order);
        return ResponseEntity.ok(Map.of("order", saved));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllOrders() {
        return ResponseEntity.ok(Map.of("orders", orderRepository.findAll()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                          @RequestBody Map<String, String> body) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(Order.Status.valueOf(body.get("status")));
            return ResponseEntity.ok(Map.of("order", orderRepository.save(order)));
        }).orElse(ResponseEntity.notFound().build());
    }
}
