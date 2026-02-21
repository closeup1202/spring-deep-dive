package com.example.saga.service;

import com.example.saga.domain.Payment;
import com.example.saga.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;

    /**
     * 결제 처리
     */
    @Transactional
    public Payment processPayment(Long bookingId, String userId, BigDecimal amount) {
        log.info("Processing payment for booking: {}, amount: {}", bookingId, amount);

        // 실제로는 외부 결제 시스템 호출
        // 여기서는 간단히 DB에 저장
        Payment payment = new Payment(bookingId, userId, amount);
        return paymentRepository.save(payment);
    }

    /**
     * 결제 취소 (보상 트랜잭션)
     */
    @Transactional
    public void refundPayment(Long bookingId) {
        log.info("Refunding payment for booking: {}", bookingId);

        paymentRepository.findByBookingId(bookingId)
                .ifPresent(payment -> {
                    payment.refund();
                    paymentRepository.save(payment);
                    log.info("Payment refunded: {}", payment.getId());
                });
    }
}
