package com.project.curve.core.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSeverity 테스트")
class EventSeverityTest {

    @Test
    @DisplayName("EventSeverity - 모든 값 존재 확인")
    void allSeverityValuesExist() {
        // when
        EventSeverity[] severities = EventSeverity.values();

        // then
        assertEquals(4, severities.length);
        assertNotNull(EventSeverity.INFO);
        assertNotNull(EventSeverity.WARN);
        assertNotNull(EventSeverity.ERROR);
        assertNotNull(EventSeverity.CRITICAL);
    }

    @Test
    @DisplayName("EventSeverity - valueOf()로 값 가져오기")
    void getValueByName() {
        // when & then
        assertEquals(EventSeverity.INFO, EventSeverity.valueOf("INFO"));
        assertEquals(EventSeverity.WARN, EventSeverity.valueOf("WARN"));
        assertEquals(EventSeverity.ERROR, EventSeverity.valueOf("ERROR"));
        assertEquals(EventSeverity.CRITICAL, EventSeverity.valueOf("CRITICAL"));
    }

    @Test
    @DisplayName("EventSeverity - valueOf() 잘못된 값으로 IllegalArgumentException")
    void getValueByInvalidName_shouldThrowException() {
        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> EventSeverity.valueOf("INVALID")
        );
    }
}
