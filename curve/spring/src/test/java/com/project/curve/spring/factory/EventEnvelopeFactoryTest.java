package com.project.curve.spring.factory;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventEnvelopeFactoryTest {

    private EventEnvelopeFactory factory;
    private ClockProvider clockProvider;
    private IdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        clockProvider = mock(ClockProvider.class);
        idGenerator = mock(IdGenerator.class);
        factory = new EventEnvelopeFactory(clockProvider, idGenerator);
    }

    @Test
    @DisplayName("Should create valid EventEnvelope")
    void create_shouldReturnValidEventEnvelope() {
        // Given
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        EventId eventId = EventId.of("12345");
        EventType eventType = new TestEventType();
        EventSeverity severity = EventSeverity.INFO;
        EventMetadata metadata = mock(EventMetadata.class);
        TestPayload payload = new TestPayload("test-data");

        when(clockProvider.now()).thenReturn(now);
        when(idGenerator.generate()).thenReturn(eventId);

        // When
        EventEnvelope<TestPayload> envelope = factory.create(eventType, severity, metadata, payload);

        // Then
        assertThat(envelope).isNotNull();
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo(eventType);
        assertThat(envelope.severity()).isEqualTo(severity);
        assertThat(envelope.metadata()).isEqualTo(metadata);
        assertThat(envelope.payload()).isEqualTo(payload);
        assertThat(envelope.occurredAt()).isEqualTo(now);
        assertThat(envelope.publishedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should use ClockProvider to get current time")
    void create_shouldUseClockProvider() {
        // Given
        Instant now = Instant.parse("2024-01-01T12:34:56Z");
        when(clockProvider.now()).thenReturn(now);
        when(idGenerator.generate()).thenReturn(EventId.of("123"));

        // When
        EventEnvelope<TestPayload> envelope = factory.create(
                new TestEventType(),
                EventSeverity.INFO,
                mock(EventMetadata.class),
                new TestPayload("data")
        );

        // Then
        verify(clockProvider, times(1)).now();
        assertThat(envelope.occurredAt()).isEqualTo(now);
        assertThat(envelope.publishedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should use IdGenerator to generate event ID")
    void create_shouldUseIdGenerator() {
        // Given
        EventId expectedId = EventId.of("generated-id-12345");
        when(idGenerator.generate()).thenReturn(expectedId);
        when(clockProvider.now()).thenReturn(Instant.now());

        // When
        EventEnvelope<TestPayload> envelope = factory.create(
                new TestEventType(),
                EventSeverity.INFO,
                mock(EventMetadata.class),
                new TestPayload("data")
        );

        // Then
        verify(idGenerator, times(1)).generate();
        assertThat(envelope.eventId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("Should create EventEnvelope with different severity levels")
    void create_withDifferentSeverities_shouldWork() {
        // Given
        when(clockProvider.now()).thenReturn(Instant.now());
        when(idGenerator.generate()).thenReturn(EventId.of("123"));
        EventMetadata metadata = mock(EventMetadata.class);
        TestPayload payload = new TestPayload("data");
        EventType eventType = new TestEventType();

        // When & Then
        for (EventSeverity severity : EventSeverity.values()) {
            EventEnvelope<TestPayload> envelope = factory.create(eventType, severity, metadata, payload);
            assertThat(envelope.severity()).isEqualTo(severity);
        }
    }

    @Test
    @DisplayName("Should create EventEnvelope with different payload types")
    void create_withDifferentPayloadTypes_shouldWork() {
        // Given
        when(clockProvider.now()).thenReturn(Instant.now());
        when(idGenerator.generate()).thenReturn(EventId.of("123"));
        EventMetadata metadata = mock(EventMetadata.class);

        // When
        EventEnvelope<TestPayload> envelope1 = factory.create(
                new TestEventType(),
                EventSeverity.INFO,
                metadata,
                new TestPayload("data1")
        );
        EventEnvelope<AnotherPayload> envelope2 = factory.create(
                new TestEventType(),
                EventSeverity.INFO,
                metadata,
                new AnotherPayload("data-another")
        );

        // Then
        assertThat(envelope1.payload()).isInstanceOf(TestPayload.class);
        assertThat(envelope2.payload()).isInstanceOf(AnotherPayload.class);
    }
}
