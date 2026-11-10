package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The {@code prod} profile must not advertise the API surface: no Swagger UI and no OpenAPI document,
 * while the API itself keeps working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers
class ProductApiProdProfileIT {

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

    @Test
    void shouldNotExposeSwaggerUiOrTheOpenApiDocument() throws Exception {
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(404);
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(404);
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(404);
    }

    @Test
    void shouldKeepServingTheApi() throws Exception {
        assertThat(get("/api/v1/products").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, BodyHandlers.ofString());
    }
}
