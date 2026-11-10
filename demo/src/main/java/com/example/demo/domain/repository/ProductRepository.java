package com.example.demo.domain.repository;

import com.example.demo.domain.model.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    /**
     * Persists a product and returns the stored version.
     */
    Product save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
