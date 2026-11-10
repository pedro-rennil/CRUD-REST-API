package com.example.demo.application.usecase;

import java.util.UUID;

public interface DeleteProductUseCase {

    /**
     * Deletes the product or throws ProductNotFoundException.
     */
    void delete(UUID id);
}
