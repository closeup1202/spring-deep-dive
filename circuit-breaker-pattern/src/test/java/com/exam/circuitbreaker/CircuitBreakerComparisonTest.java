package com.exam.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 직접 구현한 CircuitBreaker vs Resilience4j CircuitBreaker 비교 테스트
 *
 * 두 구현체가 동일한 시나리오에서 어떻게 동작하는지 비교
 */
class CircuitBreakerComparisonTest {

    @Test
    @DisplayName("[비교] 정상 동작 시 CLOSED 상태 유지")
    void closedStateComparison() {
        // Custom Implementation
        CircuitBreaker customCb = new CircuitBreaker(3, 1000);
        customCb.execute(() -> "Success");
        assertThat(customCb.getState()).isEqualTo("CLOSED");

        // Resilience4j
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(3, 1000);
        r4jCb.execute(() -> "Success");
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("[비교] 실패 임계치 도달 시 OPEN 상태로 전환")
    void openStateComparison() {
        // Custom Implementation
        CircuitBreaker customCb = new CircuitBreaker(2, 1000);

        assertThatThrownBy(() -> customCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(customCb.getState()).isEqualTo("CLOSED");

        assertThatThrownBy(() -> customCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(customCb.getState()).isEqualTo("OPEN");

        assertThatThrownBy(() -> customCb.execute(() -> "Success"))
                .hasMessage("Circuit Breaker is OPEN");

        // Resilience4j - 동일한 동작
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(2, 1000);

        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail"); }));
        // Resilience4j는 최소 호출 횟수를 만족해야 OPEN으로 전환
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");

        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(r4jCb.getState()).isEqualTo("OPEN");

        // Circuit이 OPEN 상태일 때는 CallNotPermittedException 발생
        assertThatThrownBy(() -> r4jCb.execute(() -> "Success"))
                .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    }

    @Test
    @DisplayName("[비교] OPEN → HALF-OPEN → CLOSED 상태 전환")
    void halfOpenRecoveryComparison() throws InterruptedException {
        // Custom Implementation
        CircuitBreaker customCb = new CircuitBreaker(1, 100);

        assertThatThrownBy(() -> customCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(customCb.getState()).isEqualTo("OPEN");

        Thread.sleep(150);

        String customResult = customCb.execute(() -> "Success");
        assertThat(customResult).isEqualTo("Success");
        assertThat(customCb.getState()).isEqualTo("CLOSED");

        // Resilience4j - 동일한 동작
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(1, 100);

        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(r4jCb.getState()).isEqualTo("OPEN");

        Thread.sleep(150);

        String r4jResult = r4jCb.execute(() -> "Success");
        assertThat(r4jResult).isEqualTo("Success");
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("[비교] HALF-OPEN 상태에서 실패 시 다시 OPEN으로 전환")
    void halfOpenFailureComparison() throws InterruptedException {
        // Custom Implementation
        CircuitBreaker customCb = new CircuitBreaker(1, 100);

        assertThatThrownBy(() -> customCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(customCb.getState()).isEqualTo("OPEN");

        Thread.sleep(150);

        // HALF-OPEN 상태에서 실패 -> 다시 OPEN
        assertThatThrownBy(() -> customCb.execute(() -> { throw new RuntimeException("Fail again"); }));
        assertThat(customCb.getState()).isEqualTo("OPEN");

        // Resilience4j - 동일한 동작
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(1, 100);

        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(r4jCb.getState()).isEqualTo("OPEN");

        Thread.sleep(150);

        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail again"); }));
        assertThat(r4jCb.getState()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("[Resilience4j 전용] 메트릭 수집 및 조회")
    void metricsTest() {
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(3, 1000);

        // 성공 2번
        r4jCb.execute(() -> "Success 1");
        r4jCb.execute(() -> "Success 2");

        // 실패 1번
        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail"); }));

        // 메트릭 출력 (로그로 확인)
        r4jCb.printMetrics();

        // 상태는 여전히 CLOSED (3번 실패해야 OPEN)
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("[Resilience4j 전용] 수동 상태 전환")
    void manualStateTransitionTest() {
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(3, 1000);

        assertThat(r4jCb.getState()).isEqualTo("CLOSED");

        // 수동으로 OPEN 상태로 전환
        r4jCb.transitionToOpenState();
        assertThat(r4jCb.getState()).isEqualTo("OPEN");

        // OPEN 상태에서는 요청이 차단됨
        assertThatThrownBy(() -> r4jCb.execute(() -> "Success"))
                .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

        // 수동으로 CLOSED 상태로 전환
        r4jCb.transitionToClosedState();
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");

        // 이제 정상 동작
        String result = r4jCb.execute(() -> "Success");
        assertThat(result).isEqualTo("Success");
    }

    @Test
    @DisplayName("[Resilience4j 전용] Circuit Breaker 리셋")
    void resetTest() {
        Resilience4jCircuitBreakerExample r4jCb = new Resilience4jCircuitBreakerExample(2, 1000);

        // 2번 실패 -> OPEN
        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail 1"); }));
        assertThatThrownBy(() -> r4jCb.execute(() -> { throw new RuntimeException("Fail 2"); }));
        assertThat(r4jCb.getState()).isEqualTo("OPEN");

        // 리셋 -> 모든 메트릭 및 상태 초기화
        r4jCb.reset();
        assertThat(r4jCb.getState()).isEqualTo("CLOSED");

        // 정상 동작
        String result = r4jCb.execute(() -> "Success after reset");
        assertThat(result).isEqualTo("Success after reset");
    }

    @Test
    @DisplayName("[실전 시나리오] 외부 API 호출 시뮬레이션")
    void realWorldScenarioComparison() throws InterruptedException {
        System.out.println("\n=== Custom CircuitBreaker ===");
        testExternalApiCall(new CircuitBreaker(3, 500));

        System.out.println("\n=== Resilience4j CircuitBreaker ===");
        testExternalApiCallWithR4j(new Resilience4jCircuitBreakerExample(3, 500));
    }

    private void testExternalApiCall(CircuitBreaker cb) throws InterruptedException {
        // 외부 API가 정상 동작
        for (int i = 0; i < 3; i++) {
            String result = cb.execute(() -> callExternalApi(false));
            System.out.println("Call " + (i + 1) + ": " + result + " | State: " + cb.getState());
        }

        // 외부 API 장애 발생 (3번 실패)
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> callExternalApi(true));
            } catch (Exception e) {
                System.out.println("Call " + (i + 4) + ": Failed | State: " + cb.getState());
            }
        }

        // Circuit이 OPEN 상태 - 요청 차단
        try {
            cb.execute(() -> callExternalApi(false));
        } catch (Exception e) {
            System.out.println("Call 7: Blocked by circuit breaker | State: " + cb.getState());
        }

        // 대기 후 복구 시도
        Thread.sleep(600);
        String result = cb.execute(() -> callExternalApi(false));
        System.out.println("Call 8: " + result + " (Recovered) | State: " + cb.getState());
    }

    private void testExternalApiCallWithR4j(Resilience4jCircuitBreakerExample cb) throws InterruptedException {
        // 외부 API가 정상 동작
        for (int i = 0; i < 3; i++) {
            String result = cb.execute(() -> callExternalApi(false));
            System.out.println("Call " + (i + 1) + ": " + result + " | State: " + cb.getState());
        }

        // 외부 API 장애 발생 (3번 실패)
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> callExternalApi(true));
            } catch (Exception e) {
                System.out.println("Call " + (i + 4) + ": Failed | State: " + cb.getState());
            }
        }

        // Circuit이 OPEN 상태 - 요청 차단
        try {
            cb.execute(() -> callExternalApi(false));
        } catch (Exception e) {
            System.out.println("Call 7: Blocked by circuit breaker | State: " + cb.getState());
        }

        // 대기 후 복구 시도
        Thread.sleep(600);
        String result = cb.execute(() -> callExternalApi(false));
        System.out.println("Call 8: " + result + " (Recovered) | State: " + cb.getState());

        // Resilience4j 메트릭 출력
        cb.printMetrics();
    }

    private String callExternalApi(boolean shouldFail) {
        if (shouldFail) {
            throw new RuntimeException("External API Error");
        }
        return "API Response Success";
    }
}
