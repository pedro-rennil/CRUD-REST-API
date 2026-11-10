package com.example.demo.domain.repository;

import com.example.demo.domain.model.Product;
import com.example.demo.domain.pagination.Page;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    /**
     * Persists a product and returns the stored version.
     */
    Product save(Product product);

    Optional<Product> findById(UUID id);

    /**
     * Loads one page of products. Ordering is deterministic: ties on the requested field are broken by id.
     */
    Page<Product> findAll(ProductPageQuery query);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
