package com.example.demo.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI productApiDocumentation() {
        return new OpenAPI().info(new Info()
                .title("Product API")
                .description("Template CRUD REST API built with Clean Architecture (Ports & Adapters)")
                .version("v1"));
    }
}
