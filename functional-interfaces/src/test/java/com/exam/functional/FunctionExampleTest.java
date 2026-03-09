package com.exam.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.*;

import static org.assertj.core.api.Assertions.*;

class FunctionExampleTest {

    @Test
    @DisplayName("Function - apply(): 입력을 변환하여 반환한다")
    void basicFunction() {
        Function<String, Integer> strLen = String::length;
        assertThat(strLen.apply("hello")).isEqualTo(5);

        Function<String, String> upper = String::toUpperCase;
        assertThat(upper.apply("hello")).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("Function - andThen(): f.andThen(g) = g(f(x)), 왼쪽에서 오른쪽으로 실행")
    void andThen() {
        Function<String, String> trim = String::trim;
        Function<String, String> upper = String::toUpperCase;
        Function<String, Integer> length = String::length;

        // trim -> upper -> length
        Function<String, Integer> pipeline = trim.andThen(upper).andThen(length);

        assertThat(pipeline.apply("  hello  ")).isEqualTo(5); // "HELLO".length()
        assertThat(pipeline.apply("  hi  ")).isEqualTo(2);
    }

    @Test
    @DisplayName("Function - compose(): f.compose(g) = f(g(x)), g가 먼저 실행된다")
    void compose() {
        Function<Integer, Integer> multiplyBy2 = x -> x * 2;
        Function<Integer, Integer> addTen = x -> x + 10;

        // addTen.compose(multiplyBy2) = addTen(multiplyBy2(x)) = (x*2) + 10
        Function<Integer, Integer> composed = addTen.compose(multiplyBy2);
        assertThat(composed.apply(5)).isEqualTo(20); // (5*2)+10 = 20

        // addTen.andThen(multiplyBy2) = multiplyBy2(addTen(x)) = (x+10) * 2
        Function<Integer, Integer> andThen = addTen.andThen(multiplyBy2);
        assertThat(andThen.apply(5)).isEqualTo(30); // (5+10)*2 = 30
    }

    @Test
    @DisplayName("Function.identity() - 입력값을 그대로 반환한다")
    void identity() {
        Function<String, String> id = Function.identity();
        assertThat(id.apply("hello")).isEqualTo("hello");

        // Collectors.toMap에서 value를 자신으로 매핑
        List<FunctionExample.User> users = List.of(
                new FunctionExample.User("Alice", "a@x.com", 25),
                new FunctionExample.User("Bob", "b@x.com", 30)
        );
        Map<String, FunctionExample.User> byName = FunctionExample.IdentityUsage.indexByName(users);
        assertThat(byName).containsKey("Alice");
        assertThat(byName.get("Alice").email()).isEqualTo("a@x.com");
    }

    @Test
    @DisplayName("Function - 파이프라인 조합: 여러 변환 단계를 동적으로 조립한다")
    void buildPipeline() {
        List<Function<String, String>> steps = List.of(
                String::trim,
                String::toLowerCase,
                s -> s.replace(" ", "_")
        );

        Function<String, String> pipeline = FunctionExample.FunctionComposition.buildPipeline(steps);
        assertThat(pipeline.apply("  Hello World  ")).isEqualTo("hello_world");
    }

    @Test
    @DisplayName("BiFunction - 두 인자를 받아 변환한다")
    void biFunction() {
        BiFunction<String, Integer, String> repeat = (s, n) -> s.repeat(n);
        assertThat(repeat.apply("ab", 3)).isEqualTo("ababab");
    }

    @Test
    @DisplayName("BiFunction - andThen(): 결과에 추가 변환을 적용한다")
    void biFunctionAndThen() {
        BiFunction<String, String, Integer> combinedLength = FunctionExample.BiFunctionUsage.combinedLength();
        assertThat(combinedLength.apply("hello", " world")).isEqualTo(11);
    }

    @Test
    @DisplayName("UnaryOperator - List.replaceAll(): 리스트의 모든 요소를 변환한다")
    void unaryOperator() {
        java.util.List<String> names = new java.util.ArrayList<>(List.of("  alice  ", " Bob", "CHARLIE  "));

        UnaryOperator<String> normalize = String::trim;
        names.replaceAll(normalize);

        assertThat(names).containsExactly("alice", "Bob", "CHARLIE");
    }

    @Test
    @DisplayName("BinaryOperator - Stream.reduce(): 두 값을 하나로 합친다")
    void binaryOperator() {
        BinaryOperator<Integer> max = (a, b) -> a > b ? a : b;

        List<Integer> nums = List.of(3, 1, 4, 1, 5, 9, 2, 6);
        int maxVal = nums.stream().reduce(Integer.MIN_VALUE, max);
        assertThat(maxVal).isEqualTo(9);
    }

    @Test
    @DisplayName("UserConverter - Function 주입으로 변환 로직을 외부에서 결정한다")
    void userConverter() {
        FunctionExample.UserConverter converter = FunctionExample.UserConverter.defaultConverter();

        FunctionExample.User user = new FunctionExample.User("홍길동", "hong@example.com", 30);
        FunctionExample.UserDto dto = converter.convert(user);

        assertThat(dto.displayName()).isEqualTo("홍길동");
        assertThat(dto.maskedEmail()).doesNotContain("on"); // 마스킹 확인
        assertThat(dto.maskedEmail()).contains("@example.com");
    }

    @Test
    @DisplayName("ToIntFunction - 오토박싱 없이 int 변환을 수행한다")
    void toIntFunction() {
        ToIntFunction<String> strLen = String::length;
        assertThat(strLen.applyAsInt("hello world")).isEqualTo(11);
    }

    @Test
    @DisplayName("이메일 정규화 파이프라인 - trim + lower + 공백 제거")
    void emailNormalizer() {
        Function<String, String> normalizer = FunctionExample.FunctionComposition.buildEmailNormalizer();
        assertThat(normalizer.apply("  HONG Gil Dong@EXAMPLE.COM  "))
                .isEqualTo("honggilDong@example.com".toLowerCase().replace(" ", ""));
    }
}
