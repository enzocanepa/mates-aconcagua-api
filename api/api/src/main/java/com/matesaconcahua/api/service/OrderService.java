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
    public Order create(String userId, List<Map<String, Object>> cartItems, double totalRaw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        validateStock(cartItems);

        Order order = new Order();
        order.setUser(user);
        order.setTotal(BigDecimal.valueOf(totalRaw));
        order.setStatus(Order.Status.pending);

        List<OrderItem> items = buildItems(order, cartItems);
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

    private void validateStock(List<Map<String, Object>> cartItems) {
        for (Map<String, Object> ci : cartItems) {
            Integer productId = ((Number) ci.get("id")).intValue();
            int qty           = ((Number) ci.get("quantity")).intValue();
            Product product   = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));
            if (product.getStock() != null && product.getStock() < qty)
                throw new BusinessException("Stock insuficiente para: " + product.getName());
        }
    }

    private List<OrderItem> buildItems(Order order, List<Map<String, Object>> cartItems) {
        List<OrderItem> items = new ArrayList<>();
        for (Map<String, Object> ci : cartItems) {
            Integer productId = ((Number) ci.get("id")).intValue();
            int qty           = ((Number) ci.get("quantity")).intValue();
            Product product   = productRepository.findById(productId).orElseThrow();

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
        return items;
    }
}
