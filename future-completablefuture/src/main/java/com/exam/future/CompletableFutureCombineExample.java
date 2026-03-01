package com.exam.future;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CompletableFuture 결과 조합 예제
 *
 * 두 CF 조합:
 * - thenCombine:     두 CF 결과를 합쳐 새 결과 생성 (BiFunction)
 * - thenAcceptBoth:  두 CF 결과를 소비 (BiConsumer)
 * - runAfterBoth:    두 CF 완료 후 작업 실행 (Runnable)
 * - applyToEither:   먼저 완료된 CF 결과로 변환
 * - acceptEither:    먼저 완료된 CF 결과를 소비
 * - runAfterEither:  둘 중 하나 완료 후 실행
 *
 * 여러 CF 조합:
 * - allOf:  모든 CF 완료 대기 → CompletableFuture<Void>
 * - anyOf:  가장 먼저 완료된 CF 결과 → CompletableFuture<Object>
 */
@Slf4j
public class CompletableFutureCombineExample {

    /**
     * 시나리오 1: thenCombine - 두 CF 결과를 합쳐 새 결과 생성
     *
     * 두 작업이 독립적으로 병렬 실행된 뒤,
     * 둘 다 완료되면 결과를 합산함.
     */
    static class ThenCombineExample {
        record UserProfile(String name, int age) {}
        record UserPurchaseHistory(String userId, int totalPurchases) {}
        record UserDashboard(UserProfile profile, UserPurchaseHistory history) {}

        public CompletableFuture<UserProfile> fetchProfile(String userId) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] Fetching profile: {}", Thread.currentThread().getName(), userId);
                return new UserProfile("Alice", 30);
            });
        }

        public CompletableFuture<UserPurchaseHistory> fetchPurchaseHistory(String userId) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("[{}] Fetching purchases: {}", Thread.currentThread().getName(), userId);
                return new UserPurchaseHistory(userId, 42);
            });
        }

        // 두 비동기 작업을 병렬로 실행하고 결과를 합산
        public CompletableFuture<UserDashboard> buildDashboard(String userId) {
            CompletableFuture<UserProfile> profileFuture = fetchProfile(userId);
            CompletableFuture<UserPurchaseHistory> historyFuture = fetchPurchaseHistory(userId);

            return profileFuture.thenCombine(historyFuture,
                    (profile, history) -> new UserDashboard(profile, history));
        }
    }

    /**
     * 시나리오 2: thenAcceptBoth / runAfterBoth
     */
    static class BothExample {

        public CompletableFuture<Void> logWhenBothComplete(
                CompletableFuture<String> cf1,
                CompletableFuture<String> cf2) {
            // thenAcceptBoth: 두 결과를 받아 소비 (반환 없음)
            return cf1.thenAcceptBoth(cf2, (r1, r2) ->
                    log.info("Both done: {} and {}", r1, r2));
        }

        public CompletableFuture<Void> runWhenBothComplete(
                CompletableFuture<String> cf1,
                CompletableFuture<String> cf2) {
            // runAfterBoth: 두 CF 완료 후 Runnable 실행 (결과 무관)
            return cf1.runAfterBoth(cf2, () ->
                    log.info("Both CFs are done, running cleanup"));
        }
    }

    /**
     * 시나리오 3: applyToEither / acceptEither - 먼저 완료된 결과 사용
     *
     * 두 소스 중 빠른 쪽 결과를 즉시 사용.
     * 예: 캐시 조회와 DB 조회를 동시에 시작, 먼저 응답 오는 것 사용
     */
    static class EitherExample {

        public CompletableFuture<String> fetchFromCache(String key) {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                log.info("[{}] Cache hit: {}", Thread.currentThread().getName(), key);
                return "CACHE:" + key;
            });
        }

        public CompletableFuture<String> fetchFromDb(String key) {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                log.info("[{}] DB result: {}", Thread.currentThread().getName(), key);
                return "DB:" + key;
            });
        }

        // 캐시와 DB 중 먼저 오는 결과 사용 (변환)
        public CompletableFuture<String> fetchFastest(String key) {
            return fetchFromCache(key)
                    .applyToEither(fetchFromDb(key), result -> {
                        log.info("Using fastest result: {}", result);
                        return result.toUpperCase();
                    });
        }

        // 먼저 오는 결과를 소비 (변환 없음)
        public CompletableFuture<Void> logFastest(String key) {
            return fetchFromCache(key)
                    .acceptEither(fetchFromDb(key), result ->
                            log.info("First result: {}", result));
        }
    }

    /**
     * 시나리오 4: allOf - 모든 CF 완료 대기
     *
     * allOf는 CompletableFuture<Void>를 반환하므로
     * 각 CF의 결과를 join()으로 따로 수집해야 함.
     */
    static class AllOfExample {

        // 여러 URL을 병렬로 조회하고 모든 결과 수집
        public CompletableFuture<List<String>> fetchAll(List<String> urls) {
            List<CompletableFuture<String>> futures = urls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> {
                        log.info("[{}] Fetching: {}", Thread.currentThread().getName(), url);
                        return "Response:" + url;
                    }))
                    .collect(Collectors.toList());

            // 모든 CF가 완료될 때까지 대기
            CompletableFuture<Void> allDone = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            // allOf 완료 후 각 결과 수집
            return allDone.thenApply(v ->
                    futures.stream()
                            .map(CompletableFuture::join) // 이미 완료됐으므로 블로킹 없음
                            .collect(Collectors.toList())
            );
        }

        // 여러 마이크로서비스 호출 병렬화
        public CompletableFuture<String> aggregateMicroservices(String requestId) {
            CompletableFuture<String> userSvc = CompletableFuture.supplyAsync(
                    () -> "user:" + requestId);
            CompletableFuture<String> orderSvc = CompletableFuture.supplyAsync(
                    () -> "order:" + requestId);
            CompletableFuture<String> inventorySvc = CompletableFuture.supplyAsync(
                    () -> "inventory:" + requestId);

            return CompletableFuture.allOf(userSvc, orderSvc, inventorySvc)
                    .thenApply(v -> String.join(", ",
                            userSvc.join(), orderSvc.join(), inventorySvc.join()));
        }
    }

    /**
     * 시나리오 5: anyOf - 가장 먼저 완료된 CF 결과 사용
     *
     * anyOf는 CompletableFuture<Object>를 반환함.
     * 타입 캐스팅 필요. 예: 여러 복제본 중 응답 빠른 서버 사용.
     */
    static class AnyOfExample {

        public CompletableFuture<String> fetchFromReplica(String replicaId, long delayMs) {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                log.info("[{}] Replica {} responded", Thread.currentThread().getName(), replicaId);
                return "Data from replica-" + replicaId;
            });
        }

        // 세 복제본 중 가장 빠른 응답 사용
        public CompletableFuture<String> fetchFromFastestReplica() {
            CompletableFuture<String> replica1 = fetchFromReplica("A", 300);
            CompletableFuture<String> replica2 = fetchFromReplica("B", 100);
            CompletableFuture<String> replica3 = fetchFromReplica("C", 200);

            return CompletableFuture.anyOf(replica1, replica2, replica3)
                    .thenApply(result -> (String) result); // Object → String 캐스팅
        }
    }
}
