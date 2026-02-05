package com.project.curve.spring.context.schema;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.context.SchemaContextProvider;
import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.payload.DomainEventPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Annotation-based schema context provider.
 *
 * <p>If a payload class has a {@link PayloadSchema} annotation, uses that information;
 * otherwise, uses the class name as the schema name and version 1 as the default.</p>
 *
 * <p>Caches schema information for performance.</p>
 */
public class AnnotationBasedSchemaContextProvider implements SchemaContextProvider {

    private static final String DEFAULT_SCHEMA_NAME = "DomainEvent";
    private static final int DEFAULT_SCHEMA_VERSION = 1;

    private final Map<Class<?>, EventSchema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public EventSchema getSchema() {
        return EventSchema.of(DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_VERSION);
    }

    @Override
    public EventSchema getSchemaFor(DomainEventPayload payload) {
        if (payload == null) {
            return getSchema();
        }

        return schemaCache.computeIfAbsent(payload.getClass(), this::resolveSchema);
    }

    private EventSchema resolveSchema(Class<?> payloadClass) {
        PayloadSchema annotation = payloadClass.getAnnotation(PayloadSchema.class);

        if (annotation == null) {
            return EventSchema.of(payloadClass.getSimpleName(), DEFAULT_SCHEMA_VERSION);
        }

        String name = annotation.name().isBlank()
                ? payloadClass.getSimpleName()
                : annotation.name();

        String schemaId = annotation.schemaId().isBlank()
                ? null
                : annotation.schemaId();

        return new EventSchema(name, annotation.version(), schemaId);
    }
}
