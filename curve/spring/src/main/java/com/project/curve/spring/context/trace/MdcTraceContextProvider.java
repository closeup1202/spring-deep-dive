package com.project.curve.spring.context.trace;

import com.project.curve.core.context.TraceContextProvider;
import com.project.curve.core.envelope.EventTrace;
import org.slf4j.MDC;

public class MdcTraceContextProvider implements TraceContextProvider {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";

    @Override
    public EventTrace getTrace() {
        String traceId = MDC.get(TRACE_ID_KEY);
        String spanId = MDC.get(SPAN_ID_KEY);

        return new EventTrace(
                traceId != null ? traceId : "unknown",
                spanId != null ? spanId : "unknown",
                null // parentSpanId can be extracted additionally if needed
        );
    }
}
