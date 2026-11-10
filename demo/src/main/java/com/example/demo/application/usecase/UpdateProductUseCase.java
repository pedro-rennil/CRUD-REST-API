package com.example.demo.application.usecase;

import com.example.demo.application.dto.ProductRequestDTO;
import com.example.demo.application.dto.ProductResponseDTO;
import java.util.UUID;

public interface UpdateProductUseCase {

    /**
     * Replaces the product data or throws ProductNotFoundException.
     */
    ProductResponseDTO update(UUID id, ProductRequestDTO request);
}
