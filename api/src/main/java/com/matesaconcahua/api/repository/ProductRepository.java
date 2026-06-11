package com.matesaconcahua.api.repository;

import com.matesaconcahua.api.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategory(Product.Category category);
}
