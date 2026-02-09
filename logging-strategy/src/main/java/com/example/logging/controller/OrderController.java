package com.example.logging.controller;

import com.example.logging.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 주문 API 예제 컨트롤러
 * MDC와 Structured Logging을 실습하기 위한 엔드포인트를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 생성 API
     * MDC를 통해 traceId, userId가 자동으로 로그에 포함됩니다.
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderRequest) {
        String orderId = (String) orderRequest.get("orderId");
        Integer amount = (Integer) orderRequest.get("amount");

        log.info("Creating order - orderId: {}, amount: {}", orderId, amount);

        String result = orderService.processOrder(orderId, amount);

        log.info("Order created successfully - orderId: {}", orderId);

        return Map.of(
                "success", true,
                "orderId", orderId,
                "message", result,
                "traceId", MDC.get("traceId") // traceId를 응답에도 포함
        );
    }

    /**
     * 주문 조회 API
     */
    @GetMapping("/{orderId}")
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        log.info("Fetching order details - orderId: {}", orderId);

        String orderDetails = orderService.getOrderDetails(orderId);

        return Map.of(
                "orderId", orderId,
                "details", orderDetails,
                "traceId", MDC.get("traceId")
        );
    }

    /**
     * 에러 발생 시나리오 테스트
     * 로깅 전략에서 예외를 어떻게 다루는지 확인합니다.
     */
    @PostMapping("/error")
    public Map<String, Object> createErrorOrder() {
        log.warn("Attempting to create order with invalid data");

        try {
            orderService.processOrderWithError();
        } catch (Exception e) {
            log.error("Order processing failed", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "traceId", MDC.get("traceId")
            );
        }

        return Map.of("success", true);
    }

    /**
     * Structured Logging 예제
     * 구조화된 로그를 JSON 형태로 출력합니다.
     */
    @PostMapping("/structured-log")
    public Map<String, Object> structuredLogExample(@RequestBody Map<String, Object> data) {
        // Structured Logging: 로그를 파싱하기 쉽게 구조화
        log.info("Structured log example - data: {}, userId: {}, action: {}",
                data,
                MDC.get("userId"),
                "ORDER_CREATED"
        );

        // JSON 로그 출력 (logstash-logback-encoder 사용)
        return Map.of(
                "message", "Structured logging example",
                "data", data,
                "traceId", MDC.get("traceId")
        );
    }
}
