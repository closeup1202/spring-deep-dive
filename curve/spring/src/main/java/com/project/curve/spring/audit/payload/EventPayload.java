package com.project.curve.spring.audit.payload;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import com.project.curve.spring.audit.type.DefaultEventType;

/**
 * Audit event payload automatically generated via @PublishEvent annotation.
 */
public record EventPayload(
        String eventTypeName,
        String className,
        String methodName,
        Object data) implements DomainEventPayload {

    @Override
    public EventType getEventType() {
        return new DefaultEventType(eventTypeName);
    }
}
