package com.exam.future;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompletableFuture 체이닝 예제
 *
 * 파이프라인 구성:
 * - thenApply:   결과 변환 (Function) — 동기 실행
 * - thenApplyAsync: 결과 변환 (Function) — 비동기 실행
 * - thenAccept:  결과 소비 (Consumer)  — 반환값 없음
 * - thenRun:     이후 작업 실행 (Runnable) — 결과 무관
 * - thenCompose: 다른 CF 반환 (flatMap) — 중첩 CF 평탄화
 */
@Slf4j
public class CompletableFutureChainExample {

    /**
     * 시나리오 1: thenApply - 결과 변환 (동기)
     *
     * 이전 단계와 같은 스레드에서 실행됨.
     * 순수 변환 로직(가벼운 작업)에 적합.
     */
    static class ThenApplyExample {
        // String → Integer → String 변환 파이프라인
        public CompletableFuture<String> pipeline(String input) {
            return CompletableFuture.supplyAsync(() -> {
                        log.info("[{}] Step 1: fetch", Thread.currentThread().getName());
                        return input;
                    })
                    .thenApply(s -> {
                        log.info("[{}] Step 2: parse length", Thread.currentThread().getName());
                        return s.length(); // String → Integer
                    })
                    .thenApply(len -> {
                        log.info("[{}] Step 3: format", Thread.currentThread().getName());
                        return "Length is " + len; // Integer → String
                    });
        }
    }

    /**
     * 시나리오 2: thenApplyAsync - 결과 변환 (비동기)
     *
     * 새 스레드(또는 지정 Executor)에서 실행됨.
     * 변환 로직이 무거운 경우 또는 스레드 격리가 필요한 경우.
     */
    static class ThenApplyAsyncExample {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        public CompletableFuture<String> pipeline(String input) {
            return CompletableFuture.supplyAsync(() -> {
                        log.info("[{}] Fetch", Thread.currentThread().getName());
                        return input;
                    }, executor)
                    .thenApplyAsync(s -> {
                        log.info("[{}] Heavy transform", Thread.currentThread().getName());
                        return s.toUpperCase();
                    }, executor) // 커스텀 Executor 사용
                    .thenApplyAsync(s -> {
                        log.info("[{}] Format", Thread.currentThread().getName());
                        return "[" + s + "]";
                    }); // ForkJoinPool.commonPool 사용

        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 시나리오 3: thenAccept / thenRun - 결과 소비
     *
     * thenAccept: 결과값을 받아 소비 (Consumer) → CompletableFuture<Void>
     * thenRun:    결과값 무관하게 작업 실행 (Runnable) → CompletableFuture<Void>
     */
    static class ThenAcceptRunExample {

        // thenAccept: 결과 소비 후 Void 반환
        public CompletableFuture<Void> processAndLog(String input) {
            return CompletableFuture.supplyAsync(() -> "Processed: " + input)
                    .thenAccept(result -> {
                        log.info("[{}] Result: {}", Thread.currentThread().getName(), result);
                        // DB 저장, 메시지 전송 등
                    });
        }

        // thenRun: 결과와 무관하게 후처리 작업
        public CompletableFuture<Void> processAndCleanup(String input) {
            return CompletableFuture.supplyAsync(() -> "Processed: " + input)
                    .thenRun(() -> {
                        log.info("[{}] Cleanup after task", Thread.currentThread().getName());
                        // 리소스 정리, 완료 알림 등
                    });
        }

        // 체인: thenAccept 뒤에 thenRun 연결
        public CompletableFuture<Void> fullPipeline(String input) {
            return CompletableFuture.supplyAsync(() -> "Data: " + input)
                    .thenAccept(result -> log.info("Saving: {}", result))
                    .thenRun(() -> log.info("Pipeline complete"));
        }
    }

    /**
     * 시나리오 4: thenCompose - 중첩 CF 평탄화 (flatMap)
     *
     * thenApply  : Function<T, U>          → CompletableFuture<U>
     * thenCompose: Function<T, CF<U>>      → CompletableFuture<U> (평탄화)
     *
     * 비동기 작업이 또 다른 비동기 작업을 반환할 때 사용.
     * thenApply를 쓰면 CompletableFuture<CompletableFuture<U>> 가 됨.
     */
    static class ThenComposeExample {
        // 사용자 ID → 사용자 정보 조회 → 사용자의 주문 목록 조회
        // 두 단계 모두 비동기이므로 thenCompose 적합

        public CompletableFuture<String> fetchUserName(long userId) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] Fetching user: {}", Thread.currentThread().getName(), userId);
                return "UserName-" + userId;
            });
        }

        public CompletableFuture<String> fetchOrderSummary(String userName) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] Fetching orders for: {}", Thread.currentThread().getName(), userName);
                return "Orders of " + userName + ": [order1, order2]";
            });
        }

        // thenCompose 사용: CF<String> → CF<String> (flat)
        public CompletableFuture<String> getUserOrderSummary(long userId) {
            return fetchUserName(userId)
                    .thenCompose(this::fetchOrderSummary); // 중첩 CF 평탄화
        }

        // 비교: thenApply를 쓰면 중첩 CF 발생 (비권장)
        public CompletableFuture<CompletableFuture<String>> getUserOrderSummaryNested(long userId) {
            return fetchUserName(userId)
                    .thenApply(this::fetchOrderSummary); // CF<CF<String>> — 사용 불편!
        }
    }

    /**
     * 시나리오 5: 실전 파이프라인
     * 주문 처리 파이프라인: 검증 → 재고 확인 → 결제 → 배송 등록
     */
    static class OrderPipelineExample {
        record Order(long id, String product, int quantity) {}
        record PaymentResult(long orderId, String status) {}
        record ShipmentResult(long orderId, String trackingNo) {}

        public CompletableFuture<ShipmentResult> processOrder(Order order) {
            return CompletableFuture
                    .supplyAsync(() -> validateOrder(order))       // 검증
                    .thenApply(this::checkInventory)                // 재고 확인
                    .thenApply(this::processPayment)                // 결제
                    .thenCompose(this::registerShipmentAsync);      // 비동기 배송 등록
        }

        private Order validateOrder(Order order) {
            log.info("Validating order: {}", order.id());
            if (order.quantity() <= 0) throw new IllegalArgumentException("Invalid quantity");
            return order;
        }

        private Order checkInventory(Order order) {
            log.info("Checking inventory for: {}", order.product());
            return order;
        }

        private PaymentResult processPayment(Order order) {
            log.info("Processing payment for order: {}", order.id());
            return new PaymentResult(order.id(), "PAID");
        }

        private CompletableFuture<ShipmentResult> registerShipmentAsync(PaymentResult payment) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Registering shipment for order: {}", payment.orderId());
                return new ShipmentResult(payment.orderId(), "TRACK-" + payment.orderId());
            });
        }
    }
}
