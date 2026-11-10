package com.example.demo.domain.repository;

import com.example.demo.domain.pagination.SortDirection;
import java.util.Objects;

/**
 * Which page of products to load and in which order.
 *
 * @param page      zero-based page index
 * @param size      number of products per page
 * @param sortField attribute to order by; ties are always broken by id so pages never overlap
 * @param direction ascending or descending
 */
public record ProductPageQuery(int page, int size, ProductSortField sortField, SortDirection direction) {

    public ProductPageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        Objects.requireNonNull(sortField, "sortField must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
