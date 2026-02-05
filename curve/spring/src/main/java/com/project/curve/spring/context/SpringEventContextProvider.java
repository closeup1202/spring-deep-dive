package com.project.curve.spring.context;

import com.project.curve.core.context.*;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;

public record SpringEventContextProvider(
        ActorContextProvider actorProvider,
        TraceContextProvider traceProvider,
        SourceContextProvider sourceProvider,
        SchemaContextProvider schemaProvider,
        TagsContextProvider tagsContextProvider
) implements EventContextProvider {

    @Override
    public EventMetadata currentMetadata(DomainEventPayload payload) {
        return new EventMetadata(
                sourceProvider.getSource(),
                actorProvider.getActor(),
                traceProvider.getTrace(),
                schemaProvider.getSchemaFor(payload),
                tagsContextProvider.getTags()
        );
    }
}
