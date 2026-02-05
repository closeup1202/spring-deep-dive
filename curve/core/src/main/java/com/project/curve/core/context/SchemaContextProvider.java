package com.project.curve.core.context;

import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.payload.DomainEventPayload;

/**
 * Interface for providing event schema information.
 */
public interface SchemaContextProvider {

    /**
     * Returns the default schema information.
     * Used when called without payload information.
     */
    EventSchema getSchema();

    /**
     * Returns schema information based on the payload.
     * Can dynamically determine the schema using annotations, class name, etc.
     *
     * @param payload Event payload
     * @return Schema information matching the payload
     */
    default EventSchema getSchemaFor(DomainEventPayload payload) {
        return getSchema();
    }
}
