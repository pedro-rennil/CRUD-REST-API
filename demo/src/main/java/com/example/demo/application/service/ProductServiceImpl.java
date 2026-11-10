package com.example.demo.application.service;

import com.example.demo.application.dto.ProductRequestDTO;
import com.example.demo.application.dto.ProductResponseDTO;
import com.example.demo.application.usecase.CreateProductUseCase;
import com.example.demo.application.usecase.DeleteProductUseCase;
import com.example.demo.application.usecase.GetProductUseCase;
import com.example.demo.application.usecase.ListProductsUseCase;
import com.example.demo.application.usecase.UpdateProductUseCase;
import com.example.demo.domain.exception.ProductNotFoundException;
import com.example.demo.domain.model.Product;
import com.example.demo.domain.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each use case runs in a single transaction, so read-then-write sequences (update, delete)
 * are atomic. Reads are marked read-only.
 */
@Transactional
public class ProductServiceImpl implements CreateProductUseCase, GetProductUseCase,
        ListProductsUseCase, UpdateProductUseCase, DeleteProductUseCase {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductResponseDTO create(ProductRequestDTO request) {
        Product product = Product.create(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity());

        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getById(UUID id) {
        return productRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> list() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProductResponseDTO update(UUID id, ProductRequestDTO request) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        Product updated = existing.update(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity());

        return toResponse(productRepository.save(updated));
    }

    @Override
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    private ProductResponseDTO toResponse(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCreatedAt());
    }
}
