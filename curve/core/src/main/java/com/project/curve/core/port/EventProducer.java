package com.project.curve.core.port;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;

/**
 * Primary port for publishing domain events.
 * <p>
 * This interface defines the contract for publishing domain events in the Curve library.
 * It follows the Hexagonal Architecture pattern, serving as a port that can be implemented
 * by various adapters (Kafka, RabbitMQ, etc.).
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final EventProducer eventProducer;
 *
 *     public Order createOrder(OrderRequest request) {
 *         Order order = orderRepository.save(new Order(request));
 *         eventProducer.publish(new OrderCreatedPayload(order));
 *         return order;
 *     }
 * }
 * }</pre>
 *
 * @see com.project.curve.core.payload.DomainEventPayload
 * @see com.project.curve.core.type.EventSeverity
 * @since 0.0.1
 */
public interface EventProducer {

    /**
     * Publishes a domain event with default severity level (INFO).
     * <p>
     * This method wraps the payload in an {@link com.project.curve.core.envelope.EventEnvelope}
     * with contextual metadata (actor, trace, source, etc.) and publishes it to the configured
     * message broker.
     * </p>
     *
     * @param <T> the type of the event payload
     * @param payload the domain event payload to publish
     * @throws com.project.curve.core.exception.InvalidEventException if the payload is invalid
     * @throws com.project.curve.core.exception.EventSerializationException if serialization fails
     */
    <T extends DomainEventPayload> void publish(T payload);

    /**
     * Publishes a domain event with a specified severity level.
     * <p>
     * Use this method when you need to explicitly set the event severity:
     * </p>
     * <ul>
     *   <li>{@code INFO} - Normal business events (default)</li>
     *   <li>{@code WARN} - Warning events that require attention</li>
     *   <li>{@code ERROR} - Error events indicating failures</li>
     *   <li>{@code CRITICAL} - Critical events requiring immediate action</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * eventProducer.publish(
     *     new PaymentFailedPayload(order),
     *     EventSeverity.ERROR
     * );
     * }</pre>
     *
     * @param <T> the type of the event payload
     * @param payload the domain event payload to publish
     * @param severity the severity level of the event
     * @throws com.project.curve.core.exception.InvalidEventException if the payload is invalid
     * @throws com.project.curve.core.exception.EventSerializationException if serialization fails
     */
    <T extends DomainEventPayload> void publish(T payload, EventSeverity severity);
}
