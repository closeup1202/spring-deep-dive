package com.exam.functional;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * Function<T,R> 계열 함수형 인터페이스
 *
 * 핵심 특징: 인자 있음, 반환값 있음 (변환/매핑)
 * SAM: R apply(T t)
 *
 * 주요 파생형:
 *   - BiFunction<T,U,R>   : R apply(T, U)
 *   - UnaryOperator<T>    : Function<T,T> - 같은 타입 변환
 *   - BinaryOperator<T>   : BiFunction<T,T,T> - 같은 타입 두 인자 -> 같은 타입
 *
 * Primitive 특화형 (성능 중요할 때):
 *   - IntFunction<R>      : R apply(int)
 *   - ToIntFunction<T>    : int applyAsInt(T)
 *   - IntUnaryOperator    : int applyAsInt(int)
 *   - IntBinaryOperator   : int applyAsInt(int, int)
 *   - ToIntBiFunction<T,U>: int applyAsInt(T, U)
 *   (Long, Double 버전도 동일하게 존재)
 */
@Slf4j
public class FunctionExample {

    record User(String name, String email, int age) {}
    record UserDto(String displayName, String maskedEmail) {}

    /**
     * 시나리오 1: 기본 사용법 - 타입 변환 / 매핑
     */
    static class BasicUsage {

        public static void basicFunction() {
            // String -> Integer
            Function<String, Integer> parseInt = Integer::parseInt;
            log.info("parseInt: {}", parseInt.apply("42"));

            // String -> String
            Function<String, String> toUpperCase = String::toUpperCase;
            log.info("upper: {}", toUpperCase.apply("hello"));

            // User -> UserDto (엔티티 -> DTO 변환)
            Function<User, UserDto> toDto = user -> new UserDto(
                    user.name() + " (" + user.age() + "세)",
                    maskEmail(user.email())
            );

            User user = new User("홍길동", "hong@example.com", 30);
            UserDto dto = toDto.apply(user);
            log.info("dto: {}", dto);
        }

        // Primitive 특화형: Stream.mapToInt, mapToLong 등에 사용됨
        public static void primitiveFunction() {
            // ToIntFunction<T>: 객체를 int로 변환 (오토박싱 없음)
            ToIntFunction<String> strLen = String::length;
            log.info("length: {}", strLen.applyAsInt("hello world"));

            // IntFunction<R>: int를 객체로 변환
            IntFunction<String> intToStr = i -> "NO." + i;
            log.info("intToStr: {}", intToStr.apply(7));

            // IntUnaryOperator: int -> int (Stream.range 변환 등)
            IntUnaryOperator doubler = x -> x * 2;
            log.info("doubled: {}", doubler.applyAsInt(5));

            // IntBinaryOperator: (int, int) -> int (reduce 등에 사용)
            IntBinaryOperator add = Integer::sum;
            log.info("sum: {}", add.applyAsInt(3, 4));
        }

        private static String maskEmail(String email) {
            int at = email.indexOf('@');
            if (at <= 1) return email;
            return email.charAt(0) + "***" + email.substring(at);
        }
    }

    /**
     * 시나리오 2: andThen() vs compose() - 합성 순서 차이
     *
     * f.andThen(g) : g(f(x)) - f 실행 후 g 실행 (왼쪽에서 오른쪽)
     * f.compose(g) : f(g(x)) - g 실행 후 f 실행 (오른쪽에서 왼쪽, 수학적 합성)
     *
     * 실무에서는 andThen이 훨씬 자주 사용됨 (읽기 쉬움)
     */
    static class FunctionComposition {

        public static void andThenVsCompose() {
            Function<String, String> trim = String::trim;
            Function<String, String> upper = String::toUpperCase;
            Function<String, Integer> length = String::length;

            // andThen: trim -> upper -> length (왼쪽에서 오른쪽으로 읽힘)
            Function<String, Integer> pipeline = trim.andThen(upper).andThen(length);
            log.info("andThen 결과: {}", pipeline.apply("  hello  ")); // "HELLO".length() = 5

            // compose: length(upper(trim(x))) 와 동일
            // upper.compose(trim): trim 먼저, 그 결과를 upper에 적용
            Function<String, String> trimThenUpper = upper.compose(trim);
            log.info("compose 결과: {}", trimThenUpper.apply("  hello  ")); // "HELLO"
        }

        // 실무 패턴: 변환 파이프라인 조립
        public static Function<String, String> buildEmailNormalizer() {
            Function<String, String> trim = String::trim;
            Function<String, String> lower = String::toLowerCase;
            Function<String, String> removeSpaces = s -> s.replace(" ", "");

            return trim.andThen(lower).andThen(removeSpaces);
        }

        // 변환 단계를 리스트로 관리하면 동적 파이프라인도 가능
        public static Function<String, String> buildPipeline(List<Function<String, String>> steps) {
            return steps.stream()
                    .reduce(Function.identity(), Function::andThen);
        }
    }

    /**
     * 시나리오 3: Function.identity()
     *
     * 입력값을 그대로 반환하는 함수. x -> x
     * Map의 key나 value를 그대로 쓸 때, reduce의 초기 함수로 활용
     */
    static class IdentityUsage {

        public static Map<String, User> indexByName(List<User> users) {
            // Collectors.toMap: keyMapper, valueMapper
            // Function.identity()로 value를 User 자신으로 지정
            return users.stream()
                    .collect(Collectors.toMap(User::name, Function.identity()));
        }

        // reduce의 시작점으로 활용
        public static Function<String, String> chainFunctions(List<Function<String, String>> fns) {
            return fns.stream()
                    .reduce(Function.identity(), Function::andThen);
        }
    }

    /**
     * 시나리오 4: BiFunction<T,U,R>
     *
     * 두 인자를 받아 변환
     * BiFunction에는 compose()가 없고 andThen()만 있음
     */
    static class BiFunctionUsage {

        public static void basicBiFunction() {
            BiFunction<String, Integer, String> repeat = (s, n) -> s.repeat(n);
            log.info("repeat: {}", repeat.apply("ab", 3)); // "ababab"

            // 두 User를 합쳐 새 User 생성
            BiFunction<User, User, User> merge = (u1, u2) ->
                    new User(u1.name() + " & " + u2.name(), u1.email(), Math.max(u1.age(), u2.age()));

            User u1 = new User("Alice", "a@x.com", 25);
            User u2 = new User("Bob", "b@x.com", 30);
            log.info("merged: {}", merge.apply(u1, u2));
        }

        // BiFunction.andThen: 결과에 추가 변환 적용
        public static BiFunction<String, String, Integer> combinedLength() {
            BiFunction<String, String, String> concat = (a, b) -> a + b;
            return concat.andThen(String::length);
        }
    }

    /**
     * 시나리오 5: UnaryOperator<T> / BinaryOperator<T>
     *
     * UnaryOperator<T>: Function<T,T>의 특수화 (같은 타입 입출력)
     *   - 실무: List.replaceAll, 값 변환 체인
     *
     * BinaryOperator<T>: BiFunction<T,T,T>의 특수화
     *   - 실무: Stream.reduce, 두 값을 하나로 합칠 때
     */
    static class OperatorUsage {

        public static void unaryOperator() {
            UnaryOperator<String> trim = String::trim;
            UnaryOperator<String> upper = String::toUpperCase;

            // UnaryOperator도 andThen / compose 사용 가능
            UnaryOperator<String> normalize = trim.andThen(upper)::apply;

            List<String> names = new ArrayList<>(List.of("  alice  ", " Bob", "CHARLIE  "));
            names.replaceAll(normalize); // List.replaceAll은 UnaryOperator를 받음
            log.info("normalized: {}", names);
        }

        public static void binaryOperator() {
            // Stream.reduce에서 주로 사용
            BinaryOperator<Integer> max = (a, b) -> a > b ? a : b;
            BinaryOperator<String> concat = (a, b) -> a + ", " + b;

            List<Integer> nums = List.of(3, 1, 4, 1, 5, 9, 2, 6);
            int maxVal = nums.stream().reduce(Integer.MIN_VALUE, max);
            log.info("max: {}", maxVal);

            String joined = List.of("A", "B", "C").stream().reduce("", concat);
            log.info("joined: {}", joined);

            // BinaryOperator.minBy / maxBy: Comparator를 받아 BinaryOperator 생성
            BinaryOperator<User> youngerUser = BinaryOperator.minBy(Comparator.comparingInt(User::age));
            User younger = List.of(new User("A", "a@x.com", 25), new User("B", "b@x.com", 20))
                    .stream()
                    .reduce(youngerUser)
                    .orElseThrow();
            log.info("younger: {}", younger.name());
        }
    }

    /**
     * 시나리오 6: 실무 패턴 - 엔티티 -> DTO 변환기
     *
     * Function을 주입받아 변환 로직을 외부에서 결정
     * Spring에서 @Component로 등록하거나, 테스트에서 교체하기 쉬움
     */
    static class UserConverter {
        private final Function<User, UserDto> toDtoMapper;

        public UserConverter(Function<User, UserDto> toDtoMapper) {
            this.toDtoMapper = toDtoMapper;
        }

        public UserDto convert(User user) {
            return toDtoMapper.apply(user);
        }

        public List<UserDto> convertAll(List<User> users) {
            return users.stream()
                    .map(toDtoMapper)
                    .toList();
        }

        // 기본 변환기: 정적 팩토리
        public static UserConverter defaultConverter() {
            return new UserConverter(user -> new UserDto(
                    user.name(),
                    user.email().replaceAll("(?<=.{2}).(?=.*@)", "*")
            ));
        }
    }
}
