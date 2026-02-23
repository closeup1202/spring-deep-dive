package com.exam.future;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class CompletableFutureBasicExampleTest {

    private final CompletableFutureBasicExample.CreationExample creation =
            new CompletableFutureBasicExample.CreationExample();
    private final CompletableFutureBasicExample.ManualCompletionExample manual =
            new CompletableFutureBasicExample.ManualCompletionExample();
    private final CompletableFutureBasicExample.RetrievalExample retrieval =
            new CompletableFutureBasicExample.RetrievalExample();

    @AfterEach
    void tearDown() {
        creation.shutdown();
    }

    @Test
    @DisplayName("supplyAsync - 비동기 결과 생성")
    void supplyAsyncTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> cf = creation.supplyAsync("hello");
        String result = cf.get();
        assertThat(result).isEqualTo("Result: hello");
    }

    @Test
    @DisplayName("supplyAsync - 커스텀 Executor 사용")
    void supplyAsyncWithExecutorTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> cf = creation.supplyAsyncWithExecutor("world");
        assertThat(cf.get()).isEqualTo("Result: world");
    }

    @Test
    @DisplayName("runAsync - 반환값 없는 비동기 작업")
    void runAsyncTest() throws ExecutionException, InterruptedException {
        boolean[] executed = {false};
        CompletableFuture<Void> cf = creation.runAsync(() -> executed[0] = true);
        cf.get(); // 완료 대기
        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("completedFuture - 즉시 완료된 CF")
    void completedFutureTest() {
        CompletableFuture<String> cf = creation.alreadyCompleted("immediate");
        assertThat(cf.isDone()).isTrue();
        assertThat(cf.getNow("default")).isEqualTo("immediate");
    }

    @Test
    @DisplayName("failedFuture - 즉시 실패 상태 CF")
    void failedFutureTest() {
        CompletableFuture<String> cf = creation.alreadyFailed(new RuntimeException("prebuilt error"));
        assertThat(cf.isCompletedExceptionally()).isTrue();

        assertThatThrownBy(cf::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("prebuilt error");
    }

    @Test
    @DisplayName("수동 완료 - complete()로 외부에서 값 설정")
    void manualCompleteTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> cf = manual.manualComplete();
        assertThat(cf.isDone()).isFalse();

        boolean completed = manual.tryComplete(cf, "manually done");
        assertThat(completed).isTrue();
        assertThat(cf.get()).isEqualTo("manually done");

        // 이미 완료됐으므로 두 번째 complete는 false
        boolean completedAgain = manual.tryComplete(cf, "new value");
        assertThat(completedAgain).isFalse();
        assertThat(cf.get()).isEqualTo("manually done"); // 원래 값 유지
    }

    @Test
    @DisplayName("수동 실패 - completeExceptionally()로 예외 설정")
    void manualFailTest() {
        CompletableFuture<String> cf = manual.manualFail(new IllegalStateException("fail!"));
        assertThat(cf.isCompletedExceptionally()).isTrue();

        assertThatThrownBy(cf::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("get() vs join() - 예외 타입 차이")
    void getVsJoinTest() {
        CompletableFuture<String> cf = creation.alreadyFailed(new RuntimeException("error"));

        // get()은 체크 예외(ExecutionException) 발생
        assertThatThrownBy(cf::get).isInstanceOf(ExecutionException.class);

        // join()은 언체크 예외(CompletionException) 발생
        assertThatThrownBy(cf::join).isInstanceOf(CompletionException.class);
    }

    @Test
    @DisplayName("getNow - 완료 전 기본값 반환")
    void getNowTest() {
        CompletableFuture<String> cf = new CompletableFuture<>();
        String result = retrieval.getNow(cf, "default");
        assertThat(result).isEqualTo("default"); // 아직 완료 안 됐으므로 기본값

        cf.complete("actual");
        assertThat(retrieval.getNow(cf, "default")).isEqualTo("actual");
    }

    @Test
    @DisplayName("isDone / isCompletedExceptionally 상태 확인")
    void stateCheckTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> pending = new CompletableFuture<>();
        assertThat(retrieval.isDone(pending)).isFalse();
        assertThat(retrieval.isCompletedExceptionally(pending)).isFalse();

        CompletableFuture<String> done = creation.alreadyCompleted("ok");
        assertThat(retrieval.isDone(done)).isTrue();
        assertThat(retrieval.isCompletedExceptionally(done)).isFalse();

        CompletableFuture<String> failed = creation.alreadyFailed(new RuntimeException());
        assertThat(retrieval.isDone(failed)).isTrue();
        assertThat(retrieval.isCompletedExceptionally(failed)).isTrue();
    }

    @Test
    @DisplayName("CPU 집약적 작업 - ForkJoinPool 기본 사용")
    void cpuIntensiveTaskTest() throws ExecutionException, InterruptedException {
        CompletableFutureBasicExample.ExecutorChoiceExample example =
                new CompletableFutureBasicExample.ExecutorChoiceExample();

        CompletableFuture<Long> cf = example.cpuIntensiveTask(100);
        Long primeCount = cf.get();
        assertThat(primeCount).isEqualTo(25L); // 100 이하 소수는 25개
    }

    @Test
    @DisplayName("I/O 집약적 작업 - 커스텀 Executor로 commonPool 보호")
    void ioIntensiveTaskTest() throws ExecutionException, InterruptedException {
        CompletableFutureBasicExample.ExecutorChoiceExample example =
                new CompletableFutureBasicExample.ExecutorChoiceExample();

        ExecutorService ioExecutor = Executors.newFixedThreadPool(10);
        try {
            CompletableFuture<String> cf = example.ioIntensiveTask("api.example.com", ioExecutor);
            assertThat(cf.get()).isEqualTo("Response from api.example.com");
        } finally {
            ioExecutor.shutdown();
        }
    }
}
