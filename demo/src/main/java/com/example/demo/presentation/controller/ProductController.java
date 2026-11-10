package com.example.demo.presentation.controller;

import com.example.demo.application.dto.PageResponseDTO;
import com.example.demo.application.dto.ProductRequestDTO;
import com.example.demo.application.dto.ProductResponseDTO;
import com.example.demo.application.usecase.CreateProductUseCase;
import com.example.demo.application.usecase.DeleteProductUseCase;
import com.example.demo.application.usecase.GetProductUseCase;
import com.example.demo.application.usecase.ListProductsUseCase;
import com.example.demo.application.usecase.UpdateProductUseCase;
import com.example.demo.domain.repository.ProductPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "CRUD operations for products")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    public ProductController(
            CreateProductUseCase createProductUseCase,
            GetProductUseCase getProductUseCase,
            ListProductsUseCase listProductsUseCase,
            UpdateProductUseCase updateProductUseCase,
            DeleteProductUseCase deleteProductUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.getProductUseCase = getProductUseCase;
        this.listProductsUseCase = listProductsUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.deleteProductUseCase = deleteProductUseCase;
    }

    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO request) {
        ProductResponseDTO response = createProductUseCase.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a product by id")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(getProductUseCase.getById(id));
    }

    @Operation(
            summary = "List products",
            description = "Returns one page of products. Results are ordered by `sortBy` and `direction`; "
                    + "ties are broken by id so consecutive pages never overlap. "
                    + "A page beyond the last one is not an error: it returns an empty `content` with the real totals.")
    @ApiResponse(responseCode = "200", description = "Page of products")
    @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductResponseDTO>> listProducts(
            @Parameter(description = "Zero-based page index",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = ProductListQueryParser.DEFAULT_PAGE))
            @RequestParam(defaultValue = ProductListQueryParser.DEFAULT_PAGE) int page,
            @Parameter(description = "Number of products per page",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = ProductListQueryParser.MAX_SIZE_VALUE, defaultValue = ProductListQueryParser.DEFAULT_SIZE))
            @RequestParam(defaultValue = ProductListQueryParser.DEFAULT_SIZE) int size,
            @Parameter(description = "Property to order by",
                    schema = @Schema(type = "string", allowableValues = {"name", "price", "stockQuantity", "createdAt"},
                            defaultValue = ProductListQueryParser.DEFAULT_SORT_BY))
            @RequestParam(defaultValue = ProductListQueryParser.DEFAULT_SORT_BY) String sortBy,
            @Parameter(description = "Sort direction",
                    schema = @Schema(type = "string", allowableValues = {"asc", "desc"},
                            defaultValue = ProductListQueryParser.DEFAULT_DIRECTION))
            @RequestParam(defaultValue = ProductListQueryParser.DEFAULT_DIRECTION) String direction) {
        ProductPageQuery query = ProductListQueryParser.parse(page, size, sortBy, direction);
        return ResponseEntity.ok(listProductsUseCase.list(query));
    }

    @Operation(summary = "Replace a product")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequestDTO request) {
        return ResponseEntity.ok(updateProductUseCase.update(id, request));
    }

    @Operation(summary = "Delete a product")
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        deleteProductUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
