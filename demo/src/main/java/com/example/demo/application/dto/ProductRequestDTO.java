package com.example.demo.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequestDTO(
        @NotBlank(message = "Name is required") @Size(max = 255, message = "Name must have at most 255 characters") String name,
        @Size(max = 255, message = "Description must have at most 255 characters") String description,
        // Matches the NUMERIC(19,2) column: anything wider would fail at the database with a 500.
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Price must have at most 17 integer digits and 2 decimal places") BigDecimal price,
        @NotNull(message = "Stock quantity is required") @Min(value = 0, message = "Stock quantity cannot be negative") Integer stockQuantity) {
}
