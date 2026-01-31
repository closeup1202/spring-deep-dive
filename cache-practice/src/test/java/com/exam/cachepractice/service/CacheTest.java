package com.exam.cachepractice.service;

import com.exam.cachepractice.domain.Product;
import com.exam.cachepractice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
class CacheTest {

    @Autowired
    private ProductService productService;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long productId;

    @BeforeEach
    void setUp() {
        // 테스트 전 캐시 초기화
        cacheManager.getCache("products").clear();
        
        // 데이터 준비
        Product product = productService.createProduct("Test Product", 1000L);
        productId = product.getId();
        
        // SpyBean 초기화 (createProduct 호출로 인한 카운트 리셋)
        reset(productRepository);
        // findById 호출 시 실제 DB 조회하도록 설정 (SpyBean이라 기본 동작이긴 함)
        doReturn(Optional.of(product)).when(productRepository).findById(productId);
    }

    @Test
    @DisplayName("캐시 적용 확인: 두 번째 조회부터는 DB 조회가 발생하지 않아야 한다")
    void cacheHitTest() {
        // 1. 첫 번째 조회 (DB 조회 O)
        System.out.println("=== 1st Call ===");
        productService.getProduct(productId);
        verify(productRepository, times(1)).findById(productId);

        // 2. 두 번째 조회 (DB 조회 X, 캐시 Hit)
        System.out.println("=== 2nd Call ===");
        Product cachedProduct = productService.getProduct(productId);
        verify(productRepository, times(1)).findById(productId); // 호출 횟수가 여전히 1이어야 함

        assertThat(cachedProduct.getName()).isEqualTo("Test Product");
    }

    @Test
    @DisplayName("캐시 갱신 확인: 수정 후 조회하면 DB 조회가 다시 발생해야 한다 (@CacheEvict)")
    void cacheEvictTest() {
        // 1. 조회 (캐시 저장)
        productService.getProduct(productId);
        
        // 2. 수정 (캐시 삭제)
        System.out.println("=== Update ===");
        productService.updateProduct(productId, "Updated Name", 2000L);

        // 3. 다시 조회 (DB 조회 발생해야 함)
        System.out.println("=== 3rd Call ===");
        Product updatedProduct = productService.getProduct(productId);
        
        // 총 3번 조회되어야 함 (처음 1번 + 수정 시 1번 + 수정 후 1번)
        verify(productRepository, times(3)).findById(productId);
        assertThat(updatedProduct.getName()).isEqualTo("Updated Name");
    }
}
