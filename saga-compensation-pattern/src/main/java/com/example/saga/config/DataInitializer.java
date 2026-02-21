package com.example.saga.config;

import com.example.saga.domain.Inventory;
import com.example.saga.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 테스트를 위한 초기 데이터 설정
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        // 테스트용 재고 데이터 생성
        inventoryRepository.save(new Inventory("PRODUCT-001", 100));
        inventoryRepository.save(new Inventory("PRODUCT-002", 50));
        inventoryRepository.save(new Inventory("PRODUCT-003", 10));

        log.info("Initial inventory data created");
    }
}
