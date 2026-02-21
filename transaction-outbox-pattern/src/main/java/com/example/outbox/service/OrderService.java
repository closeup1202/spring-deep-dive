package com.example.outbox.service;

import com.example.outbox.domain.Order;
import com.example.outbox.domain.OutboxEvent;
import com.example.outbox.repository.OrderRepository;
import com.example.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 주문 생성 - 트랜잭션 아웃박스 패턴 적용
     * 1. 비즈니스 데이터(주문) 저장
     * 2. 이벤트를 아웃박스 테이블에 저장
     * 3. 모두 같은 트랜잭션 내에서 처리되어 원자성 보장
     */
    @Transactional
    public Order createOrder(String customerId, String productName, BigDecimal price, Integer quantity) {
        // 1. 비즈니스 로직: 주문 생성
        Order order = new Order(customerId, productName, price, quantity);
        Order savedOrder = orderRepository.save(order);
        log.info("Order created: {}", savedOrder.getId());

        // 2. 아웃박스 이벤트 생성 (같은 트랜잭션)
        try {
            String eventPayload = createOrderCreatedEventPayload(savedOrder);
            OutboxEvent outboxEvent = new OutboxEvent(
                    savedOrder.getId().toString(),
                    "Order",
                    "OrderCreated",
                    eventPayload
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event created for order: {}", savedOrder.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to create event payload", e);
            throw new RuntimeException("Failed to create event payload", e);
        }

        // 3. 트랜잭션 커밋 시 Order와 OutboxEvent가 함께 저장됨
        return savedOrder;
    }

    private String createOrderCreatedEventPayload(Order order) throws JsonProcessingException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("customerId", order.getCustomerId());
        payload.put("productName", order.getProductName());
        payload.put("price", order.getPrice());
        payload.put("quantity", order.getQuantity());
        payload.put("status", order.getStatus().name());
        payload.put("createdAt", order.getCreatedAt().toString());
        return objectMapper.writeValueAsString(payload);
    }
}
