package com.example.servicea.controller;

import com.example.servicea.service.OrderService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Service A - 주문 API (Frontend Gateway)
 *
 * 분산 추적의 시작점입니다.
 * 여기서 생성된 traceId가 Service B까지 전파됩니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final Tracer tracer;

    /**
     * 주문 생성 API
     *
     * 1. Service A에서 요청 받음
     * 2. Service B 호출 (재고 확인)
     * 3. Service B 호출 (결제 처리)
     * 4. 응답 반환
     *
     * 전체 플로우가 하나의 traceId로 추적됩니다.
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderRequest) {
        // 현재 Span 정보 가져오기
        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "no-trace";

        log.info("=== [Service A] Order request received - traceId: {} ===", traceId);

        String orderId = "ORDER-" + System.currentTimeMillis();
        String productId = (String) orderRequest.get("productId");
        Integer quantity = (Integer) orderRequest.get("quantity");

        // 커스텀 태그 추가 (Zipkin에서 검색 가능)
        if (currentSpan != null) {
            currentSpan.tag("order.id", orderId);
            currentSpan.tag("product.id", productId);
            currentSpan.tag("service", "service-a");
        }

        try {
            // OrderService를 통해 Service B 호출
            Map<String, Object> result = orderService.processOrder(orderId, productId, quantity);

            log.info("=== [Service A] Order completed successfully - traceId: {} ===", traceId);

            return Map.of(
                    "success", true,
                    "orderId", orderId,
                    "traceId", traceId,
                    "details", result
            );

        } catch (Exception e) {
            log.error("=== [Service A] Order failed - traceId: {} ===", traceId, e);

            // 에러 발생 시 Span에 에러 정보 기록
            if (currentSpan != null) {
                currentSpan.tag("error", "true");
                currentSpan.tag("error.message", e.getMessage());
            }

            return Map.of(
                    "success", false,
                    "orderId", orderId,
                    "traceId", traceId,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 현재 Trace 정보 조회
     */
    @GetMapping("/trace-info")
    public Map<String, String> getTraceInfo() {
        Span currentSpan = tracer.currentSpan();

        if (currentSpan != null) {
            return Map.of(
                    "traceId", currentSpan.context().traceId(),
                    "spanId", currentSpan.context().spanId(),
                    "service", "service-a"
            );
        }

        return Map.of("message", "No active trace");
    }
}
