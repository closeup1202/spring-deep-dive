package com.exam.kafka.domain;

import java.time.LocalDateTime;

/**
 * 주문 이벤트 도메인.
 * <p>
 * Kafka 메시지의 key = orderId → 같은 주문의 이벤트는 항상 같은 파티션으로 전송.
 * 이를 통해 같은 주문에 대한 메시지 순서(CREATED → PAID → SHIPPED)가 보장된다.
 */
public record OrderEvent(
    String orderId,
    String userId,
    String productId,
    long amount,
    OrderStatus status,
    LocalDateTime createdAt
) {
    public enum OrderStatus {
        CREATED, PAID, SHIPPED, CANCELLED, FAILED
    }

    public static OrderEvent created(String orderId, String userId, String productId, long amount) {
        return new OrderEvent(orderId, userId, productId, amount, OrderStatus.CREATED, LocalDateTime.now());
    }

    public static OrderEvent paid(String orderId, String userId, String productId, long amount) {
        return new OrderEvent(orderId, userId, productId, amount, OrderStatus.PAID, LocalDateTime.now());
    }

    public static OrderEvent failed(String orderId, String userId, String productId, long amount) {
        return new OrderEvent(orderId, userId, productId, amount, OrderStatus.FAILED, LocalDateTime.now());
    }
}
