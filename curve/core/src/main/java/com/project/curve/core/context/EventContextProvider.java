package com.project.curve.core.context;

import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;

public interface EventContextProvider {
    EventMetadata currentMetadata(DomainEventPayload payload);
}
