package com.project.curve.core.outbox;

/**
 * Enumeration representing the publishing status of Outbox events.
 * <p>
 * Manages the lifecycle of events in the Transactional Outbox Pattern.
 *
 * <h3>Status Transitions</h3>
 * <pre>
 * PENDING → PUBLISHED (success)
 *    ↓
 * FAILED (exceeded max retries)
 * </pre>
 */
public enum OutboxStatus {

    /**
     * Awaiting publishing - not yet sent to Kafka
     */
    PENDING,

    /**
     * Publishing complete - successfully sent to Kafka
     */
    PUBLISHED,

    /**
     * Publishing failed - exceeded maximum retry count
     */
    FAILED
}
