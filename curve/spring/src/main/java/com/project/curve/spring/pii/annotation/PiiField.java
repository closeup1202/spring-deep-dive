package com.project.curve.spring.pii.annotation;

import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark PII (Personally Identifiable Information) fields and specify processing strategy.
 * Automatically masked/encrypted/hashed during Jackson serialization.
 *
 * <pre>{@code
 * public record UserPayload(
 *     @PiiField(type = PiiType.EMAIL)
 *     String email,
 *
 *     @PiiField(type = PiiType.PHONE, level = MaskingLevel.STRONG)
 *     String phone,
 *
 *     @PiiField(strategy = PiiStrategy.ENCRYPT, encryptKey = "sensitive")
 *     String ssn,
 *
 *     @PiiField(strategy = PiiStrategy.EXCLUDE)
 *     String password
 * ) {}
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PiiField {

    /**
     * PII data type (used to determine masking pattern)
     */
    PiiType type() default PiiType.CUSTOM;

    /**
     * PII processing strategy
     * - MASK: Replace some characters with *
     * - ENCRYPT: AES encryption (reversible)
     * - HASH: SHA-256 hash (irreversible)
     * - EXCLUDE: Exclude from serialization
     */
    PiiStrategy strategy() default PiiStrategy.MASK;

    /**
     * Masking strength (used when strategy=MASK)
     */
    MaskingLevel level() default MaskingLevel.NORMAL;

    /**
     * Encryption key alias (used when strategy=ENCRYPT)
     * Uses default key if empty
     */
    String encryptKey() default "";
}
