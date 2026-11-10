package com.example.demo.presentation.controller;

import com.example.demo.domain.pagination.SortDirection;
import com.example.demo.domain.repository.ProductPageQuery;
import com.example.demo.domain.repository.ProductSortField;
import com.example.demo.presentation.exception.InvalidQueryParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates the public query-string vocabulary of {@code GET /api/v1/products} into a domain query,
 * reporting every invalid parameter at once.
 *
 * <p>Defaults and the size limit are defined once here (as strings, so annotations can use them); the
 * controller references them so the OpenAPI document matches the validation actually applied.
 */
final class ProductListQueryParser {

    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_SIZE = "20";
    static final String MAX_SIZE_VALUE = "100";
    static final int MAX_SIZE = Integer.parseInt(MAX_SIZE_VALUE);
    static final String DEFAULT_SORT_BY = "createdAt";
    static final String DEFAULT_DIRECTION = "asc";

    private static final Map<String, ProductSortField> SORT_FIELDS = Map.of(
            "name", ProductSortField.NAME,
            "price", ProductSortField.PRICE,
            "stockQuantity", ProductSortField.STOCK_QUANTITY,
            "createdAt", ProductSortField.CREATED_AT);

    private ProductListQueryParser() {
    }

    static ProductPageQuery parse(int page, int size, String sortBy, String direction) {
        List<String> errors = new ArrayList<>();

        if (page < 0) {
            errors.add("page: must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add("size: must be between 1 and " + MAX_SIZE);
        }

        if (page >= 0 && size >= 1 && size <= MAX_SIZE && (long) page * size > Integer.MAX_VALUE) {
            errors.add("page: too large for the requested size (page * size must not exceed " + Integer.MAX_VALUE + ")");
        }

        ProductSortField sortField = SORT_FIELDS.get(sortBy);
        if (sortField == null) {
            errors.add("sortBy: must be one of " + String.join(", ", SORT_FIELDS.keySet().stream().sorted().toList()));
        }

        SortDirection sortDirection = parseDirection(direction);
        if (sortDirection == null) {
            errors.add("direction: must be one of asc, desc");
        }

        if (!errors.isEmpty()) {
            throw new InvalidQueryParameterException(errors);
        }
        return new ProductPageQuery(page, size, sortField, sortDirection);
    }

    private static SortDirection parseDirection(String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> SortDirection.ASC;
            case "desc" -> SortDirection.DESC;
            default -> null;
        };
    }
}
