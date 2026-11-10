package com.example.demo.application.usecase;

import com.example.demo.application.dto.ProductRequestDTO;
import com.example.demo.application.dto.ProductResponseDTO;

public interface CreateProductUseCase {

    /**
     * Creates a product from the incoming request.
     */
    ProductResponseDTO create(ProductRequestDTO request);
}
