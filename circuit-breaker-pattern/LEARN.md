# Circuit Breaker Pattern

## 개념
서킷 브레이커(Circuit Breaker) 패턴은 분산 시스템에서 장애가 발생한 서비스로의 요청을 차단하여 시스템 전체의 장애 전파를 막고, 장애 서비스가 복구될 시간을 벌어주는 패턴입니다. 전기 회로 차단기에서 유래했습니다.

## 상태 (State)

1. **CLOSED (닫힘)**:
   - 정상 상태입니다. 요청이 정상적으로 통과합니다.
   - 실패율이나 연속 실패 횟수가 임계치를 넘으면 OPEN 상태로 전환됩니다.

2. **OPEN (열림)**:
   - 장애 상태입니다. 요청을 즉시 차단(Fail Fast)하고 예외를 발생시킵니다.
   - 설정된 대기 시간(`openDurationMs`)이 지나면 HALF-OPEN 상태로 전환됩니다.

3. **HALF-OPEN (반열림)**:
   - 복구 확인 상태입니다. 제한된 수의 요청만 허용하여 서비스가 정상화되었는지 확인합니다.
   - 요청이 성공하면 CLOSED로 전환(복구)됩니다.
   - 요청이 실패하면 다시 OPEN으로 전환됩니다.

## 구현 (`CircuitBreaker.java`)

이 모듈에서는 외부 라이브러리(Resilience4j 등) 없이 순수 Java로 간단한 서킷 브레이커를 구현했습니다.

- `AtomicInteger`와 `AtomicLong`을 사용하여 스레드 안전하게 상태를 관리합니다.
- `execute(Supplier<T> action)` 메서드를 통해 실행 로직을 감싸서 처리합니다.

```java
CircuitBreaker cb = new CircuitBreaker(3, 5000); // 3회 실패 시 5초간 차단

try {
    cb.execute(() -> externalService.call());
} catch (RuntimeException e) {
    // 서킷 브레이커가 열려있거나 실행 실패 시 처리
}
```

## 활용
`curve` 라이브러리의 `OutboxEventPublisher`에서도 이와 유사한 로직을 사용하여 Kafka 발행 실패가 지속될 경우 불필요한 재시도를 막고 시스템 리소스를 보호합니다.
