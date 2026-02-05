package com.project.curve.spring.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GracefulExecutorService Test")
class GracefulExecutorServiceTest {

    @Nested
    @DisplayName("Constructor Test")
    class ConstructorTest {

        @Test
        @DisplayName("Should create with default timeout")
        void createWithDefaultTimeout() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When
            GracefulExecutorService executor = new GracefulExecutorService(delegate);

            // Then
            assertThat(executor.getTerminationTimeoutSeconds()).isEqualTo(30);
            executor.shutdown();
        }

        @Test
        @DisplayName("Should create with custom timeout")
        void createWithCustomTimeout() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 10);

            // Then
            assertThat(executor.getTerminationTimeoutSeconds()).isEqualTo(10);
            executor.shutdown();
        }

        @Test
        @DisplayName("Should throw exception if delegate is null")
        void createWithNullDelegate_shouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> new GracefulExecutorService(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("Should throw exception if timeout is not positive")
        void createWithInvalidTimeout_shouldThrowException() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When & Then
            assertThatThrownBy(() -> new GracefulExecutorService(delegate, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");

            delegate.shutdown();
        }
    }

    @Nested
    @DisplayName("Graceful Shutdown Test")
    class GracefulShutdownTest {

        @Test
        @DisplayName("Should wait for running tasks to complete")
        void waitForRunningTasks() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            AtomicBoolean taskCompleted = new AtomicBoolean(false);

            // When: Submit task that takes 2 seconds
            executor.submit(() -> {
                try {
                    Thread.sleep(2000);
                    taskCompleted.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Attempt immediate shutdown
            executor.shutdown();

            // Then: Task should be completed
            assertThat(taskCompleted.get()).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("Should force shutdown if timeout exceeded")
        void forceShutdownAfterTimeout() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 1);  // 1 second timeout

            CountDownLatch taskStarted = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean(false);

            // When: Submit task that takes 5 seconds (exceeds timeout)
            executor.submit(() -> {
                try {
                    taskStarted.countDown();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });

            taskStarted.await();  // Wait until task starts
            Thread.sleep(100);    // Ensure task is running

            executor.shutdown();

            // Then: Should be forced shutdown and interrupted
            assertThat(interrupted.get()).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("Multiple shutdown calls should be safe")
        void multipleShutdownCalls_shouldBeSafe() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When & Then
            assertThatNoException().isThrownBy(() -> {
                executor.shutdown();
                executor.shutdown();  // Second call
                executor.shutdown();  // Third call
            });
        }
    }

    @Nested
    @DisplayName("Task Execution Test")
    class TaskExecutionTest {

        @Test
        @DisplayName("Should submit Callable and get result")
        void submitCallable() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            Future<String> future = executor.submit(() -> "test-result");
            String result = future.get(1, TimeUnit.SECONDS);

            // Then
            assertThat(result).isEqualTo("test-result");

            executor.shutdown();
        }

        @Test
        @DisplayName("Should submit Runnable")
        void submitRunnable() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            AtomicInteger counter = new AtomicInteger(0);

            // When
            Future<?> future = executor.submit(counter::incrementAndGet);
            future.get(1, TimeUnit.SECONDS);

            // Then
            assertThat(counter.get()).isEqualTo(1);

            executor.shutdown();
        }

        @Test
        @DisplayName("Should execute Runnable")
        void executeRunnable() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            CountDownLatch latch = new CountDownLatch(1);

            // When
            executor.execute(latch::countDown);

            // Then
            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            executor.shutdown();
        }

        @Test
        @DisplayName("Should execute multiple tasks with invokeAll")
        void invokeAll() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            List<Callable<Integer>> tasks = List.of(
                () -> 1,
                () -> 2,
                () -> 3
            );

            // When
            List<Future<Integer>> futures = executor.invokeAll(tasks);

            // Then
            assertThat(futures).hasSize(3);
            assertThat(futures.stream().allMatch(Future::isDone)).isTrue();

            executor.shutdown();
        }

        @Test
        @DisplayName("Should return first completed result with invokeAny")
        void invokeAny() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            List<Callable<String>> tasks = List.of(
                () -> {
                    Thread.sleep(100);
                    return "slow";
                },
                () -> "fast"
            );

            // When
            String result = executor.invokeAny(tasks);

            // Then
            assertThat(result).isEqualTo("fast");

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("State Check Test")
    class StateTest {

        @Test
        @DisplayName("isShutdown() should return true after shutdown")
        void isShutdownAfterShutdown() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            executor.shutdown();

            // Then
            assertThat(executor.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("isTerminated() should return true after all tasks complete")
        void isTerminatedAfterCompletion() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            executor.submit(() -> {
                // Fast task
            });
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);

            // Then
            assertThat(terminated).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("shutdownNow() should return pending tasks")
        void shutdownNow_shouldReturnPendingTasks() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newSingleThreadExecutor();
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // Occupy thread with first task
            CountDownLatch taskRunning = new CountDownLatch(1);
            executor.submit(() -> {
                try {
                    taskRunning.countDown();
                    Thread.sleep(10000);  // Long task
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            taskRunning.await();  // Wait until first task starts

            // Submit second task (goes to queue)
            executor.submit(() -> {
                // Task that won't run
            });

            // When
            List<Runnable> pendingTasks = executor.shutdownNow();

            // Then
            assertThat(pendingTasks).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Exception Handling Test")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("Should force shutdown immediately on interrupt")
        void interruptDuringShutdown() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 10);

            CountDownLatch taskStarted = new CountDownLatch(1);

            executor.submit(() -> {
                try {
                    taskStarted.countDown();
                    Thread.sleep(20000);  // Long task
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            taskStarted.await();

            // When: Attempt shutdown in another thread and interrupt
            Thread shutdownThread = new Thread(() -> executor.shutdown());
            shutdownThread.start();
            Thread.sleep(100);
            shutdownThread.interrupt();  // Interrupt while waiting for shutdown

            shutdownThread.join(5000);

            // Then
            assertThat(executor.isShutdown()).isTrue();
        }
    }
}
