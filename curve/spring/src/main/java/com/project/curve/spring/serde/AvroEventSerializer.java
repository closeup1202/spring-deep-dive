package com.project.curve.spring.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Avro-based event serialization implementation.
 * <p>
 * Defines the EventEnvelope structure as an Avro schema,
 * converting payload and some metadata fields to JSON strings for storage.
 * This ensures payload flexibility while maintaining schema compatibility.
 */
public class AvroEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;
    private final Schema avroSchema;

    private static final String SCHEMA_STRING = """
            {
              "type": "record",
              "name": "EventEnvelope",
              "namespace": "com.project.curve.core.envelope",
              "fields": [
                {"name": "eventId", "type": "string"},
                {"name": "eventType", "type": "string"},
                {"name": "severity", "type": "string"},
                {"name": "metadata", "type": {
                    "type": "record",
                    "name": "EventMetadata",
                    "fields": [
                        {"name": "source", "type": "string"},
                        {"name": "actor", "type": "string"},
                        {"name": "trace", "type": "string"},
                        {"name": "schema", "type": "string"},
                        {"name": "tags", "type": {"type": "map", "values": "string"}}
                    ]
                }},
                {"name": "payload", "type": "string"},
                {"name": "occurredAt", "type": "long", "logicalType": "timestamp-millis"},
                {"name": "publishedAt", "type": "long", "logicalType": "timestamp-millis"}
              ]
            }
            """;

    public AvroEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.avroSchema = new Schema.Parser().parse(SCHEMA_STRING);
    }

    @Override
    public <T extends DomainEventPayload> Object serialize(EventEnvelope<T> envelope) throws EventSerializationException {
        try {
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("eventId", envelope.eventId().value());
            record.put("eventType", envelope.eventType().getValue());
            record.put("severity", envelope.severity().name());

            GenericRecord metadataRecord = new GenericData.Record(avroSchema.getField("metadata").schema());
            metadataRecord.put("source", objectMapper.writeValueAsString(envelope.metadata().source()));
            metadataRecord.put("actor", objectMapper.writeValueAsString(envelope.metadata().actor()));
            metadataRecord.put("trace", objectMapper.writeValueAsString(envelope.metadata().trace()));
            metadataRecord.put("schema", objectMapper.writeValueAsString(envelope.metadata().schema()));
            metadataRecord.put("tags", envelope.metadata().tags());

            record.put("metadata", metadataRecord);
            record.put("payload", objectMapper.writeValueAsString(envelope.payload()));
            record.put("occurredAt", envelope.occurredAt().toEpochMilli());
            record.put("publishedAt", envelope.publishedAt().toEpochMilli());

            return record;
        } catch (Exception e) {
            throw new EventSerializationException("Failed to convert EventEnvelope to Avro GenericRecord", e);
        }
    }
}
