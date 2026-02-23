package com.exam.future;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Future 예제
 *
 * Future의 핵심:
 * 1. 비동기 작업의 결과를 나타내는 컨테이너
 * 2. get()으로 결과를 가져올 때까지 호출 스레드가 블로킹됨
 * 3. 타임아웃, 취소, 완료 여부 확인 가능
 * 4. 한계: 결과 조합, 콜백 체이닝 불가 → CompletableFuture 등장
 */
@Slf4j
public class FutureExample {

    /**
     * 시나리오 1: 기본 Future 사용
     * ExecutorService.submit(Callable) → Future 반환
     */
    static class BasicFutureExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        public Future<String> fetchDataAsync(String url) {
            return executor.submit(() -> {
                log.info("[{}] Fetching: {}", Thread.currentThread().getName(), url);
                Thread.sleep(100); // 네트워크 지연 시뮬레이션
                return "Response from " + url;
            });
        }

        public Future<Integer> calculateAsync(int a, int b) {
            return executor.submit(() -> {
                log.info("[{}] Calculating: {} + {}", Thread.currentThread().getName(), a, b);
                Thread.sleep(50);
                return a + b;
            });
        }

        // get()은 결과가 나올 때까지 블로킹
        public String getResult(Future<String> future) throws ExecutionException, InterruptedException {
            return future.get();
        }

        // 타임아웃 지정 가능
        public String getResultWithTimeout(Future<String> future, long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            return future.get(timeout, unit);
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 2: Future의 상태 확인
     * isDone(), isCancelled(), cancel()
     */
    static class FutureStateExample {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        public Future<String> submitLongTask() {
            return executor.submit(() -> {
                Thread.sleep(500);
                return "Long task completed";
            });
        }

        // 완료 여부 확인 (블로킹 없음)
        public boolean isDone(Future<?> future) {
            return future.isDone();
        }

        // 취소 시도 (mayInterruptIfRunning = true: 실행 중인 작업도 중단 시도)
        public boolean cancel(Future<?> future, boolean mayInterrupt) {
            return future.cancel(mayInterrupt);
        }

        public boolean isCancelled(Future<?> future) {
            return future.isCancelled();
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 3: Future의 한계 - 블로킹 문제
     *
     * Future는 get()을 호출하면 반드시 블로킹됨.
     * 여러 비동기 작업 결과를 모으려면 각각 get()을 호출해야 해서
     * 순차 대기가 발생한다.
     */
    static class FutureLimitationExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        // 문제: task1.get() → task2.get() → task3.get() 순차 블로킹
        public String sequentialWait(String input)
                throws ExecutionException, InterruptedException {
            long start = System.currentTimeMillis();

            Future<String> task1 = executor.submit(() -> {
                Thread.sleep(100);
                return input + "-step1";
            });

            Future<String> task2 = executor.submit(() -> {
                Thread.sleep(100);
                return input + "-step2";
            });

            Future<String> task3 = executor.submit(() -> {
                Thread.sleep(100);
                return input + "-step3";
            });

            // 각 작업이 끝날 때까지 하나씩 기다림
            String r1 = task1.get(); // 블로킹
            String r2 = task2.get(); // 블로킹
            String r3 = task3.get(); // 블로킹

            long elapsed = System.currentTimeMillis() - start;
            log.info("Sequential wait: {}ms (parallel tasks took ~100ms each)", elapsed);

            return r1 + ", " + r2 + ", " + r3;
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 4: Callable vs Runnable
     *
     * Runnable: 반환값 없음, 예외 던질 수 없음
     * Callable: 반환값 있음, 체크 예외 던질 수 있음
     */
    static class CallableVsRunnableExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(2);

        // Runnable: 결과 없음
        public Future<?> submitRunnable(Runnable task) {
            return executor.submit(task); // Future<?>는 항상 null 반환
        }

        // Callable: 결과 있음, 예외 전파 가능
        public Future<String> submitCallable(Callable<String> task) {
            return executor.submit(task);
        }

        // Callable에서 발생한 예외는 Future.get() 시 ExecutionException으로 래핑됨
        public Future<String> submitFailingCallable() {
            return executor.submit(() -> {
                throw new RuntimeException("Task failed!");
            });
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 5: ExecutorService와 Future를 활용한 병렬 처리
     * invokeAll: 모든 작업 제출 후 전체 완료 대기
     * invokeAny: 가장 먼저 완료되는 작업 결과 반환
     */
    static class ExecutorInvokeExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        // invokeAll: 모든 Callable을 실행하고, 전부 끝날 때까지 기다린 뒤 Future 목록 반환
        public java.util.List<Future<String>> invokeAll(java.util.List<Callable<String>> tasks)
                throws InterruptedException {
            return executor.invokeAll(tasks);
        }

        // invokeAny: 가장 먼저 성공한 결과만 반환, 나머지는 취소
        public String invokeAny(java.util.List<Callable<String>> tasks)
                throws ExecutionException, InterruptedException {
            return executor.invokeAny(tasks);
        }

        public void shutdown() {
            executor.shutdown();
        }
    }
}
