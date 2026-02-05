package com.project.curve.spring.audit.annotation;

import com.project.curve.core.type.EventSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic event publishing on method execution.
 * <p>
 * This annotation enables declarative event publishing using Spring AOP. Simply add this
 * annotation to any Spring-managed bean method, and an event will be automatically published
 * when the method executes.
 * </p>
 *
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @PublishEvent(eventType = "ORDER_CREATED")
 *     public Order createOrder(CreateOrderRequest request) {
 *         return orderRepository.save(new Order(request));
 *     }
 * }
 * }</pre>
 *
 * <h3>With SpEL for Payload Extraction:</h3>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "USER_UPDATED",
 *     payload = "#args[0].toEventDto()"
 * )
 * public User updateUser(UserUpdateRequest request) {
 *     return userRepository.save(request.toEntity());
 * }
 * }</pre>
 *
 * <h3>With Transactional Outbox:</h3>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "ORDER_CREATED",
 *     outbox = true,
 *     aggregateType = "Order",
 *     aggregateId = "#result.orderId"
 * )
 * @Transactional
 * public Order createOrder(CreateOrderRequest request) {
 *     return orderRepository.save(new Order(request));
 * }
 * }</pre>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Automatic event envelope creation with rich metadata</li>
 *   <li>SpEL support for flexible payload extraction</li>
 *   <li>Multiple execution phases (BEFORE, AFTER_RETURNING, AFTER)</li>
 *   <li>Transactional Outbox Pattern support for data consistency</li>
 *   <li>PII field masking/encryption support</li>
 *   <li>Configurable error handling</li>
 * </ul>
 *
 * @see com.project.curve.core.port.EventProducer
 * @see com.project.curve.spring.audit.aop.PublishEventAspect
 * @since 0.0.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishEvent {

    /**
     * The event type name.
     * <p>
     * Default: Generated based on the method name (ClassName.methodName)
     *
     * @return the event type
     */
    String eventType() default "";

    /**
     * The event severity level.
     *
     * @return the event severity (default: INFO)
     */
    EventSeverity severity() default EventSeverity.INFO;

    /**
     * The parameter index to use as the event payload.
     * <ul>
     *   <li>-1: Use return value (default)</li>
     *   <li>0 or greater: Use the parameter at this index</li>
     * </ul>
     * <p>
     * This value is ignored if the {@link #payload()} attribute is set.
     *
     * @return the payload parameter index
     */
    int payloadIndex() default -1;

    /**
     * SpEL expression for extracting the event payload.
     * <p>
     * When set, this takes precedence over {@link #payloadIndex()}.
     *
     * <h3>Available Variables</h3>
     * <ul>
     *   <li>#result - Method return value (for AFTER_RETURNING phase)</li>
     *   <li>#args - Method parameter array</li>
     *   <li>#p0, #p1, ... - Individual parameters</li>
     *   <li>Parameter names - Available if compiled with -parameters option</li>
     * </ul>
     *
     * <h3>Examples</h3>
     * <pre>
     * // Extract specific fields from request object
     * payload = "#args[0].toEventDto()"
     *
     * // Use return value directly
     * payload = "#result"
     *
     * // Access nested property
     * payload = "#result.toEventDto()"
     * </pre>
     *
     * @return the SpEL expression
     */
    String payload() default "";

    /**
     * The phase when the event should be published.
     *
     * @return the event publishing phase (default: AFTER_RETURNING)
     */
    Phase phase() default Phase.AFTER_RETURNING;

    /**
     * Whether to propagate exceptions when event publishing fails.
     * <ul>
     *   <li>true: Throw exception on event publishing failure, causing business logic to fail</li>
     *   <li>false: Log error but continue business logic execution (default)</li>
     * </ul>
     *
     * @return whether to fail on error
     */
    boolean failOnError() default false;

    /**
     * Whether to use the Transactional Outbox Pattern.
     * <p>
     * When enabled, events are saved to the database within the same transaction
     * as the business logic, ensuring atomicity and consistency.
     *
     * @return whether to use outbox pattern
     */
    boolean outbox() default false;

    /**
     * The aggregate type for the outbox pattern.
     * <p>
     * Required when {@code outbox=true}.
     *
     * @return the aggregate type
     */
    String aggregateType() default "";

    /**
     * SpEL expression for extracting the aggregate ID.
     * <p>
     * Required when {@code outbox=true}.
     *
     * @return the SpEL expression for aggregate ID
     */
    String aggregateId() default "";

    /**
     * Enumeration of event publishing phases.
     */
    enum Phase {
        /**
         * Publish event before method execution.
         */
        BEFORE,

        /**
         * Publish event after method returns successfully.
         */
        AFTER_RETURNING,

        /**
         * Publish event after method execution (regardless of success or failure).
         */
        AFTER
    }
}
