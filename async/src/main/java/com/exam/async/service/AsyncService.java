package com.exam.async.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AsyncService {

    // 1. 커스텀 스레드 풀 사용
    @Async("customTaskExecutor")
    public void runAsyncTask(int index) {
        log.info("[Task {}] Start - {}", index, Thread.currentThread().getName());
        try {
            Thread.sleep(1000); // 작업 시뮬레이션
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("[Task {}] End - {}", index, Thread.currentThread().getName());
    }

    // 2. 예외 발생 테스트
    @Async("customTaskExecutor")
    public void runExceptionTask() {
        log.info("[Exception Task] Start - {}", Thread.currentThread().getName());
        throw new RuntimeException("Something went wrong in Async!");
    }

    // 3. 리턴값이 있는 비동기 (Future)
    @Async("customTaskExecutor")
    public CompletableFuture<String> runReturnTask() {
        log.info("[Return Task] Start - {}", Thread.currentThread().getName());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture("Success Result");
    }
}