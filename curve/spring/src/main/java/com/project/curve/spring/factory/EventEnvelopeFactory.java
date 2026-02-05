package com.project.curve.spring.factory;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;

import java.time.Instant;

public record EventEnvelopeFactory(ClockProvider clock, IdGenerator idGenerator) {

    public <T extends DomainEventPayload> EventEnvelope<T> create(
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload
    ) {
        Instant now = clock.now();
        return EventEnvelope.of(
                idGenerator.generate(),
                eventType,
                severity,
                metadata,
                payload,
                now,
                now
        );
    }
}