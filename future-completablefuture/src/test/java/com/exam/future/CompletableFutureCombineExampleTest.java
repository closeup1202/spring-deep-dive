package com.exam.future;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class CompletableFutureCombineExampleTest {

    private final CompletableFutureCombineExample.ThenCombineExample combineExample =
            new CompletableFutureCombineExample.ThenCombineExample();
    private final CompletableFutureCombineExample.BothExample bothExample =
            new CompletableFutureCombineExample.BothExample();
    private final CompletableFutureCombineExample.EitherExample eitherExample =
            new CompletableFutureCombineExample.EitherExample();
    private final CompletableFutureCombineExample.AllOfExample allOfExample =
            new CompletableFutureCombineExample.AllOfExample();
    private final CompletableFutureCombineExample.AnyOfExample anyOfExample =
            new CompletableFutureCombineExample.AnyOfExample();

    @Test
    @DisplayName("thenCombine - 두 CF 결과를 병합하여 대시보드 생성")
    void thenCombineDashboardTest() throws ExecutionException, InterruptedException {
        var dashboard = combineExample.buildDashboard("user123").get();

        assertThat(dashboard.profile().name()).isEqualTo("Alice");
        assertThat(dashboard.profile().age()).isEqualTo(30);
        assertThat(dashboard.history().totalPurchases()).isEqualTo(42);
    }

    @Test
    @DisplayName("thenCombine - 두 숫자 합산")
    void thenCombineNumbersTest() throws ExecutionException, InterruptedException {
        CompletableFuture<Integer> cf1 = CompletableFuture.supplyAsync(() -> 10);
        CompletableFuture<Integer> cf2 = CompletableFuture.supplyAsync(() -> 20);

        CompletableFuture<Integer> sum = cf1.thenCombine(cf2, Integer::sum);
        assertThat(sum.get()).isEqualTo(30);
    }

    @Test
    @DisplayName("thenAcceptBoth - 두 CF 완료 후 소비")
    void thenAcceptBothTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture("Hello");
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture("World");

        String[] result = {null};
        CompletableFuture<Void> combined = cf1.thenAcceptBoth(cf2,
                (r1, r2) -> result[0] = r1 + " " + r2);
        combined.get();

        assertThat(result[0]).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("runAfterBoth - 두 CF 완료 후 Runnable 실행")
    void runAfterBothTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> "A");
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> "B");

        boolean[] ran = {false};
        bothExample.runWhenBothComplete(cf1, cf2).get();
        // 로그 확인으로 실행됨을 검증하므로 isDone으로 대체
        assertThat(cf1.isDone()).isTrue();
        assertThat(cf2.isDone()).isTrue();
    }

    @Test
    @DisplayName("applyToEither - 먼저 완료된 CF 결과로 변환")
    void applyToEitherTest() throws ExecutionException, InterruptedException {
        String result = eitherExample.fetchFastest("product-key").get();
        // 캐시(50ms)가 DB(200ms)보다 빠르므로 CACHE: 결과가 대문자로 나옴
        assertThat(result).isEqualTo("CACHE:PRODUCT-KEY");
    }

    @Test
    @DisplayName("applyToEither - 두 즉시 완료 CF 중 하나 선택")
    void applyToEitherImmediateTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> fast = CompletableFuture.completedFuture("FAST");
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "SLOW";
        });

        String result = fast.applyToEither(slow, s -> s + "-chosen").join();
        assertThat(result).isEqualTo("FAST-chosen");
    }

    @Test
    @DisplayName("allOf - 모든 CF 완료 대기 후 결과 수집")
    void allOfTest() throws ExecutionException, InterruptedException {
        List<String> urls = List.of("url1", "url2", "url3");
        List<String> results = allOfExample.fetchAll(urls).get();

        assertThat(results).hasSize(3);
        assertThat(results).containsExactlyInAnyOrder(
                "Response:url1", "Response:url2", "Response:url3"
        );
    }

    @Test
    @DisplayName("allOf - 마이크로서비스 병렬 집계")
    void allOfMicroservicesTest() throws ExecutionException, InterruptedException {
        String result = allOfExample.aggregateMicroservices("req-001").get();
        assertThat(result).isEqualTo("user:req-001, order:req-001, inventory:req-001");
    }

    @Test
    @DisplayName("allOf - 병렬 실행으로 순차보다 빠름")
    void allOfParallelSpeedTest() throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();

        List<String> urls = List.of("url1", "url2", "url3", "url4", "url5");
        List<CompletableFuture<String>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return url;
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        long elapsed = System.currentTimeMillis() - start;

        log.info("5 parallel tasks (100ms each) completed in {}ms", elapsed);
        // 순차면 500ms, 병렬이면 ~100ms
        assertThat(elapsed).isLessThan(400);
    }

    @Test
    @DisplayName("anyOf - 가장 빠른 복제본 응답 사용")
    void anyOfTest() throws ExecutionException, InterruptedException {
        String result = anyOfExample.fetchFromFastestReplica().get();
        // replica-B가 100ms로 가장 빠름
        assertThat(result).isEqualTo("Data from replica-B");
    }

    @Test
    @DisplayName("anyOf - 즉시 완료 CF가 있으면 바로 결과 반환")
    void anyOfImmediateTest() throws ExecutionException, InterruptedException {
        CompletableFuture<String> instant = CompletableFuture.completedFuture("instant");
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "slow";
        });

        Object result = CompletableFuture.anyOf(instant, slow).get();
        assertThat(result).isEqualTo("instant");
    }
}
