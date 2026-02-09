package com.example.actuator.controller;

import com.example.actuator.metrics.CustomMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

/**
 * 메트릭 수집 예제를 위한 주문 API
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CustomMetrics customMetrics;
    private final Random random = new Random();

    /**
     * 주문 생성 API
     * 메트릭: orders.created, orders.active, orders.processing.time
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderRequest) {
        String orderId = "ORDER-" + System.currentTimeMillis();
        String category = (String) orderRequest.getOrDefault("category", "general");
        Double amount = ((Number) orderRequest.getOrDefault("amount", 0)).doubleValue();

        log.info("Creating order: {}", orderId);

        try {
            // 주문 생성 메트릭 기록
            customMetrics.recordOrderCreated();

            // 주문 처리 시간 측정
            customMetrics.recordOrderProcessingTime(() -> {
                try {
                    // 주문 처리 시뮬레이션 (100~500ms)
                    Thread.sleep(100 + random.nextInt(400));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 카테고리별 메트릭
            customMetrics.recordOrderByCategory(category);

            // 금액 분포 메트릭
            customMetrics.recordOrderAmount(amount);

            // 주문 완료
            customMetrics.recordOrderCompleted();

            return Map.of(
                    "success", true,
                    "orderId", orderId,
                    "message", "Order created successfully"
            );

        } catch (Exception e) {
            log.error("Order creation failed", e);
            customMetrics.recordOrderFailed();

            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 주문 실패 시뮬레이션
     * 메트릭: orders.failed
     */
    @PostMapping("/fail")
    public Map<String, Object> createFailedOrder() {
        log.warn("Simulating order failure");
        customMetrics.recordOrderFailed();

        return Map.of(
                "success", false,
                "error", "Simulated failure"
        );
    }
}
