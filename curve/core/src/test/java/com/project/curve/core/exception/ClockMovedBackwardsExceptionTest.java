package com.project.curve.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClockMovedBackwardsException 테스트")
class ClockMovedBackwardsExceptionTest {

    @Test
    @DisplayName("타임스탬프로 ClockMovedBackwardsException 생성")
    void createExceptionWithTimestamps() {
        // given
        long lastTimestamp = 1000L;
        long currentTimestamp = 500L;

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        assertNotNull(exception);
        assertEquals(lastTimestamp, exception.getLastTimestamp());
        assertEquals(currentTimestamp, exception.getCurrentTimestamp());
        assertTrue(exception.getMessage().contains("Clock moved backwards"));
        assertTrue(exception.getMessage().contains("1000"));
        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    @DisplayName("getDifferenceMs 테스트")
    void testGetDifferenceMs() {
        // given
        long lastTimestamp = 1000L;
        long currentTimestamp = 500L;
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // when
        long difference = exception.getDifferenceMs();

        // then
        assertEquals(500L, difference);
    }

    @Test
    @DisplayName("메시지로 ClockMovedBackwardsException 생성")
    void createExceptionWithMessage() {
        // given
        String message = "Custom error message";

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(-1, exception.getLastTimestamp());
        assertEquals(-1, exception.getCurrentTimestamp());
        assertEquals(0, exception.getDifferenceMs());
    }

    @Test
    @DisplayName("메시지와 cause로 ClockMovedBackwardsException 생성")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Custom error message";
        Throwable cause = new RuntimeException("Original cause");

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                message, cause
        );

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(-1, exception.getLastTimestamp());
        assertEquals(-1, exception.getCurrentTimestamp());
        assertEquals(0, exception.getDifferenceMs());
    }

    @Test
    @DisplayName("큰 타임스탬프 차이 테스트")
    void testLargeTimestampDifference() {
        // given
        long lastTimestamp = System.currentTimeMillis();
        long currentTimestamp = lastTimestamp - 60000L; // 1분 차이

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        assertEquals(60000L, exception.getDifferenceMs());
        assertTrue(exception.getMessage().contains("60000ms"));
    }

    @Test
    @DisplayName("예외 메시지 형식 테스트")
    void testExceptionMessageFormat() {
        // given
        long lastTimestamp = 2000L;
        long currentTimestamp = 1500L;

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        String message = exception.getMessage();
        assertTrue(message.contains("Clock moved backwards"));
        assertTrue(message.contains("Refusing to generate ID"));
        assertTrue(message.contains("lastTimestamp=2000"));
        assertTrue(message.contains("currentTimestamp=1500"));
        assertTrue(message.contains("diff=500ms"));
    }

    @Test
    @DisplayName("RuntimeException 상속 확인")
    void testIsRuntimeException() {
        // given
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                1000L, 500L
        );

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("타임스탬프가 같을 때 getDifferenceMs")
    void testGetDifferenceMs_sameTimestamp() {
        // given
        long timestamp = 1000L;
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                timestamp, timestamp
        );

        // when
        long difference = exception.getDifferenceMs();

        // then
        assertEquals(0L, difference);
    }
}
