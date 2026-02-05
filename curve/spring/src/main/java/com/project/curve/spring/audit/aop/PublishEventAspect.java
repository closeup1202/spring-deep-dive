package com.project.curve.spring.audit.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.exception.EventPublishException;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PublishEventAspect {

    private final EventProducer eventProducer;
    private final CurveMetricsCollector metricsCollector;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Autowired(required = false)
    private OutboxEventSaver outboxEventSaver;

    @Pointcut("@annotation(com.project.curve.spring.audit.annotation.PublishEvent)")
    public void publishEventMethod() {
    }

    @Before("publishEventMethod() && @annotation(publishEvent)")
    public void beforeMethod(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (publishEvent.phase() == PublishEvent.Phase.BEFORE) {
            publishEvent(joinPoint, publishEvent, null);
        }
    }

    @AfterReturning(pointcut = "publishEventMethod() && @annotation(publishEvent)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, PublishEvent publishEvent, Object result) {
        if (publishEvent.phase() == PublishEvent.Phase.AFTER_RETURNING) {
            publishEvent(joinPoint, publishEvent, result);
        }
    }

    @After("publishEventMethod() && @annotation(publishEvent)")
    public void afterMethod(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (publishEvent.phase() == PublishEvent.Phase.AFTER) {
            publishEvent(joinPoint, publishEvent, null);
        }
    }

    private void publishEvent(JoinPoint joinPoint, PublishEvent publishEvent, Object returnValue) {
        long startTime = System.currentTimeMillis();

        try {
            String eventType = determineEventType(joinPoint, publishEvent);
            Object payloadData = extractPayload(joinPoint, publishEvent, returnValue);
            EventPayload payload = createEventPayload(eventType, joinPoint, payloadData);

            // Check if Outbox Pattern is used
            if (publishEvent.outbox()) {
                if (outboxEventSaver == null) {
                    throw new EventPublishException(
                            "OutboxEventSaver is not configured. " +
                                    "Please enable outbox configuration or set outbox=false."
                    );
                }
                outboxEventSaver.save(joinPoint, publishEvent, payload, returnValue);
                log.debug("Event saved to outbox: eventType={}", eventType);
            } else {
                publishAndRecordSuccess(eventType, payload, publishEvent, startTime);
            }
        } catch (Exception e) {
            handlePublishFailure(joinPoint, publishEvent, startTime, e);
        }
    }

    private void publishAndRecordSuccess(String eventType, EventPayload payload, PublishEvent publishEvent, long startTime) {
        EventSeverity severity = publishEvent.severity();
        eventProducer.publish(payload, severity);
        log.debug("Event published: eventType={}, severity={}", eventType, severity);

        recordSuccessMetrics(eventType, startTime);
    }

    private void handlePublishFailure(JoinPoint joinPoint, PublishEvent publishEvent, long startTime, Exception e) {
        String eventType = determineEventTypeForFailure(joinPoint, publishEvent);

        log.error("Failed to publish event for method: {}, eventType={}, errorType={}",
                joinPoint.getSignature(), eventType, e.getClass().getSimpleName(), e);

        recordFailureMetrics(eventType, e, startTime);

        if (publishEvent.failOnError()) {
            throw new EventPublishException(
                    "Failed to publish event for method: " + joinPoint.getSignature(), e);
        } else {
            log.warn("Event publish failed but continuing execution (failOnError=false): " +
                    "eventType={}, method={}, errorType={}, errorMessage={}",
                    eventType, joinPoint.getSignature(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String determineEventTypeForFailure(JoinPoint joinPoint, PublishEvent publishEvent) {
        try {
            return determineEventType(joinPoint, publishEvent);
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private void recordSuccessMetrics(String eventType, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        metricsCollector.recordEventPublished(eventType, true, duration);
    }

    private void recordFailureMetrics(String eventType, Exception e, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        metricsCollector.recordEventPublished(eventType, false, duration);
        metricsCollector.recordAuditFailure(eventType, e.getClass().getSimpleName());
    }

    private String determineEventType(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (!publishEvent.eventType().isBlank()) {
            return publishEvent.eventType();
        }
        // Default: ClassName.methodName
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        return className + "." + methodName;
    }

    private Object extractPayload(JoinPoint joinPoint, PublishEvent publishEvent, Object returnValue) {
        // 1. Prioritize SpEL expression if present
        if (!publishEvent.payload().isBlank()) {
            return extractPayloadUsingSpel(joinPoint, publishEvent.payload(), returnValue);
        }

        // 2. Use payloadIndex
        int payloadIndex = publishEvent.payloadIndex();
        if (payloadIndex == -1) {
            // Use return value
            return returnValue;
        } else {
            // Use parameter
            Object[] args = joinPoint.getArgs();
            if (payloadIndex >= 0 && payloadIndex < args.length) {
                return args[payloadIndex];
            } else {
                log.warn("Invalid payloadIndex: {}. Using null payload.", payloadIndex);
                return null;
            }
        }
    }

    private Object extractPayloadUsingSpel(JoinPoint joinPoint, String expression, Object returnValue) {
        try {
            SimpleEvaluationContext.Builder contextBuilder = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods();

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            SimpleEvaluationContext context = contextBuilder.build();
            context.setVariable("result", returnValue);
            context.setVariable("args", args);

            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                    context.setVariable("p" + i, args[i]);
                }
            }

            Expression expr = spelParser.parseExpression(expression);
            return expr.getValue(context);
        } catch (Exception e) {
            // Log and return null on SpEL parsing/execution failure (minimize impact on business logic)
            log.warn("Failed to extract payload using SpEL expression '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    private EventPayload createEventPayload(String eventType, JoinPoint joinPoint, Object payloadData) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = signature.getDeclaringType().getName();
        String methodName = method.getName();

        return new EventPayload(
                eventType,
                className,
                methodName,
                payloadData
        );
    }

}
