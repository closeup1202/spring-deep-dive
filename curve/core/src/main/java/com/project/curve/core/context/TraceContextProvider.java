package com.project.curve.core.context;

import com.project.curve.core.envelope.EventTrace;

public interface TraceContextProvider {
    EventTrace getTrace();
}
