package com.example.demo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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

        List<Product> all = adapter.findAll();

        assertThat(all).extracting(Product::getName).containsExactlyInAnyOrder("Laptop", "Mouse");
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
