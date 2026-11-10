package com.example.demo.infrastructure.persistence;

import com.example.demo.domain.model.Product;
import com.example.demo.domain.pagination.Page;
import com.example.demo.domain.pagination.SortDirection;
import com.example.demo.domain.repository.ProductPageQuery;
import com.example.demo.domain.repository.ProductRepository;
import com.example.demo.domain.repository.ProductSortField;
import com.example.demo.infrastructure.persistence.entity.ProductEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public Page<Product> findAll(ProductPageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), toSort(query));
        var result = repository.findAll(pageable);

        return new Page<>(
                result.getContent().stream().map(this::toDomain).toList(),
                query.page(),
                query.size(),
                result.getTotalElements());
    }

    /**
     * Orders by the requested field and always by id afterwards: without a unique tie-breaker the
     * database may return rows with equal values in a different order per query, so consecutive pages
     * could repeat or skip items.
     */
    private Sort toSort(ProductPageQuery query) {
        Sort.Direction direction = query.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, toProperty(query.sortField())).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String toProperty(ProductSortField field) {
        return switch (field) {
            case NAME -> "name";
            case PRICE -> "price";
            case STOCK_QUANTITY -> "stockQuantity";
            case CREATED_AT -> "createdAt";
        };
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
