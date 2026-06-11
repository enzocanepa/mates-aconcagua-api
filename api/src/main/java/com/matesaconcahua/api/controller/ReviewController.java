package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.entity.Review;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.repository.ReviewRepository;
import com.matesaconcahua.api.repository.UserRepository;
import com.matesaconcahua.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository  reviewRepository;
    private final UserRepository    userRepository;
    private final ProductRepository productRepository;

    @GetMapping("/{productId}")
    public ResponseEntity<?> getByProduct(@PathVariable Integer productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication auth) {
        String userId   = (String) auth.getPrincipal();
        User user       = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));

        Integer productId = ((Number) body.get("productId")).intValue();
        Product product   = productRepository.findById(productId).orElse(null);
        if (product == null) return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(((Number) body.get("rating")).intValue());
        review.setComment((String) body.get("comment"));

        Review saved = reviewRepository.save(review);
        return ResponseEntity.ok(Map.of("review", saved));
    }
}
