package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventTrace 테스트")
class EventTraceTest {

    @Test
    @DisplayName("정상적인 EventTrace 생성")
    void createValidEventTrace() {
        // given
        String traceId = "trace-abc-123";
        String spanId = "span-def-456";
        String correlationId = "correlation-ghi-789";

        // when
        EventTrace trace = new EventTrace(traceId, spanId, correlationId);

        // then
        assertNotNull(trace);
        assertEquals(traceId, trace.traceId());
        assertEquals(spanId, trace.spanId());
        assertEquals(correlationId, trace.correlationId());
    }

    @Test
    @DisplayName("EventTrace - null 값들로 생성 가능 (validation 없음)")
    void createEventTraceWithNullValues() {
        // when
        EventTrace trace = new EventTrace(null, null, null);

        // then - validation이 없으므로 생성 성공
        assertNotNull(trace);
        assertNull(trace.traceId());
        assertNull(trace.spanId());
        assertNull(trace.correlationId());
    }
}
