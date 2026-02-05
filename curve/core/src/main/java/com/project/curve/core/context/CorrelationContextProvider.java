package com.project.curve.core.context;

/**
 * Port interface for providing Correlation ID and Event Chain information.
 * <p>
 * Used to track event chains in microservice environments.
 *
 * <h3>Implementation Examples</h3>
 * <ul>
 *   <li>MdcCorrelationContextProvider - Based on SLF4J MDC</li>
 *   <li>HttpHeaderCorrelationContextProvider - Based on HTTP headers</li>
 *   <li>KafkaHeaderCorrelationContextProvider - Based on Kafka headers</li>
 * </ul>
 *
 * <h3>Usage Pattern</h3>
 * <pre>
 * // 1. Extract Correlation ID from HTTP request
 * String correlationId = request.getHeader("X-Correlation-ID");
 * MDC.put("correlationId", correlationId);
 *
 * // 2. Automatically included when publishing events
 * @PublishEvent(eventType = "ORDER_CREATED")
 * public Order createOrder() { ... }
 *
 * // 3. Propagate to next service (Kafka headers)
 * headers.add("X-Correlation-ID", correlationId);
 * headers.add("X-Causation-ID", eventId);
 * </pre>
 */
public interface CorrelationContextProvider {

    /**
     * Retrieves the Correlation ID.
     * <p>
     * ID that groups all events of the same business transaction.
     *
     * @return Correlation ID (null if not available)
     */
    String getCorrelationId();

    /**
     * Retrieves the Causation ID.
     * <p>
     * ID of the parent event that triggered the current event.
     *
     * @return Causation ID (null if not available)
     */
    String getCausationId();

    /**
     * Retrieves the Root Event ID.
     * <p>
     * ID of the first event in the event chain.
     *
     * @return Root Event ID (null if not available)
     */
    String getRootEventId();

    /**
     * Sets the Correlation ID.
     * <p>
     * Called when starting a new business transaction.
     *
     * @param correlationId Correlation ID to set
     */
    void setCorrelationId(String correlationId);

    /**
     * Sets the Causation ID.
     * <p>
     * Sets the eventId to be used as the causationId for the next event when processing events.
     *
     * @param causationId Causation ID to set
     */
    void setCausationId(String causationId);

    /**
     * Sets the Root Event ID.
     *
     * @param rootEventId Root Event ID to set
     */
    void setRootEventId(String rootEventId);

    /**
     * Clears all context.
     * <p>
     * Called after request processing to prevent memory leaks.
     */
    void clear();
}
