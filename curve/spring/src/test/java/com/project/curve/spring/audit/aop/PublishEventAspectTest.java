package com.project.curve.spring.audit.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.audit.type.DefaultEventType;
import com.project.curve.spring.exception.EventPublishException;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublishEventAspect Test")
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishEventAspectTest {

    @Mock
    private EventProducer eventProducer;

    @Mock
    private CurveMetricsCollector metricsCollector;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private PublishEvent publishEvent;

    @Captor
    private ArgumentCaptor<EventPayload> payloadCaptor;

    @Captor
    private ArgumentCaptor<EventSeverity> severityCaptor;

    private PublishEventAspect aspect;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new PublishEventAspect(eventProducer, metricsCollector);

        // Default JoinPoint setup
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(TestService.class);
        when(methodSignature.getName()).thenReturn("testMethod");

        Method method = TestService.class.getMethod("testMethod", String.class);
        when(methodSignature.getMethod()).thenReturn(method);

        // Default PublishEvent setup (prevent NPE)
        when(publishEvent.payload()).thenReturn("");
        when(publishEvent.eventType()).thenReturn("");
        when(publishEvent.aggregateType()).thenReturn("");
        when(publishEvent.aggregateId()).thenReturn("");
    }

    @Nested
    @DisplayName("Phase.BEFORE Test")
    class BeforePhaseTest {

        @Test
        @DisplayName("Should publish event before method execution in BEFORE phase")
        void beforeMethod_withBeforePhase_shouldPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.BEFORE);
            when(publishEvent.eventType()).thenReturn("TEST_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(0);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"testData"});

            // When
            aspect.beforeMethod(joinPoint, publishEvent);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), severityCaptor.capture());

            EventPayload payload = payloadCaptor.getValue();
            assertThat(payload.getEventType()).isEqualTo(new DefaultEventType("TEST_EVENT"));
            assertThat(payload.data()).isEqualTo("testData");
            assertThat(severityCaptor.getValue()).isEqualTo(EventSeverity.INFO);
        }

        @Test
        @DisplayName("beforeMethod should not publish event in AFTER_RETURNING phase")
        void beforeMethod_withAfterReturningPhase_shouldNotPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);

            // When
            aspect.beforeMethod(joinPoint, publishEvent);

            // Then
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    @DisplayName("Phase.AFTER_RETURNING Test")
    class AfterReturningPhaseTest {

        @Test
        @DisplayName("Should use return value as payload in AFTER_RETURNING phase")
        void afterReturning_withAfterReturningPhase_shouldUseReturnValueAsPayload() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("ORDER_CREATED");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1); // Use return value
            Object returnValue = new TestOrder("order-123");

            // When
            aspect.afterReturning(joinPoint, publishEvent, returnValue);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), eq(EventSeverity.INFO));

            EventPayload payload = payloadCaptor.getValue();
            assertThat(payload.getEventType()).isEqualTo(new DefaultEventType("ORDER_CREATED"));
            assertThat(payload.data()).isEqualTo(returnValue);
        }

        @Test
        @DisplayName("afterReturning should not publish event in BEFORE phase")
        void afterReturning_withBeforePhase_shouldNotPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.BEFORE);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "result");

            // Then
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    @DisplayName("Phase.AFTER Test")
    class AfterPhaseTest {

        @Test
        @DisplayName("Should publish event after method execution in AFTER phase")
        void afterMethod_withAfterPhase_shouldPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER);
            when(publishEvent.eventType()).thenReturn("AFTER_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.WARN);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterMethod(joinPoint, publishEvent);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), eq(EventSeverity.WARN));
            assertThat(payloadCaptor.getValue().getEventType()).isEqualTo(new DefaultEventType("AFTER_EVENT"));
        }
    }

    @Nested
    @DisplayName("eventType Determination Test")
    class EventTypeDeterminationTest {

        @Test
        @DisplayName("Should use ClassName.methodName if eventType is not specified")
        void determineEventType_withBlankEventType_shouldUseClassName() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn(""); // Blank string
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "result");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().getEventType()).isEqualTo(new DefaultEventType("TestService.testMethod"));
        }
    }

    @Nested
    @DisplayName("payloadIndex Test")
    class PayloadIndexTest {

        @Test
        @DisplayName("Should use first parameter if payloadIndex is 0")
        void extractPayload_withPayloadIndex0_shouldUseFirstArg() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(0);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"firstArg", "secondArg"});

            // When
            aspect.afterReturning(joinPoint, publishEvent, "returnValue");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().data()).isEqualTo("firstArg");
        }

        @Test
        @DisplayName("Should return null if payloadIndex is invalid")
        void extractPayload_withInvalidPayloadIndex_shouldReturnNull() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(99); // Invalid index
            when(joinPoint.getArgs()).thenReturn(new Object[]{"arg"});

            // When
            aspect.afterReturning(joinPoint, publishEvent, "returnValue");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().data()).isNull();
        }
    }

    @Nested
    @DisplayName("Exception Handling Test")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("Should throw EventPublishException if failOnError is true and exception occurs")
        void handlePublishFailure_withFailOnErrorTrue_shouldThrowException() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);
            when(publishEvent.failOnError()).thenReturn(true);

            doThrow(new RuntimeException("Kafka connection failed"))
                    .when(eventProducer).publish(any(), any());

            // When & Then
            assertThatThrownBy(() -> aspect.afterReturning(joinPoint, publishEvent, "result"))
                    .isInstanceOf(EventPublishException.class)
                    .hasMessageContaining("Failed to publish event");
        }

        @Test
        @DisplayName("Should continue business logic if failOnError is false and exception occurs")
        void handlePublishFailure_withFailOnErrorFalse_shouldContinue() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);
            when(publishEvent.failOnError()).thenReturn(false);

            doThrow(new RuntimeException("Kafka connection failed"))
                    .when(eventProducer).publish(any(), any());

            // When & Then - Should not throw exception
            assertThatCode(() -> aspect.afterReturning(joinPoint, publishEvent, "result"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Severity Test")
    class SeverityTest {

        @Test
        @DisplayName("Should publish event with CRITICAL severity")
        void publishEvent_withCriticalSeverity_shouldPublish() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("CRITICAL_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.CRITICAL);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "criticalData");

            // Then
            verify(eventProducer).publish(any(), eq(EventSeverity.CRITICAL));
        }

        @Test
        @DisplayName("Should publish event with ERROR severity")
        void publishEvent_withErrorSeverity_shouldPublish() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("ERROR_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.ERROR);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "errorData");

            // Then
            verify(eventProducer).publish(any(), eq(EventSeverity.ERROR));
        }
    }

    // Test class
    public static class TestService {
        public String testMethod(String input) {
            return "result: " + input;
        }
    }

    public record TestOrder(String orderId) {
    }
}
