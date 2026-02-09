package com.example.logging.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 주문 처리 서비스
 * MDC가 스레드를 따라 자동으로 전파되는 것을 확인할 수 있습니다.
 */
@Slf4j
@Service
public class OrderService {

    public String processOrder(String orderId, Integer amount) {
        log.info("Processing order in service layer - orderId: {}, amount: {}", orderId, amount);

        // 비즈니스 로직 처리
        validateOrder(orderId, amount);
        saveOrder(orderId, amount);
        sendNotification(orderId);

        log.info("Order processing completed - orderId: {}", orderId);
        return "Order processed successfully";
    }

    public String getOrderDetails(String orderId) {
        log.debug("Fetching order from database - orderId: {}", orderId);
        return "Order details for " + orderId;
    }

    public void processOrderWithError() {
        log.error("Simulating order processing error");
        throw new IllegalArgumentException("Invalid order data");
    }

    private void validateOrder(String orderId, Integer amount) {
        log.debug("Validating order - orderId: {}", orderId);
        if (amount == null || amount <= 0) {
            log.warn("Invalid order amount - orderId: {}, amount: {}", orderId, amount);
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void saveOrder(String orderId, Integer amount) {
        log.debug("Saving order to database - orderId: {}, amount: {}", orderId, amount);
        // DB 저장 로직
    }

    private void sendNotification(String orderId) {
        log.info("Sending notification for order - orderId: {}", orderId);
        // 알림 전송 로직 (이메일, SMS 등)
    }
}
