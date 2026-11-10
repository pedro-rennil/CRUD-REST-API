package com.example.demo.application.usecase;

import com.example.demo.application.dto.ProductResponseDTO;
import java.util.UUID;

public interface GetProductUseCase {

    /**
     * Returns the product or throws ProductNotFoundException.
     */
    ProductResponseDTO getById(UUID id);
}
