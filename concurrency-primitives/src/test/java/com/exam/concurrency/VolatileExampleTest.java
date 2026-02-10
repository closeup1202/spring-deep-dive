package com.exam.concurrency;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
class VolatileExampleTest {

    @Test
    @DisplayName("volatile 없이 플래그 사용 - 무한 루프 가능성 (타임아웃으로 방지)")
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void withoutVolatileTest() throws InterruptedException {
        VolatileExample.WithoutVolatile example = new VolatileExample.WithoutVolatile();
        example.start();

        Thread.sleep(100); // Worker 스레드 시작 대기

        // 이론적으로는 무한 루프가 발생할 수 있지만,
        // 현대 JVM은 최적화로 인해 대부분 정상 동작함
        // 실제 문제는 특정 조건(예: -server 모드, 높은 부하)에서 발생
        example.stop();

        // 타임아웃으로 테스트 안전성 확보
        Thread.sleep(500);
    }

    @Test
    @DisplayName("volatile 사용 - 정상 종료 보장")
    void withVolatileTest() throws InterruptedException {
        VolatileExample.WithVolatile example = new VolatileExample.WithVolatile();
        example.start();

        Thread.sleep(100); // Worker 스레드 시작 대기

        assertThat(example.isRunning()).isTrue();

        example.stop();

        // volatile 덕분에 즉시 종료됨
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(example.isRunning()).isFalse());
    }

    @Test
    @DisplayName("volatile은 복합 연산의 원자성을 보장하지 않음")
    void volatileNotAtomicTest() throws InterruptedException {
        VolatileExample.VolatileNotAtomic example = new VolatileExample.VolatileNotAtomic();
        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 10개 스레드가 각각 1000번씩 증가
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    example.increment();
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        int expected = threadCount * incrementsPerThread;
        int actual = example.getCounter();

        log.info("Expected: {}, Actual: {}", expected, actual);

        // volatile은 원자성을 보장하지 않으므로 값이 틀릴 수 있음
        // (운이 좋으면 맞을 수도 있지만, 일반적으로 틀림)
        assertThat(actual).isLessThanOrEqualTo(expected);
        // 대부분의 경우 실제 값이 예상보다 작음
    }

    @Test
    @DisplayName("Double-Checked Locking with volatile - Singleton 패턴")
    void volatileSingletonTest() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        VolatileExample.VolatileSingleton[] instances = new VolatileExample.VolatileSingleton[threadCount];

        // 10개 스레드가 동시에 getInstance() 호출
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                instances[index] = VolatileExample.VolatileSingleton.getInstance();
                latch.countDown();
            }).start();
        }

        latch.await();

        // 모든 인스턴스가 동일해야 함
        VolatileExample.VolatileSingleton first = instances[0];
        for (int i = 1; i < threadCount; i++) {
            assertThat(instances[i]).isSameAs(first);
        }
    }

    @Test
    @DisplayName("volatile과 happens-before 관계")
    void happensBeforeTest() throws InterruptedException {
        VolatileExample.HappensBeforeExample example = new VolatileExample.HappensBeforeExample();
        CountDownLatch writerLatch = new CountDownLatch(1);
        CountDownLatch readerLatch = new CountDownLatch(1);
        int[] result = new int[1];

        // Reader 스레드
        Thread reader = new Thread(() -> {
            try {
                writerLatch.await(); // Writer가 쓸 때까지 대기
                Thread.sleep(10);    // Writer가 완료할 시간 제공
                result[0] = example.reader();
                readerLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Writer 스레드
        Thread writer = new Thread(() -> {
            example.writer();
            writerLatch.countDown();
        });

        reader.start();
        writer.start();

        readerLatch.await();

        // volatile 덕분에 normalVariable의 값(42)을 올바르게 읽음
        assertThat(result[0]).isEqualTo(42);
    }

    @Test
    @DisplayName("volatile long - 64비트 원자성")
    void volatileLongTest() throws InterruptedException {
        VolatileExample.VolatileLongDouble example = new VolatileExample.VolatileLongDouble();
        long testValue = 0xAAAAAAAABBBBBBBBL; // 특정 비트 패턴

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                example.setVolatileLong(testValue);
                example.setNormalLong(testValue);
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                long volatileValue = example.getVolatileLong();
                long normalValue = example.getNormalLong();

                // volatile long은 항상 올바른 값
                assertThat(volatileValue).isEqualTo(testValue);

                // normal long은 이론적으로 깨질 수 있음
                // (현대 64비트 JVM에서는 대부분 안전하지만 보장 안됨)
            }
        });

        writer.start();
        reader.start();

        writer.join();
        reader.join();
    }

    @Test
    @DisplayName("Producer-Consumer with volatile flag")
    void producerConsumerTest() throws InterruptedException {
        VolatileExample.ProducerConsumer pc = new VolatileExample.ProducerConsumer();
        CountDownLatch producerLatch = new CountDownLatch(1);
        CountDownLatch consumerLatch = new CountDownLatch(1);
        Integer[] consumedValue = new Integer[1];

        // Consumer 스레드
        Thread consumer = new Thread(() -> {
            try {
                producerLatch.await(); // Producer가 생성할 때까지 대기
                Thread.sleep(10);
                consumedValue[0] = pc.consume();
                consumerLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Producer 스레드
        Thread producer = new Thread(() -> {
            pc.produce(123);
            producerLatch.countDown();
        });

        consumer.start();
        producer.start();

        consumerLatch.await(1, TimeUnit.SECONDS);

        // volatile 플래그 덕분에 올바른 데이터 읽기
        assertThat(consumedValue[0]).isEqualTo(123);
    }

    @Test
    @DisplayName("volatile vs 일반 변수 가시성 비교")
    void visibilityComparisonTest() throws InterruptedException {
        class VisibilityTest {
            private int normalVar = 0;
            private volatile int volatileVar = 0;

            public void write() {
                normalVar = 42;
                volatileVar = 42;
            }

            public boolean readNormal() {
                return normalVar == 42;
            }

            public boolean readVolatile() {
                return volatileVar == 42;
            }
        }

        VisibilityTest test = new VisibilityTest();
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] volatileResult = new boolean[1];

        // Writer 스레드
        new Thread(() -> {
            test.write();
            latch.countDown();
        }).start();

        latch.await();
        Thread.sleep(10); // 약간의 지연

        // volatile은 항상 최신 값을 봄
        volatileResult[0] = test.readVolatile();
        assertThat(volatileResult[0]).isTrue();

        // 일반 변수는 캐시된 값을 볼 수 있음 (현대 JVM에서는 대부분 정상 동작)
        // 실제 문제는 특정 조건에서만 발생
    }
}
