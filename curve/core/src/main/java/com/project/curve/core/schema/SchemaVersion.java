package com.project.curve.core.schema;

/**
 * Record representing event schema version information.
 * <p>
 * Each event payload has a specific schema version and supports migration between versions.
 *
 * @param name        Schema name (e.g., "OrderCreated", "UserRegistered")
 * @param version     Schema version (starts from 1)
 * @param payloadClass Payload class
 */
public record SchemaVersion(
    String name,
    int version,
    Class<?> payloadClass
) {
    /**
     * Creates a SchemaVersion.
     *
     * @param name         Schema name
     * @param version      Schema version
     * @param payloadClass Payload class
     * @throws IllegalArgumentException if name is null or blank, or version is less than 1
     */
    public SchemaVersion {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema name must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Schema version must be >= 1, but was: " + version);
        }
        if (payloadClass == null) {
            throw new IllegalArgumentException("Payload class must not be null");
        }
    }

    /**
     * Returns the full key of the schema.
     * <p>
     * Format: {name}:v{version} (e.g., "OrderCreated:v1")
     *
     * @return Full schema key
     */
    public String getKey() {
        return name + ":v" + version;
    }

    /**
     * Compares with another version.
     *
     * @param other Version to compare
     * @return Positive if this version is greater, 0 if equal, negative if less
     */
    public int compareVersion(SchemaVersion other) {
        if (!this.name.equals(other.name)) {
            throw new IllegalArgumentException(
                "Cannot compare versions of different schemas: " + this.name + " vs " + other.name
            );
        }
        return Integer.compare(this.version, other.version);
    }

    /**
     * Checks if this version is newer than another version.
     *
     * @param other Version to compare
     * @return true if this version is newer
     */
    public boolean isNewerThan(SchemaVersion other) {
        return compareVersion(other) > 0;
    }

    /**
     * Checks if this version is compatible with another version.
     * <p>
     * By default, versions with the same schema name are considered compatible.
     *
     * @param other Version to compare
     * @return true if compatible
     */
    public boolean isCompatibleWith(SchemaVersion other) {
        return this.name.equals(other.name);
    }
}
