package com.exam.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
public class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openDurationMs;
    
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile State state = State.CLOSED;

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    public <T> T execute(Supplier<T> action) {
        if (!allowRequest()) {
            throw new RuntimeException("Circuit Breaker is OPEN");
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    private boolean allowRequest() {
        if (state == State.CLOSED) {
            return true;
        }

        if (state == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTime.get() >= openDurationMs) {
                log.info("Circuit transitioning to HALF-OPEN");
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }

        // HALF_OPEN: allow one request to test
        return true;
    }

    private void recordSuccess() {
        if (state == State.HALF_OPEN || state == State.OPEN) {
            log.info("Circuit transitioning to CLOSED");
            state = State.CLOSED;
            consecutiveFailures.set(0);
        } else {
            consecutiveFailures.set(0);
        }
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.CLOSED && failures >= failureThreshold) {
            log.warn("Circuit transitioning to OPEN. Failures: {}", failures);
            state = State.OPEN;
        } else if (state == State.HALF_OPEN) {
            log.warn("Circuit transitioning back to OPEN from HALF-OPEN");
            state = State.OPEN;
        }
    }
    
    public String getState() {
        return state.name();
    }
}
