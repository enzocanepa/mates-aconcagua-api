package com.matesaconcahua.api.service;

import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.Review;
import com.matesaconcahua.api.entity.User;
import com.matesaconcahua.api.exception.BusinessException;
import com.matesaconcahua.api.exception.ResourceNotFoundException;
import com.matesaconcahua.api.repository.ProductRepository;
import com.matesaconcahua.api.repository.ReviewRepository;
import com.matesaconcahua.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository  reviewRepository;
    private final UserRepository    userRepository;
    private final ProductRepository productRepository;

    public List<Review> findByProduct(Integer productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    @Transactional
    public Review create(String userId, Integer productId, Integer rating, String comment) {
        // A-05: validar rango de rating
        if (rating < 1 || rating > 5)
            throw new BusinessException("El rating debe estar entre 1 y 5");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(rating);
        review.setComment(comment);
        return reviewRepository.save(review);
    }
}
