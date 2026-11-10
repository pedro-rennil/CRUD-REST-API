# Product CRUD REST API

A RESTful CRUD API for managing products, built with **Spring Boot** and structured with
**Clean Architecture (Ports & Adapters)**. It is a small domain on purpose: the goal is to show a
well-layered, tested and production-minded backend rather than a large feature set.

## Highlights

- **Clean Architecture** with a framework-free domain, use-case interfaces and persistence hidden behind a port.
- **PostgreSQL** schema versioned with **Flyway** and validated by Hibernate (`ddl-auto: validate`).
- **Optimistic locking** (`@Version`) so concurrent updates are detected instead of silently overwritten.
- **Standard error format** using RFC 7807 `ProblemDetail`.
- **Interactive documentation** with OpenAPI / Swagger UI.
- **Layered test suite**: domain and service unit tests, controller tests and persistence integration tests
  running against a real PostgreSQL via **Testcontainers**.

## Tech stack

| Area          | Technology                                   |
|---------------|----------------------------------------------|
| Language      | Java 25                                      |
| Framework     | Spring Boot 4.1 (Web, Data JPA, Validation, Actuator) |
| Database      | PostgreSQL 16, Flyway migrations             |
| API docs      | springdoc-openapi (Swagger UI)               |
| Tests         | JUnit 5, Mockito, MockMvc, Testcontainers    |
| Build         | Maven (wrapper included)                     |

## Getting started

### Prerequisites

- JDK 25
- Docker (for the local database and for the integration tests)

### Run locally

The Maven project lives in the [`demo/`](demo) directory.

```bash
cd demo

# 1. Start PostgreSQL (exposed on localhost:5434)
docker compose up -d

# 2. Start the API on http://localhost:8080
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd` instead of `./mvnw`. Flyway creates the schema automatically on startup.

Once running:

| URL                                           | Description          |
|-----------------------------------------------|----------------------|
| http://localhost:8080/swagger-ui.html         | Interactive API docs |
| http://localhost:8080/v3/api-docs             | OpenAPI document     |
| http://localhost:8080/actuator/health         | Health check         |

### Configuration

The defaults match `docker-compose.yml`, so nothing is required for local development.
Override them with environment variables when needed:

| Variable      | Used by         | Default                                      |
|---------------|-----------------|----------------------------------------------|
| `DB_URL`      | application     | `jdbc:postgresql://localhost:5434/productdb` |
| `DB_USERNAME` | application, compose | `product`                               |
| `DB_PASSWORD` | application, compose | `product`                               |
| `DB_NAME`     | compose         | `productdb`                                  |
| `DB_PORT`     | compose (host port) | `5434`                                   |

If you change `DB_PORT` or `DB_NAME`, update `DB_URL` accordingly.

## API

Base path: `/api/v1/products`

| Method   | Path    | Description                | Success                    | Errors   |
|----------|---------|----------------------------|----------------------------|----------|
| `POST`   | `/`     | Create a product           | `201` + `Location` header  | `400`    |
| `GET`    | `/`     | List all products          | `200`                      |          |
| `GET`    | `/{id}` | Get a product by id        | `200`                      | `404`    |
| `PUT`    | `/{id}` | Replace a product          | `200`                      | `400` `404` `409` |
| `DELETE` | `/{id}` | Delete a product           | `204`                      | `404`    |

### Request body (`POST`, `PUT`)

| Field           | Type    | Rules                                                      |
|-----------------|---------|------------------------------------------------------------|
| `name`          | string  | required, not blank, at most 255 characters                |
| `description`   | string  | optional, at most 255 characters                           |
| `price`         | decimal | required, greater than zero, up to 17 integer digits and 2 decimal places |
| `stockQuantity` | integer | required, zero or greater                                  |

### Example

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Laptop", "description": "Gaming laptop", "price": 1499.99, "stockQuantity": 10}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/api/v1/products/3f1c2a9e-7b64-4d0e-9a53-1c8f0e2d4b77

{
  "id": "3f1c2a9e-7b64-4d0e-9a53-1c8f0e2d4b77",
  "name": "Laptop",
  "description": "Gaming laptop",
  "price": 1499.99,
  "stockQuantity": 10,
  "createdAt": "2026-01-15T10:15:30.123456Z"
}
```

### Errors

Errors follow RFC 7807 (`application/problem+json`). A validation failure lists every offending field:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/products",
  "errors": ["name: Name is required", "price: Price must be greater than zero"]
}
```

| Status | When                                                                        |
|--------|-----------------------------------------------------------------------------|
| `400`  | Invalid request body or a domain rule was violated                          |
| `404`  | The product does not exist                                                  |
| `409`  | The product was modified by another request while being updated; reload it and retry |

## Architecture

Dependencies point inwards: the domain knows nothing about Spring, JPA or HTTP.

```
presentation ──▶ application ──▶ domain ◀── infrastructure
 (REST, errors)   (use cases)    (model,     (JPA adapter,
                                  ports)      Flyway, wiring)
```

```
demo/src/main/java/com/example/demo
├── domain
│   ├── model/           Product (immutable, validates its own invariants)
│   ├── repository/      ProductRepository (port)
│   └── exception/       ProductNotFoundException
├── application
│   ├── usecase/         One interface per operation (Create, Get, List, Update, Delete)
│   ├── service/         ProductServiceImpl implements every use case
│   └── dto/             Request / response records
├── infrastructure
│   ├── persistence/     ProductRepositoryAdapter, Spring Data repository, JPA entity
│   └── config/          Bean wiring and OpenAPI configuration
└── presentation
    ├── controller/      ProductController
    └── exception/       GlobalExceptionHandler (ProblemDetail)
```

### Design decisions

- **Domain isolated from persistence.** `Product` is an immutable class; the JPA `ProductEntity` and the
  mapping live in the infrastructure layer, so the database model can change without touching business rules.
- **Defence in depth for validation.** Bean Validation rejects bad input at the edge with friendly messages,
  while `Product` enforces its invariants again so no invalid object can exist inside the domain.
- **Optimistic locking.** `Product` carries a `version`. Updating from stale data fails with `409 Conflict`
  rather than overwriting a concurrent change. A `null` version marks a never-persisted product, so creating
  one is a plain `INSERT` without a preliminary `SELECT`.
- **One transaction per use case.** `ProductServiceImpl` is `@Transactional` (read-only for queries), making
  read-then-write operations atomic. This is a deliberate trade-off: the application layer uses Spring's
  annotation instead of a separate transactional decorator.
- **Timestamps truncated to microseconds**, the precision PostgreSQL stores, so the value returned on creation
  equals what is read back later.
- **Schema as code.** Flyway owns the schema and Hibernate only validates it; `open-in-view` is disabled.

## Testing

```bash
cd demo

# Unit tests only (no Docker needed)
./mvnw test

# Unit + integration tests (needs Docker: starts a PostgreSQL 16 container)
./mvnw verify
```

| Level       | Scope                                                          | Tooling                      |
|-------------|----------------------------------------------------------------|------------------------------|
| Unit        | `Product` rules, use-case behaviour                            | JUnit 5, Mockito             |
| Web         | Status codes, validation, error format, `Location` header     | MockMvc (standalone)         |
| Integration | Persistence adapter, Flyway migrations, optimistic locking     | `@DataJpaTest`, Testcontainers (`*IT`, run by Failsafe) |

## Known limitations and roadmap

- Listing is not paginated yet (`GET /api/v1/products` returns every product).
- No authentication or authorization; the Swagger UI is open. Out of scope for this project.
- Deletion is physical (hard delete).
- Planned: pagination, database `CHECK` constraints, an end-to-end test of the full application, a Dockerfile
  and a CI pipeline.
