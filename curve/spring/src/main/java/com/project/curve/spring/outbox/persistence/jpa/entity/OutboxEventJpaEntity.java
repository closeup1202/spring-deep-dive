package com.project.curve.spring.outbox.persistence.jpa.entity;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox event JPA entity.
 * <p>
 * Persistence implementation using Spring Data JPA.
 *
 * <h3>Index Strategy</h3>
 * <ul>
 *   <li>status: Optimizes PENDING event queries</li>
 *   <li>(aggregateType, aggregateId): Optimizes aggregate-based queries</li>
 *   <li>occurredAt: Optimizes chronological sorting</li>
 *   <li>nextRetryAt: Optimizes retry target queries</li>
 * </ul>
 *
 * @see OutboxEvent
 */
@Getter
@Entity
@Table(
        name = "curve_outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status", columnList = "status"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
                @Index(name = "idx_outbox_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_outbox_next_retry", columnList = "next_retry_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public OutboxEventJpaEntity(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = occurredAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Converts to core domain model.
     *
     * @return OutboxEvent domain model
     */
    public OutboxEvent toDomain() {
        return OutboxEvent.restore(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                occurredAt,
                status,
                retryCount,
                publishedAt,
                errorMessage,
                nextRetryAt
        );
    }

    /**
     * Creates entity from domain model.
     *
     * @param domain OutboxEvent domain model
     * @return JPA entity
     */
    public static OutboxEventJpaEntity fromDomain(OutboxEvent domain) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                domain.getEventId(),
                domain.getAggregateType(),
                domain.getAggregateId(),
                domain.getEventType(),
                domain.getPayload(),
                domain.getOccurredAt()
        );

        entity.status = domain.getStatus();
        entity.retryCount = domain.getRetryCount();
        entity.publishedAt = domain.getPublishedAt();
        entity.errorMessage = domain.getErrorMessage();
        entity.nextRetryAt = domain.getNextRetryAt();
        entity.updatedAt = Instant.now();

        return entity;
    }

    /**
     * Updates entity with domain model state.
     *
     * @param domain OutboxEvent domain model
     */
    public void updateFromDomain(OutboxEvent domain) {
        this.status = domain.getStatus();
        this.retryCount = domain.getRetryCount();
        this.publishedAt = domain.getPublishedAt();
        this.errorMessage = domain.getErrorMessage();
        this.nextRetryAt = domain.getNextRetryAt();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
