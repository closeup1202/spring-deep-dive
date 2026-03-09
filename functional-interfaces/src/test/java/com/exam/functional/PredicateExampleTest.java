package com.exam.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.*;

class PredicateExampleTest {

    @Test
    @DisplayName("Predicate - test(): 조건을 검사하여 boolean을 반환한다")
    void basicPredicate() {
        Predicate<Integer> isPositive = n -> n > 0;
        assertThat(isPositive.test(5)).isTrue();
        assertThat(isPositive.test(-1)).isFalse();
        assertThat(isPositive.test(0)).isFalse();
    }

    @Test
    @DisplayName("Predicate - and(): 두 조건을 AND로 결합한다 (단축 평가)")
    void and() {
        Predicate<Integer> isPositive = n -> n > 0;
        Predicate<Integer> isEven = n -> n % 2 == 0;
        Predicate<Integer> isPositiveEven = isPositive.and(isEven);

        assertThat(isPositiveEven.test(4)).isTrue();
        assertThat(isPositiveEven.test(-4)).isFalse();
        assertThat(isPositiveEven.test(3)).isFalse();

        // 단축 평가 확인: isPositive가 false면 isEven을 실행하지 않음
        boolean[] secondCalled = {false};
        Predicate<Integer> falseFirst = n -> false;
        Predicate<Integer> trackSecond = n -> { secondCalled[0] = true; return true; };

        falseFirst.and(trackSecond).test(1);
        assertThat(secondCalled[0]).isFalse();
    }

    @Test
    @DisplayName("Predicate - or(): 두 조건을 OR로 결합한다 (단축 평가)")
    void or() {
        Predicate<Integer> isPositive = n -> n > 0;
        Predicate<Integer> isEven = n -> n % 2 == 0;
        Predicate<Integer> positiveOrEven = isPositive.or(isEven);

        assertThat(positiveOrEven.test(3)).isTrue();  // 양수
        assertThat(positiveOrEven.test(-4)).isTrue(); // 짝수
        assertThat(positiveOrEven.test(-3)).isFalse(); // 음수이면서 홀수

        // 단축 평가 확인: or에서 첫 번째가 true면 두 번째를 실행하지 않음
        boolean[] secondCalled = {false};
        Predicate<Integer> trueFirst = n -> true;
        Predicate<Integer> trackSecond = n -> { secondCalled[0] = true; return false; };

        trueFirst.or(trackSecond).test(1);
        assertThat(secondCalled[0]).isFalse();
    }

    @Test
    @DisplayName("Predicate - negate(): 조건을 반전한다")
    void negate() {
        Predicate<String> isEmpty = String::isEmpty;
        Predicate<String> isNotEmpty = isEmpty.negate();

        assertThat(isNotEmpty.test("")).isFalse();
        assertThat(isNotEmpty.test("hello")).isTrue();
    }

    @Test
    @DisplayName("Predicate.not() - 메서드 참조와 조합 가능 (Java 11+)")
    void notStaticMethod() {
        Predicate<String> isNotBlank = Predicate.not(String::isBlank);

        assertThat(isNotBlank.test("hello")).isTrue();
        assertThat(isNotBlank.test("   ")).isFalse();
        assertThat(isNotBlank.test("")).isFalse();
    }

    @Test
    @DisplayName("Predicate 조합 - 복합 조건을 동적으로 조립한다")
    void combinedPredicates() {
        PredicateExample.User alice = new PredicateExample.User("Alice", "alice@x.com", 25, true);
        PredicateExample.User bob = new PredicateExample.User("Bob", "bob@x.com", 15, true);
        PredicateExample.User carol = new PredicateExample.User("Carol", "", 30, false);

        Predicate<PredicateExample.User> canReceiveNewsletter =
                PredicateExample.PredicateCombination.IS_ADULT
                        .and(PredicateExample.PredicateCombination.IS_ACTIVE)
                        .and(PredicateExample.PredicateCombination.HAS_EMAIL);

        assertThat(canReceiveNewsletter.test(alice)).isTrue();  // 성인 + 활성 + 이메일 있음
        assertThat(canReceiveNewsletter.test(bob)).isFalse();   // 미성년자
        assertThat(canReceiveNewsletter.test(carol)).isFalse(); // 비활성 + 이메일 없음
    }

    @Test
    @DisplayName("ValidatorChain - Predicate 조합으로 비밀번호 정책을 검증한다")
    void passwordValidation() {
        assertThat(PredicateExample.ValidatorChain.validatePassword("Abcdefg1")).isTrue();
        assertThat(PredicateExample.ValidatorChain.validatePassword("short1A")).isFalse();  // 8자 미만
        assertThat(PredicateExample.ValidatorChain.validatePassword("nouppercase1")).isFalse(); // 대문자 없음
        assertThat(PredicateExample.ValidatorChain.validatePassword("NoDigitHere")).isFalse();  // 숫자 없음
    }

    @Test
    @DisplayName("Stream.filter - Predicate를 재사용하여 중복을 제거한다")
    void streamFilter() {
        List<PredicateExample.Product> products = List.of(
                new PredicateExample.Product("A", 50_000.0, 5),
                new PredicateExample.Product("B", 150_000.0, 0),
                new PredicateExample.Product("C", 80_000.0, 3)
        );

        List<PredicateExample.Product> available =
                PredicateExample.StreamWithPredicate.findAvailableProducts(products);

        assertThat(available).hasSize(2); // A(재고있고 10만미만), C(재고있고 10만미만)
        assertThat(available).extracting(PredicateExample.Product::name).containsExactly("A", "C");
    }

    @Test
    @DisplayName("Stream.anyMatch / allMatch / noneMatch - Predicate 적용")
    void streamMatchers() {
        List<PredicateExample.Product> products = List.of(
                new PredicateExample.Product("A", 50_000.0, 5),
                new PredicateExample.Product("B", 150_000.0, 3),
                new PredicateExample.Product("C", 2_000_000.0, 1)
        );

        assertThat(PredicateExample.StreamWithPredicate.hasAnyExpensiveItem(products)).isTrue();
        assertThat(PredicateExample.StreamWithPredicate.allInStock(products)).isTrue();
    }

    @Test
    @DisplayName("List.removeIf - Predicate로 조건에 맞는 요소를 제거한다")
    void removeIf() {
        List<PredicateExample.Product> products = new ArrayList<>(List.of(
                new PredicateExample.Product("A", 10_000.0, 5),
                new PredicateExample.Product("B", 20_000.0, 0),  // 재고 없음
                new PredicateExample.Product("C", 30_000.0, 3)
        ));

        PredicateExample.StreamWithPredicate.removeOutOfStock(products);

        assertThat(products).hasSize(2);
        assertThat(products).extracting(PredicateExample.Product::name).containsExactly("A", "C");
    }

    @Test
    @DisplayName("allOf / anyOf - 가변 인자로 Predicate를 동적으로 조합한다")
    @SuppressWarnings("unchecked")
    void allOfAnyOf() {
        PredicateExample.User user = new PredicateExample.User("Alice", "a@x.com", 25, true);

        Predicate<PredicateExample.User> combined = PredicateExample.PredicateCombination.allOf(
                PredicateExample.PredicateCombination.IS_ADULT,
                PredicateExample.PredicateCombination.IS_ACTIVE,
                PredicateExample.PredicateCombination.HAS_EMAIL
        );

        assertThat(combined.test(user)).isTrue();
    }
}
