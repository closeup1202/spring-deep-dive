package com.exam.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Resilience4j 라이브러리를 사용한 Circuit Breaker 구현
 *
 * 직접 구현한 CircuitBreaker와 비교 포인트:
 * 1. 설정 방식: Config 객체를 통한 유연한 설정
 * 2. 상태 관리: 더 정교한 상태 전이 로직
 * 3. 메트릭: 내장된 모니터링 및 메트릭 수집
 * 4. 이벤트: 이벤트 리스너를 통한 확장성
 */
@Slf4j
public class Resilience4jCircuitBreakerExample {

    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    public Resilience4jCircuitBreakerExample(int failureThreshold, long openDurationMs) {
        // CircuitBreaker 설정
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Sliding Window 방식: COUNT_BASED (호출 횟수 기반) 또는 TIME_BASED (시간 기반)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(failureThreshold) // 슬라이딩 윈도우 크기
                .failureRateThreshold(100.0f) // 실패율 임계값 (100%로 설정하여 실패 횟수로만 판단)
                .minimumNumberOfCalls(failureThreshold) // 최소 호출 횟수

                // OPEN 상태 지속 시간 (이후 HALF_OPEN으로 전환)
                .waitDurationInOpenState(Duration.ofMillis(openDurationMs))

                // HALF_OPEN 상태에서 허용할 호출 횟수
                .permittedNumberOfCallsInHalfOpenState(1)

                // 느린 호출 관련 설정 (여기서는 사용 안함)
                .slowCallDurationThreshold(Duration.ofSeconds(60))
                .slowCallRateThreshold(100.0f)

                // 자동으로 HALF_OPEN에서 CLOSED로 전환
                .automaticTransitionFromOpenToHalfOpenEnabled(false)

                .build();

        // CircuitBreaker Registry 생성 및 등록
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        this.circuitBreaker = registry.circuitBreaker("exampleCircuitBreaker");

        // 이벤트 리스너 등록 (상태 전환 모니터링)
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                    log.info("Circuit Breaker state transition: {} -> {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                .onSuccess(event ->
                    log.debug("Success call - Duration: {}ms", event.getElapsedDuration().toMillis()))
                .onError(event ->
                    log.warn("Failed call - Duration: {}ms, Error: {}",
                            event.getElapsedDuration().toMillis(),
                            event.getThrowable().getClass().getSimpleName()))
                .onCallNotPermitted(event ->
                    log.warn("Call not permitted - Circuit is OPEN"));
    }

    /**
     * 직접 구현한 CircuitBreaker와 동일한 인터페이스 제공
     */
    public <T> T execute(Supplier<T> action) {
        // Resilience4j의 decorateSupplier를 사용하여 Circuit Breaker 적용
        Supplier<T> decoratedSupplier = io.github.resilience4j.circuitbreaker.CircuitBreaker
                .decorateSupplier(circuitBreaker, action);

        return decoratedSupplier.get();
    }

    public String getState() {
        return circuitBreaker.getState().name();
    }

    /**
     * Resilience4j만의 추가 기능: 메트릭 조회
     */
    public void printMetrics() {
        var metrics = circuitBreaker.getMetrics();
        log.info("""

                === Circuit Breaker Metrics ===
                State: {}
                Failure Rate: {}%
                Slow Call Rate: {}%
                Number of Successful Calls: {}
                Number of Failed Calls: {}
                Number of Slow Calls: {}
                Number of Not Permitted Calls: {}
                ================================
                """,
                circuitBreaker.getState(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(),
                metrics.getNumberOfNotPermittedCalls());
    }

    /**
     * Resilience4j만의 추가 기능: 수동으로 상태 전환
     */
    public void transitionToClosedState() {
        circuitBreaker.transitionToClosedState();
        log.info("Manually transitioned to CLOSED state");
    }

    public void transitionToOpenState() {
        circuitBreaker.transitionToOpenState();
        log.info("Manually transitioned to OPEN state");
    }

    /**
     * Resilience4j만의 추가 기능: Circuit Breaker 리셋
     */
    public void reset() {
        circuitBreaker.reset();
        log.info("Circuit Breaker has been reset");
    }
}
