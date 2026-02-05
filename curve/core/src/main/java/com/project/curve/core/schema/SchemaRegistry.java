package com.project.curve.core.schema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing event schema versions.
 * <p>
 * Supports schema version registration, retrieval, and migration path discovery.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * // Register schemas
 * SchemaRegistry registry = new SchemaRegistry();
 * registry.register(new SchemaVersion("OrderCreated", 1, OrderCreatedPayloadV1.class));
 * registry.register(new SchemaVersion("OrderCreated", 2, OrderCreatedPayloadV2.class));
 *
 * // Register migration
 * registry.registerMigration(new OrderCreatedPayloadV1ToV2Migration());
 *
 * // Check compatibility
 * boolean compatible = registry.isCompatible("OrderCreated", 1, 2);
 *
 * // Retrieve latest version
 * SchemaVersion latest = registry.getLatestVersion("OrderCreated");
 * }</pre>
 */
public class SchemaRegistry {

    private final Map<String, Map<Integer, SchemaVersion>> schemas = new ConcurrentHashMap<>();
    private final Map<String, SchemaMigration<?, ?>> migrations = new ConcurrentHashMap<>();

    /**
     * Registers a schema version.
     *
     * @param schemaVersion the schema version to register
     * @throws IllegalArgumentException if the same version is already registered
     */
    public void register(SchemaVersion schemaVersion) {
        if (schemaVersion == null) {
            throw new IllegalArgumentException("SchemaVersion must not be null");
        }

        schemas.computeIfAbsent(schemaVersion.name(), k -> new ConcurrentHashMap<>())
               .compute(schemaVersion.version(), (v, existing) -> {
                   if (existing != null && !existing.payloadClass().equals(schemaVersion.payloadClass())) {
                       throw new IllegalArgumentException(
                           "Schema version already registered with different payload class: " +
                           schemaVersion.getKey()
                       );
                   }
                   return schemaVersion;
               });
    }

    /**
     * Registers a migration.
     *
     * @param migration the migration to register
     * @param <FROM>    source type
     * @param <TO>      target type
     * @throws IllegalArgumentException if the migration's source or target version is not registered
     */
    public <FROM, TO> void registerMigration(SchemaMigration<FROM, TO> migration) {
        if (migration == null) {
            throw new IllegalArgumentException("SchemaMigration must not be null");
        }

        SchemaVersion from = migration.fromVersion();
        SchemaVersion to = migration.toVersion();

        // Check if versions exist
        if (!isVersionRegistered(from.name(), from.version())) {
            throw new IllegalArgumentException(
                "Source schema version not registered: " + from.getKey()
            );
        }
        if (!isVersionRegistered(to.name(), to.version())) {
            throw new IllegalArgumentException(
                "Target schema version not registered: " + to.getKey()
            );
        }

        String migrationKey = getMigrationKey(from, to);
        migrations.put(migrationKey, migration);
    }

    /**
     * Retrieves a specific version of a schema.
     *
     * @param schemaName the schema name
     * @param version    the version
     * @return the schema version (Optional.empty() if not found)
     */
    public Optional<SchemaVersion> getVersion(String schemaName, int version) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.get(version));
    }

    /**
     * Retrieves the latest version of a schema.
     *
     * @param schemaName the schema name
     * @return the latest version (Optional.empty() if not found)
     */
    public Optional<SchemaVersion> getLatestVersion(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .flatMap(versionMap -> versionMap.values().stream()
                           .max(Comparator.comparingInt(SchemaVersion::version)));
    }

    /**
     * Retrieves all registered versions of a schema.
     *
     * @param schemaName the schema name
     * @return all registered versions (sorted in ascending order by version)
     */
    public List<SchemaVersion> getAllVersions(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.values().stream()
                           .sorted(Comparator.comparingInt(SchemaVersion::version))
                           .toList())
                       .orElse(Collections.emptyList());
    }

    /**
     * Retrieves the migration between two versions.
     *
     * @param from the source version
     * @param to   the target version
     * @return the migration (Optional.empty() if not found)
     */
    public Optional<SchemaMigration<?, ?>> getMigration(SchemaVersion from, SchemaVersion to) {
        String key = getMigrationKey(from, to);
        return Optional.ofNullable(migrations.get(key));
    }

    /**
     * Checks if two versions are compatible.
     * <p>
     * Compatibility conditions:
     * <ul>
     *   <li>Same schema name</li>
     *   <li>Direct or indirect migration path exists</li>
     * </ul>
     *
     * @param schemaName  the schema name
     * @param fromVersion the source version
     * @param toVersion   the target version
     * @return true if compatible
     */
    public boolean isCompatible(String schemaName, int fromVersion, int toVersion) {
        Optional<SchemaVersion> from = getVersion(schemaName, fromVersion);
        Optional<SchemaVersion> to = getVersion(schemaName, toVersion);

        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }

        if (fromVersion == toVersion) {
            return true;
        }

        // Check if migration path exists
        return findMigrationPath(from.get(), to.get()).isPresent();
    }

    /**
     * Finds the migration path between two versions.
     * <p>
     * Uses BFS (Breadth-First Search) to find the shortest path.
     *
     * @param from the starting version
     * @param to   the target version
     * @return the migration path (Optional.empty() if none exists)
     */
    public Optional<List<SchemaMigration<?, ?>>> findMigrationPath(SchemaVersion from, SchemaVersion to) {
        if (from.equals(to)) {
            return Optional.of(Collections.emptyList());
        }

        // Find shortest path using BFS
        Queue<PathNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.offer(new PathNode(from, new ArrayList<>()));
        visited.add(from.getKey());

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();

            // Explore all next versions reachable from the current version
            for (int nextVersion = current.version.version() + 1;
                 nextVersion <= to.version();
                 nextVersion++) {

                Optional<SchemaVersion> next = getVersion(from.name(), nextVersion);
                if (next.isEmpty()) continue;

                Optional<SchemaMigration<?, ?>> migration = getMigration(current.version, next.get());
                if (migration.isEmpty()) continue;

                List<SchemaMigration<?, ?>> newPath = new ArrayList<>(current.path);
                newPath.add(migration.get());

                if (next.get().equals(to)) {
                    return Optional.of(newPath);
                }

                if (visited.add(next.get().getKey())) {
                    queue.offer(new PathNode(next.get(), newPath));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if a specific version is registered.
     *
     * @param schemaName the schema name
     * @param version    the version
     * @return true if registered
     */
    public boolean isVersionRegistered(String schemaName, int version) {
        return getVersion(schemaName, version).isPresent();
    }

    /**
     * Retrieves all registered schema names.
     *
     * @return set of schema names
     */
    public Set<String> getAllSchemaNames() {
        return new HashSet<>(schemas.keySet());
    }

    /**
     * Generates a migration key.
     */
    private String getMigrationKey(SchemaVersion from, SchemaVersion to) {
        return from.getKey() + "->" + to.getKey();
    }

    /**
     * Node class for BFS traversal.
     */
    private static class PathNode {
        final SchemaVersion version;
        final List<SchemaMigration<?, ?>> path;

        PathNode(SchemaVersion version, List<SchemaMigration<?, ?>> path) {
            this.version = version;
            this.path = path;
        }
    }
}
