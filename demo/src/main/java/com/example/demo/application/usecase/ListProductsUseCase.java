package com.example.demo.application.usecase;

import com.example.demo.application.dto.ProductResponseDTO;
import java.util.List;

public interface ListProductsUseCase {

    List<ProductResponseDTO> list();
}
