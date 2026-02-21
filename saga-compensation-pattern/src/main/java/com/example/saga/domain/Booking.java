package com.example.saga.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private BookingState state;

    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Booking(String userId, String productId, Integer quantity, BigDecimal amount) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.state = BookingState.INITIATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateState(BookingState newState) {
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.state = BookingState.FAILED;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
}
