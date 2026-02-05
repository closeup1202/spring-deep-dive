package com.project.curve.core.payload;

import com.project.curve.core.type.EventType;
import com.project.curve.core.type.TestEventType;

public record TestPayload(String data) implements DomainEventPayload {
    @Override
    public EventType getEventType() {
        return new TestEventType("TEST_PAYLOAD");
    }
}
