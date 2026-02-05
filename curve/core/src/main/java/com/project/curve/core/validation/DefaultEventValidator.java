package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;

/**
 * Default event validator.
 * <p>
 * Validates only the presence of required fields and time ordering.
 */
public class DefaultEventValidator implements EventValidator {

    @Override
    public void validate(EventEnvelope<?> event) {
        EventValidator.validateDefault(event);
    }
}
