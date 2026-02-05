package com.project.curve.core.envelope;

import java.util.Collections;
import java.util.Map;

public record EventMetadata(
        EventSource source,
        EventActor actor,
        EventTrace trace,
        EventSchema schema,
        Map<String, String> tags
) {

    public EventMetadata {
        if (source == null) throw new IllegalArgumentException("source is required");
        if (actor == null) throw new IllegalArgumentException("actor is required");
        if (trace == null) throw new IllegalArgumentException("trace is required");
        if (schema == null) throw new IllegalArgumentException("schema is required");
        tags = (tags != null) ? Map.copyOf(tags) : Collections.emptyMap();
    }
}