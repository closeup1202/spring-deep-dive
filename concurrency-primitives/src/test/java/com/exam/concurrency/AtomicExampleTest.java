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
class AtomicExampleTest {

    @Test
    @DisplayName("AtomicInteger - 기본 연산")
    void atomicIntegerBasicTest() {
        AtomicExample.AtomicIntegerExample example = new AtomicExample.AtomicIntegerExample();

        assertThat(example.getCounter()).isEqualTo(0);

        assertThat(example.increment()).isEqualTo(1);
        assertThat(example.increment()).isEqualTo(2);
        assertThat(example.decrement()).isEqualTo(1);
        assertThat(example.addAndGet(10)).isEqualTo(11);
        assertThat(example.getCounter()).isEqualTo(11);
    }

    @Test
    @DisplayName("AtomicInteger - CAS 연산")
    void atomicIntegerCasTest() {
        AtomicExample.AtomicIntegerExample example = new AtomicExample.AtomicIntegerExample();

        // 현재 값이 0이면 100으로 설정
        boolean success1 = example.compareAndSet(0, 100);
        assertThat(success1).isTrue();
        assertThat(example.getCounter()).isEqualTo(100);

        // 현재 값이 0이 아니므로 실패
        boolean success2 = example.compareAndSet(0, 200);
        assertThat(success2).isFalse();
        assertThat(example.getCounter()).isEqualTo(100);

        // 현재 값이 100이므로 200으로 설정 성공
        boolean success3 = example.compareAndSet(100, 200);
        assertThat(success3).isTrue();
        assertThat(example.getCounter()).isEqualTo(200);
    }

    @Test
    @DisplayName("AtomicInteger - 함수형 업데이트")
    void atomicIntegerFunctionalUpdateTest() {
        AtomicExample.AtomicIntegerExample example = new AtomicExample.AtomicIntegerExample();

        example.increment(); // 1
        example.increment(); // 2

        // 2 * 3 = 6
        int result = example.updateAndGet(3);
        assertThat(result).isEqualTo(6);
        assertThat(example.getCounter()).isEqualTo(6);
    }

    @Test
    @DisplayName("AtomicInteger - 멀티스레드 환경에서 안전성")
    void atomicIntegerThreadSafetyTest() throws InterruptedException {
        AtomicExample.AtomicIntegerExample example = new AtomicExample.AtomicIntegerExample();
        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    example.increment();
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        // 모든 증가가 정확히 반영됨
        assertThat(example.getCounter()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    @DisplayName("AtomicLong - 성공률 계산")
    void atomicLongTest() {
        AtomicExample.AtomicLongExample example = new AtomicExample.AtomicLongExample();

        example.recordRequest(true);
        example.recordRequest(true);
        example.recordRequest(false);
        example.recordRequest(true);

        // 4번 중 3번 성공 = 75%
        assertThat(example.getSuccessRate()).isEqualTo(75.0);

        example.reset();
        assertThat(example.getSuccessRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("AtomicBoolean - 한 번만 실행되는 초기화")
    void atomicBooleanTest() {
        AtomicExample.AtomicBooleanExample example = new AtomicExample.AtomicBooleanExample();

        // 첫 번째 호출 - 성공
        assertThat(example.initializeOnce()).isTrue();

        // 두 번째 호출 - 실패 (이미 초기화됨)
        assertThat(example.initializeOnce()).isFalse();
        assertThat(example.initializeOnce()).isFalse();

        example.reset();

        // 리셋 후 다시 성공
        assertThat(example.initializeOnce()).isTrue();
    }

    @Test
    @DisplayName("AtomicBoolean - 멀티스레드 초기화 경합")
    void atomicBooleanRaceTest() throws InterruptedException {
        AtomicExample.AtomicBooleanExample example = new AtomicExample.AtomicBooleanExample();
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        boolean[] results = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                results[index] = example.initializeOnce();
                latch.countDown();
            }).start();
        }

        latch.await();

        // 정확히 하나의 스레드만 성공해야 함
        long successCount = 0;
        for (boolean result : results) {
            if (result) successCount++;
        }
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    @DisplayName("AtomicReference - 객체 참조의 원자적 업데이트")
    void atomicReferenceTest() {
        AtomicExample.AtomicReferenceExample example = new AtomicExample.AtomicReferenceExample();

        // 초기 상태
        assertThat(example.getCurrentUser().name).isEqualTo("Unknown");
        assertThat(example.getCurrentUser().age).isEqualTo(0);

        // 사용자 업데이트
        example.updateUser("Alice", 25);
        assertThat(example.getCurrentUser().name).isEqualTo("Alice");
        assertThat(example.getCurrentUser().age).isEqualTo(25);

        // 나이 증가
        var updated = example.incrementAge();
        assertThat(updated.age).isEqualTo(26);
        assertThat(example.getCurrentUser().age).isEqualTo(26);
    }

    @Test
    @DisplayName("AtomicReference - CAS로 조건부 업데이트")
    void atomicReferenceCasTest() {
        AtomicExample.AtomicReferenceExample example = new AtomicExample.AtomicReferenceExample();

        var currentUser = example.getCurrentUser();
        var newUser = new AtomicExample.AtomicReferenceExample.User("Bob", 30);

        // 현재 사용자가 예상과 같으면 변경 성공
        boolean success1 = example.updateIfMatches(currentUser, newUser);
        assertThat(success1).isTrue();
        assertThat(example.getCurrentUser().name).isEqualTo("Bob");

        // 현재 사용자가 예상과 다르므로 실패
        boolean success2 = example.updateIfMatches(currentUser, newUser);
        assertThat(success2).isFalse();
    }

    @Test
    @DisplayName("AtomicIntegerArray - 배열 요소의 원자적 업데이트")
    void atomicIntegerArrayTest() {
        AtomicExample.AtomicIntegerArrayExample example = new AtomicExample.AtomicIntegerArrayExample();

        // 인덱스별 카운터 증가
        example.incrementCounter(0);
        example.incrementCounter(0);
        example.incrementCounter(1);
        example.addToCounter(2, 10);

        assertThat(example.getTotal()).isEqualTo(13); // 2 + 1 + 10
    }

    @Test
    @DisplayName("AtomicIntegerArray - 멀티스레드 환경")
    void atomicIntegerArrayThreadSafetyTest() throws InterruptedException {
        AtomicExample.AtomicIntegerArrayExample example = new AtomicExample.AtomicIntegerArrayExample();
        int threadCount = 10;
        int incrementsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    example.incrementCounter(threadIndex % 10);
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        assertThat(example.getTotal()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    @DisplayName("AtomicStampedReference - ABA 문제 해결")
    void atomicStampedReferenceTest() {
        AtomicExample.AtomicStampedReferenceExample example =
            new AtomicExample.AtomicStampedReferenceExample();

        assertThat(example.getAccount().balance).isEqualTo(1000);
        assertThat(example.getStamp()).isEqualTo(0);

        // 첫 번째 업데이트
        example.updateBalance(1500);
        assertThat(example.getAccount().balance).isEqualTo(1500);
        assertThat(example.getStamp()).isEqualTo(1);

        // 두 번째 업데이트
        example.updateBalance(2000);
        assertThat(example.getAccount().balance).isEqualTo(2000);
        assertThat(example.getStamp()).isEqualTo(2);
    }

    @Test
    @DisplayName("LongAdder - 기본 연산")
    void longAdderBasicTest() {
        AtomicExample.LongAdderExample example = new AtomicExample.LongAdderExample();

        example.increment();
        example.increment();
        example.add(10);

        assertThat(example.sum()).isEqualTo(12);

        long sumBeforeReset = example.sumThenReset();
        assertThat(sumBeforeReset).isEqualTo(12);
        assertThat(example.sum()).isEqualTo(0);
    }

    @Test
    @DisplayName("LongAdder vs AtomicLong 성능 비교")
    void longAdderPerformanceTest() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100000;

        // AtomicLong 테스트
        java.util.concurrent.atomic.AtomicLong atomicLong = new java.util.concurrent.atomic.AtomicLong();
        long atomicStartTime = System.nanoTime();
        CountDownLatch atomicLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    atomicLong.incrementAndGet();
                }
                atomicLatch.countDown();
            }).start();
        }
        atomicLatch.await();
        long atomicDuration = System.nanoTime() - atomicStartTime;

        // LongAdder 테스트
        AtomicExample.LongAdderExample longAdder = new AtomicExample.LongAdderExample();
        long adderStartTime = System.nanoTime();
        CountDownLatch adderLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    longAdder.increment();
                }
                adderLatch.countDown();
            }).start();
        }
        adderLatch.await();
        long adderDuration = System.nanoTime() - adderStartTime;

        log.info("AtomicLong: {}ms", atomicDuration / 1_000_000);
        log.info("LongAdder: {}ms", adderDuration / 1_000_000);

        // 결과 검증
        assertThat(atomicLong.get()).isEqualTo(threadCount * incrementsPerThread);
        assertThat(longAdder.sum()).isEqualTo(threadCount * incrementsPerThread);

        // 높은 경합 상황에서 LongAdder가 일반적으로 빠름
        log.info("LongAdder speedup: {}x", (double) atomicDuration / adderDuration);
    }

    @Test
    @DisplayName("LongAccumulator - 최댓값/최솟값 추적")
    void longAccumulatorTest() {
        AtomicExample.LongAccumulatorExample example = new AtomicExample.LongAccumulatorExample();

        example.record(10);
        example.record(5);
        example.record(15);
        example.record(3);
        example.record(12);

        assertThat(example.getMax()).isEqualTo(15);
        assertThat(example.getMin()).isEqualTo(3);

        example.reset();
        assertThat(example.getMax()).isEqualTo(Long.MIN_VALUE);
        assertThat(example.getMin()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("DoubleAdder - double 타입 누산")
    void doubleAdderTest() {
        AtomicExample.DoubleAdderExample example = new AtomicExample.DoubleAdderExample();

        example.addAmount(10.5);
        example.addAmount(20.3);
        example.addAmount(5.2);

        assertThat(example.getTotal()).isCloseTo(36.0, org.assertj.core.data.Offset.offset(0.01));

        example.reset();
        assertThat(example.getTotal()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("AtomicMarkableReference - 작업 할당 및 완료")
    void atomicMarkableReferenceTest() {
        AtomicExample.AtomicMarkableReferenceExample example =
            new AtomicExample.AtomicMarkableReferenceExample();

        var task1 = new AtomicExample.AtomicMarkableReferenceExample.Task("Task 1");
        var task2 = new AtomicExample.AtomicMarkableReferenceExample.Task("Task 2");

        // 작업 할당
        assertThat(example.assignTask(task1)).isTrue();
        assertThat(example.isTaskCompleted()).isFalse();

        // 이미 작업이 할당되어 있어 실패
        assertThat(example.assignTask(task2)).isFalse();

        // 작업 완료
        assertThat(example.completeTask(task1)).isTrue();
        assertThat(example.isTaskCompleted()).isTrue();

        // 이미 완료된 작업 재완료 시도 - 실패
        assertThat(example.completeTask(task1)).isFalse();
    }

    @Test
    @DisplayName("멀티스레드 환경에서 Atomic 클래스들의 일관성")
    void atomicConsistencyTest() throws InterruptedException {
        AtomicExample.AtomicIntegerExample counter = new AtomicExample.AtomicIntegerExample();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int tasksCount = 1000;
        CountDownLatch latch = new CountDownLatch(tasksCount);

        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                try {
                    counter.increment();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // 모든 연산이 손실 없이 반영됨
        assertThat(counter.getCounter()).isEqualTo(tasksCount);
    }
}
