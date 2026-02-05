package com.project.curve.core.outbox;

import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Domain model for event storage using Transactional Outbox Pattern.
 * <p>
 * Used to guarantee atomicity between DB transactions and event publishing.
 *
 * <h3>How It Works</h3>
 * <ol>
 *   <li>Save OutboxEvent in the same transaction as business logic</li>
 *   <li>Transaction commit → OutboxEvent permanently stored in DB</li>
 *   <li>Separate scheduler publishes PENDING events to Kafka</li>
 *   <li>On success: PUBLISHED, on failure: increment retry count</li>
 * </ol>
 *
 * <h3>Atomicity Guarantee</h3>
 * <pre>
 * @Transactional
 * public Order createOrder() {
 *     Order order = orderRepo.save(...);     // DB save
 *     outboxRepo.save(outboxEvent);          // Outbox save (same transaction)
 *     return order;
 * }
 * // Transaction commit → both saved or both rolled back
 * </pre>
 *
 * @see OutboxStatus
 */
@Getter
public class OutboxEvent {

    private final String eventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant occurredAt;

    private OutboxStatus status;
    private int retryCount;
    private Instant publishedAt;
    private String errorMessage;
    private Instant nextRetryAt;

    /**
     * Outbox event constructor.
     *
     * @param eventId       Unique event ID
     * @param aggregateType Aggregate type (e.g., "Order", "User")
     * @param aggregateId   Aggregate ID (e.g., orderId)
     * @param eventType     Event type (e.g., "ORDER_CREATED")
     * @param payload       Event payload (JSON)
     * @param occurredAt    Event occurrence time
     */
    public OutboxEvent(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant occurredAt
    ) {
        validateNotBlank(eventId, "eventId");
        validateNotBlank(aggregateType, "aggregateType");
        validateNotBlank(aggregateId, "aggregateId");
        validateNotBlank(eventType, "eventType");
        validateNotBlank(payload, "payload");

        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }

        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = occurredAt; // Initially eligible for immediate processing
    }

    /**
     * Factory method for restoring domain model from persistence layer.
     * <p>
     * Unlike constructor, directly sets existing state (status, retryCount, etc.).
     *
     * @param eventId       Event ID
     * @param aggregateType Aggregate type
     * @param aggregateId   Aggregate ID
     * @param eventType     Event type
     * @param payload       Payload
     * @param occurredAt    Occurrence time
     * @param status        Current status
     * @param retryCount    Retry count
     * @param publishedAt   Publish time (nullable)
     * @param errorMessage  Error message (nullable)
     * @param nextRetryAt   Next retry time (nullable)
     * @return Restored OutboxEvent
     */
    public static OutboxEvent restore(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant occurredAt,
            OutboxStatus status,
            int retryCount,
            Instant publishedAt,
            String errorMessage,
            Instant nextRetryAt
    ) {
        OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, payload, occurredAt);
        event.status = status;
        event.retryCount = retryCount;
        event.publishedAt = publishedAt;
        event.errorMessage = errorMessage;
        event.nextRetryAt = nextRetryAt != null ? nextRetryAt : occurredAt;
        return event;
    }

    /**
     * Marks event as successfully published.
     */
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.errorMessage = null;
        this.nextRetryAt = null;
    }

    /**
     * Marks event as failed.
     *
     * @param errorMessage Failure reason
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.nextRetryAt = null;
    }

    /**
     * Increments retry count and sets next retry time.
     *
     * @param backoffMs Retry delay time (milliseconds)
     * @return Incremented retry count
     */
    public int scheduleNextRetry(long backoffMs) {
        this.retryCount++;
        this.nextRetryAt = Instant.now().plus(backoffMs, ChronoUnit.MILLIS);
        return this.retryCount;
    }

    /**
     * Checks if maximum retry count is exceeded.
     *
     * @param maxRetries Maximum retry count
     * @return true if exceeded
     */
    public boolean exceededMaxRetries(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    /**
     * Checks if event can be published.
     *
     * @return true if status is PENDING
     */
    public boolean canPublish() {
        return this.status == OutboxStatus.PENDING;
    }

    // Getters

    // Private helpers

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "eventId='" + eventId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", occurredAt=" + occurredAt +
                ", nextRetryAt=" + nextRetryAt +
                '}';
    }
}
