package com.project.curve.core.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for Outbox event repository.
 * <p>
 * Acts as a Port in Hexagonal Architecture, independent of persistence technology (JPA, MongoDB, etc.).
 *
 * <h3>Implementation Examples</h3>
 * <ul>
 *   <li>JpaOutboxEventRepository (Spring Data JPA)</li>
 *   <li>MongoOutboxEventRepository (MongoDB)</li>
 *   <li>RedisOutboxEventRepository (Redis)</li>
 * </ul>
 *
 * @see OutboxEvent
 */
public interface OutboxEventRepository {

    /**
     * Saves Outbox event.
     * <p>
     * Must be called within the same transaction as business logic to guarantee atomicity.
     *
     * @param event Event to save
     */
    void save(OutboxEvent event);

    /**
     * Finds event by ID.
     *
     * @param eventId Event ID
     * @return Event (empty if not found)
     */
    Optional<OutboxEvent> findById(String eventId);

    /**
     * Finds events by status.
     * <p>
     * Primarily used to query PENDING events for publishing.
     *
     * @param status Status to query
     * @param limit  Maximum number of events to retrieve
     * @return List of events
     */
    List<OutboxEvent> findByStatus(OutboxStatus status, int limit);

    /**
     * Finds PENDING events for publishing with pessimistic lock (FOR UPDATE SKIP LOCKED).
     * <p>
     * Prevents duplicate processing of the same event in multi-instance environments.
     * Skips rows already locked by other instances and returns only unlocked rows.
     *
     * @param limit Maximum number of events to retrieve
     * @return List of PENDING events with acquired locks
     */
    List<OutboxEvent> findPendingForProcessing(int limit);

    /**
     * Finds events by aggregate.
     * <p>
     * Used to query all events for a specific Order.
     *
     * @param aggregateType Aggregate type (e.g., "Order")
     * @param aggregateId   Aggregate ID (e.g., orderId)
     * @return List of events (sorted by occurrence time ascending)
     */
    List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId);

    /**
     * Deletes event (optional).
     * <p>
     * Used to clean up old PUBLISHED events.
     *
     * @param eventId Event ID to delete
     */
    void deleteById(String eventId);

    /**
     * Batch deletes old events.
     * <p>
     * Deletes events with specific status that occurred before the cutoff time.
     * For bulk deletions, it's recommended to delete in batches to reduce DB load.
     *
     * @param status Status to delete (mainly PUBLISHED)
     * @param before Cutoff time (deletes data before this time)
     * @param limit  Maximum number to delete at once
     * @return Number of deleted events
     */
    int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit);

    /**
     * Counts total events.
     *
     * @return Event count
     */
    long count();

    /**
     * Counts events by status.
     *
     * @param status Status to query
     * @return Event count
     */
    long countByStatus(OutboxStatus status);
}
