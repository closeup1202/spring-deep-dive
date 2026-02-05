package com.project.curve.kafka.dlq;

/**
 * Failed event record stored in DLQ (Dead Letter Queue)
 * <p>
 * Stored in DLQ with metadata and original payload of events that failed Kafka transmission
 * Enables failure cause tracking and reprocessing
 *
 * @param eventId Original event ID
 * @param originalTopic Original topic name
 * @param originalPayload Original event payload (JSON)
 * @param exceptionType Type of exception that occurred
 * @param exceptionMessage Exception message
 * @param failedAt Timestamp when failure occurred (epoch millis)
 */
public record FailedEventRecord(
        String eventId,
        String originalTopic,
        String originalPayload,
        String exceptionType,
        String exceptionMessage,
        long failedAt
) {
}
