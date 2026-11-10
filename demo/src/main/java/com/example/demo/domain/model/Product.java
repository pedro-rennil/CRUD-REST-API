package com.example.demo.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public final class Product {

    private final UUID id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final int stockQuantity;
    private final Instant createdAt;
    /** Optimistic-lock version; {@code null} until the product has been persisted for the first time. */
    private final Long version;

    private Product(UUID id, String name, String description, BigDecimal price, int stockQuantity, Instant createdAt, Long version) {
        validate(name, description, price, stockQuantity);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name;
        this.description = description;
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.stockQuantity = stockQuantity;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    /**
     * Creates a new product with a generated identifier and timestamp.
     * The timestamp is truncated to microseconds, the precision PostgreSQL stores,
     * so the value returned on creation matches what is read back later.
     */
    public static Product create(String name, String description, BigDecimal price, int stockQuantity) {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Product(UUID.randomUUID(), name, description, price, stockQuantity, createdAt, null);
    }

    /**
     * Reconstructs a product loaded from persistence. A {@code null} version means "never persisted".
     */
    public static Product restore(UUID id, String name, String description, BigDecimal price, int stockQuantity, Instant createdAt, Long version) {
        return new Product(id, name, description, price, stockQuantity, createdAt, version);
    }

    /**
     * Returns a copy with the new data, preserving identity, creation time and version
     * (so a concurrent modification is detected when the copy is saved).
     */
    public Product update(String name, String description, BigDecimal price, int stockQuantity) {
        return new Product(this.id, name, description, price, stockQuantity, this.createdAt, this.version);
    }

    private static void validate(String name, String description, BigDecimal price, int stockQuantity) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // PostgreSQL cannot store NUL in text columns; reject it here instead of failing later with a 500.
        if (containsNul(name) || (description != null && containsNul(description))) {
            throw new IllegalArgumentException("name and description must not contain NUL characters");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity must be greater than or equal to zero");
        }
    }

    private static boolean containsNul(String text) {
        return text.indexOf('\u0000') >= 0;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
