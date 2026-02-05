package com.project.curve.spring.factory;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;

public record AnotherPayload(String data) implements DomainEventPayload {
    @Override
    public EventType getEventType() {
        return null;
    }
}
