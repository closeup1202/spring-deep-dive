package com.project.curve.spring.exception;

/**
 * Exception thrown when event publishing fails through @PublishEvent annotation.
 * <p>
 * Only thrown when failOnError=true is configured.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
