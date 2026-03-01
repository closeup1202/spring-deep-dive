package com.exam.future;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class CompletableFutureChainExampleTest {

    private final CompletableFutureChainExample.ThenApplyExample applyExample =
            new CompletableFutureChainExample.ThenApplyExample();
    private final CompletableFutureChainExample.ThenApplyAsyncExample applyAsyncExample =
            new CompletableFutureChainExample.ThenApplyAsyncExample();
    private final CompletableFutureChainExample.ThenAcceptRunExample acceptRunExample =
            new CompletableFutureChainExample.ThenAcceptRunExample();
    private final CompletableFutureChainExample.ThenComposeExample composeExample =
            new CompletableFutureChainExample.ThenComposeExample();

    @AfterEach
    void tearDown() {
        applyAsyncExample.shutdown();
    }

    @Test
    @DisplayName("thenApply - 순차 변환 파이프라인")
    void thenApplyTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = applyExample.pipeline("hello");
        assertThat(result.get()).isEqualTo("Length is 5");
    }

    @Test
    @DisplayName("thenApply - 빈 문자열 길이 변환")
    void thenApplyEmptyStringTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = applyExample.pipeline("");
        assertThat(result.get()).isEqualTo("Length is 0");
    }

    @Test
    @DisplayName("thenApplyAsync - 커스텀 Executor에서 변환")
    void thenApplyAsyncTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = applyAsyncExample.pipeline("hello");
        assertThat(result.get()).isEqualTo("[HELLO]");
    }

    @Test
    @DisplayName("thenAccept - 결과 소비 후 Void 반환")
    void thenAcceptTest() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> cf = acceptRunExample.processAndLog("spring");
        cf.get(); // Void 완료 대기
        assertThat(cf.isDone()).isTrue();
    }

    @Test
    @DisplayName("thenRun - 결과 무관 후처리 실행")
    void thenRunTest() throws ExecutionException, InterruptedException {
        boolean[] cleanupCalled = {false};

        CompletableFuture<String> source = CompletableFuture.completedFuture("data");
        CompletableFuture<Void> cf = source.thenRun(() -> cleanupCalled[0] = true);
        cf.get();

        assertThat(cleanupCalled[0]).isTrue();
    }

    @Test
    @DisplayName("thenCompose - 중첩 CF 평탄화 (flatMap)")
    void thenComposeTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = composeExample.getUserOrderSummary(42L);
        String summary = result.get();

        assertThat(summary).isEqualTo("Orders of UserName-42: [order1, order2]");
    }

    @Test
    @DisplayName("thenApply vs thenCompose - 중첩 CF 문제")
    void thenApplyVsThenComposeTest() throws ExecutionException, InterruptedException {
        // thenCompose: CF<String> — 평탄화됨
        CompletableFuture<String> flat = composeExample.getUserOrderSummary(1L);
        assertThat(flat.get()).isNotNull();

        // thenApply: CF<CF<String>> — 중첩됨 (사용 불편)
        CompletableFuture<CompletableFuture<String>> nested =
                composeExample.getUserOrderSummaryNested(1L);
        // 중첩 CF는 두 번 .get()이 필요
        String result = nested.get().get();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("OrderPipeline - 주문 처리 파이프라인 정상 케이스")
    void orderPipelineSuccessTest() throws ExecutionException, InterruptedException {
        CompletableFutureChainExample.OrderPipelineExample pipeline =
                new CompletableFutureChainExample.OrderPipelineExample();

        var order = new CompletableFutureChainExample.OrderPipelineExample.Order(1L, "Book", 2);
        var result = pipeline.processOrder(order).get();

        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.trackingNo()).isEqualTo("TRACK-1");
    }

    @Test
    @DisplayName("OrderPipeline - 유효하지 않은 수량으로 예외 발생")
    void orderPipelineInvalidQuantityTest() {
        CompletableFutureChainExample.OrderPipelineExample pipeline =
                new CompletableFutureChainExample.OrderPipelineExample();

        var order = new CompletableFutureChainExample.OrderPipelineExample.Order(2L, "Book", 0);

        assertThatThrownBy(() -> pipeline.processOrder(order).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid quantity");
    }

    @Test
    @DisplayName("체이닝 - 각 단계 결과 누적")
    void chainAccumulationTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = CompletableFuture
                .supplyAsync(() -> "A")
                .thenApply(s -> s + "B")
                .thenApply(s -> s + "C")
                .thenApply(s -> s + "D");

        assertThat(result.get()).isEqualTo("ABCD");
    }

    @Test
    @DisplayName("thenAcceptAsync - 비동기 소비")
    void thenAcceptAsyncTest() throws ExecutionException, InterruptedException {
        String[] captured = {null};

        CompletableFuture<Void> cf = CompletableFuture
                .supplyAsync(() -> "async data")
                .thenAcceptAsync(s -> captured[0] = s);

        cf.get();
        assertThat(captured[0]).isEqualTo("async data");
    }
}
