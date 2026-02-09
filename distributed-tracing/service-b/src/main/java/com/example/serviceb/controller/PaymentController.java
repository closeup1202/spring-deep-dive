package com.example.serviceb.controller;

import com.example.serviceb.service.PaymentService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Service B - 결제 처리 API
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final Tracer tracer;

    /**
     * 결제 처리 API
     */
    @PostMapping("/process")
    public Map<String, Object> processPayment(@RequestBody Map<String, Object> paymentRequest) {

        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "no-trace";

        String orderId = (String) paymentRequest.get("orderId");
        Integer amount = (Integer) paymentRequest.get("amount");

        log.info("=== [Service B] Payment request received - traceId: {}, order: {}, amount: {} ===",
                traceId, orderId, amount);

        // 커스텀 태그 추가
        if (currentSpan != null) {
            currentSpan.tag("order.id", orderId);
            currentSpan.tag("payment.amount", String.valueOf(amount));
            currentSpan.tag("service", "service-b");
            currentSpan.tag("operation", "process-payment");
        }

        boolean success = paymentService.processPayment(orderId, amount);

        Map<String, Object> response = Map.of(
                "orderId", orderId,
                "amount", amount,
                "success", success,
                "paymentId", "PAY-" + System.currentTimeMillis(),
                "traceId", traceId
        );

        log.info("=== [Service B] Payment completed - traceId: {}, success: {} ===",
                traceId, success);

        return response;
    }
}
