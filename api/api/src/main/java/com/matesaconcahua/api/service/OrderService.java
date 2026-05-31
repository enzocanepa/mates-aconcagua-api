package com.matesaconcahua.api.service;

import com.matesaconcahua.api.entity.Order;
import com.matesaconcahua.api.entity.OrderItem;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.exception.BusinessException;
import com.matesaconcahua.api.exception.ResourceNotFoundException;
import com.matesaconcahua.api.repository.OrderRepository;
import com.matesaconcahua.api.repository.ProductRepository;
import com.matesaconcahua.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository   orderRepository;
    private final UserRepository    userRepository;
    private final ProductRepository productRepository;

    public List<Order> findByUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden", id));
    }

    @Transactional
    public Order create(String userId, List<Map<String, Object>> cartItems) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(Order.Status.pending);

        // C-01 + C-02: precios desde DB con bloqueo pesimista, validación y descuento
        // en una sola pasada para evitar race conditions
        List<OrderItem> items = new ArrayList<>();
        BigDecimal serverTotal = BigDecimal.ZERO;

        for (Map<String, Object> ci : cartItems) {
            Integer productId = ((Number) ci.get("id")).intValue();
            int qty           = ((Number) ci.get("quantity")).intValue();

            // Bloqueo pesimista: impide que otra transacción lea/escriba el mismo producto
            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));

            if (product.getStock() != null && product.getStock() < qty)
                throw new BusinessException("Stock insuficiente para: " + product.getName());

            if (product.getStock() != null)
                product.setStock(product.getStock() - qty);
            productRepository.save(product);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(qty);
            item.setUnitPrice(product.getPrice()); // C-01: precio siempre desde la DB
            items.add(item);

            serverTotal = serverTotal.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }

        order.setTotal(serverTotal); // A-02: total calculado en el servidor
        order.setItems(items);

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateStatus(String id, String status) {
        Order order = findById(id);
        try {
            order.setStatus(Order.Status.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Estado inválido: " + status);
        }
        return orderRepository.save(order);
    }
}
