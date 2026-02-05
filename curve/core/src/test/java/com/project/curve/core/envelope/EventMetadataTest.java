package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventMetadata 테스트")
class EventMetadataTest {

    @Test
    @DisplayName("정상적인 EventMetadata 생성")
    void createValidEventMetadata() {
        // given
        EventSource source = createValidSource();
        EventActor actor = createValidActor();
        EventTrace trace = createValidTrace();
        EventSchema schema = createValidSchema();
        Map<String, String> tags = Map.of("env", "prod", "region", "us-east-1");

        // when
        EventMetadata metadata = new EventMetadata(source, actor, trace, schema, tags);

        // then
        assertNotNull(metadata);
        assertEquals(source, metadata.source());
        assertEquals(actor, metadata.actor());
        assertEquals(trace, metadata.trace());
        assertEquals(schema, metadata.schema());
        assertEquals(2, metadata.tags().size());
        assertEquals("prod", metadata.tags().get("env"));
        assertEquals("us-east-1", metadata.tags().get("region"));
    }

    @Test
    @DisplayName("EventMetadata 생성 실패 - source가 null")
    void createMetadataWithNullSource_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventMetadata(null, createValidActor(), createValidTrace(), createValidSchema(), Map.of())
        );
        assertEquals("source is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventMetadata 생성 실패 - actor가 null")
    void createMetadataWithNullActor_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventMetadata(createValidSource(), null, createValidTrace(), createValidSchema(), Map.of())
        );
        assertEquals("actor is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventMetadata 생성 실패 - trace가 null")
    void createMetadataWithNullTrace_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventMetadata(createValidSource(), createValidActor(), null, createValidSchema(), Map.of())
        );
        assertEquals("trace is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventMetadata 생성 실패 - schema가 null")
    void createMetadataWithNullSchema_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventMetadata(createValidSource(), createValidActor(), createValidTrace(), null, Map.of())
        );
        assertEquals("schema is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventMetadata - tags가 null이면 빈 맵으로 초기화")
    void createMetadataWithNullTags_shouldInitializeEmptyMap() {
        // when
        EventMetadata metadata = new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                null
        );

        // then
        assertNotNull(metadata.tags());
        assertTrue(metadata.tags().isEmpty());
    }

    @Test
    @DisplayName("EventMetadata - tags 불변성 보장 (외부 수정 방지)")
    void metadataTags_shouldBeImmutable() {
        // given
        Map<String, String> mutableTags = new HashMap<>();
        mutableTags.put("key1", "value1");

        EventMetadata metadata = new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                mutableTags
        );

        // when - 원본 맵 수정
        mutableTags.put("key2", "value2");

        // then - metadata의 tags는 영향받지 않음
        assertEquals(1, metadata.tags().size());
        assertTrue(metadata.tags().containsKey("key1"));
        assertFalse(metadata.tags().containsKey("key2"));
    }

    @Test
    @DisplayName("EventMetadata - tags 반환 맵 수정 불가 (Unmodifiable)")
    void metadataTagsMap_shouldBeUnmodifiable() {
        // given
        EventMetadata metadata = new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                Map.of("key1", "value1")
        );

        // when & then
        assertThrows(
                UnsupportedOperationException.class,
                () -> metadata.tags().put("key2", "value2")
        );
    }

    // Helper methods
    private EventSource createValidSource() {
        return new EventSource("test-service", "test", "instance-1", "localhost", "1.0.0");
    }

    private EventActor createValidActor() {
        return new EventActor("user-123", "ROLE_USER", "127.0.0.1");
    }

    private EventTrace createValidTrace() {
        return new EventTrace("trace-123", "span-456", "correlation-789");
    }

    private EventSchema createValidSchema() {
        return EventSchema.of("TestEvent", 1);
    }
}
