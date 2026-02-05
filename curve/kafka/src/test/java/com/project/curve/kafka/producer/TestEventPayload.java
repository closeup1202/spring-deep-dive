package com.project.curve.kafka.producer;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;

public record TestEventPayload(String orderId, String productName, int amount) implements DomainEventPayload {

    @Override
    public EventType getEventType() {
        return () -> "TEST_ORDER_CREATED";
    }
}
