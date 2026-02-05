package com.project.curve.spring.audit.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.exception.EventPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Component that saves events to the Outbox table.
 * <p>
 * Called by {@link PublishEventAspect} when outbox=true.
 * Handles aggregateId extraction via SpEL expressions and payload serialization.
 *
 * @see PublishEventAspect
 * @see OutboxEventRepository
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventSaver {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * Saves an event to the Outbox table.
     *
     * @param joinPoint    AOP join point
     * @param publishEvent @PublishEvent annotation
     * @param payload      Event payload
     * @param returnValue  Method return value
     */
    public void save(JoinPoint joinPoint, PublishEvent publishEvent, EventPayload payload, Object returnValue) {
        String aggregateType = validateAggregateType(publishEvent);
        String aggregateId = validateAndExtractAggregateId(publishEvent, joinPoint, returnValue);
        String payloadJson = serializePayload(payload);

        String eventId = UUID.randomUUID().toString();
        OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                payload.eventTypeName(),
                payloadJson,
                Instant.now()
        );

        outboxEventRepository.save(outboxEvent);

        log.debug("Event saved to outbox: eventId={}, aggregateType={}, aggregateId={}, eventType={}",
                eventId, aggregateType, aggregateId, payload.eventTypeName());
    }

    private String validateAggregateType(PublishEvent publishEvent) {
        String aggregateType = publishEvent.aggregateType();
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new EventPublishException(
                    "aggregateType must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateType=\"Order\")"
            );
        }
        return aggregateType;
    }

    private String validateAndExtractAggregateId(PublishEvent publishEvent, JoinPoint joinPoint, Object returnValue) {
        String expression = publishEvent.aggregateId();
        if (expression == null || expression.isBlank()) {
            throw new EventPublishException(
                    "aggregateId must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateId=\"#result.orderId\")"
            );
        }
        return extractAggregateId(expression, joinPoint, returnValue);
    }

    private String serializePayload(EventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to serialize event payload to JSON: " + e.getMessage(), e
            );
        }
    }

    /**
     * Extracts Aggregate ID using SpEL expression.
     */
    private String extractAggregateId(String expression, JoinPoint joinPoint, Object returnValue) {
        try {
            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .build();
            context.setVariable("result", returnValue);
            context.setVariable("args", joinPoint.getArgs());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }

            Expression expr = spelParser.parseExpression(expression);
            Object value = expr.getValue(context);

            if (value == null) {
                throw new EventPublishException(
                        "Failed to extract aggregateId: expression '" + expression + "' returned null"
                );
            }

            return value.toString();
        } catch (EventPublishException e) {
            throw e;
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to extract aggregateId using expression '" + expression + "': " + e.getMessage(), e
            );
        }
    }
}
