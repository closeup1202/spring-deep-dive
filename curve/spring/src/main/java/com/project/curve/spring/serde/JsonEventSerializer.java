package com.project.curve.spring.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * JSON event serialization implementation using Jackson ObjectMapper.
 * <p>
 * Modules registered with {@link ObjectMapper} (such as PiiModule) are automatically applied,
 * transparently handling PII masking, date format conversion, etc.
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>ObjectMapper configuration-based serialization (PII, date format, etc.)</li>
 *   <li>JsonProcessingException → EventSerializationException conversion</li>
 *   <li>Thread-safe (ObjectMapper is thread-safe)</li>
 * </ul>
 *
 * @see com.project.curve.spring.pii.jackson.PiiModule
 * @see ObjectMapper
 * @see EventSerializer
 */
@RequiredArgsConstructor
@Component
public class JsonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public <T extends DomainEventPayload> String serialize(EventEnvelope<T> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "Failed to serialize EventEnvelope. eventId=" + envelope.eventId().value(), e
            );
        }
    }
}
