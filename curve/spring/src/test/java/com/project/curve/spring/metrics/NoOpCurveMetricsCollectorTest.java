package com.project.curve.spring.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoOpCurveMetricsCollector Test")
class NoOpCurveMetricsCollectorTest {

    @Test
    @DisplayName("Create NoOpCurveMetricsCollector")
    void createNoOpCollector() {
        // when
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // then
        assertNotNull(collector);
    }

    @Test
    @DisplayName("Implement CurveMetricsCollector interface")
    void implementsCurveMetricsCollector() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // then
        assertTrue(collector instanceof CurveMetricsCollector);
    }

    @Test
    @DisplayName("recordEventPublished does not throw exception")
    void recordEventPublishedDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordEventPublished("ORDER_CREATED", true, 100L)
        );
    }

    @Test
    @DisplayName("recordDlqEvent does not throw exception")
    void recordDlqEventDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordDlqEvent("ORDER_CREATED", "KAFKA_ERROR")
        );
    }

    @Test
    @DisplayName("recordRetry does not throw exception")
    void recordRetryDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordRetry("ORDER_CREATED", 3, "SUCCESS")
        );
    }

    @Test
    @DisplayName("recordKafkaError does not throw exception")
    void recordKafkaErrorDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordKafkaError("CONNECTION_ERROR")
        );
    }

    @Test
    @DisplayName("recordAuditFailure does not throw exception")
    void recordAuditFailureDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordAuditFailure("ORDER_CREATED", "VALIDATION_ERROR")
        );
    }

    @Test
    @DisplayName("recordPiiProcessing does not throw exception")
    void recordPiiProcessingDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordPiiProcessing("MASKING", true)
        );
    }

    @Test
    @DisplayName("recordIdGeneration does not throw exception")
    void recordIdGenerationDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordIdGeneration("SNOWFLAKE", 1000000L)
        );
    }

    @Test
    @DisplayName("Methods with null values do not throw exception")
    void methodsWithNullValuesDoNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            collector.recordEventPublished(null, true, 0);
            collector.recordDlqEvent(null, null);
            collector.recordRetry(null, 0, null);
            collector.recordKafkaError(null);
            collector.recordAuditFailure(null, null);
            collector.recordPiiProcessing(null, false);
            collector.recordIdGeneration(null, 0);
        });
    }

    @Test
    @DisplayName("Stable across multiple calls")
    void stableAcrossMultipleCalls() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 1000; i++) {
                collector.recordEventPublished("EVENT_" + i, true, i);
                collector.recordDlqEvent("EVENT_" + i, "REASON_" + i);
                collector.recordRetry("EVENT_" + i, i, "STATUS_" + i);
                collector.recordKafkaError("ERROR_" + i);
                collector.recordAuditFailure("EVENT_" + i, "ERROR_" + i);
                collector.recordPiiProcessing("STRATEGY_" + i, i % 2 == 0);
                collector.recordIdGeneration("GENERATOR_" + i, i * 1000L);
            }
        });
    }

    @Test
    @DisplayName("Methods with extreme values do not throw exception")
    void methodsWithExtremeValuesDoNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            collector.recordEventPublished("", false, Long.MAX_VALUE);
            collector.recordRetry("", Integer.MAX_VALUE, "");
            collector.recordIdGeneration("", Long.MIN_VALUE);
        });
    }
}
