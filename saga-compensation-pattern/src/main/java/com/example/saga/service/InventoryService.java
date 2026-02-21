package com.example.saga.service;

import com.example.saga.domain.Inventory;
import com.example.saga.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    /**
     * 재고 예약
     */
    @Transactional
    public void reserveInventory(String productId, Integer quantity) {
        log.info("Reserving inventory for product: {}, quantity: {}", productId, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        inventory.reserve(quantity);
        inventoryRepository.save(inventory);

        log.info("Inventory reserved: available={}, reserved={}",
                inventory.getAvailableQuantity(), inventory.getReservedQuantity());
    }

    /**
     * 재고 예약 해제 (보상 트랜잭션)
     */
    @Transactional
    public void releaseInventory(String productId, Integer quantity) {
        log.info("Releasing inventory for product: {}, quantity: {}", productId, quantity);

        inventoryRepository.findByProductId(productId)
                .ifPresent(inventory -> {
                    inventory.releaseReservation(quantity);
                    inventoryRepository.save(inventory);
                    log.info("Inventory released: available={}, reserved={}",
                            inventory.getAvailableQuantity(), inventory.getReservedQuantity());
                });
    }

    /**
     * 재고 예약 확정
     */
    @Transactional
    public void confirmInventory(String productId, Integer quantity) {
        log.info("Confirming inventory for product: {}, quantity: {}", productId, quantity);

        inventoryRepository.findByProductId(productId)
                .ifPresent(inventory -> {
                    inventory.confirmReservation(quantity);
                    inventoryRepository.save(inventory);
                });
    }
}
