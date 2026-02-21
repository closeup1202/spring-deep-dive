package com.exam.threadpool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CustomPoolService {

    public void testCustomPoolFlow() {
        log.info("=== Testing Custom ThreadPoolExecutor Flow ===");
        
        // Core: 2, Max: 5, Queue: 10
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 5, 
                0L, TimeUnit.MILLISECONDS, 
                new ArrayBlockingQueue<>(10)
        );

        // 작업 16개 투입 (Max 5 + Queue 10 = 15개 처리 가능, 1개는 Reject 예상)
        for (int i = 1; i <= 16; i++) {
            try {
                executor.submit(() -> {
                    try {
                        Thread.sleep(2000); // 오래 걸리는 작업
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                log.info("Task {} submitted. Pool: {}, Queue: {}", i, executor.getPoolSize(), executor.getQueue().size());
            } catch (RejectedExecutionException e) {
                log.error("Task {} REJECTED! Pool: {}, Queue: {}", i, executor.getPoolSize(), executor.getQueue().size());
            }
        }
        
        executor.shutdown();
    }
}