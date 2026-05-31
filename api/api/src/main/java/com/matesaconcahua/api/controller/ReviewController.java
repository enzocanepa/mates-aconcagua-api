package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{productId}")
    public ResponseEntity<?> getByProduct(@PathVariable Integer productId) {
        return ResponseEntity.ok(Map.of("reviews", reviewService.findByProduct(productId)));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication auth) {
        // C-04: validar campos requeridos antes de castear para evitar NPE
        if (body.get("productId") == null || body.get("rating") == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan campos: productId, rating"));

        String userId     = (String) auth.getPrincipal();
        Integer productId = ((Number) body.get("productId")).intValue();
        Integer rating    = ((Number) body.get("rating")).intValue();
        String comment    = (String) body.get("comment");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("review", reviewService.create(userId, productId, rating, comment)));
    }
}
