package com.project.curve.core.exception;

/**
 * Exception thrown when event serialization fails.
 * <p>
 * Occurs when serializing EventEnvelope to a specific format (JSON, Avro, etc.).
 * Primarily thrown by EventProducer implementations.
 */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message) {
        super(message);
    }

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
