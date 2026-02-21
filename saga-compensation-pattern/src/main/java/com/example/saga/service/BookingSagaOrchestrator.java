package com.example.saga.service;

import com.example.saga.domain.Booking;
import com.example.saga.domain.BookingState;
import com.example.saga.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga Orchestrator - 중앙 집중식 조정자
 *
 * 전체 Saga의 흐름을 제어하고 보상 트랜잭션을 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSagaOrchestrator {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;

    /**
     * Saga 실행: 예약 -> 결제 -> 재고예약
     *
     * 각 단계에서 실패 시 이미 실행된 단계들을 역순으로 보상(compensate)합니다.
     */
    @Transactional
    public Booking executeBookingSaga(String userId, String productId, Integer quantity, BigDecimal amount) {
        // 1. 예약 생성
        Booking booking = createBooking(userId, productId, quantity, amount);

        try {
            // 2. 결제 처리
            processPaymentStep(booking);

            // 3. 재고 예약
            reserveInventoryStep(booking);

            // 4. 성공 - 완료 상태로 변경
            booking.updateState(BookingState.COMPLETED);
            bookingRepository.save(booking);

            log.info("Booking saga completed successfully: {}", booking.getId());
            return booking;

        } catch (Exception e) {
            log.error("Booking saga failed: {}", e.getMessage());

            // 보상 트랜잭션 실행
            compensate(booking, e.getMessage());

            throw new RuntimeException("예약 처리 실패: " + e.getMessage(), e);
        }
    }

    private Booking createBooking(String userId, String productId, Integer quantity, BigDecimal amount) {
        Booking booking = new Booking(userId, productId, quantity, amount);
        booking.updateState(BookingState.INITIATED);
        return bookingRepository.save(booking);
    }

    private void processPaymentStep(Booking booking) {
        log.info("Step 1: Processing payment for booking {}", booking.getId());
        booking.updateState(BookingState.PAYMENT_PENDING);
        bookingRepository.save(booking);

        paymentService.processPayment(booking.getId(), booking.getUserId(), booking.getAmount());

        booking.updateState(BookingState.PAYMENT_COMPLETED);
        bookingRepository.save(booking);
    }

    private void reserveInventoryStep(Booking booking) {
        log.info("Step 2: Reserving inventory for booking {}", booking.getId());

        inventoryService.reserveInventory(booking.getProductId(), booking.getQuantity());

        booking.updateState(BookingState.INVENTORY_RESERVED);
        bookingRepository.save(booking);
    }

    /**
     * 보상 트랜잭션 실행
     *
     * 실행된 단계를 역순으로 되돌립니다.
     */
    private void compensate(Booking booking, String reason) {
        log.info("Starting compensation for booking: {}", booking.getId());

        booking.updateState(BookingState.COMPENSATING);
        bookingRepository.save(booking);

        // 역순으로 보상 실행
        switch (booking.getState()) {
            case INVENTORY_RESERVED:
                // 재고 예약 취소
                log.info("Compensating: Releasing inventory");
                inventoryService.releaseInventory(booking.getProductId(), booking.getQuantity());
                // fall through to compensate payment

            case PAYMENT_COMPLETED:
            case PAYMENT_PENDING:
                // 결제 취소
                log.info("Compensating: Refunding payment");
                paymentService.refundPayment(booking.getId());
                break;

            default:
                log.info("No compensation needed for state: {}", booking.getState());
        }

        booking.markAsFailed(reason);
        booking.updateState(BookingState.COMPENSATED);
        bookingRepository.save(booking);

        log.info("Compensation completed for booking: {}", booking.getId());
    }
}
