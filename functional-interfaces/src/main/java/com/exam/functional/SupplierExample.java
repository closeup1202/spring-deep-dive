package com.exam.functional;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.*;

/**
 * Supplier<T> 계열 함수형 인터페이스
 *
 * 핵심 특징: 인자 없음, 반환값 있음
 * SAM(Single Abstract Method): T get()
 *
 * 파생 특화형:
 *   - IntSupplier    : int getAsInt()
 *   - LongSupplier   : long getAsLong()
 *   - DoubleSupplier : double getAsDouble()
 *   - BooleanSupplier: boolean getAsBoolean()
 */
@Slf4j
public class SupplierExample {

    /**
     * 시나리오 1: 기본 사용법
     * Supplier는 "값을 나중에 제공하겠다"는 약속
     */
    static class BasicUsage {

        // 단순 값 공급
        public static String getGreeting() {
            Supplier<String> greeting = () -> "Hello, Functional!";
            return greeting.get();
        }

        // 매번 새 객체를 만들어야 할 때 (팩토리 역할)
        public static List<String> createNewList() {
            Supplier<List<String>> listFactory = ArrayList::new;
            List<String> list1 = listFactory.get();
            List<String> list2 = listFactory.get();
            list1.add("A");
            // list1 != list2 : 매번 새 인스턴스
            return list2;
        }

        // Primitive 특화형 - 오토박싱 없이 성능 최적화
        public static int getRandomInt() {
            IntSupplier random = () -> (int) (Math.random() * 100);
            return random.getAsInt();
        }

        public static boolean isBusinessHour() {
            BooleanSupplier check = () -> {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                return hour >= 9 && hour < 18;
            };
            return check.getAsBoolean();
        }
    }

    /**
     * 시나리오 2: 지연 초기화 (Lazy Initialization)
     *
     * Supplier의 가장 중요한 실무 패턴.
     * "지금 당장 계산하지 말고, 실제로 필요할 때 계산하라"
     */
    static class LazyInitialization {
        private String cachedValue = null;

        // 잘못된 방식: orElse는 항상 인자를 평가함 (비용이 큰 연산도 무조건 실행)
        public String badWay(String value) {
            return Optional.ofNullable(value)
                    .orElse(expensiveOperation()); // expensiveOperation()이 항상 호출됨!
        }

        // 올바른 방식: orElseGet은 Supplier를 받아서 필요할 때만 평가
        public String goodWay(String value) {
            return Optional.ofNullable(value)
                    .orElseGet(() -> expensiveOperation()); // value가 null일 때만 호출
        }

        // orElseThrow도 Supplier를 받음: 예외를 "나중에" 생성
        public String getOrThrow(String value) {
            return Optional.ofNullable(value)
                    .orElseThrow(() -> new IllegalArgumentException("값이 없습니다"));
        }

        // 지연 캐시: 처음 요청 시에만 계산하고 이후엔 캐시 반환
        public String getWithCache(Supplier<String> supplier) {
            if (cachedValue == null) {
                cachedValue = supplier.get(); // 딱 한 번만 실행
            }
            return cachedValue;
        }

        private String expensiveOperation() {
            log.info("expensiveOperation 실행됨! (DB 조회, 네트워크 호출 등)");
            return "expensive result";
        }
    }

    /**
     * 시나리오 3: 로거 지연 평가 (실무 필수 패턴)
     *
     * 로그 레벨이 DEBUG가 아닐 때 문자열 연산 비용을 0으로 만드는 패턴.
     * SLF4J logger.debug(String, Object...) 도 같은 원리.
     */
    static class LazyLogging {

        // 나쁜 방법: 로그 레벨과 무관하게 문자열이 항상 생성됨
        public void badLogging(boolean isDebugEnabled, List<String> bigList) {
            if (isDebugEnabled) {
                log.debug("List contents: {}", String.join(", ", bigList)); // bigList가 클 때 비용 큼
            }
        }

        // 좋은 방법: Supplier로 감싸 실제 필요할 때만 문자열 생성
        public void lazyLog(boolean isDebugEnabled, Supplier<String> messageSupplier) {
            if (isDebugEnabled) {
                log.debug(messageSupplier.get()); // 레벨이 맞을 때만 실행
            }
        }

        // 사용 예: lazyLog(log.isDebugEnabled(), () -> "List: " + expensiveToString(bigList));
    }

    /**
     * 시나리오 4: 의존성 주입 / 전략 교체
     *
     * Supplier를 필드로 가지면 런타임에 값 생성 전략을 바꿀 수 있음
     */
    static class IdGenerator {
        private final Supplier<String> idStrategy;

        // UUID 기반
        public static IdGenerator uuid() {
            return new IdGenerator(() -> UUID.randomUUID().toString());
        }

        // 순번 기반 (테스트에서 예측 가능한 ID를 사용할 때 유용)
        public static IdGenerator sequential() {
            int[] counter = {0};
            return new IdGenerator(() -> "ID-" + (++counter[0]));
        }

        // 고정값 (테스트용)
        public static IdGenerator fixed(String id) {
            return new IdGenerator(() -> id);
        }

        private IdGenerator(Supplier<String> idStrategy) {
            this.idStrategy = idStrategy;
        }

        public String generate() {
            return idStrategy.get();
        }
    }

    /**
     * 시나리오 5: Map.computeIfAbsent와 Supplier
     *
     * Map.computeIfAbsent(key, Function<K,V>) 는 Supplier와 유사.
     * 키가 없을 때만 값을 계산해서 넣음.
     */
    static class CacheMap {
        private final Map<String, List<String>> cache = new HashMap<>();

        // 키가 없으면 새 리스트를 만들어 넣고 반환 (Null-safe 방어 코드 불필요)
        public void addToGroup(String key, String value) {
            cache.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        // 비용이 큰 연산도 캐시되어 중복 실행 안 됨
        public String computeOnce(String key, Supplier<String> computation) {
            return cache.computeIfAbsent(key, k -> List.of(computation.get())).get(0);
        }

        public Map<String, List<String>> getCache() {
            return Collections.unmodifiableMap(cache);
        }
    }
}
