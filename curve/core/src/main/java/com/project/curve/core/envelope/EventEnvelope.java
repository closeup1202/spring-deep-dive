package com.project.curve.core.envelope;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import lombok.NonNull;

import java.time.Instant;

/**
 * Standardized envelope for domain events.
 * <p>
 * EventEnvelope wraps business event payloads with rich contextual metadata,
 * enabling comprehensive event tracking, auditing, and processing in distributed systems.
 * All events in the Curve library follow this standardized structure.
 * </p>
 *
 * <h3>Event Structure:</h3>
 * <pre>
 * EventEnvelope
 * ├── eventId          Unique event identifier (Snowflake ID)
 * ├── eventType        Event type/name
 * ├── severity         Event severity (INFO, WARN, ERROR, CRITICAL)
 * ├── metadata         Contextual metadata
 * │   ├── source       Event source (service, environment, version)
 * │   ├── actor        Who triggered the event (user, role, IP)
 * │   ├── trace        Distributed trace (traceId, spanId, correlationId)
 * │   ├── schema       Event schema information
 * │   └── tags         Custom metadata tags
 * ├── payload          Business event data
 * ├── occurredAt       When the event occurred
 * └── publishedAt      When the event was published
 * </pre>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>Immutable</b> - Java Record ensures immutability</li>
 *   <li><b>Type-safe</b> - Generic payload type parameter</li>
 *   <li><b>Rich metadata</b> - Comprehensive contextual information</li>
 *   <li><b>Traceability</b> - Support for distributed tracing and event chains</li>
 *   <li><b>Null-safe</b> - All fields are non-null</li>
 * </ul>
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
 *     EventId.of("1234567890"),
 *     new OrderCreatedEventType(),
 *     EventSeverity.INFO,
 *     metadata,
 *     new OrderCreatedPayload(order),
 *     Instant.now(),
 *     Instant.now()
 * );
 * }</pre>
 *
 * @param <T> the type of the event payload, must extend {@link DomainEventPayload}
 * @param eventId unique event identifier
 * @param eventType the type/name of the event
 * @param severity the severity level of the event
 * @param metadata contextual metadata (source, actor, trace, etc.)
 * @param payload the business event data
 * @param occurredAt timestamp when the event occurred
 * @param publishedAt timestamp when the event was published
 * @see EventMetadata
 * @see DomainEventPayload
 * @see EventSeverity
 * @since 0.0.1
 */
public record EventEnvelope<T extends DomainEventPayload>(
        @NonNull EventId eventId,
        @NonNull EventType eventType,
        @NonNull EventSeverity severity,
        @NonNull EventMetadata metadata,
        @NonNull T payload,
        @NonNull Instant occurredAt,
        @NonNull Instant publishedAt
) {

    public static <T extends DomainEventPayload> EventEnvelope<T> of(
            EventId eventId,
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload,
            Instant occurredAt,
            Instant publishedAt
    ) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                severity,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );
    }
}
