package com.project.curve.spring.metrics;

/**
 * NoOp implementation that does not collect metrics.
 * <p>
 * Automatically registered when Micrometer is not present in the classpath,
 * safely ignoring metric calls without null checks.
 */
public class NoOpCurveMetricsCollector implements CurveMetricsCollector {

    @Override
    public void recordEventPublished(String eventType, boolean success, long durationMs) {
        // no-op
    }

    @Override
    public void recordDlqEvent(String eventType, String reason) {
        // no-op
    }

    @Override
    public void recordRetry(String eventType, int retryCount, String finalStatus) {
        // no-op
    }

    @Override
    public void recordKafkaError(String errorType) {
        // no-op
    }

    @Override
    public void recordAuditFailure(String eventType, String errorType) {
        // no-op
    }

    @Override
    public void recordPiiProcessing(String strategy, boolean success) {
        // no-op
    }

    @Override
    public void recordIdGeneration(String generatorType, long durationNanos) {
        // no-op
    }
}
