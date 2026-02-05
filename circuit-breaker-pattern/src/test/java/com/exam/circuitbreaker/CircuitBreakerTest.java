package com.exam.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    @Test
    @DisplayName("정상 동작 시 CLOSED 상태 유지")
    void closedStateTest() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);

        cb.execute(() -> "Success");
        assertThat(cb.getState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("실패 임계치 도달 시 OPEN 상태로 전환")
    void openStateTest() {
        CircuitBreaker cb = new CircuitBreaker(2, 1000);

        // 1st failure
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(cb.getState()).isEqualTo("CLOSED");

        // 2nd failure -> OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(cb.getState()).isEqualTo("OPEN");

        // Request blocked
        assertThatThrownBy(() -> cb.execute(() -> "Success"))
                .hasMessage("Circuit Breaker is OPEN");
    }

    @Test
    @DisplayName("OPEN 상태에서 일정 시간 후 HALF-OPEN 전환 및 복구")
    void halfOpenRecoveryTest() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 100); // 100ms timeout

        // Fail -> OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("Fail"); }));
        assertThat(cb.getState()).isEqualTo("OPEN");

        // Wait for timeout
        Thread.sleep(150);

        // Next request -> HALF-OPEN -> Success -> CLOSED
        String result = cb.execute(() -> "Success");
        assertThat(result).isEqualTo("Success");
        assertThat(cb.getState()).isEqualTo("CLOSED");
    }
}
