# Circuit Breaker Pattern

서킷 브레이커 패턴을 직접 구현한 코드와 Resilience4j 라이브러리를 사용한 구현을 비교하는 모듈입니다.

## 개요

Circuit Breaker는 **장애 전파를 방지**하고 **시스템의 복원력**을 높이기 위한 디자인 패턴입니다.
마이크로서비스 아키텍처에서 외부 서비스 호출 시 발생할 수 있는 장애로부터 시스템을 보호합니다.

### 동작 원리

```
CLOSED (정상) → OPEN (차단) → HALF-OPEN (테스트) → CLOSED (복구)
     ↓              ↓              ↓
   정상 동작    실패 임계치 도달   일정 시간 후
                요청 즉시 차단     제한적 허용
```

## 구현 비교

### 1. 직접 구현 (Custom Implementation)

**파일**: `CircuitBreaker.java`

**특징**:
- 간단하고 직관적인 구현
- 3가지 상태 (CLOSED, OPEN, HALF_OPEN) 관리
- 연속 실패 횟수 기반 상태 전환
- AtomicInteger, AtomicLong을 사용한 스레드 안전성

**핵심 로직**:
```java
// 실패 임계치 도달 시 OPEN
if (failures >= failureThreshold) {
    state = State.OPEN;
}

// 일정 시간 후 HALF-OPEN으로 전환
if (now - lastFailureTime >= openDurationMs) {
    state = State.HALF_OPEN;
}

// HALF-OPEN에서 성공 시 CLOSED로 복구
if (state == State.HALF_OPEN) {
    state = State.CLOSED;
}
```

### 2. Resilience4j 구현

**파일**: `Resilience4jCircuitBreakerExample.java`

**특징**:
- 프로덕션 레벨의 완성도 높은 라이브러리
- 더 정교한 설정 옵션 제공
- 내장 메트릭 및 모니터링
- 이벤트 기반 확장성
- Spring Boot 통합 지원

**추가 기능**:
1. **Sliding Window 방식**: 실패율을 더 정확하게 측정
   - COUNT_BASED: 최근 N번의 호출 기준
   - TIME_BASED: 최근 N초간의 호출 기준

2. **메트릭 수집**:
   ```java
   metrics.getFailureRate()
   metrics.getNumberOfSuccessfulCalls()
   metrics.getNumberOfFailedCalls()
   ```

3. **이벤트 리스너**:
   ```java
   circuitBreaker.getEventPublisher()
       .onStateTransition(event -> ...)
       .onSuccess(event -> ...)
       .onError(event -> ...)
   ```

4. **수동 제어**:
   ```java
   circuitBreaker.transitionToClosedState()
   circuitBreaker.transitionToOpenState()
   circuitBreaker.reset()
   ```

## 주요 차이점 비교

| 항목 | Custom Implementation | Resilience4j |
|------|----------------------|--------------|
| **구현 복잡도** | 낮음 (100줄 미만) | 높음 (설정 복잡) |
| **실패 감지 방식** | 연속 실패 횟수 | 슬라이딩 윈도우 기반 실패율 |
| **메트릭** | 없음 | 상세한 메트릭 제공 |
| **이벤트 처리** | 없음 | 이벤트 리스너 제공 |
| **확장성** | 제한적 | 높음 (이벤트, 데코레이터) |
| **Spring 통합** | 수동 | 자동 설정 지원 |
| **프로덕션 적합성** | 학습용 | 실전 사용 가능 |
| **느린 호출 감지** | 없음 | 지원 |
| **수동 제어** | 없음 | 지원 |

## 테스트

### 기본 동작 테스트

**파일**: `CircuitBreakerTest.java`

- CLOSED 상태 유지 테스트
- OPEN 상태 전환 테스트
- HALF-OPEN → CLOSED 복구 테스트

### 비교 테스트

**파일**: `CircuitBreakerComparisonTest.java`

1. **동일한 시나리오 비교**:
   - 정상 동작 비교
   - 실패 시 동작 비교
   - 복구 과정 비교

2. **Resilience4j 전용 기능 테스트**:
   - 메트릭 수집 및 조회
   - 수동 상태 전환
   - Circuit Breaker 리셋

3. **실전 시나리오**:
   - 외부 API 호출 시뮬레이션
   - 장애 발생 및 복구 과정 비교

### 테스트 실행

```bash
./gradlew :circuit-breaker-pattern:test
```

특정 테스트만 실행:
```bash
# 비교 테스트
./gradlew :circuit-breaker-pattern:test --tests CircuitBreakerComparisonTest

# 실전 시나리오
./gradlew :circuit-breaker-pattern:test --tests "*realWorldScenario*"
```

## 사용 예시

### Custom Implementation

```java
CircuitBreaker cb = new CircuitBreaker(
    failureThreshold: 3,    // 3번 실패 시 OPEN
    openDurationMs: 5000    // 5초 후 HALF-OPEN
);

try {
    String result = cb.execute(() -> callExternalApi());
    System.out.println(result);
} catch (Exception e) {
    System.out.println("Circuit is OPEN or API failed");
}
```

### Resilience4j

```java
Resilience4jCircuitBreakerExample cb =
    new Resilience4jCircuitBreakerExample(3, 5000);

try {
    String result = cb.execute(() -> callExternalApi());
    System.out.println(result);
} catch (CallNotPermittedException e) {
    System.out.println("Circuit is OPEN");
} catch (Exception e) {
    System.out.println("API failed");
}

// 메트릭 확인
cb.printMetrics();
```

## 실전 적용 가이드

### Custom Implementation 사용이 적합한 경우:
- 학습 목적
- 매우 간단한 요구사항
- 라이브러리 의존성을 최소화하고 싶을 때
- Circuit Breaker의 내부 동작을 이해하고 싶을 때

### Resilience4j 사용이 적합한 경우:
- **프로덕션 환경** (강력 추천)
- 정교한 장애 감지가 필요할 때
- 모니터링 및 메트릭이 필요할 때
- Spring Boot 애플리케이션
- 느린 호출(Slow Call) 감지가 필요할 때
- Rate Limiter, Retry, Bulkhead 등 다른 패턴과 함께 사용할 때

## Resilience4j 추가 학습 포인트

### 1. Sliding Window 방식

```java
// COUNT_BASED: 최근 10번의 호출 중 실패율이 50% 이상이면 OPEN
CircuitBreakerConfig.custom()
    .slidingWindowType(COUNT_BASED)
    .slidingWindowSize(10)
    .failureRateThreshold(50.0f)
    .build();

// TIME_BASED: 최근 10초간의 호출 중 실패율이 50% 이상이면 OPEN
CircuitBreakerConfig.custom()
    .slidingWindowType(TIME_BASED)
    .slidingWindowSize(10)
    .failureRateThreshold(50.0f)
    .build();
```

### 2. 느린 호출 감지

```java
CircuitBreakerConfig.custom()
    .slowCallDurationThreshold(Duration.ofSeconds(2))
    .slowCallRateThreshold(50.0f)  // 느린 호출이 50% 이상이면 OPEN
    .build();
```

### 3. Spring Boot 자동 설정

```yaml
resilience4j.circuitbreaker:
  instances:
    backendA:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
```

```java
@Service
public class MyService {
    @CircuitBreaker(name = "backendA", fallbackMethod = "fallback")
    public String callApi() {
        return restTemplate.getForObject("http://api.example.com", String.class);
    }

    public String fallback(Exception e) {
        return "Fallback response";
    }
}
```

## 참고 자료

- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [마이크로서비스 패턴 (크리스 리처드슨)](https://microservices.io/patterns/reliability/circuit-breaker.html)

## 학습 목표

이 모듈을 통해 다음을 학습할 수 있습니다:

1. ✅ Circuit Breaker 패턴의 동작 원리
2. ✅ 직접 구현을 통한 내부 메커니즘 이해
3. ✅ 프로덕션 레벨 라이브러리의 고급 기능
4. ✅ Custom vs Library 구현의 트레이드오프
5. ✅ 실전 적용 시 고려사항
