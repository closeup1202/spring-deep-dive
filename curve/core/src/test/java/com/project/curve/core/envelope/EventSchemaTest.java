package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSchema test")
class EventSchemaTest {

    @Test
    @DisplayName("정상적인 EventSchema 생성 - of() 팩토리 메서드")
    void createEventSchemaUsingFactory() {
        // given
        String name = "OrderCreatedEvent";
        int version = 1;

        // when
        EventSchema schema = EventSchema.of(name, version);

        // then
        assertNotNull(schema);
        assertEquals(name, schema.name());
        assertEquals(version, schema.version());
        assertNull(schema.schemaId());
    }

    @Test
    @DisplayName("정상적인 EventSchema 생성 - 생성자로 schemaId 포함")
    void createEventSchemaWithSchemaId() {
        // given
        String name = "OrderCreatedEvent";
        int version = 2;
        String schemaId = "schema-registry-id-123";

        // when
        EventSchema schema = new EventSchema(name, version, schemaId);

        // then
        assertNotNull(schema);
        assertEquals(name, schema.name());
        assertEquals(version, schema.version());
        assertEquals(schemaId, schema.schemaId());
    }

    @Test
    @DisplayName("EventSchema 생성 실패 - name이 null")
    void createEventSchemaWithNullName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of(null, 1)
        );
        assertEquals("schema.name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema 생성 실패 - name이 빈 문자열")
    void createEventSchemaWithEmptyName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("", 1)
        );
        assertEquals("schema.name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema 생성 실패 - version이 0")
    void createEventSchemaWithZeroVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("TestEvent", 0)
        );
        assertEquals("schema.version must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema 생성 실패 - version이 음수")
    void createEventSchemaWithNegativeVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("TestEvent", -1)
        );
        assertEquals("schema.version must be positive", exception.getMessage());
    }
}
