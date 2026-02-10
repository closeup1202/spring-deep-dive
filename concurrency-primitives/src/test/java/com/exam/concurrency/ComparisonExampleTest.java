package com.exam.concurrency;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class ComparisonExampleTest {

    @Test
    @DisplayName("synchronized vs volatile vs Atomic - 카운터 비교")
    void counterComparisonTest() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount * 3);

        // 1. synchronized - 안전
        ComparisonExample.CounterComparison.SyncCounter syncCounter =
            new ComparisonExample.CounterComparison.SyncCounter();

        // 2. volatile - 불안전
        ComparisonExample.CounterComparison.VolatileCounter volatileCounter =
            new ComparisonExample.CounterComparison.VolatileCounter();

        // 3. Atomic - 안전
        ComparisonExample.CounterComparison.AtomCounter atomicCounter =
            new ComparisonExample.CounterComparison.AtomCounter();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    syncCounter.increment();
                }
                latch.countDown();
            }).start();

            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    volatileCounter.increment();
                }
                latch.countDown();
            }).start();

            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    atomicCounter.increment();
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        int expected = threadCount * incrementsPerThread;

        log.info("Expected: {}", expected);
        log.info("Synchronized: {}", syncCounter.get());
        log.info("Volatile: {}", volatileCounter.get());
        log.info("Atomic: {}", atomicCounter.get());

        // synchronized와 Atomic은 정확한 값
        assertThat(syncCounter.get()).isEqualTo(expected);
        assertThat(atomicCounter.get()).isEqualTo(expected);

        // volatile은 대부분 틀린 값 (복합 연산이 원자적이지 않음)
        assertThat(volatileCounter.get()).isLessThanOrEqualTo(expected);
    }

    @Test
    @DisplayName("플래그 사용 시나리오 - volatile 적합")
    void flagScenarioTest() throws InterruptedException {
        ComparisonExample.FlagComparison.VolatileFlag flag =
            new ComparisonExample.FlagComparison.VolatileFlag();

        assertThat(flag.getFlag()).isFalse();

        Thread worker = new Thread(() -> {
            while (!flag.getFlag()) {
                // 작업 수행
            }
            log.info("Worker stopped");
        });

        worker.start();
        Thread.sleep(100);

        flag.setFlag(true);
        worker.join(1000);

        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    @DisplayName("AtomicBoolean - CAS 기반 플래그")
    void atomicFlagTest() {
        ComparisonExample.FlagComparison.AtomicFlag flag =
            new ComparisonExample.FlagComparison.AtomicFlag();

        assertThat(flag.getFlag()).isFalse();

        // CAS로 플래그 변경
        boolean success = flag.compareAndSet(false, true);
        assertThat(success).isTrue();
        assertThat(flag.getFlag()).isTrue();

        // 이미 true이므로 실패
        success = flag.compareAndSet(false, true);
        assertThat(success).isFalse();
    }

    @Test
    @DisplayName("여러 변수의 일관성 - synchronized 필요")
    void multiVariableConsistencyTest() throws InterruptedException {
        int threadCount = 10;
        int depositsPerThread = 100;
        int depositAmount = 10;

        // synchronized - 일관성 보장
        ComparisonExample.MultiVariableComparison.SynchronizedAccount syncAccount =
            new ComparisonExample.MultiVariableComparison.SynchronizedAccount();

        CountDownLatch syncLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < depositsPerThread; j++) {
                    syncAccount.deposit(depositAmount);
                }
                syncLatch.countDown();
            }).start();
        }
        syncLatch.await();

        int expectedTransactions = threadCount * depositsPerThread;
        int expectedBalance = threadCount * depositsPerThread * depositAmount;

        assertThat(syncAccount.getTransactions()).isEqualTo(expectedTransactions);
        assertThat(syncAccount.getBalance()).isEqualTo(expectedBalance);

        log.info("Synchronized - Balance: {}, Transactions: {}",
            syncAccount.getBalance(), syncAccount.getTransactions());
    }

    @Test
    @DisplayName("Atomic 클래스로 여러 변수 - 일관성 보장 안됨")
    void atomicMultiVariableInconsistencyTest() throws InterruptedException {
        int threadCount = 10;
        int depositsPerThread = 100;
        int depositAmount = 10;

        ComparisonExample.MultiVariableComparison.AtomicAccount atomicAccount =
            new ComparisonExample.MultiVariableComparison.AtomicAccount();

        CountDownLatch atomicLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < depositsPerThread; j++) {
                    atomicAccount.deposit(depositAmount);
                }
                atomicLatch.countDown();
            }).start();
        }
        atomicLatch.await();

        int expectedTransactions = threadCount * depositsPerThread;
        int expectedBalance = threadCount * depositsPerThread * depositAmount;

        // 각 변수는 정확하지만, 읽는 시점에 일관성이 없을 수 있음
        assertThat(atomicAccount.getTransactions()).isEqualTo(expectedTransactions);
        assertThat(atomicAccount.getBalance()).isEqualTo(expectedBalance);

        log.info("Atomic - Balance: {}, Transactions: {}",
            atomicAccount.getBalance(), atomicAccount.getTransactions());

        // 주의: 이 테스트는 최종 값은 맞지만,
        // 중간 읽기 시점에 일관성이 깨질 수 있음을 보여줌
    }

    @Test
    @DisplayName("성능 비교 - synchronized vs Atomic (낮은 경합)")
    void performanceComparisonLowContentionTest() throws InterruptedException {
        int threadCount = 2; // 낮은 경합
        int incrementsPerThread = 1000000;

        // synchronized
        ComparisonExample.SynchronizedCounter syncCounter =
            new ComparisonExample.SynchronizedCounter();
        long syncStart = System.nanoTime();
        CountDownLatch syncLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    syncCounter.increment();
                }
                syncLatch.countDown();
            }).start();
        }
        syncLatch.await();
        long syncDuration = System.nanoTime() - syncStart;

        // Atomic
        ComparisonExample.AtomicCounter atomicCounter =
            new ComparisonExample.AtomicCounter();
        long atomicStart = System.nanoTime();
        CountDownLatch atomicLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    atomicCounter.increment();
                }
                atomicLatch.countDown();
            }).start();
        }
        atomicLatch.await();
        long atomicDuration = System.nanoTime() - atomicStart;

        log.info("Low Contention ({} threads)", threadCount);
        log.info("Synchronized: {}ms", syncDuration / 1_000_000);
        log.info("Atomic: {}ms", atomicDuration / 1_000_000);
        log.info("Speedup: {}x", (double) syncDuration / atomicDuration);

        // 낮은 경합에서 Atomic이 일반적으로 빠름
        assertThat(syncCounter.getCounter()).isEqualTo(threadCount * incrementsPerThread);
        assertThat(atomicCounter.getCounter()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    @DisplayName("성능 비교 - synchronized vs Atomic (높은 경합)")
    void performanceComparisonHighContentionTest() throws InterruptedException {
        int threadCount = 20; // 높은 경합
        int incrementsPerThread = 100000;

        // synchronized
        ComparisonExample.SynchronizedCounter syncCounter =
            new ComparisonExample.SynchronizedCounter();
        long syncStart = System.nanoTime();
        CountDownLatch syncLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    syncCounter.increment();
                }
                syncLatch.countDown();
            }).start();
        }
        syncLatch.await();
        long syncDuration = System.nanoTime() - syncStart;

        // Atomic
        ComparisonExample.AtomicCounter atomicCounter =
            new ComparisonExample.AtomicCounter();
        long atomicStart = System.nanoTime();
        CountDownLatch atomicLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    atomicCounter.increment();
                }
                atomicLatch.countDown();
            }).start();
        }
        atomicLatch.await();
        long atomicDuration = System.nanoTime() - atomicStart;

        log.info("High Contention ({} threads)", threadCount);
        log.info("Synchronized: {}ms", syncDuration / 1_000_000);
        log.info("Atomic: {}ms", atomicDuration / 1_000_000);
        log.info("Speedup: {}x", (double) syncDuration / atomicDuration);

        assertThat(syncCounter.getCounter()).isEqualTo(threadCount * incrementsPerThread);
        assertThat(atomicCounter.getCounter()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    @DisplayName("메모리 가시성 비교")
    void visibilityComparisonTest() throws InterruptedException {
        ComparisonExample.VisibilityComparison comparison =
            new ComparisonExample.VisibilityComparison();

        // synchronized를 통한 가시성 보장
        comparison.setSyncVar(42);
        assertThat(comparison.getSyncVar()).isEqualTo(42);

        // volatile, Atomic은 항상 최신 값 보장
        // 일반 변수는 가시성 보장 없음 (캐시된 값을 볼 수 있음)
    }

    @Test
    @DisplayName("실전 시나리오 - 요청 카운터")
    void requestCounterScenarioTest() throws InterruptedException {
        // AtomicLong을 사용한 요청 카운터 (가장 적합)
        java.util.concurrent.atomic.AtomicLong requestCounter =
            new java.util.concurrent.atomic.AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int requestCount = 10000;
        CountDownLatch latch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    // 요청 처리
                    requestCounter.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(requestCounter.get()).isEqualTo(requestCount);
        log.info("Total requests processed: {}", requestCounter.get());
    }

    @Test
    @DisplayName("실전 시나리오 - 연결 풀 관리")
    void connectionPoolScenarioTest() {
        // AtomicInteger로 활성 연결 수 관리
        java.util.concurrent.atomic.AtomicInteger activeConnections =
            new java.util.concurrent.atomic.AtomicInteger(0);
        final int MAX_CONNECTIONS = 10;

        // 연결 획득 시도
        boolean acquired = false;
        int current = activeConnections.get();
        while (current < MAX_CONNECTIONS) {
            if (activeConnections.compareAndSet(current, current + 1)) {
                acquired = true;
                break;
            }
            current = activeConnections.get();
        }

        if (acquired) {
            log.info("Connection acquired. Active: {}", activeConnections.get());
            assertThat(activeConnections.get()).isEqualTo(1);

            // 연결 해제
            activeConnections.decrementAndGet();
            assertThat(activeConnections.get()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("실전 시나리오 - Circuit Breaker 상태 관리")
    void circuitBreakerScenarioTest() {
        // volatile을 사용한 상태 플래그 (단순 읽기/쓰기)
        class CircuitBreakerState {
            private volatile boolean open = false;

            public void open() { open = true; }
            public void close() { open = false; }
            public boolean isOpen() { return open; }
        }

        CircuitBreakerState state = new CircuitBreakerState();

        assertThat(state.isOpen()).isFalse();
        state.open();
        assertThat(state.isOpen()).isTrue();
        state.close();
        assertThat(state.isOpen()).isFalse();
    }
}
