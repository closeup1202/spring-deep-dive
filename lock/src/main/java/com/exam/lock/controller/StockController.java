package com.exam.lock.controller;

import com.exam.lock.deadlock.DeadlockDemo;
import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import com.exam.lock.service.OptimisticLockStockFacade;
import com.exam.lock.service.PessimisticLockStockService;
import com.exam.lock.service.RedissonLockStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockRepository stockRepository;
    private final PessimisticLockStockService pessimisticLockStockService;
    private final OptimisticLockStockFacade optimisticLockStockFacade; // Service -> Facade 변경
    private final RedissonLockStockService redissonLockStockService;
    private final DeadlockDemo deadlockDemo;

    // 초기 재고 생성
    @PostMapping("/stocks")
    public Long createStock(@RequestParam Long productId, @RequestParam Long quantity) {
        Stock stock = new Stock(productId, quantity);
        return stockRepository.save(stock).getId();
    }

    // 비관적 락 테스트
    @PostMapping("/stocks/{id}/decrease/pessimistic")
    public String decreasePessimistic(@PathVariable Long id, @RequestParam Long quantity) {
        pessimisticLockStockService.decrease(id, quantity);
        return "Pessimistic Lock Decrease Success";
    }

    // 낙관적 락 테스트
    @PostMapping("/stocks/{id}/decrease/optimistic")
    public String decreaseOptimistic(@PathVariable Long id, @RequestParam Long quantity) throws InterruptedException {
        optimisticLockStockFacade.decrease(id, quantity); // Facade 호출
        return "Optimistic Lock Decrease Success";
    }

    // 분산 락 테스트
    @PostMapping("/stocks/{id}/decrease/redisson")
    public String decreaseRedisson(@PathVariable Long id, @RequestParam Long quantity) {
        redissonLockStockService.decrease(id, quantity);
        return "Redisson Lock Decrease Success";
    }

    // 데드락 테스트
    @GetMapping("/deadlock")
    public String triggerDeadlock() {
        deadlockDemo.triggerDeadlock();
        return "Deadlock Triggered! Check Console.";
    }
}
