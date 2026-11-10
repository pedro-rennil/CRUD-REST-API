package com.example.demo.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void shouldTruncateCreationTimestampToMicroseconds() {
        Product product = Product.create("Laptop", null, new BigDecimal("10.00"), 1);

        assertEquals(product.getCreatedAt().truncatedTo(ChronoUnit.MICROS), product.getCreatedAt());
    }

    @Test
    void shouldKeepIdentityAndCreationDateWhenUpdating() {
        Product product = Product.create("Laptop", "Gaming", new BigDecimal("10.00"), 1);

        Product updated = product.update("Desktop", null, new BigDecimal("5.00"), 2);

        assertEquals(product.getId(), updated.getId());
        assertEquals(product.getCreatedAt(), updated.getCreatedAt());
        assertEquals("Desktop", updated.getName());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> Product.create(" ", null, new BigDecimal("10.00"), 1));
    }

    @Test
    void shouldRejectNonPositivePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("Laptop", null, BigDecimal.ZERO, 1));
    }

    @Test
    void shouldRejectNegativeStock() {
        assertThrows(IllegalArgumentException.class,
                () -> Product.restore(UUID.randomUUID(), "Laptop", null, new BigDecimal("10.00"), -1, Instant.now(), 0L));
    }

    @Test
    void shouldStartWithoutVersionAndKeepItWhenUpdating() {
        Product created = Product.create("Laptop", null, new BigDecimal("10.00"), 1);
        assertNull(created.getVersion());

        Product loaded = Product.restore(created.getId(), "Laptop", null, new BigDecimal("10.00"), 1, created.getCreatedAt(), 3L);

        assertEquals(3L, loaded.update("Desktop", null, new BigDecimal("5.00"), 2).getVersion());
    }

    @Test
    void shouldRejectInvalidDataOnUpdate() {
        Product product = Product.create("Laptop", null, new BigDecimal("10.00"), 1);

        assertThrows(IllegalArgumentException.class,
                () -> product.update("Laptop", null, new BigDecimal("-1"), 1));
    }
}
