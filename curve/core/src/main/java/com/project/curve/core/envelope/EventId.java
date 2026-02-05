package com.project.curve.core.envelope;

public record EventId(String value) {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }

    public static EventId of(String value) {
        return new EventId(value);
    }
}
