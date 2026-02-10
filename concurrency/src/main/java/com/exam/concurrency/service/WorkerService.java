package com.exam.concurrency.service;

import com.exam.concurrency.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class WorkerService {

    private final ThreadPoolTaskExecutor taskExecutor;

    public WorkerService(@Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void executeWorkers(int workerCount) throws InterruptedException {
        log.info("=== Start Workers (Count: {}) ===", workerCount);

        // Latch 생성: workerCount만큼 카운트다운해야 문이 열림
        CountDownLatch latch = new CountDownLatch(workerCount);

        for (int i = 1; i <= workerCount; i++) {
            int workerId = i;
            taskExecutor.execute(() -> {
                try {
                    // 1. ThreadLocal 테스트: 각 스레드마다 다른 사용자 정보 세팅
                    String username = "User-" + workerId;
                    UserContextHolder.set(username);

                    log.info("[Worker {}] Working... (User: {})", workerId, UserContextHolder.get());
                    Thread.sleep(1000); // 작업 시뮬레이션

                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                } finally {
                    // 2. 중요: ThreadLocal 정리
                    UserContextHolder.clear();
                    
                    // 3. 작업 완료 신호
                    latch.countDown();
                    log.info("[Worker {}] Finished. Latch count: {}", workerId, latch.getCount());
                }
            });
        }

        // 메인 스레드는 여기서 대기 (Latch가 0이 될 때까지)
        log.info("Main thread is waiting for workers...");
        latch.await(); 
        
        log.info("=== All Workers Finished! ===");
    }
}