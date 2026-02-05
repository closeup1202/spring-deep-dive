package com.project.curve.spring.outbox.persistence.jpa.repository;

import com.project.curve.core.outbox.OutboxStatus;
import com.project.curve.spring.outbox.persistence.jpa.entity.OutboxEventJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox event Spring Data JPA repository.
 * <p>
 * Provides custom queries for efficiently retrieving PENDING events.
 *
 * @see OutboxEventJpaEntity
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    /**
     * Retrieves events by status (ascending order by occurrence time, supports limit).
     * <p>
     * Helps guarantee ordering by processing older events first.
     *
     * @param status   Status to query
     * @param pageable Paging (limit setting)
     * @return List of events
     */
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status ORDER BY e.occurredAt ASC")
    List<OutboxEventJpaEntity> findByStatusOrderByOccurredAtAsc(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    /**
     * Retrieves PENDING events with pessimistic locking (FOR UPDATE SKIP LOCKED).
     * <p>
     * Prevents duplicate processing of the same event in a multi-instance environment.
     * Skips rows already locked by other instances and returns only unlocked rows.
     * Also checks nextRetryAt to ensure backoff strategy is respected.
     *
     * @param status   Status to query
     * @param now      Current timestamp
     * @param pageable Paging (limit setting)
     * @return List of events with acquired locks
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status AND e.nextRetryAt <= :now ORDER BY e.occurredAt ASC")
    List<OutboxEventJpaEntity> findByStatusAndNextRetryAtLessThanEqualForUpdateSkipLocked(
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Retrieves events by aggregate (ascending order by occurrence time).
     *
     * @param aggregateType Aggregate type
     * @param aggregateId   Aggregate ID
     * @return List of events
     */
    List<OutboxEventJpaEntity> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
            String aggregateType,
            String aggregateId
    );

    /**
     * Retrieves the count of events by status.
     *
     * @param status Status to query
     * @return Event count
     */
    long countByStatus(OutboxStatus status);

    /**
     * Deletes old events (for batch processing).
     * <p>
     * Since JPQL does not directly support LIMIT, subqueries or native queries may be needed.
     * Here we can use the approach of querying ID list then deleting, or use native queries.
     * For DB compatibility, the ID query then delete approach is recommended, but native queries can be used for performance.
     * <p>
     * Here we add a query method to first retrieve the ID list.
     */
    @Query("SELECT e.eventId FROM OutboxEventJpaEntity e WHERE e.status = :status AND e.occurredAt < :before")
    List<String> findIdsByStatusAndOccurredAtBefore(
            @Param("status") OutboxStatus status,
            @Param("before") Instant before,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e WHERE e.eventId IN :ids")
    int deleteByEventIds(@Param("ids") List<String> ids);
}
