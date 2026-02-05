package com.project.curve.spring.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventPublishException Test")
class EventPublishExceptionTest {

    @Test
    @DisplayName("Create exception with message")
    void createExceptionWithMessage() {
        // given
        String message = "Failed to publish event";

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Create exception with message and cause")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Failed to publish event";
        Throwable cause = new RuntimeException("Kafka connection failed");

        // when
        EventPublishException exception = new EventPublishException(message, cause);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should extend RuntimeException")
    void extendsRuntimeException() {
        // given
        EventPublishException exception = new EventPublishException("test");

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("Create exception with null message")
    void createExceptionWithNullMessage() {
        // when
        EventPublishException exception = new EventPublishException(null);

        // then
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with empty message")
    void createExceptionWithEmptyMessage() {
        // given
        String message = "";

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with long message")
    void createExceptionWithLongMessage() {
        // given
        String message = "Failed to publish event: " + "x".repeat(1000);

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertTrue(exception.getMessage().length() > 1000);
    }

    @Test
    @DisplayName("Handle nested exceptions")
    void testNestedExceptions() {
        // given
        Throwable rootCause = new IllegalStateException("Invalid state");
        Throwable intermediateCause = new RuntimeException("Processing failed", rootCause);
        EventPublishException exception = new EventPublishException(
                "Failed to publish event", intermediateCause
        );

        // when
        Throwable cause = exception.getCause();
        Throwable rootCauseFound = cause.getCause();

        // then
        assertNotNull(cause);
        assertEquals(intermediateCause, cause);
        assertEquals(rootCause, rootCauseFound);
    }
}
