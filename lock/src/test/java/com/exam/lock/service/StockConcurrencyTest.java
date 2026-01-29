package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private PessimisticLockStockService pessimisticLockStockService;

    @Autowired
    private OptimisticLockStockFacade optimisticLockStockFacade; // Service -> Facade 변경

    @Autowired
    private RedissonLockStockService redissonLockStockService;

    @Autowired
    private StockRepository stockRepository;

    private Long stockId;

    @BeforeEach
    void setUp() {
        // 테스트 전 재고 100개 생성
        Stock stock = new Stock(1L, 100L);
        stockRepository.saveAndFlush(stock);
        stockId = stock.getId();
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("비관적 락: 100명이 동시에 1개씩 주문하면 재고가 0이 되어야 한다")
    void pessimisticLockTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pessimisticLockStockService.decrease(stockId, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(0L);
        System.out.println("비관적 락 테스트 성공: 남은 재고 = " + stock.getQuantity());
    }

    @Test
    @DisplayName("낙관적 락: 100명이 동시에 1개씩 주문하면 재고가 0이 되어야 한다")
    void optimisticLockTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // Facade를 통해 재시도 로직이 포함된 메서드 호출
                    optimisticLockStockFacade.decrease(stockId, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(0L);
        System.out.println("낙관적 락 테스트 성공: 남은 재고 = " + stock.getQuantity());
    }

    @Test
    @DisplayName("Redisson 분산 락: 100명이 동시에 1개씩 주문하면 재고가 0이 되어야 한다")
    void redissonLockTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    redissonLockStockService.decrease(stockId, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(0L);
        System.out.println("Redisson 락 테스트 성공: 남은 재고 = " + stock.getQuantity());
    }
}
