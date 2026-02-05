package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventId 테스트")
class EventIdTest {

    @Test
    @DisplayName("정상적인 EventId 생성 - 유효한 value")
    void createValidEventId() {
        // given
        String value = "event-12345";

        // when
        EventId eventId = EventId.of(value);

        // then
        assertNotNull(eventId);
        assertEquals(value, eventId.value());
    }

    @Test
    @DisplayName("EventId 생성 실패 - null value")
    void createEventIdWithNullValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of(null)
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId 생성 실패 - 빈 문자열")
    void createEventIdWithEmptyValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of("")
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId 생성 실패 - 공백만 있는 문자열")
    void createEventIdWithBlankValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of("   ")
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId 동등성 테스트 - 같은 value면 동일한 객체")
    void eventIdEquality() {
        // given
        String value = "event-123";
        EventId eventId1 = EventId.of(value);
        EventId eventId2 = EventId.of(value);

        // then
        assertEquals(eventId1, eventId2);
        assertEquals(eventId1.hashCode(), eventId2.hashCode());
    }
}
