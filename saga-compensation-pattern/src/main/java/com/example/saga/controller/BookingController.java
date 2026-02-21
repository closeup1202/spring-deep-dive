package com.example.saga.controller;

import com.example.saga.domain.Booking;
import com.example.saga.service.BookingSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingSagaOrchestrator sagaOrchestrator;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        try {
            Booking booking = sagaOrchestrator.executeBookingSaga(
                    request.userId(),
                    request.productId(),
                    request.quantity(),
                    request.amount()
            );

            return ResponseEntity.ok(new BookingResponse(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getProductId(),
                    booking.getQuantity(),
                    booking.getAmount(),
                    booking.getState().name(),
                    null
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new BookingResponse(
                    null,
                    request.userId(),
                    request.productId(),
                    request.quantity(),
                    request.amount(),
                    "FAILED",
                    e.getMessage()
            ));
        }
    }

    public record BookingRequest(
            String userId,
            String productId,
            Integer quantity,
            BigDecimal amount
    ) {}

    public record BookingResponse(
            Long bookingId,
            String userId,
            String productId,
            Integer quantity,
            BigDecimal amount,
            String state,
            String error
    ) {}
}
