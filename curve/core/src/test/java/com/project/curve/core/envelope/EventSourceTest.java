package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSource 테스트")
class EventSourceTest {

    @Test
    @DisplayName("정상적인 EventSource 생성 - 모든 필드 유효")
    void createValidEventSource() {
        // given
        String service = "order-service";
        String environment = "production";
        String instanceId = "instance-1";
        String host = "192.168.1.1";
        String version = "1.0.0";

        // when
        EventSource eventSource = new EventSource(service, environment, instanceId, host, version);

        // then
        assertNotNull(eventSource);
        assertEquals(service, eventSource.service());
        assertEquals(environment, eventSource.environment());
        assertEquals(instanceId, eventSource.instanceId());
        assertEquals(host, eventSource.host());
        assertEquals(version, eventSource.version());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 null")
    void createEventSourceWithNullService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource(null, "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 빈 문자열")
    void createEventSourceWithEmptyService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 공백만 있는 문자열")
    void createEventSourceWithBlankService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("   ", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("Event Chain이 있는 EventSource 생성")
    void createEventSourceWithEventChain() {
        // given
        String service = "order-service";
        String environment = "prod";
        String instanceId = "instance-1";
        String host = "localhost";
        String version = "1.0.0";
        String correlationId = "corr-123";
        String causationId = "evt-000";
        String rootEventId = "evt-root";

        // when
        EventSource eventSource = new EventSource(
                service, environment, instanceId, host, version,
                correlationId, causationId, rootEventId
        );

        // then
        assertNotNull(eventSource);
        assertEquals(correlationId, eventSource.correlationId());
        assertEquals(causationId, eventSource.causationId());
        assertEquals(rootEventId, eventSource.rootEventId());
    }

    @Test
    @DisplayName("hasEventChain 테스트 - Event Chain이 있음")
    void testHasEventChain_true() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertTrue(hasChain);
    }

    @Test
    @DisplayName("hasEventChain 테스트 - Event Chain이 없음 (null)")
    void testHasEventChain_false_null() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("hasEventChain 테스트 - Event Chain이 없음 (빈 문자열)")
    void testHasEventChain_false_empty() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "", null, null
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("hasEventChain 테스트 - Event Chain이 없음 (공백)")
    void testHasEventChain_false_blank() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "   ", null, null
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("isRootEvent 테스트 - Root Event (causationId가 null)")
    void testIsRootEvent_true_nullCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", null, "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent 테스트 - Root Event (causationId가 빈 문자열)")
    void testIsRootEvent_true_emptyCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent 테스트 - Root Event (causationId가 공백)")
    void testIsRootEvent_true_blankCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "   ", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent 테스트 - Root Event가 아님")
    void testIsRootEvent_false() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertFalse(isRoot);
    }

    @Test
    @DisplayName("estimateChainDepth 테스트 - Event Chain이 없음")
    void testEstimateChainDepth_noChain() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(0, depth);
    }

    @Test
    @DisplayName("estimateChainDepth 테스트 - Root Event")
    void testEstimateChainDepth_rootEvent() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", null, "evt-root"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(1, depth);
    }

    @Test
    @DisplayName("estimateChainDepth 테스트 - Child Event")
    void testEstimateChainDepth_childEvent() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(2, depth);
    }

    @Test
    @DisplayName("EventSource 5개 파라미터 생성자 테스트")
    void testFiveParameterConstructor() {
        // given & when
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // then
        assertEquals("order-service", eventSource.service());
        assertEquals("prod", eventSource.environment());
        assertEquals("inst-1", eventSource.instanceId());
        assertEquals("host", eventSource.host());
        assertEquals("1.0.0", eventSource.version());
        assertNull(eventSource.correlationId());
        assertNull(eventSource.causationId());
        assertNull(eventSource.rootEventId());
    }
}
