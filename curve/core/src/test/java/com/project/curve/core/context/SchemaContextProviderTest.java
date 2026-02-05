package com.project.curve.core.context;

import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaContextProvider test")
class SchemaContextProviderTest {

    static class TestPayload implements DomainEventPayload {
        @Override
        public EventType getEventType() {
            return () -> "TEST_EVENT";
        }
    }

    @Test
    @DisplayName("Default implementation test - getSchema invocation")
    void testGetSchema() {
        // given
        EventSchema defaultSchema = EventSchema.of("DefaultSchema", 1);
        SchemaContextProvider provider = () -> defaultSchema;

        // when
        EventSchema schema = provider.getSchema();

        // then
        assertNotNull(schema);
        assertEquals(defaultSchema, schema);
        assertEquals("DefaultSchema", schema.name());
        assertEquals(1, schema.version());
    }

    @Test
    @DisplayName("Default implementation test - getSchemaFor delegates to getSchema")
    void testGetSchemaFor_usesDefaultImplementation() {
        // given
        EventSchema defaultSchema = EventSchema.of("DefaultSchema", 1);
        SchemaContextProvider provider = () -> defaultSchema;
        TestPayload payload = new TestPayload();

        // when
        EventSchema schema = provider.getSchemaFor(payload);

        // then
        assertNotNull(schema);
        assertEquals(defaultSchema, schema);
    }

    @Test
    @DisplayName("Custom implementation test - getSchemaFor override")
    void testGetSchemaFor_customImplementation() {
        // given
        EventSchema defaultSchema = EventSchema.of("DefaultSchema", 1);
        EventSchema customSchema = EventSchema.of("CustomSchema", 2);

        SchemaContextProvider provider = new SchemaContextProvider() {
            @Override
            public EventSchema getSchema() {
                return defaultSchema;
            }

            @Override
            public EventSchema getSchemaFor(DomainEventPayload payload) {
                if (payload instanceof TestPayload) {
                    return customSchema;
                }
                return getSchema();
            }
        };

        TestPayload payload = new TestPayload();

        // when
        EventSchema schema = provider.getSchemaFor(payload);

        // then
        assertNotNull(schema);
        assertEquals(customSchema, schema);
        assertEquals("CustomSchema", schema.name());
        assertEquals(2, schema.version());
    }

    @Test
    @DisplayName("Return schema based on different payload types")
    void testGetSchemaFor_multiplePayloadTypes() {
        // given
        EventSchema defaultSchema = EventSchema.of("DefaultSchema", 1);
        EventSchema testSchema = EventSchema.of("TestSchema", 1);
        EventSchema otherSchema = EventSchema.of("OtherSchema", 1);

        SchemaContextProvider provider = new SchemaContextProvider() {
            @Override
            public EventSchema getSchema() {
                return defaultSchema;
            }

            @Override
            public EventSchema getSchemaFor(DomainEventPayload payload) {
                if (payload instanceof TestPayload) {
                    return testSchema;
                }
                if (payload.getClass().getSimpleName().equals("OtherPayload")) {
                    return otherSchema;
                }
                return getSchema();
            }
        };

        // when
        EventSchema schema1 = provider.getSchemaFor(new TestPayload());
        EventSchema schema2 = provider.getSchema();

        // then
        assertEquals(testSchema, schema1);
        assertEquals(defaultSchema, schema2);
    }

    @Test
    @DisplayName("null 페이로드로 getSchemaFor 호출")
    void testGetSchemaFor_withNullPayload() {
        // given
        EventSchema defaultSchema = EventSchema.of("DefaultSchema", 1);

        SchemaContextProvider provider = new SchemaContextProvider() {
            @Override
            public EventSchema getSchema() {
                return defaultSchema;
            }

            @Override
            public EventSchema getSchemaFor(DomainEventPayload payload) {
                if (payload == null) {
                    return EventSchema.of("NullSchema", 1);
                }
                return getSchema();
            }
        };

        // when
        EventSchema schema = provider.getSchemaFor(null);

        // then
        assertNotNull(schema);
        assertEquals("NullSchema", schema.name());
    }

    @Test
    @DisplayName("버전이 다른 스키마 반환 테스트")
    void testGetSchemaFor_differentVersions() {
        // given
        EventSchema v1 = EventSchema.of("Schema", 1);
        EventSchema v2 = EventSchema.of("Schema", 2);
        EventSchema v3 = EventSchema.of("Schema", 3);

        SchemaContextProvider provider = new SchemaContextProvider() {
            private int callCount = 0;

            @Override
            public EventSchema getSchema() {
                return v1;
            }

            @Override
            public EventSchema getSchemaFor(DomainEventPayload payload) {
                callCount++;
                switch (callCount) {
                    case 1: return v1;
                    case 2: return v2;
                    default: return v3;
                }
            }
        };

        // when
        EventSchema schema1 = provider.getSchemaFor(new TestPayload());
        EventSchema schema2 = provider.getSchemaFor(new TestPayload());
        EventSchema schema3 = provider.getSchemaFor(new TestPayload());

        // then
        assertEquals(1, schema1.version());
        assertEquals(2, schema2.version());
        assertEquals(3, schema3.version());
    }

    @Test
    @DisplayName("동적 스키마 이름 생성 테스트")
    void testGetSchemaFor_dynamicSchemaName() {
        // given
        SchemaContextProvider provider = new SchemaContextProvider() {
            @Override
            public EventSchema getSchema() {
                return EventSchema.of("Default", 1);
            }

            @Override
            public EventSchema getSchemaFor(DomainEventPayload payload) {
                String schemaName = payload.getClass().getSimpleName().replace("Payload", "");
                return EventSchema.of(schemaName, 1);
            }
        };

        // when
        EventSchema schema = provider.getSchemaFor(new TestPayload());

        // then
        assertEquals("Test", schema.name());
    }

    @Test
    @DisplayName("람다로 구현한 SchemaContextProvider")
    void testLambdaImplementation() {
        // given
        SchemaContextProvider provider = () -> EventSchema.of("LambdaSchema", 1);

        // when
        EventSchema schema = provider.getSchema();

        // then
        assertNotNull(schema);
        assertEquals("LambdaSchema", schema.name());
        assertEquals(1, schema.version());
    }

    @Test
    @DisplayName("상태를 가진 SchemaContextProvider")
    void testStatefulProvider() {
        // given
        SchemaContextProvider provider = new SchemaContextProvider() {
            private int versionCounter = 1;

            @Override
            public EventSchema getSchema() {
                return EventSchema.of("Schema", versionCounter++);
            }
        };

        // when
        EventSchema schema1 = provider.getSchema();
        EventSchema schema2 = provider.getSchema();
        EventSchema schema3 = provider.getSchema();

        // then
        assertEquals(1, schema1.version());
        assertEquals(2, schema2.version());
        assertEquals(3, schema3.version());
    }
}
