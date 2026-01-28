package com.exam.async.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "customTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);      // 기본 스레드 2개
        executor.setMaxPoolSize(5);       // 최대 스레드 5개
        executor.setQueueCapacity(10);    // 대기열 10개
        executor.setThreadNamePrefix("Async-Executor-");
        
        // 중요: 큐가 꽉 차고 스레드도 Max에 도달했을 때 처리 전략
        // CallerRunsPolicy: 요청한 스레드(Main)에서 직접 실행 (유실 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }

    // void 반환형 메서드에서 예외 발생 시 처리
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("=== Async Exception Caught ===");
            log.error("Method: {}", method.getName());
            log.error("Exception: {}", ex.getMessage());
            log.error("============================");
        };
    }
}