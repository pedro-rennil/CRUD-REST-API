package com.example.demo.infrastructure.persistence;

import com.example.demo.infrastructure.persistence.entity.ProductEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProductRepository extends JpaRepository<ProductEntity, UUID> {
}
