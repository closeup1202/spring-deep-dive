package com.example.outbox.controller;

import com.example.outbox.domain.Order;
import com.example.outbox.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.createOrder(
                request.customerId(),
                request.productName(),
                request.price(),
                request.quantity()
        );

        return ResponseEntity.ok(new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductName(),
                order.getPrice(),
                order.getQuantity(),
                order.getStatus().name()
        ));
    }

    public record OrderRequest(
            String customerId,
            String productName,
            BigDecimal price,
            Integer quantity
    ) {}

    public record OrderResponse(
            Long orderId,
            String customerId,
            String productName,
            BigDecimal price,
            Integer quantity,
            String status
    ) {}
}
