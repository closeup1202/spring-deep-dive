package com.exam.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class SupplierExampleTest {

    @Test
    @DisplayName("Supplier - 기본 동작: get()으로 값을 공급한다")
    void basicSupplier() {
        String result = SupplierExample.BasicUsage.getGreeting();
        assertThat(result).isEqualTo("Hello, Functional!");
    }

    @Test
    @DisplayName("Supplier - 팩토리: 호출할 때마다 새 인스턴스를 반환한다")
    void supplierAsFactory() {
        List<String> list = SupplierExample.BasicUsage.createNewList();
        assertThat(list).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Supplier - 지연 평가: orElse는 항상 실행되고 orElseGet은 null일 때만 실행된다")
    void lazyEvaluation() {
        SupplierExample.LazyInitialization lazy = new SupplierExample.LazyInitialization();

        // 값이 있을 때: orElseGet의 Supplier는 호출되지 않아야 함
        boolean[] supplierCalled = {false};
        String result = Optional.of("existing")
                .orElseGet(() -> {
                    supplierCalled[0] = true;
                    return "fallback";
                });

        assertThat(result).isEqualTo("existing");
        assertThat(supplierCalled[0]).isFalse(); // Supplier 미실행 확인
    }

    @Test
    @DisplayName("Supplier - orElseThrow: null이면 Supplier가 제공하는 예외를 던진다")
    void orElseThrow() {
        SupplierExample.LazyInitialization lazy = new SupplierExample.LazyInitialization();

        assertThatThrownBy(() -> lazy.getOrThrow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("값이 없습니다");

        assertThat(lazy.getOrThrow("value")).isEqualTo("value");
    }

    @Test
    @DisplayName("Supplier - 캐시: 동일한 Supplier로 여러 번 호출해도 첫 번째 결과만 사용한다")
    void supplierWithCache() {
        SupplierExample.LazyInitialization lazy = new SupplierExample.LazyInitialization();
        int[] callCount = {0};

        Supplier<String> countingSupplier = () -> {
            callCount[0]++;
            return "computed-" + callCount[0];
        };

        String first = lazy.getWithCache(countingSupplier);
        String second = lazy.getWithCache(countingSupplier); // 캐시 반환

        assertThat(first).isEqualTo("computed-1");
        assertThat(second).isEqualTo("computed-1"); // 동일한 결과
        assertThat(callCount[0]).isEqualTo(1);       // 딱 한 번만 실행
    }

    @Test
    @DisplayName("IdGenerator - Supplier 주입으로 ID 전략을 교체할 수 있다")
    void idGeneratorStrategy() {
        SupplierExample.IdGenerator fixed = SupplierExample.IdGenerator.fixed("TEST-ID");
        assertThat(fixed.generate()).isEqualTo("TEST-ID");
        assertThat(fixed.generate()).isEqualTo("TEST-ID"); // 항상 동일

        SupplierExample.IdGenerator seq = SupplierExample.IdGenerator.sequential();
        assertThat(seq.generate()).isEqualTo("ID-1");
        assertThat(seq.generate()).isEqualTo("ID-2");
        assertThat(seq.generate()).isEqualTo("ID-3");
    }

    @Test
    @DisplayName("CacheMap - computeIfAbsent로 키가 없을 때만 값을 계산한다")
    void cacheMap() {
        SupplierExample.CacheMap cache = new SupplierExample.CacheMap();

        cache.addToGroup("fruits", "apple");
        cache.addToGroup("fruits", "banana");
        cache.addToGroup("vegs", "carrot");

        assertThat(cache.getCache().get("fruits")).containsExactly("apple", "banana");
        assertThat(cache.getCache().get("vegs")).containsExactly("carrot");
    }
}
