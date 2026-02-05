package com.project.curve.core.schema;

/**
 * Interface for supporting migration between schema versions.
 * <p>
 * Provides logic to transform data when event payloads are upgraded to a new version.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * public class OrderCreatedPayloadV1ToV2Migration implements SchemaMigration<OrderCreatedPayloadV1, OrderCreatedPayloadV2> {
 *     @Override
 *     public OrderCreatedPayloadV2 migrate(OrderCreatedPayloadV1 source) {
 *         return new OrderCreatedPayloadV2(
 *             source.orderId(),
 *             source.customerId(),
 *             source.productName(),
 *             source.quantity(),
 *             source.totalAmount(),
 *             "PENDING"  // Default value for new field
 *         );
 *     }
 *
 *     @Override
 *     public SchemaVersion fromVersion() {
 *         return new SchemaVersion("OrderCreated", 1, OrderCreatedPayloadV1.class);
 *     }
 *
 *     @Override
 *     public SchemaVersion toVersion() {
 *         return new SchemaVersion("OrderCreated", 2, OrderCreatedPayloadV2.class);
 *     }
 * }
 * }</pre>
 *
 * @param <FROM> source payload type (old version)
 * @param <TO>   target payload type (new version)
 */
public interface SchemaMigration<FROM, TO> {

    /**
     * Transforms an old version payload to a new version.
     *
     * @param source the old version payload
     * @return the new version payload
     */
    TO migrate(FROM source);

    /**
     * Returns the migration source version.
     *
     * @return the source version
     */
    SchemaVersion fromVersion();

    /**
     * Returns the migration target version.
     *
     * @return the target version
     */
    SchemaVersion toVersion();

    /**
     * Checks if the migration is applicable.
     *
     * @param from the source version
     * @param to   the target version
     * @return true if applicable
     */
    default boolean isApplicable(SchemaVersion from, SchemaVersion to) {
        return fromVersion().equals(from) && toVersion().equals(to);
    }
}
