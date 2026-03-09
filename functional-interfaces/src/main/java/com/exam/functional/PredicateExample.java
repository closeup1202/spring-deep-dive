package com.exam.functional;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.*;

/**
 * Predicate<T> / BiPredicate<T,U> 계열 함수형 인터페이스
 *
 * 핵심 특징: 인자 있음, boolean 반환 (조건 검사 / 필터링)
 * SAM: boolean test(T t)
 *
 * 파생 특화형:
 *   - IntPredicate    : boolean test(int)
 *   - LongPredicate   : boolean test(long)
 *   - DoublePredicate : boolean test(double)
 *   - BiPredicate<T,U>: boolean test(T, U)
 */
@Slf4j
public class PredicateExample {

    record User(String name, String email, int age, boolean active) {}
    record Product(String name, double price, int stock) {}

    /**
     * 시나리오 1: 기본 사용법
     */
    static class BasicUsage {

        public static void basicPredicate() {
            Predicate<String> isEmpty = String::isEmpty;
            Predicate<Integer> isPositive = n -> n > 0;
            Predicate<User> isAdult = user -> user.age() >= 18;

            log.info("isEmpty: {}", isEmpty.test(""));
            log.info("isPositive: {}", isPositive.test(-1));
            log.info("isAdult: {}", isAdult.test(new User("Alice", "a@x.com", 17, true)));

            // Primitive 특화형: 오토박싱 없음
            IntPredicate isEven = n -> n % 2 == 0;
            LongPredicate isBigNumber = n -> n > 1_000_000_000L;
            log.info("isEven(4): {}", isEven.test(4));
            log.info("isBigNumber: {}", isBigNumber.test(9_999_999_999L));
        }
    }

    /**
     * 시나리오 2: 논리 조합 메서드
     *
     * and(p)  : this && p  (단축 평가: this가 false면 p 미실행)
     * or(p)   : this || p  (단축 평가: this가 true면 p 미실행)
     * negate(): !this
     * not(p)  : !p (Java 11+, 정적 메서드)
     */
    static class PredicateCombination {

        static final Predicate<User> IS_ADULT = user -> user.age() >= 18;
        static final Predicate<User> IS_ACTIVE = User::active;
        static final Predicate<User> HAS_EMAIL = user ->
                user.email() != null && !user.email().isBlank();

        public static void combinePredicates() {
            // and: 성인 AND 활성 AND 이메일 보유
            Predicate<User> canReceiveNewsletter = IS_ADULT.and(IS_ACTIVE).and(HAS_EMAIL);

            // or: 성인 OR 이메일 보유
            Predicate<User> canAccessContent = IS_ADULT.or(HAS_EMAIL);

            // negate: 비활성 사용자
            Predicate<User> isInactive = IS_ACTIVE.negate();

            // Predicate.not (Java 11+): 메서드 참조와 조합 가능
            Predicate<String> isNotEmpty = Predicate.not(String::isEmpty);

            List<User> users = List.of(
                    new User("Alice", "alice@x.com", 25, true),
                    new User("Bob", "bob@x.com", 15, true),
                    new User("Carol", "", 30, false)
            );

            users.stream()
                    .filter(canReceiveNewsletter)
                    .forEach(u -> log.info("뉴스레터 대상: {}", u.name()));

            users.stream()
                    .filter(isInactive)
                    .forEach(u -> log.info("비활성: {}", u.name()));
        }

        // 여러 Predicate를 동적으로 조합 (AND 체인)
        @SafeVarargs
        public static Predicate<User> allOf(Predicate<User>... predicates) {
            Predicate<User> combined = u -> true; // 항상 참인 시작점
            for (Predicate<User> p : predicates) {
                combined = combined.and(p);
            }
            return combined;
        }

        // 여러 Predicate를 동적으로 조합 (OR 체인)
        @SafeVarargs
        public static Predicate<User> anyOf(Predicate<User>... predicates) {
            Predicate<User> combined = u -> false; // 항상 거짓인 시작점
            for (Predicate<User> p : predicates) {
                combined = combined.or(p);
            }
            return combined;
        }
    }

    /**
     * 시나리오 3: BiPredicate<T,U>
     *
     * 두 인자를 받아 조건을 검사
     * and / or / negate 조합 가능
     */
    static class BiPredicateUsage {

        public static void basicBiPredicate() {
            BiPredicate<String, Integer> longerThan = (s, n) -> s.length() > n;
            BiPredicate<User, Integer> olderThan = (user, age) -> user.age() > age;

            log.info("longerThan: {}", longerThan.test("hello", 3)); // true
            log.info("olderThan: {}", olderThan.test(new User("A", "a@x.com", 25, true), 20)); // true

            // BiPredicate 조합
            BiPredicate<Product, Double> affordable = (p, budget) -> p.price() <= budget;
            BiPredicate<Product, Double> inStock = (p, budget) -> p.stock() > 0;
            BiPredicate<Product, Double> canBuy = affordable.and(inStock);

            Product product = new Product("노트북", 1_500_000.0, 5);
            log.info("canBuy(200만원): {}", canBuy.test(product, 2_000_000.0));
        }
    }

    /**
     * 시나리오 4: 검증 체인 (Validator 패턴)
     *
     * Predicate를 활용한 유연한 유효성 검증 구성
     */
    static class ValidatorChain {

        record ValidationResult(boolean valid, String message) {
            static ValidationResult ok() { return new ValidationResult(true, ""); }
            static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
        }

        // Predicate 기반 단순 검증기
        static Predicate<String> notBlank = s -> s != null && !s.isBlank();
        static Predicate<String> validEmail = s -> s != null && s.contains("@") && s.contains(".");
        static Predicate<String> minLength(int min) {
            return s -> s != null && s.length() >= min;
        }
        static Predicate<String> maxLength(int max) {
            return s -> s != null && s.length() <= max;
        }

        public static boolean validatePassword(String password) {
            Predicate<String> rule = notBlank
                    .and(minLength(8))
                    .and(maxLength(20))
                    .and(s -> s.chars().anyMatch(Character::isUpperCase))
                    .and(s -> s.chars().anyMatch(Character::isDigit));
            return rule.test(password);
        }

        public static boolean validateEmail(String email) {
            return notBlank.and(validEmail).test(email);
        }
    }

    /**
     * 시나리오 5: Stream과 Predicate
     *
     * filter, anyMatch, allMatch, noneMatch 모두 Predicate를 받음
     * Predicate 재사용으로 중복 제거
     */
    static class StreamWithPredicate {

        static final Predicate<Product> IN_STOCK = p -> p.stock() > 0;
        static final Predicate<Product> AFFORDABLE = p -> p.price() < 100_000;

        public static List<Product> findAvailableProducts(List<Product> products) {
            return products.stream()
                    .filter(IN_STOCK.and(AFFORDABLE)) // 재고 있고 10만원 미만
                    .toList();
        }

        public static boolean hasAnyExpensiveItem(List<Product> products) {
            return products.stream().anyMatch(p -> p.price() > 1_000_000);
        }

        public static boolean allInStock(List<Product> products) {
            return products.stream().allMatch(IN_STOCK);
        }

        public static boolean noOutOfStock(List<Product> products) {
            return products.stream().noneMatch(IN_STOCK.negate());
        }

        // removeIf도 Predicate를 받음
        public static void removeOutOfStock(List<Product> products) {
            products.removeIf(p -> p.stock() == 0); // 재고 없는 상품 제거
        }
    }
}
