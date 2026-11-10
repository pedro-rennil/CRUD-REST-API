package com.example.demo.domain.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One slice of a larger result set, plus the information needed to navigate it.
 *
 * @param content       the items in this page, already in the requested order
 * @param page          zero-based index of this page
 * @param size          requested page size (the last page may hold fewer items)
 * @param totalElements number of items across all pages
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        Objects.requireNonNull(content, "content must not be null");
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be greater than or equal to zero");
        }
        content = List.copyOf(content);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
