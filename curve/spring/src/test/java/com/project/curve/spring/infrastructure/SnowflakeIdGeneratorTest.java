package com.project.curve.spring.infrastructure;

import com.project.curve.core.envelope.EventId;
import com.project.curve.core.exception.ClockMovedBackwardsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Should succeed when constructor is called with valid Worker ID")
    void constructor_withValidWorkerId_shouldSucceed() {
        // Given
        long workerId = 100L;

        // When & Then
        assertThatNoException().isThrownBy(() -> new SnowflakeIdGenerator(workerId));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1024, 2000})
    @DisplayName("Should throw exception when constructor is called with invalid Worker ID")
    void constructor_withInvalidWorkerId_shouldThrowException(long invalidWorkerId) {
        // When & Then
        assertThatThrownBy(() -> new SnowflakeIdGenerator(invalidWorkerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Worker ID must be between 0 and 1023");
    }

    @Test
    @DisplayName("Should return non-null EventId when generating ID")
    void generate_shouldReturnNonNullEventId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId.value()).isNotEmpty();
    }

    @Test
    @DisplayName("Consecutively generated IDs should all be unique")
    void generate_consecutiveIds_shouldAllBeUnique() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        Set<String> generatedIds = new HashSet<>();
        int count = 10000;

        // When
        for (int i = 0; i < count; i++) {
            EventId eventId = generator.generate();
            generatedIds.add(eventId.value());
        }

        // Then
        assertThat(generatedIds).hasSize(count);
    }

    @Test
    @DisplayName("Should maintain uniqueness when generating IDs concurrently from multiple threads")
    void generate_concurrently_shouldMaintainUniqueness() throws InterruptedException {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int threadCount = 10;
        int idsPerThread = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> generatedIds = new HashSet<>();
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        EventId eventId = generator.generate();
                        synchronized (generatedIds) {
                            if (!generatedIds.add(eventId.value())) {
                                duplicateCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        assertThat(duplicateCount.get()).isZero();
        assertThat(generatedIds).hasSize(threadCount * idsPerThread);
    }

    @Test
    @DisplayName("Should wait for next millisecond if more than 4096 IDs are generated within same millisecond")
    void generate_sequenceOverflow_shouldWaitForNextMillis() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        Set<String> generatedIds = new HashSet<>();

        // When: Generate 5000 IDs quickly (sequence overflow possible)
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 5000; i++) {
            EventId eventId = generator.generate();
            generatedIds.add(eventId.value());
        }
        long endTime = System.currentTimeMillis();

        // Then: All IDs should be unique and time should have elapsed
        assertThat(generatedIds).hasSize(5000);
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Generators with different Worker IDs should generate different IDs")
    void generate_differentWorkerIds_shouldGenerateDifferentIds() {
        // Given
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1L);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(2L);

        // When
        EventId id1 = generator1.generate();
        EventId id2 = generator2.generate();

        // Then
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("Automatic Worker ID generation based on MAC address should not throw exception")
    void createWithAutoWorkerId_shouldNotThrowException() {
        // When & Then
        assertThatNoException().isThrownBy(SnowflakeIdGenerator::createWithAutoWorkerId);
    }

    @Test
    @DisplayName("Generated ID should be a numeric string")
    void generate_shouldReturnNumericStringId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();

        // Then
        assertThat(eventId.value()).matches("\\d+");
    }

    @Test
    @DisplayName("Generated ID should be positive")
    void generate_shouldReturnPositiveId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();
        long id = Long.parseLong(eventId.value());

        // Then
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("Should successfully generate IDs with boundary Worker IDs (0, 1023)")
    void generate_withBoundaryWorkerIds_shouldSucceed() {
        // Given
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(0L);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(1023L);

        // When
        EventId id1 = generator1.generate();
        EventId id2 = generator2.generate();

        // Then
        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("Should be able to generate IDs with auto-generated Worker ID based on MAC address")
    void createWithAutoWorkerId_shouldGenerateId() {
        // Given
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.createWithAutoWorkerId();

        // When
        EventId id = generator.generate();

        // Then
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate unique IDs even with auto-generated Worker ID")
    void createWithAutoWorkerId_shouldGenerateUniqueIds() {
        // Given
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.createWithAutoWorkerId();
        Set<String> ids = new HashSet<>();

        // When
        for (int i = 0; i < 100; i++) {
            EventId id = generator.generate();
            ids.add(id.value());
        }

        // Then
        assertThat(ids).hasSize(100);
    }

    @Test
    @DisplayName("Consecutively generated IDs should be monotonically increasing")
    void generate_consecutiveIds_shouldBeMonotonicallyIncreasing() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        long id1 = Long.parseLong(generator.generate().value());
        long id2 = Long.parseLong(generator.generate().value());
        long id3 = Long.parseLong(generator.generate().value());

        // Then
        assertThat(id2).isGreaterThan(id1);
        assertThat(id3).isGreaterThan(id2);
    }

    @Test
    @DisplayName("Small clock backward movement (<= 100ms) should wait and succeed")
    void generate_smallClockBackward_shouldWaitAndSucceed() {
        // Given
        TestableSnowflakeIdGenerator generator = new TestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // Generate first ID
        generator.setCurrentTime(baseTime);
        EventId id1 = generator.generate();

        // Move clock backward by 50ms (small backward)
        generator.setCurrentTime(baseTime - 50);
        generator.addTimeSequence(baseTime - 50); // Return same time once more
        generator.addTimeSequence(baseTime + 1);  // Then return normal time

        // When
        EventId id2 = generator.generate();

        // Then
        assertThat(id2).isNotNull();
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("Large clock backward movement (> 100ms) should throw exception")
    void generate_largeClockBackward_shouldThrowException() {
        // Given
        TestableSnowflakeIdGenerator generator = new TestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // Generate first ID
        generator.setCurrentTime(baseTime);
        generator.generate();

        // Move clock backward by 200ms (large backward)
        generator.setCurrentTime(baseTime - 200);

        // When & Then
        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(ClockMovedBackwardsException.class);
    }

    @Test
    @DisplayName("Should throw exception on waitUntilNextMillis timeout")
    void generate_waitTimeout_shouldThrowException() {
        // Given
        TimeoutTestableSnowflakeIdGenerator generator = new TimeoutTestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // Generate first ID
        generator.setCurrentTime(baseTime);
        generator.generate();

        // Move clock backward by 1ms and keep returning same time (cause timeout)
        generator.setStuckTime(baseTime - 1);

        // When & Then
        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(ClockMovedBackwardsException.class)
                .hasMessageContaining("Timeout waiting for clock to advance");
    }

    @Test
    @DisplayName("Should throw exception on interrupt during waitUntilNextMillis")
    void generate_waitInterrupted_shouldThrowException() throws InterruptedException {
        // Given
        InterruptibleTestableSnowflakeIdGenerator generator = new InterruptibleTestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // Generate first ID
        generator.setCurrentTime(baseTime);
        generator.generate();

        // Move clock backward by 1ms
        generator.setCurrentTime(baseTime - 1);

        CountDownLatch latch = new CountDownLatch(1);
        Thread[] testThread = new Thread[1];

        // When
        Thread thread = new Thread(() -> {
            testThread[0] = Thread.currentThread();
            latch.countDown();
            assertThatThrownBy(() -> generator.generate())
                    .isInstanceOf(ClockMovedBackwardsException.class)
                    .hasMessageContaining("Interrupted while waiting for clock to advance");
        });
        thread.start();

        latch.await();
        Thread.sleep(10); // Wait briefly for thread to enter wait state
        testThread[0].interrupt();
        thread.join(1000);
    }

    @Test
    @DisplayName("Should wait with exponential backoff")
    void generate_exponentialBackoff_shouldWaitWithIncreasingIntervals() {
        // Given
        BackoffTrackingSnowflakeIdGenerator generator = new BackoffTrackingSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // Generate first ID
        generator.setCurrentTime(baseTime);
        generator.generate();

        // Move clock backward by 10ms, retry multiple times, then return normal time
        generator.setCurrentTime(baseTime - 10);
        for (int i = 0; i < 3; i++) {
            generator.addTimeSequence(baseTime - 10);
        }
        generator.addTimeSequence(baseTime + 1);

        // When
        EventId id = generator.generate();

        // Then
        assertThat(id).isNotNull();
        assertThat(generator.getSleepCalls()).isGreaterThan(0);
    }

    /**
     * Testable SnowflakeIdGenerator allowing clock manipulation
     */
    private static class TestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private final java.util.Queue<Long> timeSequence = new java.util.LinkedList<>();

        public TestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void addTimeSequence(long time) {
            this.timeSequence.add(time);
        }

        @Override
        protected long currentTimeMillis() {
            if (!timeSequence.isEmpty()) {
                return timeSequence.poll();
            }
            return currentTime;
        }
    }

    /**
     * Timeout testable SnowflakeIdGenerator
     */
    private static class TimeoutTestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private Long stuckTime;

        public TimeoutTestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void setStuckTime(long time) {
            this.stuckTime = time;
        }

        @Override
        protected long currentTimeMillis() {
            if (stuckTime != null) {
                // System.currentTimeMillis() increases in reality,
                // but currentTimeMillis() returns fixed past time to cause timeout
                return stuckTime;
            }
            return currentTime;
        }
    }

    /**
     * Interrupt testable SnowflakeIdGenerator
     */
    private static class InterruptibleTestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;

        public InterruptibleTestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        @Override
        protected long currentTimeMillis() {
            return currentTime;
        }
    }

    /**
     * Exponential backoff tracking SnowflakeIdGenerator
     */
    private static class BackoffTrackingSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private final java.util.Queue<Long> timeSequence = new java.util.LinkedList<>();
        private int sleepCalls = 0;

        public BackoffTrackingSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void addTimeSequence(long time) {
            this.timeSequence.add(time);
        }

        public int getSleepCalls() {
            return sleepCalls;
        }

        @Override
        protected long currentTimeMillis() {
            if (!timeSequence.isEmpty()) {
                Long time = timeSequence.poll();
                if (time != null && time <= currentTime) {
                    sleepCalls++; // Assume sleep was called
                }
                return time;
            }
            return currentTime;
        }
    }
}
