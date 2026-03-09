package com.exam.functional;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.*;

/**
 * Consumer<T> / BiConsumer<T,U> 계열 함수형 인터페이스
 *
 * 핵심 특징: 인자 있음, 반환값 없음 (부수효과를 위해 사용)
 * SAM: void accept(T t)
 *
 * 파생 특화형:
 *   - IntConsumer    : void accept(int)
 *   - LongConsumer   : void accept(long)
 *   - DoubleConsumer : void accept(double)
 *   - ObjIntConsumer<T>   : void accept(T, int)  -- BiConsumer 특화
 *   - ObjLongConsumer<T>  : void accept(T, long)
 *   - ObjDoubleConsumer<T>: void accept(T, double)
 */
@Slf4j
public class ConsumerExample {

    record User(String name, String email, int age) {}

    /**
     * 시나리오 1: 기본 사용법
     */
    static class BasicUsage {

        public static void basicConsumer() {
            Consumer<String> printer = s -> log.info("출력: {}", s);
            printer.accept("Hello");

            Consumer<User> userLogger = user ->
                    log.info("User[name={}, email={}]", user.name(), user.email());
            userLogger.accept(new User("홍길동", "hong@exam.com", 30));

            // 메서드 참조로 Consumer 정의
            Consumer<String> sysout = System.out::println;
            sysout.accept("method reference consumer");
        }

        // Primitive 특화형: 박싱 비용 없음
        public static void primitiveConsumers() {
            IntConsumer printInt = i -> log.info("int: {}", i);
            printInt.accept(42);

            LongConsumer printLong = l -> log.info("long: {}", l);
            printLong.accept(1_000_000_000L);

            // ObjIntConsumer: 객체 + int 인자 (인덱스 처리 등에 유용)
            ObjIntConsumer<String> printWithIndex = (s, i) -> log.info("[{}] {}", i, s);
            printWithIndex.accept("item", 0);
        }
    }

    /**
     * 시나리오 2: andThen() - Consumer 체이닝
     *
     * consumer1.andThen(consumer2): consumer1 실행 후 consumer2 실행
     * 순서가 보장되므로 파이프라인 구성에 활용
     */
    static class ConsumerChaining {

        public static Consumer<User> buildUserPipeline() {
            Consumer<User> validate = user -> {
                if (user.name() == null || user.name().isBlank()) {
                    throw new IllegalArgumentException("이름은 필수입니다");
                }
            };

            Consumer<User> logUser = user ->
                    log.info("[감사 로그] 사용자 처리: {}", user.name());

            Consumer<User> sendWelcomeEmail = user ->
                    log.info("[이메일 발송] {} 님께 환영 메일 전송", user.email());

            // 체인: validate -> logUser -> sendWelcomeEmail
            return validate.andThen(logUser).andThen(sendWelcomeEmail);
        }

        public static void processUsers(List<User> users, Consumer<User> processor) {
            users.forEach(processor); // Iterable.forEach는 Consumer를 받음
        }
    }

    /**
     * 시나리오 3: BiConsumer<T,U>
     *
     * 두 개의 인자를 받고, 반환값 없음
     * 대표 사용처: Map.forEach
     */
    static class BiConsumerUsage {

        public static void mapForEach() {
            Map<String, Integer> scores = Map.of("Alice", 95, "Bob", 87, "Charlie", 92);

            // Map.forEach는 BiConsumer<K, V>를 받음
            BiConsumer<String, Integer> printScore =
                    (name, score) -> log.info("{}: {}점", name, score);

            scores.forEach(printScore);
        }

        // 두 컬렉션을 병렬 처리할 때
        public static void processWithIndex(List<String> items) {
            BiConsumer<Integer, String> indexedPrint =
                    (index, item) -> log.info("[{}] {}", index, item);

            for (int i = 0; i < items.size(); i++) {
                indexedPrint.accept(i, items.get(i));
            }
        }

        // BiConsumer도 andThen 체이닝 가능
        public static BiConsumer<String, List<String>> buildGroupProcessor() {
            BiConsumer<String, List<String>> logGroup =
                    (key, values) -> log.info("그룹 [{}]: {} 개", key, values.size());

            BiConsumer<String, List<String>> validateGroup =
                    (key, values) -> {
                        if (values.isEmpty()) {
                            throw new IllegalStateException("그룹 " + key + "이 비어있습니다");
                        }
                    };

            return logGroup.andThen(validateGroup);
        }
    }

    /**
     * 시나리오 4: 실무 패턴 - 이벤트 핸들러 / 콜백
     *
     * Consumer를 콜백으로 전달하면 처리 로직을 외부에서 주입할 수 있음
     * Observer 패턴의 함수형 버전
     */
    static class EventHandler {
        private final List<Consumer<String>> listeners = new ArrayList<>();

        public void addListener(Consumer<String> listener) {
            listeners.add(listener);
        }

        public void publish(String event) {
            listeners.forEach(listener -> listener.accept(event));
        }

        public static EventHandler buildSampleHandler() {
            EventHandler handler = new EventHandler();
            handler.addListener(e -> log.info("[로그] 이벤트: {}", e));
            handler.addListener(e -> log.info("[알림] 이벤트 발생: {}", e));
            return handler;
        }
    }

    /**
     * 시나리오 5: Consumer vs Function 선택 기준
     *
     * Consumer: 부수효과(로깅, DB 저장, 이메일 발송)가 목적, 반환값 불필요
     * Function: 변환/계산이 목적, 반환값이 필요
     *
     * Consumer를 써야 하는 신호:
     *   - 반환값을 사용하지 않음
     *   - 외부 상태를 변경하거나 I/O 작업을 수행
     *   - forEach, peek(디버깅용)에서 사용
     */
    static class ConsumerVsFunction {

        // 잘못된 선택: 반환값이 없는데 Function 사용
        @SuppressWarnings("unused")
        Function<User, Void> badChoice = user -> {
            log.info("user: {}", user);
            return null; // null을 반환해야 해서 어색함
        };

        // 올바른 선택: Consumer 사용
        Consumer<User> goodChoice = user -> log.info("user: {}", user);

        // Stream.peek()은 중간 디버깅용 Consumer (collect 전 로그)
        public static List<User> processWithDebug(List<User> users) {
            return users.stream()
                    .peek(u -> log.debug("처리 전: {}", u.name()))
                    .filter(u -> u.age() >= 18)
                    .peek(u -> log.debug("필터 통과: {}", u.name()))
                    .toList();
        }
    }
}
