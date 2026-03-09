package com.exam.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class PracticalExampleTest {

    @Test
    @DisplayName("전략 패턴 - 할인 없음: 금액이 그대로 유지된다")
    void discountNone() {
        PracticalExample.DiscountStrategy strategy = PracticalExample.DiscountStrategy.none();
        assertThat(strategy.apply(100_000.0)).isEqualTo(100_000.0);
    }

    @Test
    @DisplayName("전략 패턴 - 퍼센트 할인: 비율만큼 금액이 감소한다")
    void discountPercentage() {
        PracticalExample.DiscountStrategy strategy = PracticalExample.DiscountStrategy.percentage(0.10);
        assertThat(strategy.apply(100_000.0)).isEqualTo(90_000.0);
    }

    @Test
    @DisplayName("전략 패턴 - 고정 할인: 지정 금액만큼 차감되며 최소 0원이다")
    void discountFixed() {
        PracticalExample.DiscountStrategy strategy = PracticalExample.DiscountStrategy.fixed(20_000.0);
        assertThat(strategy.apply(100_000.0)).isEqualTo(80_000.0);
        assertThat(strategy.apply(10_000.0)).isEqualTo(0.0); // 음수 방지
    }

    @Test
    @DisplayName("전략 패턴 - VIP 여부에 따라 할인 전략이 동적으로 결정된다")
    void discountVip() {
        PracticalExample.DiscountStrategy vip = PracticalExample.DiscountStrategy.vip("VIP");
        PracticalExample.DiscountStrategy normal = PracticalExample.DiscountStrategy.vip("NORMAL");

        assertThat(vip.apply(100_000.0)).isEqualTo(80_000.0);    // 20% 할인
        assertThat(normal.apply(100_000.0)).isEqualTo(100_000.0); // 할인 없음
    }

    @Test
    @DisplayName("템플릿 메서드 패턴 - 비즈니스 로직의 반환값이 그대로 전달된다")
    void transactionTemplate() {
        String result = PracticalExample.TransactionTemplate.execute(() -> "business-result");
        assertThat(result).isEqualTo("business-result");
    }

    @Test
    @DisplayName("템플릿 메서드 패턴 - 예외 발생 시 RuntimeException으로 전파된다")
    void transactionTemplateException() {
        assertThatThrownBy(() ->
                PracticalExample.TransactionTemplate.execute(() -> {
                    throw new IllegalStateException("오류 발생");
                })
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("오류 발생");
    }

    @Test
    @DisplayName("재시도 로직 - 성공할 때까지 지정 횟수만큼 재시도한다")
    void retryOnSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> flaky = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) throw new RuntimeException("일시적 오류");
            return "성공";
        };

        String result = PracticalExample.RetryUtil.withRetry(flaky, 5);
        assertThat(result).isEqualTo("성공");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("재시도 로직 - 최대 횟수 초과 시 RuntimeException을 던진다")
    void retryExhausted() {
        Supplier<String> alwaysFails = () -> { throw new RuntimeException("항상 실패"); };

        assertThatThrownBy(() -> PracticalExample.RetryUtil.withRetry(alwaysFails, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("최대 재시도 초과");
    }

    @Test
    @DisplayName("그룹핑 - partitioningBy: Predicate로 두 그룹으로 분리한다")
    void partitioningBy() {
        List<PracticalExample.User> users = List.of(
                new PracticalExample.User("Alice", "a@x.com", 25, "USER"),
                new PracticalExample.User("Bob", "b@x.com", 15, "USER"),
                new PracticalExample.User("Carol", "c@x.com", 30, "ADMIN")
        );

        Map<Boolean, List<PracticalExample.User>> partitioned =
                PracticalExample.GroupingPatterns.partitionByAdult(users);

        assertThat(partitioned.get(true)).extracting(PracticalExample.User::name)
                .containsExactlyInAnyOrder("Alice", "Carol");
        assertThat(partitioned.get(false)).extracting(PracticalExample.User::name)
                .containsExactly("Bob");
    }

    @Test
    @DisplayName("그룹핑 - groupingBy: Function으로 그룹을 나눈다")
    void groupingBy() {
        List<PracticalExample.User> users = List.of(
                new PracticalExample.User("Alice", "a@x.com", 25, "USER"),
                new PracticalExample.User("Bob", "b@x.com", 30, "ADMIN"),
                new PracticalExample.User("Carol", "c@x.com", 22, "USER")
        );

        Map<String, List<PracticalExample.User>> grouped =
                PracticalExample.GroupingPatterns.groupByRole(users);

        assertThat(grouped.get("USER")).hasSize(2);
        assertThat(grouped.get("ADMIN")).hasSize(1);
    }

    @Test
    @DisplayName("Comparator 조합 - 정렬 필드를 동적으로 조립한다")
    void dynamicComparator() {
        List<PracticalExample.User> users = new ArrayList<>(List.of(
                new PracticalExample.User("Charlie", "c@x.com", 25, "USER"),
                new PracticalExample.User("Alice", "a@x.com", 30, "USER"),
                new PracticalExample.User("Bob", "b@x.com", 25, "USER")
        ));

        java.util.Comparator<PracticalExample.User> comp =
                PracticalExample.ComparatorComposition.buildComparator(List.of("age", "name"));
        users.sort(comp);

        // age 오름차순 -> name 오름차순
        assertThat(users).extracting(PracticalExample.User::name)
                .containsExactly("Bob", "Charlie", "Alice");
    }

    @Test
    @DisplayName("ThrowingSupplier - checked exception을 Supplier로 래핑한다")
    void throwingSupplierWrap() {
        Supplier<String> safe = PracticalExample.CallableVsSupplier.wrap(() -> "result");
        assertThat(safe.get()).isEqualTo("result");
    }

    @Test
    @DisplayName("ThrowingSupplier - 내부 예외는 RuntimeException으로 감싸진다")
    void throwingSupplierWrapsException() {
        Supplier<String> failing = PracticalExample.CallableVsSupplier.wrap(() -> {
            throw new java.io.IOException("IO 오류");
        });

        assertThatThrownBy(failing::get)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(java.io.IOException.class);
    }
}
