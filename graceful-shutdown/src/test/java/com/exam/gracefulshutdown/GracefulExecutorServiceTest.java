package com.exam.gracefulshutdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GracefulExecutorServiceTest {

    @Test
    @DisplayName("작업 완료 후 정상 종료 확인")
    void gracefulShutdownTest() throws InterruptedException {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        GracefulExecutorService gracefulExecutor = new GracefulExecutorService(delegate, 2);

        CountDownLatch latch = new CountDownLatch(1);

        gracefulExecutor.submit(() -> {
            try {
                Thread.sleep(500); // 0.5초 작업
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 종료 시작
        gracefulExecutor.shutdown();

        // 작업이 완료될 때까지 대기 (최대 2초)
        boolean completed = latch.await(1, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(gracefulExecutor.isShutdown()).isTrue();
        assertThat(gracefulExecutor.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("타임아웃 발생 시 강제 종료 확인")
    void forcedShutdownTest() throws InterruptedException {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        // 타임아웃 1초 설정
        GracefulExecutorService gracefulExecutor = new GracefulExecutorService(delegate, 1);

        gracefulExecutor.submit(() -> {
            try {
                Thread.sleep(5000); // 5초 작업 (타임아웃보다 김)
            } catch (InterruptedException e) {
                // 강제 종료 시 인터럽트 발생
                Thread.currentThread().interrupt();
            }
        });

        long start = System.currentTimeMillis();
        gracefulExecutor.shutdown(); // 여기서 1초 대기 후 강제 종료
        long end = System.currentTimeMillis();

        // 약 1초 정도 대기했는지 확인 (오차 범위 고려)
        assertThat(end - start).isGreaterThanOrEqualTo(1000);
        assertThat(gracefulExecutor.isShutdown()).isTrue();
        // 강제 종료되었으므로 terminated 상태여야 함
        assertThat(gracefulExecutor.isTerminated()).isTrue();
    }
}
