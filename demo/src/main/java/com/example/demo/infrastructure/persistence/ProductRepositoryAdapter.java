package com.example.demo.infrastructure.persistence;

import com.example.demo.domain.model.Product;
import com.example.demo.domain.repository.ProductRepository;
import com.example.demo.infrastructure.persistence.entity.ProductEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryAdapter implements ProductRepository {

    private final SpringDataProductRepository repository;

    public ProductRepositoryAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = toEntity(product);
        ProductEntity savedEntity = repository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private ProductEntity toEntity(Product product) {
        return new ProductEntity(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCreatedAt(),
                product.getVersion());
    }

    private Product toDomain(ProductEntity entity) {
        return Product.restore(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getStockQuantity(),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
