package com.exam.threadpool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class ThreadPoolService {

    public void testCachedThreadPool() throws InterruptedException {
        log.info("=== Testing CachedThreadPool (Danger!) ===");
        // 특징: 스레드 수 제한 없음 (Integer.MAX_VALUE). 요청 오면 무조건 스레드 생성.
        // 60초 동안 안 쓰면 제거됨.
        ExecutorService executor = Executors.newCachedThreadPool();
        
        runTasks(executor, 100); // 100개 작업 투하
        
        printPoolStatus("Cached", executor);
        executor.shutdown();
    }

    public void testFixedThreadPool() throws InterruptedException {
        log.info("=== Testing FixedThreadPool (Safe) ===");
        // 특징: 고정된 스레드 수(5개). 나머지는 큐(LinkedBlockingQueue)에서 대기.
        // 주의: 큐 사이즈가 무제한(Integer.MAX_VALUE)이라서 OOM 발생 가능성 있음.
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        runTasks(executor, 100); // 100개 작업 투하
        
        printPoolStatus("Fixed", executor);
        executor.shutdown();
    }

    private void runTasks(ExecutorService executor, int taskCount) throws InterruptedException {
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(500); // 0.5초 대기
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(100); // 스레드 생성될 시간 잠깐 대기
    }

    private void printPoolStatus(String name, ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            log.info("[{}] Pool Size: {}, Active Threads: {}, Queue Size: {}",
                    name, tpe.getPoolSize(), tpe.getActiveCount(), tpe.getQueue().size());
        }
    }
}