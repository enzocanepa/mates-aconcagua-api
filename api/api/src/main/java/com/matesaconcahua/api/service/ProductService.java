package com.matesaconcahua.api.service;

import com.matesaconcahua.api.entity.Product;
import com.matesaconcahua.api.entity.ProductImage;
import com.matesaconcahua.api.entity.ProductVariant;
import com.matesaconcahua.api.exception.ResourceNotFoundException;
import com.matesaconcahua.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> findAll() {
        return productRepository.findAllWithDetails();
    }

    public Product findById(Integer id) {
        return productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    @Transactional
    public Product create(Product product) {
        if (product.getImages() != null)
            product.getImages().forEach(img -> img.setProduct(product));
        if (product.getVariants() != null)
            product.getVariants().forEach(v -> v.setProduct(product));
        Product saved = productRepository.save(product);
        // Recargar con JOIN FETCH para evitar LazyInitializationException al serializar
        // (open-in-view=false cierra la sesión antes de que Jackson serialice las colecciones)
        return productRepository.findByIdWithDetails(saved.getId()).orElse(saved);
    }

    @Transactional
    public Product update(Integer id, Product body) {
        Product existing = findById(id);

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

        return productRepository.save(existing);
    }

    @Transactional
    public void delete(Integer id) {
        if (!productRepository.existsById(id))
            throw new ResourceNotFoundException("Producto", id);
        productRepository.deleteById(id);
    }
}
