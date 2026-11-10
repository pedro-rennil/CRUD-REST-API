package com.example.demo.application.usecase;

import com.example.demo.application.dto.PageResponseDTO;
import com.example.demo.application.dto.ProductResponseDTO;
import com.example.demo.domain.repository.ProductPageQuery;

public interface ListProductsUseCase {

    PageResponseDTO<ProductResponseDTO> list(ProductPageQuery query);
}
