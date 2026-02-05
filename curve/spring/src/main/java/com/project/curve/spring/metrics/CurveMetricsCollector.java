package com.project.curve.spring.metrics;

/**
 * Interface for Curve event publishing metrics collector.
 * <p>
 * Uses {@link MicrometerCurveMetricsCollector} if Micrometer is in the classpath,
 * otherwise uses {@link NoOpCurveMetricsCollector}.
 */
public interface CurveMetricsCollector {

    void recordEventPublished(String eventType, boolean success, long durationMs);

    void recordDlqEvent(String eventType, String reason);

    void recordRetry(String eventType, int retryCount, String finalStatus);

    void recordKafkaError(String errorType);

    void recordAuditFailure(String eventType, String errorType);

    void recordPiiProcessing(String strategy, boolean success);

    void recordIdGeneration(String generatorType, long durationNanos);
}
