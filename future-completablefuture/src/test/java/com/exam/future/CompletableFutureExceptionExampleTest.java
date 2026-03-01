package com.exam.future;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class CompletableFutureExceptionExampleTest {

    private final CompletableFutureExceptionExample.ExceptionallyExample exceptionallyExample =
            new CompletableFutureExceptionExample.ExceptionallyExample();
    private final CompletableFutureExceptionExample.HandleExample handleExample =
            new CompletableFutureExceptionExample.HandleExample();
    private final CompletableFutureExceptionExample.WhenCompleteExample whenCompleteExample =
            new CompletableFutureExceptionExample.WhenCompleteExample();
    private final CompletableFutureExceptionExample.ExceptionPropagationExample propagationExample =
            new CompletableFutureExceptionExample.ExceptionPropagationExample();
    private final CompletableFutureExceptionExample.ComparisonExample comparisonExample =
            new CompletableFutureExceptionExample.ComparisonExample();

    // ---------- exceptionally ----------

    @Test
    @DisplayName("exceptionally - 정상 완료 시 호출되지 않음")
    void exceptionallySuccessTest() throws ExecutionException, InterruptedException {
        String result = exceptionallyExample.fetchWithFallback(false).get();
        assertThat(result).isEqualTo("Fetched data");
    }

    @Test
    @DisplayName("exceptionally - 예외 발생 시 복구값 반환")
    void exceptionallyFailTest() throws ExecutionException, InterruptedException {
        String result = exceptionallyExample.fetchWithFallback(true).get();
        assertThat(result).isEqualTo("Default data");
    }

    @Test
    @DisplayName("exceptionally - 중간 단계 예외 시 이후 단계 건너뜀")
    void exceptionallyPipelineTest() throws ExecutionException, InterruptedException {
        String success = exceptionallyExample.pipelineWithException(false).get();
        assertThat(success).isEqualTo("Step1-OK-Step2-OK-Step3-OK");

        String recovered = exceptionallyExample.pipelineWithException(true).get();
        assertThat(recovered).startsWith("Recovered:");
        assertThat(recovered).contains("Step2 failed!");
    }

    // ---------- handle ----------

    @Test
    @DisplayName("handle - 정상 완료 시 결과 변환")
    void handleSuccessTest() throws ExecutionException, InterruptedException {
        String result = handleExample.handleBothCases(false).get();
        assertThat(result).isEqualTo("SUCCESS DATA"); // toUpperCase 적용
    }

    @Test
    @DisplayName("handle - 예외 발생 시 대체값 반환")
    void handleFailTest() throws ExecutionException, InterruptedException {
        String result = handleExample.handleBothCases(true).get();
        assertThat(result).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("handle - 예외 처리 후 체이닝 계속 가능")
    void handleThenChainTest() throws ExecutionException, InterruptedException {
        int successResult = handleExample.transformAfterHandle(false).get();
        assertThat(successResult).isEqualTo(42);

        int failResult = handleExample.transformAfterHandle(true).get();
        assertThat(failResult).isEqualTo(0); // "0" → 0 파싱
    }

    // ---------- whenComplete ----------

    @Test
    @DisplayName("whenComplete - 성공 시 결과 그대로 전달")
    void whenCompleteSuccessTest() throws ExecutionException, InterruptedException {
        String result = whenCompleteExample.logAndPassThrough(false).get();
        assertThat(result).isEqualTo("OK");
    }

    @Test
    @DisplayName("whenComplete - 예외 발생 시 예외 그대로 전파")
    void whenCompleteFailTest() {
        CompletableFuture<String> cf = whenCompleteExample.logAndPassThrough(true);
        assertThatThrownBy(cf::get)
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Error occurred");
    }

    @Test
    @DisplayName("whenComplete + exceptionally 조합 - 로그 후 복구")
    void whenCompleteWithRecoveryTest() throws ExecutionException, InterruptedException {
        String result = whenCompleteExample.logThenRecover(true).get();
        assertThat(result).isEqualTo("Recovered");

        String success = whenCompleteExample.logThenRecover(false).get();
        assertThat(success).isEqualTo("Data");
    }

    // ---------- 예외 전파 ----------

    @Test
    @DisplayName("예외 전파 - 중간 단계 예외 후 exceptionally 에서 복구")
    void propagationTest() throws ExecutionException, InterruptedException {
        String result = propagationExample.demonstratePropagation().get();
        assertThat(result).isEqualTo("recovered-done");
    }

    // ---------- 세 방식 비교 ----------

    @Test
    @DisplayName("비교: exceptionally는 예외만 처리, 성공 시 그대로")
    void comparisonExceptionallyTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> success = CompletableFuture.completedFuture("ok");
        assertThat(comparisonExample.withExceptionally(success).get()).isEqualTo("ok");

        CompletableFuture<String> failed = CompletableFuture.failedFuture(new RuntimeException("err"));
        assertThat(comparisonExample.withExceptionally(failed).get()).startsWith("fallback:");
    }

    @Test
    @DisplayName("비교: handle은 성공/실패 모두 처리, 결과 변환 가능")
    void comparisonHandleTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> success = CompletableFuture.completedFuture("ok");
        assertThat(comparisonExample.withHandle(success).get()).isEqualTo("OK"); // 대문자 변환

        CompletableFuture<String> failed = CompletableFuture.failedFuture(new RuntimeException("err"));
        assertThat(comparisonExample.withHandle(failed).get()).startsWith("fallback:");
    }

    @Test
    @DisplayName("비교: whenComplete는 관찰만, 성공/예외 그대로 전달")
    void comparisonWhenCompleteTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> success = CompletableFuture.completedFuture("ok");
        assertThat(comparisonExample.withWhenComplete(success).get()).isEqualTo("ok"); // 그대로

        CompletableFuture<String> failed = CompletableFuture.failedFuture(new RuntimeException("err"));
        assertThatThrownBy(() -> comparisonExample.withWhenComplete(failed).get())
                .isInstanceOf(ExecutionException.class); // 예외 그대로 전파
    }

    @Test
    @DisplayName("CompletionException - join()은 CompletionException, get()은 ExecutionException")
    void completionExceptionUnwrapTest() throws ExecutionException, InterruptedException {
        // supplyAsync에서 발생한 예외는 CompletionException 으로 래핑됨
        CompletableFuture<String> cf = CompletableFuture
                .<String>supplyAsync(() -> { throw new IllegalArgumentException("root cause"); });

        // join(): CompletionException (언체크) — 원인: IllegalArgumentException
        assertThatThrownBy(cf::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root cause");

        // get(): ExecutionException (체크) — 원인: IllegalArgumentException
        // cf는 이미 예외 완료 상태이므로 새로 생성
        CompletableFuture<String> cf2 = CompletableFuture
                .<String>supplyAsync(() -> { throw new IllegalArgumentException("root cause"); });

        assertThatThrownBy(cf2::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
