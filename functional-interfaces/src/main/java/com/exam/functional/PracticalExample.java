package com.exam.functional;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * 실무에서 자주 쓰이는 함수형 인터페이스 패턴 모음
 *
 * 다루는 패턴:
 *   1. 전략 패턴 (Strategy Pattern)
 *   2. 템플릿 메서드 패턴 (Template Method - 함수형 버전)
 *   3. 데코레이터 / 미들웨어 체인
 *   4. Null-Safe 유틸리티
 *   5. Comparator 조합
 *   6. 재시도 로직 (Retry with Supplier)
 */
@Slf4j
public class PracticalExample {

    record User(String name, String email, int age, String role) {}
    record Order(String id, User user, double amount) {}

    /**
     * 패턴 1: 전략 패턴 (Strategy Pattern)
     *
     * 전통적인 인터페이스/구현체 대신 Function/Predicate로 전략 주입
     * 클래스 폭발 없이 행위를 런타임에 교체 가능
     */
    static class DiscountStrategy {
        // 할인 전략: 주문 금액 -> 할인 후 금액
        private final Function<Double, Double> discountFn;

        private DiscountStrategy(Function<Double, Double> discountFn) {
            this.discountFn = discountFn;
        }

        public static DiscountStrategy none() {
            return new DiscountStrategy(Function.identity());
        }

        public static DiscountStrategy percentage(double rate) {
            return new DiscountStrategy(amount -> amount * (1 - rate));
        }

        public static DiscountStrategy fixed(double discountAmount) {
            return new DiscountStrategy(amount -> Math.max(0, amount - discountAmount));
        }

        public static DiscountStrategy vip(String role) {
            // VIP면 20% 할인, 아니면 할인 없음
            return "VIP".equals(role) ? percentage(0.20) : none();
        }

        public double apply(double amount) {
            return discountFn.apply(amount);
        }
    }

    public static void strategyPattern() {
        Order order = new Order("ORD-001", new User("Alice", "a@x.com", 30, "VIP"), 100_000.0);
        DiscountStrategy strategy = DiscountStrategy.vip(order.user().role());
        log.info("할인 후 금액: {}원", strategy.apply(order.amount()));
    }

    /**
     * 패턴 2: 템플릿 메서드 패턴 (함수형 버전)
     *
     * 공통 흐름(트랜잭션, 로깅, 예외처리)을 한 곳에 두고
     * 실제 비즈니스 로직만 Function/Supplier로 주입
     */
    static class TransactionTemplate {

        // 공통: 시작 로그 -> 비즈니스 로직 -> 종료 로그 -> 예외 처리
        public static <T> T execute(Supplier<T> businessLogic) {
            log.info("[TX] 트랜잭션 시작");
            try {
                T result = businessLogic.get();
                log.info("[TX] 트랜잭션 커밋");
                return result;
            } catch (Exception e) {
                log.error("[TX] 트랜잭션 롤백: {}", e.getMessage());
                throw e;
            }
        }

        // 반환값 없는 버전
        public static void executeVoid(Runnable businessLogic) {
            execute(() -> {
                businessLogic.run();
                return null;
            });
        }
    }

    public static void templateMethod() {
        String result = TransactionTemplate.execute(() -> {
            log.info("주문 처리 중...");
            return "ORDER-DONE";
        });
        log.info("결과: {}", result);
    }

    /**
     * 패턴 3: 미들웨어 / 데코레이터 체인
     *
     * Function<T,T>를 andThen으로 이어 붙여 처리 파이프라인 구성
     * HTTP 필터, 로깅 래퍼, 캐시 래퍼 등에 적용
     */
    static class RequestPipeline {

        record Request(String path, Map<String, String> headers, String body) {}

        // 각 미들웨어는 UnaryOperator<Request>
        static final UnaryOperator<Request> logging = req -> {
            log.info("[미들웨어] 요청: {} {}", req.path(), req.body());
            return req;
        };

        static final UnaryOperator<Request> authentication = req -> {
            String token = req.headers().getOrDefault("Authorization", "");
            if (token.isBlank()) {
                throw new SecurityException("인증 토큰 없음");
            }
            return req;
        };

        static final UnaryOperator<Request> sanitize = req -> {
            String sanitized = req.body().replaceAll("<[^>]+>", ""); // HTML 태그 제거
            return new Request(req.path(), req.headers(), sanitized);
        };

        // 파이프라인 조립: 로깅 -> 인증 -> 소독
        public static UnaryOperator<Request> buildPipeline(List<UnaryOperator<Request>> middlewares) {
            return middlewares.stream()
                    .reduce(UnaryOperator.identity(), (f, g) -> req -> g.apply(f.apply(req)));
        }
    }

    /**
     * 패턴 4: Null-Safe 래퍼 유틸리티
     *
     * Supplier / Function을 활용한 안전한 null 처리
     */
    static class NullSafe {

        // Optional.map 체인 대신 Function 파이프라인
        public static <T, R> Optional<R> safeGet(T obj, Function<T, R> getter) {
            return Optional.ofNullable(obj).map(getter);
        }

        // null 안전 변환 체인
        public static <T, R> R getOrDefault(T obj, Function<T, R> getter, R defaultValue) {
            return Optional.ofNullable(obj).map(getter).orElse(defaultValue);
        }

        // 여러 단계의 null 안전 접근: user?.address?.city 같은 패턴
        @SuppressWarnings("unchecked")
        public static <T, R> R deepGet(Object root, Function<Object, Object>... getters) {
            Object current = root;
            for (Function<Object, Object> getter : getters) {
                if (current == null) return null;
                current = getter.apply(current);
            }
            return (R) current;
        }
    }

    /**
     * 패턴 5: Comparator 조합
     *
     * Comparator는 함수형 인터페이스이자 풍부한 조합 API를 제공
     * 실무: 복합 정렬, 정렬 방향 전환
     */
    static class ComparatorComposition {

        public static void sortUsers(List<User> users) {
            // 1차: 역할 오름차순, 2차: 나이 내림차순, 3차: 이름 오름차순
            Comparator<User> comparator = Comparator.comparing(User::role)
                    .thenComparingInt(User::age).reversed()
                    .thenComparing(User::name);

            users.sort(comparator);
            users.forEach(u -> log.info("{} / {} / {}", u.role(), u.age(), u.name()));
        }

        // null 안전 정렬
        public static void sortWithNullSafe(List<String> items) {
            items.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        }

        // 동적 정렬 조건 조합
        public static Comparator<User> buildComparator(List<String> sortFields) {
            Comparator<User> base = Comparator.comparing(u -> 0); // 기본 동등
            for (String field : sortFields) {
                base = switch (field) {
                    case "name" -> base.thenComparing(User::name);
                    case "age"  -> base.thenComparingInt(User::age);
                    case "role" -> base.thenComparing(User::role);
                    default -> base;
                };
            }
            return base;
        }
    }

    /**
     * 패턴 6: 재시도 로직 (Retry with Supplier)
     *
     * Supplier를 받아 실패 시 재시도하는 공통 유틸
     * 외부 API 호출, 데이터베이스 연결 등에 사용
     */
    static class RetryUtil {

        public static <T> T withRetry(Supplier<T> operation, int maxAttempts) {
            Exception lastException = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    lastException = e;
                    log.warn("시도 {}/{} 실패: {}", attempt, maxAttempts, e.getMessage());
                }
            }
            throw new RuntimeException("최대 재시도 초과", lastException);
        }

        public static void withRetryVoid(Runnable operation, int maxAttempts) {
            withRetry(() -> {
                operation.run();
                return null;
            }, maxAttempts);
        }
    }

    /**
     * 패턴 7: 그룹핑 / 파티셔닝 with Predicate
     *
     * Collectors.groupingBy, partitioningBy와 Predicate 조합
     */
    static class GroupingPatterns {

        public static Map<Boolean, List<User>> partitionByAdult(List<User> users) {
            // partitioningBy: Predicate로 true/false 두 그룹으로 분리
            return users.stream()
                    .collect(Collectors.partitioningBy(u -> u.age() >= 18));
        }

        public static Map<String, List<User>> groupByRole(List<User> users) {
            return users.stream()
                    .collect(Collectors.groupingBy(User::role));
        }

        // 역할별 평균 나이
        public static Map<String, Double> avgAgeByRole(List<User> users) {
            return users.stream()
                    .collect(Collectors.groupingBy(User::role,
                            Collectors.averagingInt(User::age)));
        }
    }

    /**
     * 패턴 8: Callable vs Supplier
     *
     * Callable<T>: java.util.concurrent 패키지, throws Exception
     * Supplier<T>: java.util.function 패키지, 예외 없음 (언체크만 허용)
     *
     * checked exception이 필요하면 Callable, 없으면 Supplier 사용
     */
    static class CallableVsSupplier {

        @FunctionalInterface
        interface ThrowingSupplier<T> {
            T get() throws Exception;
        }

        // checked exception을 Supplier로 감싸는 유틸 (실무에서 자주 필요)
        public static <T> Supplier<T> wrap(ThrowingSupplier<T> throwingSupplier) {
            return () -> {
                try {
                    return throwingSupplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        // 사용 예: 파일 읽기 같은 checked exception 작업을 Supplier로 래핑
        public static void example() {
            Supplier<String> safeReader = wrap(() -> {
                // new FileReader("file.txt") 같은 checked exception 발생 작업
                return "file contents";
            });

            String content = safeReader.get(); // 이제 예외처리 없이 사용 가능
            log.info("content: {}", content);
        }
    }
}
