package com.project.curve.core.serde;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;

/**
 * Interface for serializing event envelopes.
 * <p>
 * Supports various serialization strategies such as PII (Personally Identifiable Information) handling, compression, and encryption.
 *
 * <h3>Implementation Examples</h3>
 * <ul>
 *   <li>JsonEventSerializer: JSON serialization (default)</li>
 *   <li>PiiMaskingEventSerializer: Serialization after masking PII fields</li>
 *   <li>CompressedEventSerializer: Compressed serialization</li>
 *   <li>AvroEventSerializer: Avro binary serialization</li>
 * </ul>
 *
 * @see EventEnvelope
 * @see EventSerializationException
 */
public interface EventSerializer {

    /**
     * Serializes an EventEnvelope.
     *
     * @param envelope the event envelope to serialize
     * @param <T>      the event payload type
     * @return the serialized object (String, byte[], GenericRecord, etc.)
     * @throws EventSerializationException if serialization fails
     */
    <T extends DomainEventPayload> Object serialize(EventEnvelope<T> envelope) throws EventSerializationException;
}
