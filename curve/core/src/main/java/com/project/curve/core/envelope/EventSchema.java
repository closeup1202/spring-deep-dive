package com.project.curve.core.envelope;

public record EventSchema(
        String name,
        int version,
        String schemaId
) {
    public EventSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("schema.name must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("schema.version must be positive");
        }
    }

    public static EventSchema of(String name, int version) {
        return new EventSchema(name, version, null);
    }
}
