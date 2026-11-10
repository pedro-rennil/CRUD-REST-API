# Product CRUD REST API

A RESTful CRUD API for managing products, built with **Spring Boot** and structured with
**Clean Architecture (Ports & Adapters)**. It is a small domain on purpose: the goal is to show a
well-layered, tested and production-minded backend rather than a large feature set.

## Highlights

- **Clean Architecture** with a framework-free domain, use-case interfaces and persistence hidden behind a port.
- **PostgreSQL** schema versioned with **Flyway** and validated by Hibernate (`ddl-auto: validate`).
- **Paginated and sortable listing** with a stable order, documented parameter by parameter in OpenAPI.
- **Optimistic locking** (`@Version`) so concurrent updates are detected instead of silently overwritten.
- **Standard error format** using RFC 7807 `ProblemDetail`.
- **Interactive documentation** with OpenAPI / Swagger UI.
- **Containerized**: a multi-stage `Dockerfile` (JRE-only image, non-root user) and a Compose profile that runs
  the API together with its database.
- **Continuous integration**: GitHub Actions builds and runs every test, and checks that the Docker image builds.
- **Layered test suite**: domain and service unit tests, controller tests, persistence integration tests and
  end-to-end HTTP tests running against a real PostgreSQL via **Testcontainers**.

## Tech stack

| Area          | Technology                                   |
|---------------|----------------------------------------------|
| Language      | Java 25                                      |
| Framework     | Spring Boot 4.1 (Web, Data JPA, Validation, Actuator) |
| Database      | PostgreSQL 16, Flyway migrations             |
| API docs      | springdoc-openapi (Swagger UI)               |
| Tests         | JUnit 5, Mockito, MockMvc, Testcontainers    |
| Build         | Maven (wrapper included)                     |
| Packaging/CI  | Docker (multi-stage), Docker Compose, GitHub Actions |

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

### Run everything in containers

To run the API itself in a container (no JDK needed), enable the `app` profile. Compose builds the image from
[`demo/Dockerfile`](demo/Dockerfile), waits for the database to be healthy and starts the API on port 8080:

```bash
cd demo
docker compose --profile app up --build
```

A plain `docker compose up` starts only the database, which is what you want when running the API from your
IDE or with `./mvnw spring-boot:run`. Stop and remove the containers with `docker compose --profile app down`
(add `-v` to also delete the database volume).

The image can also be built and run on its own; point it at any PostgreSQL with the variables below:

```bash
docker build -t product-api demo
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5434/productdb \
  product-api
```

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
| `APP_PORT`    | compose (host port of the `app` profile) | `8080`              |

If you change `DB_PORT` or `DB_NAME` while running the API outside Docker, update `DB_URL` accordingly.
(The `app` profile builds `DB_URL` for you.)

## API

Base path: `/api/v1/products`

| Method   | Path    | Description                | Success                    | Errors   |
|----------|---------|----------------------------|----------------------------|----------|
| `POST`   | `/`     | Create a product           | `201` + `Location` header  | `400`    |
| `GET`    | `/`     | List products (paginated)  | `200`                      | `400`    |
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

### Listing: pagination and sorting

`GET /api/v1/products` returns one page at a time. All parameters are optional.

| Parameter   | Type    | Default     | Allowed values                                  |
|-------------|---------|-------------|-------------------------------------------------|
| `page`      | integer | `0`         | zero or greater (zero-based page index)         |
| `size`      | integer | `20`        | 1 to 100                                        |
| `sortBy`    | string  | `createdAt` | `name`, `price`, `stockQuantity`, `createdAt`   |
| `direction` | string  | `asc`       | `asc`, `desc` (case-insensitive)                |

```bash
curl "http://localhost:8080/api/v1/products?page=0&size=10&sortBy=price&direction=desc"
```

```json
{
  "content": [
    { "id": "3f1c2a9e-7b64-4d0e-9a53-1c8f0e2d4b77", "name": "Laptop", "description": "Gaming laptop",
      "price": 1499.99, "stockQuantity": 10, "createdAt": "2026-01-15T10:15:30.123456Z" }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

- The order is deterministic: ties on the chosen field are broken by `id`, so consecutive pages never repeat
  or skip a product.
- Asking for a page beyond the last one is not an error; it returns an empty `content` with the real totals.
- Invalid parameters return `400` and report every problem at once (see [Errors](#errors)).
- The parameters, their bounds, defaults and allowed values are part of the generated OpenAPI document
  (`/v3/api-docs`), so generated clients and Swagger UI present them accurately.

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
  "detail": "Validation failed",
  "instance": "/api/v1/products",
  "status": 400,
  "title": "Bad Request",
  "errors": ["name: Name is required", "price: Price must be greater than zero"]
}
```

| Status | When                                                                        |
|--------|-----------------------------------------------------------------------------|
| `400`  | Invalid request body or query parameters, or a domain rule was violated     |
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
│   ├── pagination/      Page, SortDirection
│   ├── repository/      ProductRepository (port), ProductPageQuery, ProductSortField
│   └── exception/       ProductNotFoundException
├── application
│   ├── usecase/         One interface per operation (Create, Get, List, Update, Delete)
│   ├── service/         ProductServiceImpl implements every use case
│   └── dto/             Request / response records, PageResponseDTO
├── infrastructure
│   ├── persistence/     ProductRepositoryAdapter, Spring Data repository, JPA entity
│   └── config/          Bean wiring and OpenAPI configuration
└── presentation
    ├── controller/      ProductController, ProductListQueryParser
    └── exception/       GlobalExceptionHandler (ProblemDetail)
```

### Design decisions

- **Domain isolated from persistence.** `Product` is an immutable class; the JPA `ProductEntity` and the
  mapping live in the infrastructure layer, so the database model can change without touching business rules.
- **Defence in depth for validation.** Bean Validation rejects bad input at the edge with friendly messages,
  `Product` enforces its invariants again so no invalid object can exist inside the domain, and database `CHECK`
  constraints (non-blank name, `price > 0`, `stock_quantity >= 0`) guarantee that nothing writing to the database
  directly can store an invalid product either.
- **Optimistic locking.** `Product` carries a `version`. Updating from stale data fails with `409 Conflict`
  rather than overwriting a concurrent change. A `null` version marks a never-persisted product, so creating
  one is a plain `INSERT` without a preliminary `SELECT`.
- **One transaction per use case.** `ProductServiceImpl` is `@Transactional` (read-only for queries), making
  read-then-write operations atomic. This is a deliberate trade-off: the application layer uses Spring's
  annotation instead of a separate transactional decorator.
- **Pagination without framework types in the core.** The domain defines its own `Page` and `ProductPageQuery`;
  only the infrastructure adapter translates them to Spring Data. The query-string vocabulary
  (`sortBy=stockQuantity`) is parsed in the presentation layer, so renaming a database column never changes the
  public API.
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
| Integration | Persistence adapter, Flyway migrations, optimistic locking, paging and sorting | `@DataJpaTest`, Testcontainers (`*IT`, run by Failsafe) |
| End-to-end  | Full application over HTTP: CRUD, pagination, error format, OpenAPI document | `@SpringBootTest` (random port), Testcontainers |

### Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to `main` and on every pull request:

1. **Build and test**: JDK 25, `./mvnw -B verify` (unit, integration and end-to-end tests; Testcontainers uses the
   Docker available on GitHub-hosted runners). Test reports are uploaded as an artifact when the build fails.
2. **Docker image builds**: `docker build` of `demo/Dockerfile`, so the image cannot silently break. Nothing is
   published.

## Known limitations and roadmap

- Listing supports ordering but no filtering or free-text search yet.
- Pagination is offset-based: very deep pages get slower, and items created while paging can shift later pages.
- No authentication or authorization; the Swagger UI is open. Out of scope for this project.
- Deletion is physical (hard delete).
- The Docker image is built in CI but not published to a registry yet.
