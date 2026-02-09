package com.example.actuator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer를 사용한 커스텀 메트릭 관리
 *
 * Micrometer는 다양한 모니터링 시스템(Prometheus, Datadog, CloudWatch 등)과
 * 통합할 수 있는 메트릭 파사드(Facade)를 제공합니다.
 */
@Slf4j
@Component
public class CustomMetrics {

    private final Counter orderCounter;
    private final Counter orderFailureCounter;
    private final Timer orderProcessingTimer;
    private final AtomicInteger activeOrders;
    private final MeterRegistry meterRegistry;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 1. Counter: 누적 값만 증가 (주문 수, 에러 수 등)
        this.orderCounter = Counter.builder("orders.created")
                .description("Total number of orders created")
                .tag("type", "total")
                .register(meterRegistry);

        this.orderFailureCounter = Counter.builder("orders.failed")
                .description("Total number of failed orders")
                .tag("type", "error")
                .register(meterRegistry);

        // 2. Timer: 이벤트의 빈도와 소요 시간 측정 (API 응답 시간 등)
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
                .description("Time taken to process an order")
                .register(meterRegistry);

        // 3. Gauge: 현재 상태 값 (활성 커넥션 수, 큐 크기 등)
        this.activeOrders = meterRegistry.gauge(
                "orders.active",
                new AtomicInteger(0)
        );
    }

    /**
     * 주문 생성 메트릭 기록
     */
    public void recordOrderCreated() {
        orderCounter.increment();
        activeOrders.incrementAndGet();
        log.info("Order created - Total: {}, Active: {}",
                orderCounter.count(), activeOrders.get());
    }

    /**
     * 주문 실패 메트릭 기록
     */
    public void recordOrderFailed() {
        orderFailureCounter.increment();
        log.warn("Order failed - Total failures: {}", orderFailureCounter.count());
    }

    /**
     * 주문 처리 완료 메트릭 기록
     */
    public void recordOrderCompleted() {
        activeOrders.decrementAndGet();
        log.info("Order completed - Active orders: {}", activeOrders.get());
    }

    /**
     * 주문 처리 시간 측정
     */
    public void recordOrderProcessingTime(Runnable task) {
        orderProcessingTimer.record(task);
    }

    /**
     * 태그를 사용한 세분화된 메트릭
     * 예: 상품 카테고리별, 결제 수단별 주문 수
     */
    public void recordOrderByCategory(String category) {
        Counter.builder("orders.by.category")
                .description("Orders grouped by category")
                .tag("category", category)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 분포(Distribution) 측정
     * 예: 주문 금액의 분포를 히스토그램으로 기록
     */
    public void recordOrderAmount(double amount) {
        meterRegistry.summary("orders.amount")
                .record(amount);
    }
}
