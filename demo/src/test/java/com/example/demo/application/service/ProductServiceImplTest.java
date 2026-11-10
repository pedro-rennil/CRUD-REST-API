package com.example.demo.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.application.dto.ProductRequestDTO;
import com.example.demo.application.dto.ProductResponseDTO;
import com.example.demo.domain.exception.ProductNotFoundException;
import com.example.demo.domain.model.Product;
import com.example.demo.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl service;

    private static Product existingProduct() {
        return Product.create("Laptop", "Gaming laptop", new BigDecimal("1499.99"), 10);
    }

    @Test
    void shouldCreateProductSuccessfully() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Laptop",
                "Gaming laptop",
                new BigDecimal("1499.99"),
                10);

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = service.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        Product product = captor.getValue();

        assertNotNull(product.getId());
        assertEquals(request.name(), product.getName());
        assertEquals(request.description(), product.getDescription());
        assertEquals(request.price(), product.getPrice());
        assertEquals(request.stockQuantity(), product.getStockQuantity());
        assertNotNull(product.getCreatedAt());

        assertNotNull(response.id());
        assertEquals(request.name(), response.name());
        assertEquals(request.description(), response.description());
        assertEquals(request.price(), response.price());
        assertEquals(request.stockQuantity(), response.stockQuantity());
        assertNotNull(response.createdAt());
        assertEquals(product.getId(), response.id());
        assertEquals(product.getCreatedAt(), response.createdAt());
    }

    @Test
    void shouldGetProductById() {
        Product product = existingProduct();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        ProductResponseDTO response = service.getById(product.getId());

        assertEquals(product.getId(), response.id());
        assertEquals(product.getName(), response.name());
    }

    @Test
    void shouldThrowWhenProductNotFoundOnGet() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void shouldListAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(existingProduct(), existingProduct()));

        List<ProductResponseDTO> response = service.list();

        assertEquals(2, response.size());
    }

    @Test
    void shouldUpdateProductKeepingIdAndCreationDate() {
        Product product = existingProduct();
        ProductRequestDTO request = new ProductRequestDTO("Desktop", null, new BigDecimal("999.00"), 5);

        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = service.update(product.getId(), request);

        assertEquals(product.getId(), response.id());
        assertEquals(product.getCreatedAt(), response.createdAt());
        assertEquals("Desktop", response.name());
        assertEquals(new BigDecimal("999.00"), response.price());
        assertEquals(5, response.stockQuantity());
    }

    @Test
    void shouldThrowWhenProductNotFoundOnUpdate() {
        UUID id = UUID.randomUUID();
        ProductRequestDTO request = new ProductRequestDTO("Desktop", null, new BigDecimal("999.00"), 5);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> service.update(id, request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldDeleteExistingProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(productRepository).deleteById(id);
    }

    @Test
    void shouldThrowWhenProductNotFoundOnDelete() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> service.delete(id));
        verify(productRepository, never()).deleteById(any());
    }
}
