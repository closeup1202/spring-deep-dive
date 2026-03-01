package com.exam.future;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * CompletableFuture 예외 처리 예제
 *
 * 예외 처리 API:
 * - exceptionally:  예외 발생 시 복구값 반환 (Function<Throwable, T>)
 * - handle:         성공/실패 모두 처리 (BiFunction<T, Throwable, U>)
 * - whenComplete:   성공/실패 관찰만 (BiConsumer<T, Throwable>) — 결과 변환 불가
 *
 * 예외 전파:
 * - 파이프라인 중간에서 예외 발생 시 이후 단계는 건너뜀
 * - 예외는 CompletionException으로 래핑되어 전파
 */
@Slf4j
public class CompletableFutureExceptionExample {

    /**
     * 시나리오 1: exceptionally - 예외 발생 시 복구
     *
     * 예외가 발생하면 대체 값을 반환하여 파이프라인을 계속 진행.
     * 정상 완료 시에는 호출되지 않음.
     */
    static class ExceptionallyExample {

        public CompletableFuture<String> fetchWithFallback(boolean shouldFail) {
            return CompletableFuture.supplyAsync(() -> {
                        if (shouldFail) throw new RuntimeException("Network error!");
                        return "Fetched data";
                    })
                    .exceptionally(ex -> {
                        log.warn("Exception caught: {}", ex.getMessage());
                        return "Default data"; // 복구값 반환
                    });
        }

        // 중간 단계에서 예외 발생 시 이후 단계는 건너뜀
        public CompletableFuture<String> pipelineWithException(boolean failAtStep2) {
            return CompletableFuture.supplyAsync(() -> "Step1-OK")
                    .thenApply(s -> {
                        if (failAtStep2) throw new RuntimeException("Step2 failed!");
                        return s + "-Step2-OK";
                    })
                    .thenApply(s -> s + "-Step3-OK") // Step2 예외 시 건너뜀
                    .exceptionally(ex -> "Recovered: " + ex.getMessage()); // 최종 복구
        }
    }

    /**
     * 시나리오 2: handle - 성공/실패 모두 처리 가능
     *
     * 성공이든 실패든 항상 호출됨.
     * BiFunction<T, Throwable, U>: (result, exception) → newResult
     * 결과를 변환할 수 있음 (exceptionally와의 차이).
     */
    static class HandleExample {

        public CompletableFuture<String> handleBothCases(boolean shouldFail) {
            return CompletableFuture.supplyAsync(() -> {
                        if (shouldFail) throw new RuntimeException("Something went wrong");
                        return "Success data";
                    })
                    .handle((result, ex) -> {
                        if (ex != null) {
                            log.warn("Handling exception: {}", ex.getMessage());
                            return "FALLBACK"; // 예외 시 대체값
                        }
                        log.info("Handling success: {}", result);
                        return result.toUpperCase(); // 성공 시 변환
                    });
        }

        // handle 후 체이닝 계속 가능
        public CompletableFuture<Integer> transformAfterHandle(boolean shouldFail) {
            return CompletableFuture.supplyAsync(() -> {
                        if (shouldFail) throw new IllegalStateException("Error");
                        return "42";
                    })
                    .handle((result, ex) -> ex != null ? "0" : result) // 복구
                    .thenApply(Integer::parseInt); // 계속 체이닝
        }
    }

    /**
     * 시나리오 3: whenComplete - 결과 관찰 (변환 없음)
     *
     * 성공이든 실패든 항상 호출됨.
     * BiConsumer이므로 결과를 변환하지 않음 — 로깅, 모니터링에 적합.
     * 예외는 아래로 그대로 전파됨 (복구하지 않음).
     */
    static class WhenCompleteExample {

        public CompletableFuture<String> logAndPassThrough(boolean shouldFail) {
            return CompletableFuture.supplyAsync(() -> {
                        if (shouldFail) throw new RuntimeException("Error occurred");
                        return "OK";
                    })
                    .whenComplete((result, ex) -> {
                        // 결과를 관찰만 하고 전파
                        if (ex != null) {
                            log.error("Task failed: {}", ex.getMessage());
                        } else {
                            log.info("Task succeeded: {}", result);
                        }
                        // 반환값 없음 — 결과/예외 그대로 전달됨
                    });
        }

        // whenComplete + exceptionally 조합: 로그 후 복구
        public CompletableFuture<String> logThenRecover(boolean shouldFail) {
            return CompletableFuture.supplyAsync(() -> {
                        if (shouldFail) throw new RuntimeException("Error");
                        return "Data";
                    })
                    .whenComplete((result, ex) -> {
                        if (ex != null) log.error("Caught exception for monitoring: {}", ex.getMessage());
                    })
                    .exceptionally(ex -> "Recovered"); // whenComplete 이후에도 예외 전파됨
        }
    }

    /**
     * 시나리오 4: 예외 전파 규칙 이해
     *
     * 파이프라인 중간에서 예외가 발생하면:
     * - thenApply, thenAccept, thenRun, thenCompose → 건너뜀
     * - handle, whenComplete → 예외를 받아 처리 가능
     * - exceptionally → 예외가 있을 때만 호출
     */
    static class ExceptionPropagationExample {

        public CompletableFuture<String> demonstratePropagation() {
            return CompletableFuture.supplyAsync(() -> {
                        log.info("Step 1: OK");
                        return "data";
                    })
                    .thenApply(s -> {
                        log.info("Step 2: throws");
                        throw new RuntimeException("Step 2 error");
                    })
                    .thenApply(s -> {
                        log.info("Step 3: SKIPPED (exception propagated)");
                        return s + "-step3";
                    })
                    .thenApply(s -> {
                        log.info("Step 4: SKIPPED");
                        return s + "-step4";
                    })
                    .exceptionally(ex -> {
                        log.info("Step 5 (exceptionally): caught {}", ex.getMessage());
                        return "recovered";
                    })
                    .thenApply(s -> {
                        log.info("Step 6: resumed after recovery");
                        return s + "-done";
                    });
        }
    }

    /**
     * 시나리오 5: 예외 처리 비교 정리
     *
     * exceptionally vs handle vs whenComplete
     */
    static class ComparisonExample {

        // exceptionally: 예외만 처리, 성공 시 호출 안됨
        public CompletableFuture<String> withExceptionally(CompletableFuture<String> cf) {
            return cf.exceptionally(ex -> "fallback:" + ex.getMessage());
        }

        // handle: 항상 호출, 결과 변환 가능
        public CompletableFuture<String> withHandle(CompletableFuture<String> cf) {
            return cf.handle((result, ex) -> {
                if (ex != null) return "fallback:" + ex.getMessage();
                return result.toUpperCase();
            });
        }

        // whenComplete: 항상 호출, 결과 변환 불가 (관찰만)
        public CompletableFuture<String> withWhenComplete(CompletableFuture<String> cf) {
            return cf.whenComplete((result, ex) -> {
                // 관찰만 가능, result/ex는 그대로 전달됨
                log.info("Observed — result: {}, exception: {}", result, ex);
            });
        }
    }
}
