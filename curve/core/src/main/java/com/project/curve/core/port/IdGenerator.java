package com.project.curve.core.port;

import com.project.curve.core.envelope.EventId;

public interface IdGenerator {
    EventId generate();
}
