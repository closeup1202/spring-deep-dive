package com.exam.cachepractice.service;

import com.exam.cachepractice.domain.Product;
import com.exam.cachepractice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // 1. Look Aside 패턴
    // 캐시에 있으면 리턴, 없으면 DB 조회 후 캐시 저장
    // key: "products::1" (cacheNames::key)
    @Cacheable(cacheNames = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        log.info("Fetching product from DB... id={}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    // 2. Cache Eviction (삭제)
    // 데이터가 수정되면 캐시 데이터는 낡은 데이터(Stale Data)가 되므로 삭제해야 함
    @CacheEvict(cacheNames = "products", key = "#id")
    @Transactional
    public void updateProduct(Long id, String name, Long price) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.update(name, price);
        log.info("Product updated in DB. Cache evicted. id={}", id);
    }

    // 3. Cache Put (갱신)
    // 캐시를 삭제하지 않고, 수정된 값으로 바로 덮어씌움 (DB 조회 없이 캐시 갱신)
    // 주의: 리턴 타입이 캐시에 저장될 타입과 같아야 함
    @CachePut(cacheNames = "products", key = "#id")
    @Transactional
    public Product updateProductAndRefreshCache(Long id, String name, Long price) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.update(name, price);
        log.info("Product updated in DB. Cache refreshed. id={}", id);
        return product;
    }
    
    @Transactional
    public Product createProduct(String name, Long price) {
        return productRepository.save(new Product(null, name, price));
    }
}
