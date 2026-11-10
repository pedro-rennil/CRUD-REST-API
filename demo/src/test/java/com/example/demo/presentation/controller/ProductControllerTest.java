package com.example.demo.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.application.dto.ProductResponseDTO;
import com.example.demo.application.usecase.CreateProductUseCase;
import com.example.demo.application.usecase.DeleteProductUseCase;
import com.example.demo.application.usecase.GetProductUseCase;
import com.example.demo.application.usecase.ListProductsUseCase;
import com.example.demo.application.usecase.UpdateProductUseCase;
import com.example.demo.domain.exception.ProductNotFoundException;
import com.example.demo.presentation.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {

    private final CreateProductUseCase create = Mockito.mock(CreateProductUseCase.class);
    private final GetProductUseCase get = Mockito.mock(GetProductUseCase.class);
    private final ListProductsUseCase list = Mockito.mock(ListProductsUseCase.class);
    private final UpdateProductUseCase update = Mockito.mock(UpdateProductUseCase.class);
    private final DeleteProductUseCase delete = Mockito.mock(DeleteProductUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProductController(create, get, list, update, delete))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldReturn201WithLocationOnCreate() throws Exception {
        UUID id = UUID.randomUUID();
        when(create.create(any())).thenReturn(
                new ProductResponseDTO(id, "Laptop", null, new BigDecimal("10.00"), 1, Instant.now()));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"price\":10.00,\"stockQuantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/products/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1,\"stockQuantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldReturn400WhenNameExceedsColumnLength() throws Exception {
        String longName = "x".repeat(256);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\",\"price\":10.00,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("name:")));
    }

    @Test
    void shouldReturn400WhenPriceDoesNotFitColumn() throws Exception {
        // 18 integer digits (NUMERIC(19,2) holds 17) and 3 decimal places.
        for (String price : new String[] {"123456789012345678.00", "10.001"}) {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Laptop\",\"price\":" + price + ",\"stockQuantity\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("price:")));
        }
    }

    @Test
    void shouldAcceptMaximumPriceAndNameLength() throws Exception {
        UUID id = UUID.randomUUID();
        when(create.create(any())).thenReturn(
                new ProductResponseDTO(id, "x", null, new BigDecimal("10.00"), 1, Instant.now()));
        String name = "x".repeat(255);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":12345678901234567.99,\"stockQuantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn409WhenProductWasModifiedConcurrently() throws Exception {
        UUID id = UUID.randomUUID();
        when(update.update(org.mockito.ArgumentMatchers.eq(id), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, id));

        mockMvc.perform(put("/api/v1/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"price\":10.00,\"stockQuantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn404WhenProductDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(get.getById(id)).thenThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn204OnDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404OnDeleteOfMissingProduct() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductNotFoundException(id)).when(delete).delete(id);

        mockMvc.perform(delete("/api/v1/products/" + id))
                .andExpect(status().isNotFound());
    }
}
