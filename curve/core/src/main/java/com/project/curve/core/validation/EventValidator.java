package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.InvalidEventException;

/**
 * Interface for event validation.
 * <p>
 * Implementations validate the structural validity and business rules of events.
 */
public interface EventValidator {

    /**
     * Validates an event.
     *
     * @param event the event to validate
     * @throws InvalidEventException if the event is invalid
     */
    void validate(EventEnvelope<?> event);

    /**
     * Static method for performing default validation logic (for backward compatibility and default implementation).
     */
    static void validateDefault(EventEnvelope<?> event) {
        if (event == null) {
            throw new InvalidEventException("event must not be null");
        }
        if (event.occurredAt().isAfter(event.publishedAt())) {
            throw new InvalidEventException("occurredAt must be <= publishedAt");
        }
    }
}
