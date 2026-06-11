package com.matesaconcahua.api.controller;

import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.ProductImage;
import com.matesaconcahua.api.entity.ProductVariant;
import com.matesaconcahua.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<?> getAll() {
        List<Product> products = productRepository.findAll();
        return ResponseEntity.ok(Map.of("products", products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Integer id) {
        return productRepository.findById(id)
                .map(p -> ResponseEntity.ok(Map.of("product", p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody Product product) {
        if (product.getImages() != null)
            product.getImages().forEach(img -> img.setProduct(product));
        if (product.getVariants() != null)
            product.getVariants().forEach(v -> v.setProduct(product));
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(Map.of("product", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Product body) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(body.getName());
            existing.setDescription(body.getDescription());
            existing.setFullDescription(body.getFullDescription());
            existing.setPrice(body.getPrice());
            existing.setImage(body.getImage());
            existing.setCategory(body.getCategory());
            existing.setStock(body.getStock());

            existing.getImages().clear();
            if (body.getImages() != null) {
                for (ProductImage img : body.getImages()) {
                    img.setProduct(existing);
                    existing.getImages().add(img);
                }
            }

            existing.getVariants().clear();
            if (body.getVariants() != null) {
                for (ProductVariant v : body.getVariants()) {
                    v.setProduct(existing);
                    existing.getVariants().add(v);
                }
            }

            Product saved = productRepository.save(existing);
            return ResponseEntity.ok(Map.of("product", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
