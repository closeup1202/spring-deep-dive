package com.example.servicea.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Service A - 주문 처리 서비스
 *
 * Service B를 호출하여 재고 확인 및 결제 처리를 수행합니다.
 * WebClient는 자동으로 traceId를 HTTP 헤더에 포함시켜 전파합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final WebClient.Builder webClientBuilder;
    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;

    private static final String SERVICE_B_URL = "http://localhost:8081";

    public Map<String, Object> processOrder(String orderId, String productId, Integer quantity) {
        log.info("[Service A] Processing order: {} - product: {}, qty: {}", orderId, productId, quantity);

        // 1. 재고 확인 (Service B 호출)
        Map<String, Object> stockInfo = checkStock(productId, quantity);

        // 2. 결제 처리 (Service B 호출)
        Map<String, Object> paymentInfo = processPayment(orderId, productId, quantity);

        return Map.of(
                "stock", stockInfo,
                "payment", paymentInfo
        );
    }

    /**
     * Service B에 재고 확인 요청
     *
     * Observation을 사용하여 커스텀 Span 생성
     */
    private Map<String, Object> checkStock(String productId, Integer quantity) {
        return Observation.createNotStarted("check-stock", observationRegistry)
                .lowCardinalityKeyValue("product.id", productId)
                .observe(() -> {
                    log.info("[Service A] Calling Service B - Check stock for product: {}", productId);

                    // WebClient가 자동으로 traceId를 propagation
                    Map response = webClientBuilder.build()
                            .get()
                            .uri(SERVICE_B_URL + "/api/inventory/check?productId=" + productId + "&quantity=" + quantity)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    log.info("[Service A] Stock check response: {}", response);
                    return response;
                });
    }

    /**
     * Service B에 결제 처리 요청
     *
     * 커스텀 Span을 만들어 세부 작업 추적
     */
    private Map<String, Object> processPayment(String orderId, String productId, Integer quantity) {
        // 커스텀 Span 생성
        Span customSpan = tracer.nextSpan().name("payment-processing");

        try (Tracer.SpanInScope ws = tracer.withSpan(customSpan.start())) {
            customSpan.tag("order.id", orderId);
            customSpan.tag("payment.type", "credit-card");

            log.info("[Service A] Calling Service B - Process payment for order: {}", orderId);

            Map response = webClientBuilder.build()
                    .post()
                    .uri(SERVICE_B_URL + "/api/payment/process")
                    .bodyValue(Map.of(
                            "orderId", orderId,
                            "productId", productId,
                            "quantity", quantity,
                            "amount", quantity * 10000
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("[Service A] Payment response: {}", response);
            return response;

        } catch (Exception e) {
            customSpan.tag("error", "true");
            customSpan.error(e);
            throw e;
        } finally {
            customSpan.end();
        }
    }
}
