package com.example.serviceb.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service B - 재고 관리 서비스
 *
 * 실제로는 DB에서 재고를 조회하지만, 여기서는 시뮬레이션
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final Tracer tracer;

    public boolean checkStock(String productId, Integer quantity) {
        // 커스텀 Span 생성 (DB 조회 시뮬레이션)
        Span dbSpan = tracer.nextSpan().name("db-query-stock");

        try (Tracer.SpanInScope ws = tracer.withSpan(dbSpan.start())) {
            dbSpan.tag("db.operation", "SELECT");
            dbSpan.tag("db.table", "inventory");
            dbSpan.tag("product.id", productId);

            log.info("[Service B] Checking stock in database for product: {}", productId);

            // DB 쿼리 시뮬레이션 (50ms 소요)
            simulateDbQuery(50);

            // 재고 확인 로직 (간단하게 시뮬레이션)
            boolean available = quantity <= 100; // 재고 100개 가정

            log.info("[Service B] Stock check result - product: {}, available: {}", productId, available);

            return available;

        } finally {
            dbSpan.end();
        }
    }

    private void simulateDbQuery(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
