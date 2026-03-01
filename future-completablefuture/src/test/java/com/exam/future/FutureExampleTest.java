package com.exam.future;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class FutureExampleTest {

    private FutureExample.BasicFutureExample basicExample = new FutureExample.BasicFutureExample();
    private FutureExample.FutureStateExample stateExample = new FutureExample.FutureStateExample();
    private FutureExample.FutureLimitationExample limitExample = new FutureExample.FutureLimitationExample();
    private FutureExample.CallableVsRunnableExample callableExample = new FutureExample.CallableVsRunnableExample();
    private FutureExample.ExecutorInvokeExample invokeExample = new FutureExample.ExecutorInvokeExample();

    @AfterEach
    void tearDown() {
        basicExample.shutdown();
        stateExample.shutdown();
        limitExample.shutdown();
        callableExample.shutdown();
        invokeExample.shutdown();
    }

    @Test
    @DisplayName("Future - 비동기 작업 결과 가져오기")
    void basicFutureGetTest() throws ExecutionException, InterruptedException {
        Future<String> future = basicExample.fetchDataAsync("https://example.com");

        assertThat(future.isDone()).isFalse(); // 아직 완료 전일 수 있음

        String result = future.get(); // 결과가 나올 때까지 블로킹
        assertThat(result).isEqualTo("Response from https://example.com");
        assertThat(future.isDone()).isTrue();
    }

    @Test
    @DisplayName("Future - 산술 계산 비동기 처리")
    void futureCalculationTest() throws ExecutionException, InterruptedException {
        Future<Integer> future = basicExample.calculateAsync(10, 20);
        assertThat(future.get()).isEqualTo(30);
    }

    @Test
    @DisplayName("Future - 타임아웃 초과 시 TimeoutException 발생")
    void futureTimeoutTest() {
        Future<String> future = basicExample.fetchDataAsync("https://slow.example.com");

        // 1ms 타임아웃 — 100ms 작업이므로 반드시 TimeoutException 발생
        assertThatThrownBy(() ->
                basicExample.getResultWithTimeout(future, 1, TimeUnit.MILLISECONDS)
        ).isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("Future - 취소 후 CancellationException 발생")
    void futureCancelTest() throws InterruptedException {
        Future<String> future = stateExample.submitLongTask();

        Thread.sleep(10); // 작업 시작 대기

        boolean cancelled = stateExample.cancel(future, true);
        assertThat(cancelled).isTrue();
        assertThat(stateExample.isCancelled(future)).isTrue();
        assertThat(stateExample.isDone(future)).isTrue(); // 취소도 완료로 간주

        assertThatThrownBy(future::get)
                .isInstanceOf(CancellationException.class);
    }

    @Test
    @DisplayName("Future - isDone은 블로킹 없이 완료 여부 확인")
    void futureIsDoneNonBlockingTest() throws InterruptedException, ExecutionException {
        Future<String> future = stateExample.submitLongTask();

        // 즉시 확인 — 아직 완료 안 됐을 가능성 높음
        boolean doneBefore = stateExample.isDone(future);
        log.info("Before wait: isDone={}", doneBefore);

        future.get(); // 완료 대기
        assertThat(stateExample.isDone(future)).isTrue();
    }

    @Test
    @DisplayName("Callable - 예외가 ExecutionException으로 래핑됨")
    void callableExceptionWrappingTest() {
        Future<String> future = callableExample.submitFailingCallable();

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Task failed!");
    }

    @Test
    @DisplayName("Runnable - Future.get()은 항상 null 반환")
    void runnableFutureNullResultTest() throws ExecutionException, InterruptedException {
        Future<?> future = callableExample.submitRunnable(() -> log.info("Runnable executed"));
        Object result = future.get(); // 블로킹 후 완료
        assertThat(result).isNull(); // Runnable은 반환값 없음
    }

    @Test
    @DisplayName("invokeAll - 모든 작업 완료 후 결과 목록 반환")
    void invokeAllTest() throws InterruptedException, ExecutionException {
        List<Callable<String>> tasks = List.of(
                () -> { Thread.sleep(50); return "Task1"; },
                () -> { Thread.sleep(30); return "Task2"; },
                () -> { Thread.sleep(10); return "Task3"; }
        );

        List<Future<String>> futures = invokeExample.invokeAll(tasks);
        assertThat(futures).hasSize(3);

        // 모두 완료된 상태
        for (Future<String> future : futures) {
            assertThat(future.isDone()).isTrue();
        }

        List<String> results = List.of(
                futures.get(0).get(),
                futures.get(1).get(),
                futures.get(2).get()
        );
        assertThat(results).containsExactly("Task1", "Task2", "Task3");
    }

    @Test
    @DisplayName("invokeAny - 가장 먼저 완료된 결과 반환")
    void invokeAnyTest() throws ExecutionException, InterruptedException {
        List<Callable<String>> tasks = List.of(
                () -> { Thread.sleep(300); return "Slow"; },
                () -> { Thread.sleep(10);  return "Fast"; },
                () -> { Thread.sleep(200); return "Medium"; }
        );

        String result = invokeExample.invokeAny(tasks);
        // 가장 빠른 작업의 결과가 반환됨
        assertThat(result).isEqualTo("Fast");
    }

    @Test
    @DisplayName("Future 한계 - 병렬 작업도 get() 호출은 순차적")
    void futureLimitationTest() throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        String result = limitExample.sequentialWait("test");
        long elapsed = System.currentTimeMillis() - start;

        log.info("Elapsed: {}ms", elapsed);
        assertThat(result).isEqualTo("test-step1, test-step2, test-step3");
        // 작업은 병렬이지만 get()은 순서대로 기다림 (~100ms)
        assertThat(elapsed).isLessThan(400); // 세 작업이 병렬 실행됨
    }
}
