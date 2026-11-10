package com.example.demo.infrastructure.config;

import com.example.demo.application.service.ProductServiceImpl;
import com.example.demo.domain.repository.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    /**
     * Single bean that satisfies every product use case interface.
     */
    @Bean
    public ProductServiceImpl productService(ProductRepository productRepository) {
        return new ProductServiceImpl(productRepository);
    }
}
