package de.bananaco.bpermissions.imp.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;
import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;
import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import de.bananaco.bpermissions.util.Debugger;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB implementation of the StorageBackend interface.
 * <p>
 * This backend stores permissions data in MongoDB collections with the following schema:
 * <ul>
 *   <li>permissions_users - User permissions and group memberships</li>
 *   <li>permissions_groups - Group definitions and inherited groups</li>
 *   <li>permissions_worlds - World metadata (default groups, settings)</li>
 *   <li>permissions_changelog - Change tracking for multi-server sync</li>
 * </ul>
 * </p>
 */
public class MongoBackend implements StorageBackend {

    private static final String COLLECTION_USERS = "permissions_users";
    private static final String COLLECTION_GROUPS = "permissions_groups";
    private static final String COLLECTION_WORLDS = "permissions_worlds";
    private static final String COLLECTION_CHANGELOG = "permissions_changelog";

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> groupsCollection;
    private MongoCollection<Document> worldsCollection;
    private MongoCollection<Document> changelogCollection;

    private String serverId;  // Unique ID for this server instance

    // Connection configuration (stored for reconnection)
    private String connectionString;
    private String databaseName;
    private int maxReconnectAttempts = 3;
    private long reconnectDelayMs = 5000; // 5 seconds

    @Override
    public void initialize(Map<String, Object> config) throws StorageException {
        try {
            // Extract and store configuration for reconnection
            this.connectionString = (String) config.get("connection-string");
            if (this.connectionString == null) {
                throw new StorageException("MongoDB connection-string not provided in configuration");
            }

            this.databaseName = (String) config.getOrDefault("database", "bpermissions");
            this.serverId = (String) config.getOrDefault("server-id", UUID.randomUUID().toString());

            // Optional reconnection configuration
            if (config.containsKey("max-reconnect-attempts")) {
                this.maxReconnectAttempts = (Integer) config.get("max-reconnect-attempts");
            }
            if (config.containsKey("reconnect-delay-ms")) {
                this.reconnectDelayMs = ((Number) config.get("reconnect-delay-ms")).longValue();
            }

            Debugger.log("[MongoBackend] Initializing with database: " + databaseName + ", server-id: " + serverId);

            // Create MongoClient with connection pooling
            mongoClient = MongoClients.create(createMongoClientSettings());
            database = mongoClient.getDatabase(databaseName);

            // Get collections
            usersCollection = database.getCollection(COLLECTION_USERS);
            groupsCollection = database.getCollection(COLLECTION_GROUPS);
            worldsCollection = database.getCollection(COLLECTION_WORLDS);
            changelogCollection = database.getCollection(COLLECTION_CHANGELOG);

            // Create indexes for performance
            createIndexes();

            Debugger.log("[MongoBackend] Successfully initialized MongoDB backend");

        } catch (Exception e) {
            throw new StorageException.ConnectionFailedException("Failed to initialize MongoDB backend", e);
        }
    }

    /**
     * Create MongoClient settings for MongoDB connection pool.
     * <p>
     * This method is extracted to allow reuse during reconnection.
     * </p>
     *
     * @return Configured MongoClientSettings instance
     */
    private MongoClientSettings createMongoClientSettings() {
        ConnectionString connString = new ConnectionString(connectionString);
        return MongoClientSettings.builder()
                .applyConnectionString(connString)
                .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(20)  // Max connections for 6+ servers
                                .minSize(5)  // Min idle connections
                                .maxWaitTime(10, TimeUnit.SECONDS)
                                .maxConnectionLifeTime(30, TimeUnit.MINUTES)
                                .maxConnectionIdleTime(10, TimeUnit.MINUTES))
                .build();
    }

    /**
     * Attempt to reconnect to MongoDB.
     * <p>
     * This method closes the existing MongoClient and creates a new one.
     * Used for automatic recovery when MongoDB connection is lost.
     * </p>
     *
     * @return true if reconnection succeeded, false otherwise
     */
    private synchronized boolean reconnect() {
        try {
            Debugger.log("[MongoBackend] Attempting to reconnect to MongoDB...");

            // Close old client
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    Debugger.log("[MongoBackend] Error closing old MongoClient: " + e.getMessage());
                }
            }

            // Wait before reconnecting to avoid connection storms
            Thread.sleep(reconnectDelayMs);

            // Create new client and reinitialize collections
            mongoClient = MongoClients.create(createMongoClientSettings());
            database = mongoClient.getDatabase(databaseName);

            usersCollection = database.getCollection(COLLECTION_USERS);
            groupsCollection = database.getCollection(COLLECTION_GROUPS);
            worldsCollection = database.getCollection(COLLECTION_WORLDS);
            changelogCollection = database.getCollection(COLLECTION_CHANGELOG);

            // Verify connection with ping
            database.runCommand(new Document("ping", 1));

            Debugger.log("[MongoBackend] Successfully reconnected to MongoDB");
            return true;

        } catch (Exception e) {
            Debugger.log("[MongoBackend] Reconnection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a MongoException is a connection-related error that should trigger reconnection.
     * <p>
     * Connection errors include socket exceptions, timeout exceptions, and specific error codes.
     * </p>
     *
     * @param e The MongoException to check
     * @return true if this is a connection error, false otherwise
     */
    private boolean shouldRetryOnError(MongoException e) {
        // Socket and timeout exceptions are always connection errors
        if (e instanceof MongoSocketException || e instanceof MongoTimeoutException) {
            return true;
        }

        // Check for specific error codes related to connection issues
        // Error code 89: Network timeout
        // Error code 6: Host unreachable
        // Error code 7: Host not found
        // Error code 91: Shutdown in progress
        int errorCode = e.getCode();
        return errorCode == 89 || errorCode == 6 || errorCode == 7 || errorCode == 91;
    }

    /**
     * Execute a MongoDB operation with automatic retry on connection failure.
     * <p>
     * This method wraps MongoDB operations and automatically retries once
     * after attempting to reconnect if a connection error is detected.
     * </p>
     *
     * @param operation The MongoDB operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws StorageException if the operation fails (even after retry)
     */
    private <T> T executeWithRetry(MongoOperation<T> operation) throws StorageException {
        for (int attempt = 0; attempt <= 1; attempt++) {
            try {
                return operation.execute();
            } catch (MongoException e) {
                // Only retry on first attempt and if it's a connection error
                if (attempt == 0 && shouldRetryOnError(e)) {
                    Debugger.log("[MongoBackend] Connection error detected, attempting reconnection...");
                    if (reconnect()) {
                        Debugger.log("[MongoBackend] Reconnected, retrying operation...");
                        continue; // Retry the operation
                    } else {
                        Debugger.log("[MongoBackend] Reconnection failed");
                    }
                }
                // Either not a connection error, or retry failed
                throw new StorageException.ConnectionFailedException("MongoDB operation failed", e);
            }
        }
        // Should never reach here, but satisfy compiler
        throw new StorageException("MongoDB operation failed after retry");
    }

    /**
     * Functional interface for MongoDB operations that can be retried.
     */
    @FunctionalInterface
    private interface MongoOperation<T> {
        T execute() throws MongoException;
    }

    @Override
    public void shutdown() throws StorageException {
        try {
            if (mongoClient != null) {
                mongoClient.close();
                Debugger.log("[MongoBackend] Closed MongoDB connection");
            }
        } catch (Exception e) {
            throw new StorageException("Failed to shutdown MongoDB backend", e);
        }
    }

    @Override
    public boolean isConnected() {
        try {
            if (mongoClient == null) {
                return false;
            }
            // Test connection by running a ping command
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create indexes for performance optimization.
     */
    private void createIndexes() {
        try {
            // Users collection indexes
            usersCollection.createIndex(Indexes.ascending("uuid", "world"), new IndexOptions().unique(true));
            usersCollection.createIndex(Indexes.descending("lastModified"));

            // Groups collection indexes
            groupsCollection.createIndex(Indexes.ascending("name", "world"), new IndexOptions().unique(true));
            groupsCollection.createIndex(Indexes.descending("lastModified"));

            // Worlds collection index
            worldsCollection.createIndex(Indexes.ascending("worldName"), new IndexOptions().unique(true));

            // Changelog collection indexes
            changelogCollection.createIndex(Indexes.descending("timestamp"));
            changelogCollection.createIndex(new Document("world", 1).append("timestamp", -1));

            Debugger.log("[MongoBackend] Created indexes");
        } catch (Exception e) {
            Debugger.log("[MongoBackend] Warning: Failed to create indexes: " + e.getMessage());
        }
    }

    // ========== User Operations ==========

    @Override
    public UserData loadUser(String uuid, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            Document doc = usersCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToUserData(doc);
        });
    }

    @Override
    public void saveUser(UserData userData, String worldName) throws StorageException {
        userData.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            Document doc = userDataToDocument(userData, worldName);
            String id = buildUserId(userData.getUuid(), worldName);
            doc.put("_id", id);

            // Upsert (insert or update)
            Document filter = new Document("_id", id);
            ReplaceOptions options = new ReplaceOptions().upsert(true);

            usersCollection.replaceOne(filter, doc, options);

            // Log change for other servers
            logChange(worldName, CalculableType.USER, userData.getUuid(), ChangeRecord.ChangeType.UPDATE);

            Debugger.log("[MongoBackend] Saved user " + userData.getUuid() + " in world " + worldName);

            return null; // Void operation
        });
    }

    @Override
    public boolean userExists(String uuid, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            return usersCollection.countDocuments(filter) > 0;
        });
    }

    @Override
    public Set<String> getAllUserIds(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Set<String> uuids = new HashSet<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = usersCollection.find(filter).projection(Projections.include("uuid"));

            for (Document doc : results) {
                String uuid = doc.getString("uuid");
                if (uuid != null) {
                    uuids.add(uuid);
                }
            }

            return uuids;
        });
    }

    @Override
    public void deleteUser(String uuid, String worldName) throws StorageException {
        executeWithRetry(() -> {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            usersCollection.deleteOne(filter);

            // Log change
            logChange(worldName, CalculableType.USER, uuid, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MongoBackend] Deleted user " + uuid + " from world " + worldName);

            return null; // Void operation
        });
    }

    // ========== Group Operations ==========

    @Override
    public GroupData loadGroup(String groupName, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            Document doc = groupsCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToGroupData(doc);
        });
    }

    @Override
    public void saveGroup(GroupData groupData, String worldName) throws StorageException {
        groupData.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            Document doc = groupDataToDocument(groupData, worldName);
            String id = buildGroupId(groupData.getName(), worldName);
            doc.put("_id", id);

            // Upsert (insert or update)
            Document filter = new Document("_id", id);
            ReplaceOptions options = new ReplaceOptions().upsert(true);

            groupsCollection.replaceOne(filter, doc, options);

            // Log change for other servers
            logChange(worldName, CalculableType.GROUP, groupData.getName(), ChangeRecord.ChangeType.UPDATE);

            Debugger.log("[MongoBackend] Saved group " + groupData.getName() + " in world " + worldName);

            return null; // Void operation
        });
    }

    @Override
    public boolean groupExists(String groupName, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            return groupsCollection.countDocuments(filter) > 0;
        });
    }

    @Override
    public Set<String> getAllGroupNames(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Set<String> groupNames = new HashSet<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = groupsCollection.find(filter).projection(Projections.include("name"));

            for (Document doc : results) {
                String name = doc.getString("name");
                if (name != null) {
                    groupNames.add(name);
                }
            }

            return groupNames;
        });
    }

    @Override
    public void deleteGroup(String groupName, String worldName) throws StorageException {
        executeWithRetry(() -> {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            groupsCollection.deleteOne(filter);

            // Log change
            logChange(worldName, CalculableType.GROUP, groupName, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MongoBackend] Deleted group " + groupName + " from world " + worldName);

            return null; // Void operation
        });
    }

    // ========== World Metadata Operations ==========

    @Override
    public WorldMetadata loadWorldMetadata(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("_id", worldName);
            Document doc = worldsCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToWorldMetadata(doc);
        });
    }

    @Override
    public void saveWorldMetadata(WorldMetadata metadata, String worldName) throws StorageException {
        metadata.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            Document doc = worldMetadataToDocument(metadata);
            doc.put("_id", worldName);

            // Upsert
            Document filter = new Document("_id", worldName);
            ReplaceOptions options = new ReplaceOptions().upsert(true);

            worldsCollection.replaceOne(filter, doc, options);

            Debugger.log("[MongoBackend] Saved world metadata for " + worldName);

            return null; // Void operation
        });
    }

    // ========== Change Detection ==========

    @Override
    public Set<ChangeRecord> getChangesSince(long timestamp, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Set<ChangeRecord> changes = new HashSet<>();

            // Query: world matches, timestamp > given timestamp, serverSource != this server
            Document filter = new Document("world", worldName)
                    .append("timestamp", new Document("$gt", timestamp))
                    .append("serverSource", new Document("$ne", serverId));

            FindIterable<Document> results = changelogCollection.find(filter)
                    .sort(Sorts.ascending("timestamp"));

            for (Document doc : results) {
                changes.add(documentToChangeRecord(doc));
            }

            return changes;
        });
    }

    @Override
    public long getLastModifiedTimestamp(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Document filter = new Document("world", worldName);
            Document result = changelogCollection.find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(1)
                    .first();

            if (result == null) {
                return 0L;
            }

            return result.getLong("timestamp");
        });
    }

    // ========== Batch Operations ==========

    @Override
    public Map<String, UserData> loadAllUsers(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Map<String, UserData> users = new HashMap<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = usersCollection.find(filter);

            for (Document doc : results) {
                UserData userData = documentToUserData(doc);
                users.put(userData.getUuid(), userData);
            }

            Debugger.log("[MongoBackend] Loaded " + users.size() + " users for world " + worldName);
            return users;
        });
    }

    @Override
    public Map<String, GroupData> loadAllGroups(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            Map<String, GroupData> groups = new HashMap<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = groupsCollection.find(filter);

            for (Document doc : results) {
                GroupData groupData = documentToGroupData(doc);
                groups.put(groupData.getName(), groupData);
            }

            Debugger.log("[MongoBackend] Loaded " + groups.size() + " groups for world " + worldName);
            return groups;
        });
    }

    // ========== Helper Methods ==========

    /**
     * Build a unique user ID for MongoDB _id field.
     */
    private String buildUserId(String uuid, String worldName) {
        return uuid + "_" + worldName;
    }

    /**
     * Build a unique group ID for MongoDB _id field.
     */
    private String buildGroupId(String groupName, String worldName) {
        return groupName + "_" + worldName;
    }

    /**
     * Log a change to the changelog collection.
     */
    private void logChange(String worldName, CalculableType type, String name, ChangeRecord.ChangeType changeType) {
        try {
            Document changeDoc = new Document()
                    .append("world", worldName)
                    .append("calculableType", type.name())
                    .append("calculableName", name)
                    .append("changeType", changeType.name())
                    .append("timestamp", System.currentTimeMillis())
                    .append("serverSource", serverId);

            changelogCollection.insertOne(changeDoc);

        } catch (Exception e) {
            Debugger.log("[MongoBackend] Warning: Failed to log change: " + e.getMessage());
        }
    }

    // ========== Conversion Methods: Document <-> DTO ==========

    private UserData documentToUserData(Document doc) {
        String uuid = doc.getString("uuid");
        String username = doc.getString("username");

        @SuppressWarnings("unchecked")
        List<String> permissionsList = (List<String>) doc.get("permissions", List.class);
        Set<String> permissions = permissionsList != null ? new HashSet<>(permissionsList) : new HashSet<>();

        @SuppressWarnings("unchecked")
        List<String> groupsList = (List<String>) doc.get("groups", List.class);
        Set<String> groups = groupsList != null ? new HashSet<>(groupsList) : new HashSet<>();

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) doc.get("metadata", Map.class);
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        long lastModified = doc.getLong("lastModified");

        return new UserData(uuid, username, permissions, groups, metadata, lastModified);
    }

    private Document userDataToDocument(UserData userData, String worldName) {
        return new Document()
                .append("uuid", userData.getUuid())
                .append("username", userData.getUsername())
                .append("world", worldName)
                .append("permissions", new ArrayList<>(userData.getPermissions()))
                .append("groups", new ArrayList<>(userData.getGroups()))
                .append("metadata", userData.getMetadata())
                .append("lastModified", userData.getLastModified());
    }

    private GroupData documentToGroupData(Document doc) {
        String name = doc.getString("name");

        @SuppressWarnings("unchecked")
        List<String> permissionsList = (List<String>) doc.get("permissions", List.class);
        Set<String> permissions = permissionsList != null ? new HashSet<>(permissionsList) : new HashSet<>();

        @SuppressWarnings("unchecked")
        List<String> groupsList = (List<String>) doc.get("groups", List.class);
        Set<String> groups = groupsList != null ? new HashSet<>(groupsList) : new HashSet<>();

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) doc.get("metadata", Map.class);
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        long lastModified = doc.getLong("lastModified");

        return new GroupData(name, permissions, groups, metadata, lastModified);
    }

    private Document groupDataToDocument(GroupData groupData, String worldName) {
        return new Document()
                .append("name", groupData.getName())
                .append("world", worldName)
                .append("permissions", new ArrayList<>(groupData.getPermissions()))
                .append("groups", new ArrayList<>(groupData.getGroups()))
                .append("metadata", groupData.getMetadata())
                .append("lastModified", groupData.getLastModified());
    }

    private WorldMetadata documentToWorldMetadata(Document doc) {
        String worldName = doc.getString("worldName");
        String defaultGroup = doc.getString("defaultGroup");
        long lastModified = doc.getLong("lastModified");

        @SuppressWarnings("unchecked")
        Map<String, Object> customSettings = (Map<String, Object>) doc.get("customSettings", Map.class);
        if (customSettings == null) {
            customSettings = new HashMap<>();
        }

        return new WorldMetadata(worldName, defaultGroup, lastModified, customSettings);
    }

    private Document worldMetadataToDocument(WorldMetadata metadata) {
        return new Document()
                .append("worldName", metadata.getWorldName())
                .append("defaultGroup", metadata.getDefaultGroup())
                .append("lastModified", metadata.getLastModified())
                .append("customSettings", metadata.getCustomSettings());
    }

    private ChangeRecord documentToChangeRecord(Document doc) {
        String worldName = doc.getString("world");
        String calculableTypeStr = doc.getString("calculableType");
        String calculableName = doc.getString("calculableName");
        String changeTypeStr = doc.getString("changeType");
        long timestamp = doc.getLong("timestamp");
        String serverSource = doc.getString("serverSource");

        CalculableType calculableType = CalculableType.valueOf(calculableTypeStr);
        ChangeRecord.ChangeType changeType = ChangeRecord.ChangeType.valueOf(changeTypeStr);

        return new ChangeRecord(worldName, calculableType, calculableName, changeType, timestamp, serverSource);
    }
}
