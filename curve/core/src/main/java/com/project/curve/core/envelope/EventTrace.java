package com.project.curve.core.envelope;

public record EventTrace(
        String traceId,
        String spanId,
        String correlationId
) {
}
