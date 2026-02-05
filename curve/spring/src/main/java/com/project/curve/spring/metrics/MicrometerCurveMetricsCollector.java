package com.project.curve.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer-based metrics collector implementation.
 * <p>
 * Automatically registered when Micrometer is present in the classpath.
 */
@Slf4j
public record MicrometerCurveMetricsCollector(MeterRegistry meterRegistry) implements CurveMetricsCollector {

    @Override
    public void recordEventPublished(String eventType, boolean success, long durationMs) {
        try {
            Counter.builder("curve.events.published")
                    .tag("eventType", eventType)
                    .tag("success", String.valueOf(success))
                    .description("Total number of events published")
                    .register(meterRegistry)
                    .increment();

            Timer.builder("curve.events.publish.duration")
                    .tag("eventType", eventType)
                    .tag("success", String.valueOf(success))
                    .description("Event publish duration in milliseconds")
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to record event publish metric", e);
        }
    }

    @Override
    public void recordDlqEvent(String eventType, String reason) {
        try {
            Counter.builder("curve.events.dlq.count")
                    .tag("eventType", eventType)
                    .tag("reason", reason)
                    .description("Total number of events sent to DLQ")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record DLQ metric", e);
        }
    }

    @Override
    public void recordRetry(String eventType, int retryCount, String finalStatus) {
        try {
            Counter.builder("curve.events.retry.count")
                    .tag("eventType", eventType)
                    .tag("finalStatus", finalStatus)
                    .description("Total number of event publish retries")
                    .register(meterRegistry)
                    .increment(retryCount);
        } catch (Exception e) {
            log.warn("Failed to record retry metric", e);
        }
    }

    @Override
    public void recordKafkaError(String errorType) {
        try {
            Counter.builder("curve.kafka.producer.errors")
                    .tag("errorType", errorType)
                    .description("Total number of Kafka producer errors")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record Kafka error metric", e);
        }
    }

    @Override
    public void recordAuditFailure(String eventType, String errorType) {
        try {
            Counter.builder("curve.audit.failures")
                    .tag("eventType", eventType)
                    .tag("errorType", errorType)
                    .description("Total number of audit event failures")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record audit failure metric", e);
        }
    }

    @Override
    public void recordPiiProcessing(String strategy, boolean success) {
        try {
            Counter.builder("curve.pii.processing")
                    .tag("strategy", strategy)
                    .tag("success", String.valueOf(success))
                    .description("Total number of PII processing operations")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record PII processing metric", e);
        }
    }

    @Override
    public void recordIdGeneration(String generatorType, long durationNanos) {
        try {
            Counter.builder("curve.id.generation.count")
                    .tag("generatorType", generatorType)
                    .description("Total number of IDs generated")
                    .register(meterRegistry)
                    .increment();

            Timer.builder("curve.id.generation.duration")
                    .tag("generatorType", generatorType)
                    .description("ID generation duration in nanoseconds")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("Failed to record ID generation metric", e);
        }
    }
}
