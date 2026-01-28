package com.exam.concurrency.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ThreadLocalVariantRunner implements ApplicationRunner {

    // 1. 기본 ThreadLocal: 나만 볼 수 있음
    public static ThreadLocal<String> basicThreadLocal = new ThreadLocal<>();

    // 2. InheritableThreadLocal: 자식 스레드에게 상속됨
    public static InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== ThreadLocal Variant Test ===");

        // 부모 스레드(Main)에서 값 설정
        basicThreadLocal.set("Parent-Basic");
        inheritableThreadLocal.set("Parent-Inheritable");

        log.info("[Main] Basic: {}, Inheritable: {}", basicThreadLocal.get(), inheritableThreadLocal.get());

        // -------------------------------------------------------
        // Case 1: 자식 스레드를 직접 생성 (new Thread) -> 상속 성공
        // -------------------------------------------------------
        Thread childThread = new Thread(() -> {
            log.info("[Child-New] Basic: {}, Inheritable: {}", 
                    basicThreadLocal.get(),         // null (상속 안됨)
                    inheritableThreadLocal.get());  // Parent-Inheritable (상속 됨!)
        });
        childThread.start();
        childThread.join();

        // -------------------------------------------------------
        // Case 2: 스레드 풀 사용 (ThreadPool) -> 상속 실패 가능성 높음
        // -------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(1);
        
        // 첫 번째 작업: 스레드가 처음 생성되므로 상속받을 수도 있음
        executor.submit(() -> {
            log.info("[Pool-1] Basic: {}, Inheritable: {}", 
                    basicThreadLocal.get(), 
                    inheritableThreadLocal.get());
        }).get();

        // 부모 값 변경
        inheritableThreadLocal.set("Parent-Changed-Value");

        // 두 번째 작업: 이미 생성된 스레드를 재사용하므로, 변경된 부모 값을 못 가져옴 (과거의 망령)
        executor.submit(() -> {
            log.info("[Pool-2] Basic: {}, Inheritable: {} (Warning: Old value persisted!)", 
                    basicThreadLocal.get(), 
                    inheritableThreadLocal.get());
        }).get();

        executor.shutdown();
    }
}