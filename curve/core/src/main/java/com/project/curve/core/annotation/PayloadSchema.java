package com.project.curve.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for defining schema information on event payload classes.
 *
 * <p>Example:</p>
 * <pre>
 * {@code
 * @PayloadSchema(name = "UserCreated", version = 2)
 * public record UserCreatedPayload(String userId, String email)
 *     implements DomainEventPayload { ... }
 * }
 * </pre>
 *
 * <p>If the annotation is not present, the class name is used as the schema name with version 1.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PayloadSchema {

    /**
     * Schema name. Uses the class name if empty.
     */
    String name() default "";

    /**
     * Schema version. Default is 1.
     */
    int version() default 1;

    /**
     * Schema ID to use when integrating with external Schema Registry.
     * Treated as null if empty.
     */
    String schemaId() default "";
}
