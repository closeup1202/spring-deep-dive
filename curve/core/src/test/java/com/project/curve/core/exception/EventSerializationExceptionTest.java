package com.project.curve.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSerializationException 테스트")
class EventSerializationExceptionTest {

    @Test
    @DisplayName("메시지로 EventSerializationException 생성")
    void createExceptionWithMessage() {
        // given
        String message = "Failed to serialize event";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("메시지와 cause로 EventSerializationException 생성")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Failed to serialize event";
        Throwable cause = new RuntimeException("JSON parsing error");

        // when
        EventSerializationException exception = new EventSerializationException(
                message, cause
        );

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("RuntimeException 상속 확인")
    void testIsRuntimeException() {
        // given
        EventSerializationException exception = new EventSerializationException(
                "Test message"
        );

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("중첩된 예외 처리 테스트")
    void testNestedExceptions() {
        // given
        Throwable rootCause = new IllegalArgumentException("Invalid field");
        Throwable intermediateCause = new RuntimeException("Processing failed", rootCause);
        EventSerializationException exception = new EventSerializationException(
                "Failed to serialize event", intermediateCause
        );

        // when
        Throwable cause = exception.getCause();
        Throwable rootCauseFound = cause.getCause();

        // then
        assertNotNull(cause);
        assertEquals(intermediateCause, cause);
        assertEquals(rootCause, rootCauseFound);
    }

    @Test
    @DisplayName("null 메시지로 예외 생성")
    void createExceptionWithNullMessage() {
        // when
        EventSerializationException exception = new EventSerializationException(null);

        // then
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("빈 메시지로 예외 생성")
    void createExceptionWithEmptyMessage() {
        // given
        String message = "";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("긴 메시지로 예외 생성")
    void createExceptionWithLongMessage() {
        // given
        String message = "Failed to serialize event: " + "x".repeat(1000);

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertTrue(exception.getMessage().length() > 1000);
    }

    @Test
    @DisplayName("특수 문자를 포함한 메시지로 예외 생성")
    void createExceptionWithSpecialCharactersMessage() {
        // given
        String message = "Failed to serialize: \\n\\t\\r\"quotes\"";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }
}
