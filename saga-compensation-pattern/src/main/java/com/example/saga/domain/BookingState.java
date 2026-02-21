package com.example.saga.domain;

public enum BookingState {
    INITIATED,           // 시작됨
    PAYMENT_PENDING,     // 결제 대기
    PAYMENT_COMPLETED,   // 결제 완료
    INVENTORY_RESERVED,  // 재고 예약
    COMPLETED,           // 완료
    FAILED,              // 실패
    COMPENSATING,        // 보상 중
    COMPENSATED          // 보상 완료
}
