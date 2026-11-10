package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end tests: the complete application (web, use cases, transactions, JPA, Flyway) on a random port
 * backed by a real PostgreSQL, called over HTTP exactly as a client would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductApiIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE products");
    }

    @Test
    void shouldPaginateAndSortTheProductListing() throws Exception {
        for (int i = 1; i <= 25; i++) {
            createProduct(String.format("Product %02d", i), i);
        }

        DocumentContext firstPage = json(send("GET", "/api/v1/products?size=10&sortBy=name", null));
        assertThat(firstPage.read("$.content.length()", Integer.class)).isEqualTo(10);
        assertThat(firstPage.read("$.content[0].name", String.class)).isEqualTo("Product 01");
        assertThat(firstPage.read("$.page", Integer.class)).isZero();
        assertThat(firstPage.read("$.size", Integer.class)).isEqualTo(10);
        assertThat(firstPage.read("$.totalElements", Integer.class)).isEqualTo(25);
        assertThat(firstPage.read("$.totalPages", Integer.class)).isEqualTo(3);

        DocumentContext lastPage = json(send("GET", "/api/v1/products?size=10&page=2&sortBy=name", null));
        assertThat(lastPage.read("$.content.length()", Integer.class)).isEqualTo(5);
        assertThat(lastPage.read("$.content[4].name", String.class)).isEqualTo("Product 25");

        DocumentContext priciest = json(send("GET", "/api/v1/products?size=3&sortBy=price&direction=desc", null));
        assertThat(priciest.read("$.content[*].name", List.class))
                .containsExactly("Product 25", "Product 24", "Product 23");

        DocumentContext beyond = json(send("GET", "/api/v1/products?page=99", null));
        assertThat(beyond.read("$.content.length()", Integer.class)).isZero();
        assertThat(beyond.read("$.totalElements", Integer.class)).isEqualTo(25);
    }

    @Test
    void shouldUseDefaultsWhenNoParametersAreGiven() throws Exception {
        createProduct("Only one", 1);

        DocumentContext body = json(send("GET", "/api/v1/products", null));

        assertThat(body.read("$.page", Integer.class)).isZero();
        assertThat(body.read("$.size", Integer.class)).isEqualTo(20);
        assertThat(body.read("$.totalElements", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidQueryParametersWithProblemDetail() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/products?size=1000&sortBy=color", null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(json(response).read("$.errors.length()", Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldRejectNulCharactersInsteadOfFailingWithA500() throws Exception {
        // Found by a security scan: PostgreSQL cannot store NUL, so this used to be an internal server error.
        HttpResponse<String> response = send("POST", "/api/v1/products",
                "{\"name\":\"a\\u0000b\",\"price\":1.00,\"stockQuantity\":1}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).read("$.errors[0]", String.class)).startsWith("name:");
    }

    @Test
    void shouldRejectPageOffsetsThatOverflowAnIntInsteadOfFailingWithA500() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/products?page=2147483647&size=100", null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).read("$.errors[0]", String.class)).startsWith("page:");
    }

    @Test
    void shouldRejectBodiesAboveTheJsonSizeLimitWith400() throws Exception {
        // The limit is 64 KB (spring.jackson.factory.constraints.read.max-document-length); a product needs < 1 KB.
        String oversizedName = "a".repeat(200 * 1024);

        HttpResponse<String> response = send("POST", "/api/v1/products",
                "{\"name\":\"" + oversizedName + "\",\"price\":1.00,\"stockQuantity\":1}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).doesNotContain("aaaa");
    }

    @Test
    void shouldAbortHugeBodiesEarlyAndStayHealthy() throws Exception {
        // 5 MB is far beyond the limit: the server stops reading at the limit and may close the connection while
        // the client is still sending, so the client sees either the 400 or a reset. Either way nothing is
        // buffered or stored, and the API must keep working.
        String hugeName = "a".repeat(5 * 1024 * 1024);
        try {
            HttpResponse<String> response = send("POST", "/api/v1/products",
                    "{\"name\":\"" + hugeName + "\",\"price\":1.00,\"stockQuantity\":1}");
            assertThat(response.statusCode()).isEqualTo(400);
        } catch (IOException connectionClosedByServer) {
            // acceptable: the server rejected the upload by closing the connection
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isZero();
        assertThat(send("GET", "/actuator/health", null).statusCode()).isEqualTo(200);
    }

    @Test
    void shouldSendSecurityHeaders() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/products", null);

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
    }

    @Test
    void shouldKeepSensitiveActuatorEndpointsClosed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/mappings")) {
            assertThat(send("GET", path, null).statusCode()).as(path).isEqualTo(404);
        }
    }

    @Test
    void shouldUpdateAndDeleteThroughTheTransactionalService() throws Exception {
        String id = createProduct("Laptop", 10);

        HttpResponse<String> updated = send("PUT", "/api/v1/products/" + id, productJson("Desktop", 5));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(send("GET", "/api/v1/products/" + id, null)).read("$.name", String.class)).isEqualTo("Desktop");

        assertThat(send("DELETE", "/api/v1/products/" + id, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/products/" + id, null).statusCode()).isEqualTo(404);
    }

    @Test
    void shouldDescribeListingParametersAndPageSchemaInTheOpenApiDocument() throws Exception {
        DocumentContext doc = json(send("GET", "/v3/api-docs", null));
        String params = "$.paths['/api/v1/products'].get.parameters";

        assertThat(doc.read(params + "[*].name", List.class)).containsExactly("page", "size", "sortBy", "direction");

        assertThat(doc.read(params + "[?(@.name=='page')].schema.minimum", List.class)).containsExactly(0);
        assertThat(doc.read(params + "[?(@.name=='page')].schema.default", List.class)).containsExactly(0);
        assertThat(doc.read(params + "[?(@.name=='size')].schema.minimum", List.class)).containsExactly(1);
        assertThat(doc.read(params + "[?(@.name=='size')].schema.maximum", List.class)).containsExactly(100);
        assertThat(doc.read(params + "[?(@.name=='size')].schema.default", List.class)).containsExactly(20);
        assertThat(doc.read(params + "[?(@.name=='sortBy')].schema.enum[*]", List.class))
                .containsExactlyInAnyOrder("name", "price", "stockQuantity", "createdAt");
        assertThat(doc.read(params + "[?(@.name=='direction')].schema.enum[*]", List.class))
                .containsExactlyInAnyOrder("asc", "desc");

        assertThat(doc.read("$.paths['/api/v1/products'].get.responses", java.util.Map.class))
                .containsKeys("200", "400");
        assertThat(doc.read("$.components.schemas.*.properties.totalPages", List.class)).isNotEmpty();
    }

    private String createProduct(String name, int price) throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/products", productJson(name, price));
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response).read("$.id", String.class);
    }

    private static String productJson(String name, int price) {
        return "{\"name\":\"" + name + "\",\"price\":" + new BigDecimal(price).setScale(2) + ",\"stockQuantity\":1}";
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, application/problem+json");
        request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        return http.send(request.build(), BodyHandlers.ofString());
    }

    private static DocumentContext json(HttpResponse<String> response) {
        return JsonPath.parse(response.body());
    }
}
