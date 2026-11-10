package com.example.demo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.domain.model.Product;
import com.example.demo.domain.pagination.Page;
import com.example.demo.domain.pagination.SortDirection;
import com.example.demo.domain.repository.ProductPageQuery;
import com.example.demo.domain.repository.ProductSortField;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the persistence adapter against a real PostgreSQL (same major version as docker-compose),
 * with the schema created by the Flyway migrations and validated by Hibernate (ddl-auto: validate).
 *
 * Transactions are disabled for the test methods: each adapter call commits on its own, so reads
 * really hit the database instead of the persistence context's first-level cache.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductRepositoryAdapter.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductRepositoryAdapterIT {

    // PostgreSQL stores timestamps with microsecond precision; Instant.now() may carry nanoseconds.
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:15:30.123456Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProductRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE products");
    }

    @Test
    void shouldPersistAllFieldsWhenSavingDomainProduct() {
        Product product = product("Laptop", "Gaming laptop", "1499.99", 10);

        Product saved = adapter.save(product);

        assertThat(saved.getId()).isEqualTo(product.getId());
        assertThat(saved.getName()).isEqualTo("Laptop");

        var row = jdbc.queryForMap("SELECT * FROM products WHERE id = ?", product.getId());
        assertThat(row.get("name")).isEqualTo("Laptop");
        assertThat(row.get("description")).isEqualTo("Gaming laptop");
        assertThat((BigDecimal) row.get("price")).isEqualByComparingTo("1499.99");
        assertThat(row.get("stock_quantity")).isEqualTo(10);
        OffsetDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM products WHERE id = ?", OffsetDateTime.class, product.getId());
        assertThat(createdAt.toInstant()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldPersistNullDescription() {
        Product product = product("Mouse", null, "25.00", 3);

        adapter.save(product);

        Optional<Product> found = adapter.findById(product.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isNull();
    }

    @Test
    void shouldMapRowToDomainWhenFindingById() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO products (id, name, description, price, stock_quantity, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, "Keyboard", "Mechanical", new BigDecimal("349.90"), 7, OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));

        Optional<Product> found = adapter.findById(id);

        assertThat(found).isPresent();
        Product product = found.get();
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getName()).isEqualTo("Keyboard");
        assertThat(product.getDescription()).isEqualTo("Mechanical");
        assertThat(product.getPrice()).isEqualByComparingTo("349.90");
        assertThat(product.getStockQuantity()).isEqualTo(7);
        assertThat(product.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldReturnEmptyWhenIdDoesNotExist() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldReturnAllProducts() {
        adapter.save(product("Laptop", "Gaming laptop", "1499.99", 10));
        adapter.save(product("Mouse", null, "25.00", 3));

        Page<Product> all = adapter.findAll(query(0, 10, ProductSortField.NAME, SortDirection.ASC));

        assertThat(all.content()).extracting(Product::getName).containsExactly("Laptop", "Mouse");
        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(all.totalPages()).isEqualTo(1);
    }

    @Test
    void shouldSplitResultsIntoPagesWithTotals() {
        saveProducts("E", "C", "A", "D", "B");

        Page<Product> first = adapter.findAll(query(0, 2, ProductSortField.NAME, SortDirection.ASC));
        Page<Product> second = adapter.findAll(query(1, 2, ProductSortField.NAME, SortDirection.ASC));
        Page<Product> last = adapter.findAll(query(2, 2, ProductSortField.NAME, SortDirection.ASC));

        assertThat(first.content()).extracting(Product::getName).containsExactly("A", "B");
        assertThat(second.content()).extracting(Product::getName).containsExactly("C", "D");
        assertThat(last.content()).extracting(Product::getName).containsExactly("E");
        assertThat(last.page()).isEqualTo(2);
        assertThat(last.size()).isEqualTo(2);
        assertThat(last.totalElements()).isEqualTo(5);
        assertThat(last.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyContentWithRealTotalsWhenPageIsBeyondTheLastOne() {
        saveProducts("A", "B", "C");

        Page<Product> page = adapter.findAll(query(5, 2, ProductSortField.NAME, SortDirection.ASC));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyPageWhenThereAreNoProducts() {
        Page<Product> page = adapter.findAll(query(0, 20, ProductSortField.CREATED_AT, SortDirection.ASC));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldSortByEachSupportedFieldInBothDirections() {
        adapter.save(product("Bravo", null, "20.00", 5));
        adapter.save(product("Alpha", null, "30.00", 1));
        adapter.save(product("Charlie", null, "10.00", 9));

        assertThat(names(ProductSortField.NAME, SortDirection.ASC)).containsExactly("Alpha", "Bravo", "Charlie");
        assertThat(names(ProductSortField.NAME, SortDirection.DESC)).containsExactly("Charlie", "Bravo", "Alpha");
        assertThat(names(ProductSortField.PRICE, SortDirection.ASC)).containsExactly("Charlie", "Bravo", "Alpha");
        assertThat(names(ProductSortField.PRICE, SortDirection.DESC)).containsExactly("Alpha", "Bravo", "Charlie");
        assertThat(names(ProductSortField.STOCK_QUANTITY, SortDirection.ASC)).containsExactly("Alpha", "Bravo", "Charlie");
        assertThat(names(ProductSortField.STOCK_QUANTITY, SortDirection.DESC)).containsExactly("Charlie", "Bravo", "Alpha");
    }

    @Test
    void shouldSortByCreationDate() {
        adapter.save(Product.restore(UUID.randomUUID(), "Old", null, new BigDecimal("1.00"), 1, CREATED_AT, null));
        adapter.save(Product.restore(UUID.randomUUID(), "New", null, new BigDecimal("1.00"), 1, CREATED_AT.plusSeconds(60), null));

        assertThat(names(ProductSortField.CREATED_AT, SortDirection.ASC)).containsExactly("Old", "New");
        assertThat(names(ProductSortField.CREATED_AT, SortDirection.DESC)).containsExactly("New", "Old");
    }

    @Test
    void shouldNeverRepeatOrSkipProductsWhenTheSortedValueIsTied() {
        // Same price and creation date everywhere: only the id tie-breaker keeps pages consistent.
        saveProducts("A", "B", "C", "D", "E", "F", "G");

        List<UUID> collected = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            adapter.findAll(query(page, 2, ProductSortField.PRICE, SortDirection.ASC))
                    .content().forEach(product -> collected.add(product.getId()));
        }

        // Compare with the database's own id order: java.util.UUID.compareTo is signed, PostgreSQL's is not.
        List<UUID> idsInDatabaseOrder = jdbc.queryForList("SELECT id FROM products ORDER BY id", UUID.class);
        assertThat(collected).hasSize(7).doesNotHaveDuplicates().containsExactlyElementsOf(idsInDatabaseOrder);
    }

    @Test
    void shouldRejectRowsThatViolateBusinessRulesEvenWhenInsertedDirectlyIntoTheDatabase() {
        assertThatThrownBy(() -> insertRow("Laptop", "0.00", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_price_positive");
        assertThatThrownBy(() -> insertRow("Laptop", "-5.00", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_price_positive");
        assertThatThrownBy(() -> insertRow("Laptop", "10.00", -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_stock_non_negative");
        assertThatThrownBy(() -> insertRow("   ", "10.00", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_name_not_blank");
        assertThatThrownBy(() -> insertRow("", "10.00", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_name_not_blank");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isZero();
    }

    @Test
    void shouldRejectUpdatingAnExistingRowToAnInvalidValue() {
        Product saved = adapter.save(product("Laptop", null, "10.00", 1));

        assertThatThrownBy(() -> jdbc.update("UPDATE products SET stock_quantity = -1 WHERE id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_stock_non_negative");
    }

    @Test
    void shouldAcceptTheSmallestValidValuesAtTheConstraintBoundaries() {
        // stock 0 and price 0.01 are the closest valid values to the CHECK limits.
        insertRow("A", "0.01", 0);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(1);
    }

    private void insertRow(String name, String price, int stock) {
        jdbc.update(
                "INSERT INTO products (id, name, description, price, stock_quantity, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), name, null, new BigDecimal(price), stock, OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
    }

    private List<String> names(ProductSortField field, SortDirection direction) {
        return adapter.findAll(query(0, 10, field, direction)).content().stream().map(Product::getName).toList();
    }

    private void saveProducts(String... names) {
        for (String name : names) {
            adapter.save(product(name, null, "10.00", 1));
        }
    }

    private static ProductPageQuery query(int page, int size, ProductSortField field, SortDirection direction) {
        return new ProductPageQuery(page, size, field, direction);
    }

    @Test
    void shouldUpdateExistingRowWhenSavingProductWithSameId() {
        Product original = adapter.save(product("Laptop", "Gaming laptop", "1499.99", 10));

        adapter.save(original.update("Desktop", "Workstation", new BigDecimal("999.00"), 4));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(1);
        Product reloaded = adapter.findById(original.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Desktop");
        assertThat(reloaded.getPrice()).isEqualByComparingTo("999.00");
        assertThat(reloaded.getStockQuantity()).isEqualTo(4);
        assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldStartVersionAtZeroAndIncrementItOnEachUpdate() {
        Product saved = adapter.save(product("Laptop", null, "1499.99", 10));
        assertThat(saved.getVersion()).isZero();

        Product updated = adapter.save(saved.update("Laptop", null, new BigDecimal("1399.99"), 10));

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldRejectUpdateBasedOnStaleVersion() {
        Product saved = adapter.save(product("Laptop", null, "1499.99", 10));
        adapter.save(saved.update("Laptop", null, new BigDecimal("1399.99"), 10)); // concurrent winner

        // `saved` still carries version 0, so this write would silently discard the update above.
        assertThatThrownBy(() -> adapter.save(saved.update("Laptop", null, new BigDecimal("1.00"), 10)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(adapter.findById(saved.getId()).orElseThrow().getPrice()).isEqualByComparingTo("1399.99");
    }

    @Test
    void shouldRoundPriceToTwoDecimalPlaces() {
        Product product = product("Cable", null, "10.5", 1);

        adapter.save(product);

        BigDecimal stored = jdbc.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class, product.getId());
        assertThat(stored).hasScaleOf(2).isEqualByComparingTo("10.50");
    }

    @Test
    void shouldReportExistence() {
        Product product = product("Laptop", null, "1499.99", 10);
        adapter.save(product);

        assertThat(adapter.existsById(product.getId())).isTrue();
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldPhysicallyDeleteRow() {
        Product product = product("Laptop", null, "1499.99", 10);
        Product other = product("Mouse", null, "25.00", 3);
        adapter.save(product);
        adapter.save(other);

        adapter.deleteById(product.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ?", Integer.class, product.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(1);
    }

    private static Product product(String name, String description, String price, int stock) {
        // version == null marks a product that was never persisted (plain INSERT).
        return Product.restore(UUID.randomUUID(), name, description, new BigDecimal(price), stock, CREATED_AT, null);
    }
}
