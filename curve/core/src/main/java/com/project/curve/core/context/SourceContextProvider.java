package com.project.curve.core.context;

import com.project.curve.core.envelope.EventSource;

public interface SourceContextProvider {
    EventSource getSource();
}
