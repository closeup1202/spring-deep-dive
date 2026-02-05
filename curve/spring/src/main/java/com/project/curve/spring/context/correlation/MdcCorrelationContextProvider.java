package com.project.curve.spring.context.correlation;

import com.project.curve.core.context.CorrelationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * SLF4J MDC-based Correlation Context Provider.
 * <p>
 * Uses MDC (Mapped Diagnostic Context) to manage Correlation ID, Causation ID, and Root Event ID.
 *
 * <h3>MDC Keys</h3>
 * <ul>
 *   <li>correlationId: Business transaction ID</li>
 *   <li>causationId: ID of the event that caused this event</li>
 *   <li>rootEventId: ID of the first event in the event chain</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // 1. Set in HTTP Filter
 * String correlationId = request.getHeader("X-Correlation-ID");
 * if (correlationId == null) {
 *     correlationId = UUID.randomUUID().toString();
 * }
 * MDC.put("correlationId", correlationId);
 *
 * // 2. Automatically included when publishing events
 * @PublishEvent(eventType = "ORDER_CREATED")
 * public Order createOrder() { ... }
 *
 * // 3. Clean up after request completion
 * MDC.clear();
 * </pre>
 *
 * @see CorrelationContextProvider
 * @see MDC
 */
@Slf4j
@Component
public class MdcCorrelationContextProvider implements CorrelationContextProvider {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CAUSATION_ID_KEY = "causationId";
    private static final String ROOT_EVENT_ID_KEY = "rootEventId";

    @Override
    public String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    @Override
    public String getCausationId() {
        return MDC.get(CAUSATION_ID_KEY);
    }

    @Override
    public String getRootEventId() {
        return MDC.get(ROOT_EVENT_ID_KEY);
    }

    @Override
    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
            log.debug("Set correlationId in MDC: {}", correlationId);
        } else {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    @Override
    public void setCausationId(String causationId) {
        if (causationId != null && !causationId.isBlank()) {
            MDC.put(CAUSATION_ID_KEY, causationId);
            log.debug("Set causationId in MDC: {}", causationId);
        } else {
            MDC.remove(CAUSATION_ID_KEY);
        }
    }

    @Override
    public void setRootEventId(String rootEventId) {
        if (rootEventId != null && !rootEventId.isBlank()) {
            MDC.put(ROOT_EVENT_ID_KEY, rootEventId);
            log.debug("Set rootEventId in MDC: {}", rootEventId);
        } else {
            MDC.remove(ROOT_EVENT_ID_KEY);
        }
    }

    @Override
    public void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(CAUSATION_ID_KEY);
        MDC.remove(ROOT_EVENT_ID_KEY);
        log.trace("Cleared correlation context from MDC");
    }

    /**
     * Clears all MDC context (for debugging).
     */
    public void clearAll() {
        MDC.clear();
        log.trace("Cleared all MDC context");
    }
}
