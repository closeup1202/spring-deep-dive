package com.example.serviceb.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service B - 결제 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Tracer tracer;

    public boolean processPayment(String orderId, Integer amount) {
        log.info("[Service B] Processing payment - order: {}, amount: {}", orderId, amount);

        // 1. 결제 검증
        validatePayment(orderId, amount);

        // 2. 외부 PG사 호출 시뮬레이션
        callPaymentGateway(orderId, amount);

        // 3. 결제 결과 저장
        savePaymentResult(orderId, amount);

        log.info("[Service B] Payment processing completed - order: {}", orderId);

        return true;
    }

    /**
     * 결제 검증 (커스텀 Span)
     */
    private void validatePayment(String orderId, Integer amount) {
        Span validationSpan = tracer.nextSpan().name("payment-validation");

        try (Tracer.SpanInScope ws = tracer.withSpan(validationSpan.start())) {
            validationSpan.tag("order.id", orderId);
            validationSpan.tag("validation.type", "amount-check");

            log.info("[Service B] Validating payment - amount: {}", amount);

            // 검증 로직 시뮬레이션
            simulateProcessing(30);

            if (amount <= 0) {
                throw new IllegalArgumentException("Invalid amount");
            }

        } finally {
            validationSpan.end();
        }
    }

    /**
     * 외부 PG사 호출 시뮬레이션 (커스텀 Span)
     */
    private void callPaymentGateway(String orderId, Integer amount) {
        Span pgSpan = tracer.nextSpan().name("external-pg-call");

        try (Tracer.SpanInScope ws = tracer.withSpan(pgSpan.start())) {
            pgSpan.tag("pg.provider", "toss-payments");
            pgSpan.tag("order.id", orderId);
            pgSpan.tag("payment.amount", String.valueOf(amount));

            log.info("[Service B] Calling external payment gateway");

            // 외부 API 호출 시뮬레이션 (100ms 소요)
            simulateProcessing(100);

        } finally {
            pgSpan.end();
        }
    }

    /**
     * 결제 결과 DB 저장 (커스텀 Span)
     */
    private void savePaymentResult(String orderId, Integer amount) {
        Span dbSpan = tracer.nextSpan().name("db-insert-payment");

        try (Tracer.SpanInScope ws = tracer.withSpan(dbSpan.start())) {
            dbSpan.tag("db.operation", "INSERT");
            dbSpan.tag("db.table", "payments");

            log.info("[Service B] Saving payment result to database");

            // DB INSERT 시뮬레이션
            simulateProcessing(50);

        } finally {
            dbSpan.end();
        }
    }

    private void simulateProcessing(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
