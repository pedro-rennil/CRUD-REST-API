package com.example.demo.domain.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.domain.repository.ProductPageQuery;
import com.example.demo.domain.repository.ProductSortField;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    void shouldRoundTotalPagesUp() {
        assertEquals(3, new Page<>(List.of(1, 2), 2, 10, 21).totalPages());
        assertEquals(2, new Page<>(List.of(1), 1, 10, 11).totalPages());
        assertEquals(1, new Page<>(List.of(1), 0, 10, 10).totalPages());
    }

    @Test
    void shouldHaveNoPagesWhenThereIsNothing() {
        assertEquals(0, new Page<>(List.<Integer>of(), 0, 10, 0).totalPages());
    }

    @Test
    void shouldMapContentKeepingMetadata() {
        Page<String> mapped = new Page<>(List.of(1, 2), 3, 2, 8).map(n -> "#" + n);

        assertEquals(List.of("#1", "#2"), mapped.content());
        assertEquals(3, mapped.page());
        assertEquals(2, mapped.size());
        assertEquals(8, mapped.totalElements());
    }

    @Test
    void shouldRejectInvalidPaginationValues() {
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of(), -1, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of(), 0, 10, -1));
    }

    @Test
    void shouldRejectInvalidPageQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductPageQuery(-1, 10, ProductSortField.NAME, SortDirection.ASC));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductPageQuery(0, 0, ProductSortField.NAME, SortDirection.ASC));
        assertThrows(NullPointerException.class,
                () -> new ProductPageQuery(0, 10, null, SortDirection.ASC));
        assertThrows(NullPointerException.class,
                () -> new ProductPageQuery(0, 10, ProductSortField.NAME, null));
    }

    @Test
    void shouldRejectPageOffsetsBeyondIntegerRange() {
        // 2147483647 * 100 does not fit in an int offset; this used to end in a 500 from the persistence layer.
        assertThrows(IllegalArgumentException.class,
                () -> new ProductPageQuery(Integer.MAX_VALUE, 100, ProductSortField.NAME, SortDirection.ASC));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductPageQuery(Integer.MAX_VALUE / 100 + 1, 100, ProductSortField.NAME, SortDirection.ASC));
    }

    @Test
    void shouldAcceptTheLargestOffsetThatFitsAnInt() {
        new ProductPageQuery(Integer.MAX_VALUE, 1, ProductSortField.NAME, SortDirection.ASC);
        new ProductPageQuery(Integer.MAX_VALUE / 100, 100, ProductSortField.NAME, SortDirection.ASC);
    }
}
