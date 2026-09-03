package com.exam.jvmheap.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 부하 테스트가 때릴 정상 엔드포인트. 베이스라인(기준선)을 만드는 데 쓴다. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Map<String, Object> place(@RequestParam(defaultValue = "5") int items) {
        OrderService.Order order = orderService.place(Math.min(items, 1000));
        return Map.of(
                "id", order.id(),
                "sequence", order.sequence(),
                "amount", order.amount(),
                "cached", orderService.cachedCount()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderService.Order> find(@PathVariable String id) {
        OrderService.Order order = orderService.find(id);
        return order == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(order);
    }
}
