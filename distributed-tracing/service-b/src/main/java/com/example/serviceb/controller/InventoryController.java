package com.example.serviceb.controller;

import com.example.serviceb.service.InventoryService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Service B - 재고 관리 API
 *
 * Service A로부터 전파된 traceId를 받아서 처리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final Tracer tracer;

    /**
     * 재고 확인 API
     *
     * Service A에서 전파된 traceId를 자동으로 받습니다.
     */
    @GetMapping("/check")
    public Map<String, Object> checkStock(
            @RequestParam String productId,
            @RequestParam Integer quantity) {

        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "no-trace";

        log.info("=== [Service B] Stock check request received - traceId: {}, product: {}, qty: {} ===",
                traceId, productId, quantity);

        // 커스텀 태그 추가
        if (currentSpan != null) {
            currentSpan.tag("product.id", productId);
            currentSpan.tag("service", "service-b");
            currentSpan.tag("operation", "check-stock");
        }

        boolean available = inventoryService.checkStock(productId, quantity);

        Map<String, Object> response = Map.of(
                "productId", productId,
                "requestedQuantity", quantity,
                "available", available,
                "traceId", traceId
        );

        log.info("=== [Service B] Stock check completed - traceId: {}, available: {} ===",
                traceId, available);

        return response;
    }
}
