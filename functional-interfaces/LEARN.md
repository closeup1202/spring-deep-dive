# 함수형 인터페이스 (Functional Interfaces)

`java.util.function` 패키지의 핵심 함수형 인터페이스 정리.
모든 함수형 인터페이스는 `@FunctionalInterface`가 붙은 **SAM(Single Abstract Method) 인터페이스**이며, 람다/메서드 참조로 구현할 수 있다.

---

## 전체 구조 한 눈에 보기

| 인터페이스 | 인자 | 반환 | 추상 메서드 | 핵심 용도 |
|---|---|---|---|---|
| `Supplier<T>` | 없음 | T | `get()` | 지연 생성, 팩토리 |
| `Consumer<T>` | T | 없음 | `accept(T)` | 부수효과(로깅, 저장) |
| `BiConsumer<T,U>` | T, U | 없음 | `accept(T,U)` | Map.forEach |
| `Function<T,R>` | T | R | `apply(T)` | 변환/매핑 |
| `BiFunction<T,U,R>` | T, U | R | `apply(T,U)` | 두 값 합성 |
| `UnaryOperator<T>` | T | T | `apply(T)` | 같은 타입 변환 |
| `BinaryOperator<T>` | T, T | T | `apply(T,T)` | reduce, 두 값 병합 |
| `Predicate<T>` | T | boolean | `test(T)` | 조건 검사/필터 |
| `BiPredicate<T,U>` | T, U | boolean | `test(T,U)` | 두 값 조건 검사 |
| `Runnable` | 없음 | 없음 | `run()` | 비동기 태스크 |

---

## 1. Supplier\<T\>

> **"지금 말고 나중에 값을 주겠다"** — 지연(Lazy) 평가의 핵심

```java
Supplier<String> greeting = () -> "Hello";
String value = greeting.get(); // "Hello"

// 팩토리 패턴
Supplier<List<String>> listFactory = ArrayList::new;
List<String> newList = listFactory.get(); // 매번 새 인스턴스
```

### 핵심 패턴: orElse vs orElseGet

```java
// 나쁜 방법: 값이 있어도 expensiveOp()가 항상 실행됨
String v1 = Optional.ofNullable(value).orElse(expensiveOp());

// 좋은 방법: value가 null일 때만 Supplier 실행
String v2 = Optional.ofNullable(value).orElseGet(() -> expensiveOp());
```

### 지연 초기화 캐시

```java
private String cached = null;

public String getWithCache(Supplier<String> supplier) {
    if (cached == null) cached = supplier.get(); // 딱 한 번만
    return cached;
}
```

### Primitive 특화형 (오토박싱 제거)

```java
IntSupplier    randomInt  = () -> (int)(Math.random() * 100);
LongSupplier   timestamp  = System::currentTimeMillis;
BooleanSupplier isEnabled  = featureFlag::isOn;
DoubleSupplier  randomRate = Math::random;
```

---

## 2. Consumer\<T\>

> **"값을 받아서 처리하되 반환하지 않는다"** — 부수효과(Side Effect) 전용

```java
Consumer<String> printer = System.out::println;
printer.accept("hello"); // "hello" 출력
```

### andThen() — 체이닝 (순서 보장)

```java
Consumer<User> validate  = u -> { if(u.name().isBlank()) throw new ...};
Consumer<User> log       = u -> logger.info("처리: {}", u.name());
Consumer<User> sendEmail = u -> emailService.send(u.email());

// validate → log → sendEmail 순서로 실행
Consumer<User> pipeline = validate.andThen(log).andThen(sendEmail);
users.forEach(pipeline);
```

### BiConsumer\<T,U\>

```java
// Map.forEach는 BiConsumer<K,V>를 받음
map.forEach((key, value) -> System.out.println(key + "=" + value));
```

### Consumer vs Function 선택 기준

- 반환값을 쓰지 않는다 → **Consumer** (Function에서 `return null`은 냄새)
- 로깅, DB 저장, 이메일 발송 → **Consumer**
- 값 변환, 계산 결과가 필요 → **Function**

---

## 3. Function\<T,R\>

> **"입력을 받아 변환하여 반환한다"** — 매핑/변환의 기본

```java
Function<String, Integer> strLen = String::length;
strLen.apply("hello"); // 5

// 엔티티 -> DTO 변환
Function<User, UserDto> toDto = user -> new UserDto(user.name(), maskEmail(user.email()));
```

### andThen() vs compose() — 합성 순서 차이

```java
Function<String, String> trim  = String::trim;
Function<String, String> upper = String::toUpperCase;

// andThen: trim 먼저 → upper (왼쪽에서 오른쪽, 읽기 쉬움)
trim.andThen(upper).apply("  hello  "); // "HELLO"

// compose: upper(trim(x)) — 수학적 합성 (오른쪽에서 왼쪽)
upper.compose(trim).apply("  hello  "); // "HELLO" (동일 결과)
```

**실무에서는 `andThen`을 거의 항상 사용한다** (읽는 방향 = 실행 방향).

### Function.identity()

```java
// x -> x 와 동일
Function<String, String> id = Function.identity();

// Collectors.toMap에서 value를 자기 자신으로
Map<String, User> byName = users.stream()
    .collect(Collectors.toMap(User::name, Function.identity()));
```

### 파이프라인 동적 조립

```java
List<Function<String, String>> steps = List.of(
    String::trim, String::toLowerCase, s -> s.replace(" ", "_")
);
Function<String, String> pipeline = steps.stream()
    .reduce(Function.identity(), Function::andThen);
```

### UnaryOperator\<T\> / BinaryOperator\<T\>

```java
// UnaryOperator: 같은 타입 변환 → List.replaceAll에 사용
UnaryOperator<String> upper = String::toUpperCase;
names.replaceAll(upper);

// BinaryOperator: 두 값을 하나로 → Stream.reduce에 사용
BinaryOperator<Integer> max = (a, b) -> a > b ? a : b;
int maxVal = nums.stream().reduce(Integer.MIN_VALUE, max);
```

---

## 4. Predicate\<T\>

> **"조건을 검사하여 true/false를 반환한다"** — 필터링/검증의 기본

```java
Predicate<Integer> isPositive = n -> n > 0;
isPositive.test(5);  // true
isPositive.test(-1); // false
```

### 논리 조합 메서드

| 메서드 | 동작 | 단축 평가 |
|---|---|---|
| `and(p)` | this && p | this가 false면 p 미실행 |
| `or(p)` | this \|\| p | this가 true면 p 미실행 |
| `negate()` | !this | - |
| `Predicate.not(p)` | !p (static, Java 11+) | 메서드 참조와 조합 가능 |

```java
Predicate<User> canReceiveNewsletter =
    IS_ADULT.and(IS_ACTIVE).and(HAS_EMAIL);

Predicate<String> isNotBlank = Predicate.not(String::isBlank); // Java 11+
```

### 검증 체인 패턴

```java
Predicate<String> validatePassword = notBlank
    .and(minLength(8))
    .and(maxLength(20))
    .and(s -> s.chars().anyMatch(Character::isUpperCase))
    .and(s -> s.chars().anyMatch(Character::isDigit));
```

### Stream과 Predicate

```java
// filter, anyMatch, allMatch, noneMatch, removeIf 모두 Predicate 수신
list.stream().filter(IS_ADULT.and(IS_ACTIVE)).toList();
list.anyMatch(p -> p.price() > 1_000_000);
list.removeIf(p -> p.stock() == 0); // 재고 없는 항목 제거
```

---

## 5. Primitive 특화형 — 성능이 중요할 때

오토박싱/언박싱 비용을 없애는 특화형. 대량 처리 시 GC 압력 감소.

```
Supplier  계열: IntSupplier, LongSupplier, DoubleSupplier, BooleanSupplier
Consumer  계열: IntConsumer, LongConsumer, DoubleConsumer
                ObjIntConsumer<T>, ObjLongConsumer<T>  (BiConsumer 특화)
Function  계열: IntFunction<R>, ToIntFunction<T>, IntUnaryOperator, IntBinaryOperator
Predicate 계열: IntPredicate, LongPredicate, DoublePredicate
```

```java
ToIntFunction<String> strLen = String::length;  // String -> int (박싱 없음)
IntUnaryOperator doubler = x -> x * 2;           // int -> int
IntBinaryOperator add    = Integer::sum;          // (int,int) -> int
```

---

## 6. 실무 패턴 (PracticalExample)

### 전략 패턴 — 클래스 폭발 없이 행위 교체

```java
// 인터페이스 구현체 대신 Function으로 전략 주입
class DiscountStrategy {
    private final Function<Double, Double> discountFn;

    static DiscountStrategy percentage(double rate) {
        return new DiscountStrategy(amount -> amount * (1 - rate));
    }
    static DiscountStrategy vip(String role) {
        return "VIP".equals(role) ? percentage(0.20) : none();
    }
}
```

### 템플릿 메서드 패턴 — 공통 흐름에 로직 주입

```java
// 공통: 트랜잭션 시작/커밋/롤백
public static <T> T execute(Supplier<T> businessLogic) {
    log.info("[TX] 시작");
    try {
        T result = businessLogic.get(); // 비즈니스 로직만 주입
        log.info("[TX] 커밋");
        return result;
    } catch (Exception e) {
        log.error("[TX] 롤백");
        throw e;
    }
}
```

### 재시도 로직 — Supplier 래핑

```java
public static <T> T withRetry(Supplier<T> op, int maxAttempts) {
    for (int i = 1; i <= maxAttempts; i++) {
        try { return op.get(); }
        catch (Exception e) { /* 로그 */ }
    }
    throw new RuntimeException("최대 재시도 초과");
}

// 사용: 외부 API, DB 커넥션
String result = withRetry(() -> externalApi.call(), 3);
```

### checked exception → Supplier 래핑

```java
@FunctionalInterface
interface ThrowingSupplier<T> {
    T get() throws Exception;
}

static <T> Supplier<T> wrap(ThrowingSupplier<T> ts) {
    return () -> {
        try { return ts.get(); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}

// Stream에서 IOException이 있는 작업도 깔끔하게 사용 가능
Supplier<String> safe = wrap(() -> Files.readString(path));
```

### Comparator 조합

```java
// 1차: 역할, 2차: 나이 역순, 3차: 이름
Comparator<User> comp = Comparator.comparing(User::role)
    .thenComparingInt(User::age).reversed()
    .thenComparing(User::name);

// null 안전
items.sort(Comparator.nullsLast(Comparator.naturalOrder()));
```

---

## 7. Callable vs Supplier 선택 기준

| | `Callable<T>` | `Supplier<T>` |
|---|---|---|
| 패키지 | `java.util.concurrent` | `java.util.function` |
| checked exception | O (`throws Exception`) | X (언체크만) |
| 주 사용처 | `ExecutorService.submit()` | Stream, Optional, 람다 |

> checked exception이 필요하면 `Callable`, 없으면 `Supplier`.
> Stream/Optional에서 checked exception이 필요하면 `ThrowingSupplier`로 감싸라.

---

## 8. 커스텀 함수형 인터페이스를 만들어야 할 때

표준 인터페이스로 표현하기 어려운 경우에만 커스텀을 정의한다.

```java
// 세 인자가 필요할 때 (TriFunction은 표준에 없음)
@FunctionalInterface
interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);
}

// checked exception이 필요할 때
@FunctionalInterface
interface ThrowingSupplier<T> {
    T get() throws Exception;
}
```

---

## 실습 파일 구조

```
SupplierExample.java   — Supplier 지연 평가, 팩토리, 캐시, IdGenerator
ConsumerExample.java   — Consumer 체이닝, BiConsumer, 이벤트 핸들러
FunctionExample.java   — Function 합성(andThen/compose), identity, BiFunction, Operator
PredicateExample.java  — Predicate 조합(and/or/negate), Stream 필터, 검증 체인
PracticalExample.java  — 전략 패턴, 템플릿 메서드, 재시도, Comparator, 그룹핑
```
