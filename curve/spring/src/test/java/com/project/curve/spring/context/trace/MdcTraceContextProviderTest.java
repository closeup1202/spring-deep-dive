package com.project.curve.spring.context.trace;

import com.project.curve.core.envelope.EventTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MdcTraceContextProvider Test")
class MdcTraceContextProviderTest {

    private MdcTraceContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MdcTraceContextProvider();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should return values if traceId and spanId are set in MDC")
    void getTrace_withMdcValues_shouldReturnValues() {
        // Given
        MDC.put("traceId", "abc123");
        MDC.put("spanId", "span456");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("abc123");
        assertThat(trace.spanId()).isEqualTo("span456");
        assertThat(trace.correlationId()).isNull();
    }

    @Test
    @DisplayName("Should return 'unknown' if MDC has no values")
    void getTrace_withNoMdcValues_shouldReturnUnknown() {
        // Given - MDC is empty

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("unknown");
        assertThat(trace.spanId()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("Should return traceId and 'unknown' spanId if only traceId is set")
    void getTrace_withOnlyTraceId_shouldReturnPartialValues() {
        // Given
        MDC.put("traceId", "trace-only");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("trace-only");
        assertThat(trace.spanId()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("Should return spanId and 'unknown' traceId if only spanId is set")
    void getTrace_withOnlySpanId_shouldReturnPartialValues() {
        // Given
        MDC.put("spanId", "span-only");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("unknown");
        assertThat(trace.spanId()).isEqualTo("span-only");
    }

    @Test
    @DisplayName("Should return consistent results when called multiple times")
    void getTrace_calledMultipleTimes_shouldReturnConsistentResults() {
        // Given
        MDC.put("traceId", "consistent-trace");
        MDC.put("spanId", "consistent-span");

        // When
        EventTrace trace1 = provider.getTrace();
        EventTrace trace2 = provider.getTrace();

        // Then
        assertThat(trace1.traceId()).isEqualTo(trace2.traceId());
        assertThat(trace1.spanId()).isEqualTo(trace2.spanId());
    }
}
