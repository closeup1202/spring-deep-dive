package com.exam.future;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * CompletableFuture 기본 예제
 *
 * CompletableFuture의 핵심:
 * 1. 수동으로 완료(complete)시킬 수 있는 Future
 * 2. 비동기 파이프라인 구성 가능 (콜백 체이닝)
 * 3. 예외 처리, 결과 조합 등 풍부한 API 제공
 * 4. Future의 블로킹 한계를 비동기 콜백으로 극복
 */
@Slf4j
public class CompletableFutureBasicExample {

    /**
     * 시나리오 1: CompletableFuture 생성 방법
     */
    static class CreationExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        // supplyAsync: 반환값이 있는 비동기 작업 (ForkJoinPool.commonPool 사용)
        public CompletableFuture<String> supplyAsync(String input) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] supplyAsync: {}", Thread.currentThread().getName(), input);
                return "Result: " + input;
            });
        }

        // supplyAsync: 커스텀 Executor 지정
        public CompletableFuture<String> supplyAsyncWithExecutor(String input) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] supplyAsync (custom executor): {}", Thread.currentThread().getName(), input);
                return "Result: " + input;
            }, executor);
        }

        // runAsync: 반환값이 없는 비동기 작업 (Runnable)
        public CompletableFuture<Void> runAsync(Runnable task) {
            return CompletableFuture.runAsync(() -> {
                log.info("[{}] runAsync", Thread.currentThread().getName());
                task.run();
            });
        }

        // completedFuture: 이미 완료된 상태로 생성 (테스트/기본값에 유용)
        public CompletableFuture<String> alreadyCompleted(String value) {
            return CompletableFuture.completedFuture(value);
        }

        // failedFuture: 이미 실패 상태로 생성 (Java 9+)
        public CompletableFuture<String> alreadyFailed(Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 2: 수동 완료 (CompletableFuture의 핵심 차별점)
     */
    static class ManualCompletionExample {

        // complete: 정상 완료
        public CompletableFuture<String> manualComplete() {
            CompletableFuture<String> cf = new CompletableFuture<>();
            // 나중에 어딘가에서 cf.complete("value") 를 호출해야 완료됨
            return cf;
        }

        // completeExceptionally: 예외로 완료
        public CompletableFuture<String> manualFail(Throwable ex) {
            CompletableFuture<String> cf = new CompletableFuture<>();
            cf.completeExceptionally(ex);
            return cf;
        }

        // obtrudeValue: 이미 완료된 CF의 값을 강제 덮어쓰기 (드물게 사용)
        public void forceComplete(CompletableFuture<String> cf, String newValue) {
            cf.obtrudeValue(newValue);
        }

        // complete는 아직 완료되지 않은 경우에만 적용됨
        public boolean tryComplete(CompletableFuture<String> cf, String value) {
            return cf.complete(value); // 이미 완료됐으면 false
        }
    }

    /**
     * 시나리오 3: 결과 가져오기
     * get() vs join() 차이
     */
    static class RetrievalExample {

        // get(): 체크 예외(ExecutionException, InterruptedException) 발생 가능
        public String blockingGet(CompletableFuture<String> cf)
                throws ExecutionException, InterruptedException {
            return cf.get();
        }

        // get(timeout): 타임아웃 지정
        public String blockingGetWithTimeout(CompletableFuture<String> cf,
                                              long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            return cf.get(timeout, unit);
        }

        // join(): 언체크 예외(CompletionException)로 래핑, 스트림에서 유용
        public String blockingJoin(CompletableFuture<String> cf) {
            return cf.join(); // InterruptedException 없이 블로킹
        }

        // getNow: 완료됐으면 결과, 아니면 기본값 반환 (블로킹 없음)
        public String getNow(CompletableFuture<String> cf, String defaultValue) {
            return cf.getNow(defaultValue);
        }

        // isDone, isCompletedExceptionally, isCancelled
        public boolean isDone(CompletableFuture<?> cf) {
            return cf.isDone();
        }

        public boolean isCompletedExceptionally(CompletableFuture<?> cf) {
            return cf.isCompletedExceptionally();
        }
    }

    /**
     * 시나리오 4: ForkJoinPool vs Custom Executor
     *
     * 기본적으로 CompletableFuture는 ForkJoinPool.commonPool()을 사용함.
     * I/O 작업이 많으면 commonPool 고갈 → 커스텀 Executor 필수!
     */
    static class ExecutorChoiceExample {
        // CPU 집약적 작업: ForkJoinPool.commonPool (기본값) 적합
        public CompletableFuture<Long> cpuIntensiveTask(long n) {
            return CompletableFuture.supplyAsync(() -> {
                // 소수 개수 계산 (CPU 집약적)
                long count = 0;
                for (long i = 2; i <= n; i++) {
                    if (isPrime(i)) count++;
                }
                return count;
            });
        }

        // I/O 집약적 작업: 별도 스레드풀 사용 (commonPool 고갈 방지)
        public CompletableFuture<String> ioIntensiveTask(String url,
                                                          ExecutorService ioExecutor) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] I/O task", Thread.currentThread().getName());
                try {
                    Thread.sleep(100); // I/O 지연 시뮬레이션
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Response from " + url;
            }, ioExecutor);
        }

        private boolean isPrime(long n) {
            if (n < 2) return false;
            for (long i = 2; i * i <= n; i++) {
                if (n % i == 0) return false;
            }
            return true;
        }
    }
}
