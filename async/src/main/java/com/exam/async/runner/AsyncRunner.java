package com.exam.async.runner;

import com.exam.async.service.AsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncRunner implements ApplicationRunner {

    private final AsyncService asyncService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 1. Exception Handling Test ===");
        asyncService.runExceptionTask();
        Thread.sleep(100); // 로그 섞임 방지

        log.info("\n=== 2. Return Value Test ===");
        CompletableFuture<String> future = asyncService.runReturnTask();
        // 비동기 결과 대기 (Blocking)
        log.info("Result: {}", future.get());

        log.info("\n=== 3. Thread Pool Saturation Test (Load Test) ===");
        // 설정: Core(2), Queue(10), Max(5) -> 동시 처리 가능량 넘어서면 CallerRunsPolicy 동작
        for (int i = 1; i <= 20; i++) {
            asyncService.runAsyncTask(i);
        }
    }
}