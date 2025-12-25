package de.bananaco.bpermissions.imp.storage;

import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.imp.Config;
import de.bananaco.bpermissions.imp.DatabaseWorld;
import de.bananaco.bpermissions.imp.Permissions;
import de.bananaco.bpermissions.imp.YamlWorld;
import de.bananaco.bpermissions.util.Debugger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating World instances based on the configured storage backend.
 * <p>
 * This factory creates the appropriate World implementation (YamlWorld, DatabaseWorld)
 * based on the storage backend configuration (yaml, mongodb, mysql).
 * </p>
 */
public class WorldFactory {

    private final Permissions permissions;
    private final Config config;
    private final Map<String, StorageBackend> sharedBackends;

    /**
     * Create a new WorldFactory.
     *
     * @param permissions The Permissions plugin instance
     * @param config      The plugin configuration
     */
    public WorldFactory(Permissions permissions, Config config) {
        this.permissions = permissions;
        this.config = config;
        this.sharedBackends = new HashMap<>();
    }

    /**
     * Create a World instance for the specified world name.
     * <p>
     * The type of World created depends on the storage backend configuration.
     * </p>
     *
     * @param worldName The world name
     * @return World instance
     * @throws StorageException if world creation fails
     */
    public World createWorld(String worldName) throws StorageException {
        String backend = config.getStorageBackend();

        Debugger.log("[WorldFactory] Creating world " + worldName + " with backend: " + backend);

        switch (backend.toLowerCase()) {
            case "yaml":
                return createYamlWorld(worldName);

            case "mongodb":
                return createMongoWorld(worldName);

            case "mysql":
                return createMySQLWorld(worldName);

            default:
                throw new IllegalArgumentException("Unknown storage backend: " + backend);
        }
    }

    /**
     * Create a YAML-based world.
     */
    private World createYamlWorld(String worldName) {
        File root = new File("plugins/bPermissions/" + worldName);
        return new YamlWorld(worldName, permissions, root);
    }

    /**
     * Create a MongoDB-based world.
     */
    private World createMongoWorld(String worldName) throws StorageException {
        // Reuse the same MongoDB backend for all worlds (shared connection pool)
        StorageBackend backend = sharedBackends.get("mongodb");
        if (backend == null) {
            backend = new MongoBackend();
            backend.initialize(config.getMongoConfig());
            sharedBackends.put("mongodb", backend);
            Debugger.log("[WorldFactory] Initialized shared MongoDB backend");
        }

        return new DatabaseWorld(worldName, permissions, backend, config.getPollInterval());
    }

    /**
     * Create a MySQL-based world.
     */
    private World createMySQLWorld(String worldName) throws StorageException {
        // Reuse the same MySQL backend for all worlds (shared connection pool)
        StorageBackend backend = sharedBackends.get("mysql");
        if (backend == null) {
            backend = new MySQLBackend();
            backend.initialize(config.getMysqlConfig());
            sharedBackends.put("mysql", backend);
            Debugger.log("[WorldFactory] Initialized shared MySQL backend");
        }

        return new DatabaseWorld(worldName, permissions, backend, config.getPollInterval());
    }

    /**
     * Shutdown all shared storage backends.
     * <p>
     * This should be called when the plugin is disabled to properly close
     * database connections.
     * </p>
     */
    public void shutdown() {
        for (Map.Entry<String, StorageBackend> entry : sharedBackends.entrySet()) {
            try {
                Debugger.log("[WorldFactory] Shutting down " + entry.getKey() + " backend");
                entry.getValue().shutdown();
            } catch (StorageException e) {
                Debugger.log("[WorldFactory] Error shutting down " + entry.getKey() + " backend: " + e.getMessage());
            }
        }
        sharedBackends.clear();
    }
}
