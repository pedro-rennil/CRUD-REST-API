package com.example.demo.application.dto;

import com.example.demo.domain.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Envelope for paginated responses.
 */
@Schema(description = "A page of results with the metadata needed to navigate the full collection")
public record PageResponseDTO<T>(
        @Schema(description = "Items in this page, in the requested order") List<T> content,
        @Schema(description = "Zero-based index of this page", example = "0") int page,
        @Schema(description = "Requested page size; the last page may contain fewer items", example = "20") int size,
        @Schema(description = "Total number of items across all pages", example = "42") long totalElements,
        @Schema(description = "Total number of pages", example = "3") int totalPages) {

    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(
                page.content(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
