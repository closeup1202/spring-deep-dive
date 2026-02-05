package com.project.curve.core.envelope;

/**
 * Event source information.
 * <p>
 * Supports Event Chain Tracking to trace event flows in microservice environments.
 *
 * <h3>Event Chain Tracking</h3>
 * <ul>
 *   <li>correlationId: Groups all events of the same business transaction</li>
 *   <li>causationId: ID of the event that triggered this event (parent event)</li>
 *   <li>rootEventId: ID of the first event in the event chain</li>
 * </ul>
 *
 * <h3>Example: Order Flow</h3>
 * <pre>
 * 1. ORDER_CREATED
 *    - eventId: "evt-001"
 *    - correlationId: "txn-123"
 *    - causationId: null (initial event)
 *    - rootEventId: "evt-001"
 *
 * 2. PAYMENT_PROCESSED (triggered by ORDER_CREATED)
 *    - eventId: "evt-002"
 *    - correlationId: "txn-123" (same transaction)
 *    - causationId: "evt-001" (triggered by ORDER_CREATED)
 *    - rootEventId: "evt-001"
 *
 * 3. INVENTORY_RESERVED (triggered by PAYMENT_PROCESSED)
 *    - eventId: "evt-003"
 *    - correlationId: "txn-123"
 *    - causationId: "evt-002" (triggered by PAYMENT_PROCESSED)
 *    - rootEventId: "evt-001"
 * </pre>
 *
 * @param service       Service name (e.g., "order-service")
 * @param environment   Environment (e.g., "prod", "dev")
 * @param instanceId    Instance ID
 * @param host          Host information
 * @param version       Service version
 * @param correlationId Correlation ID (groups business transactions)
 * @param causationId   Causation ID (ID of the event that triggered this event)
 * @param rootEventId   Root Event ID (first event in the chain)
 */
public record EventSource(
        String service,
        String environment,
        String instanceId,
        String host,
        String version,
        String correlationId,
        String causationId,
        String rootEventId
) {
    public EventSource {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service is required");
        }
    }

    /**
     * Creates without Event Chain Tracking (backward compatible).
     */
    public EventSource(
            String service,
            String environment,
            String instanceId,
            String host,
            String version
    ) {
        this(service, environment, instanceId, host, version, null, null, null);
    }

    /**
     * Checks if Event Chain exists.
     *
     * @return true if correlationId exists
     */
    public boolean hasEventChain() {
        return correlationId != null && !correlationId.isBlank();
    }

    /**
     * Checks if this is the root event (no causationId).
     *
     * @return true if causationId is absent
     */
    public boolean isRootEvent() {
        return causationId == null || causationId.isBlank();
    }

    /**
     * Calculates event chain depth (approximate, requires DB query for accuracy).
     * <p>
     * At least 1 if rootEventId exists
     *
     * @return Event chain depth (estimated)
     */
    public int estimateChainDepth() {
        if (!hasEventChain()) {
            return 0;
        }
        if (isRootEvent()) {
            return 1; // Initial event
        }
        // At least 2 if causationId exists (requires DB query for actual depth)
        return 2;
    }
}
