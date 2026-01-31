package com.exam.cachepractice.repository;

import com.exam.cachepractice.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
