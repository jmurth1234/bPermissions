package de.bananaco.bpermissions.imp.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
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

    @Override
    public void initialize(Map<String, Object> config) throws StorageException {
        try {
            // Extract configuration
            String connectionString = (String) config.get("connection-string");
            if (connectionString == null) {
                throw new StorageException("MongoDB connection-string not provided in configuration");
            }

            String databaseName = (String) config.getOrDefault("database", "bpermissions");
            this.serverId = (String) config.getOrDefault("server-id", UUID.randomUUID().toString());

            Debugger.log("[MongoBackend] Initializing with database: " + databaseName + ", server-id: " + serverId);

            // Create MongoClient with connection pooling
            ConnectionString connString = new ConnectionString(connectionString);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connString)
                    .applyToConnectionPoolSettings(builder ->
                            builder.maxSize(20)  // Max connections for 6+ servers
                                    .minSize(5)  // Min idle connections
                                    .maxWaitTime(10, TimeUnit.SECONDS)
                                    .maxConnectionLifeTime(30, TimeUnit.MINUTES)
                                    .maxConnectionIdleTime(10, TimeUnit.MINUTES))
                    .build();

            mongoClient = MongoClients.create(settings);
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
            changelogCollection.createIndex(Indexes.compound(Indexes.ascending("world"), Indexes.descending("timestamp")));

            Debugger.log("[MongoBackend] Created indexes");
        } catch (Exception e) {
            Debugger.log("[MongoBackend] Warning: Failed to create indexes: " + e.getMessage());
        }
    }

    // ========== User Operations ==========

    @Override
    public UserData loadUser(String uuid, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            Document doc = usersCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToUserData(doc);

        } catch (Exception e) {
            throw new StorageException("Failed to load user " + uuid + " in world " + worldName, e);
        }
    }

    @Override
    public void saveUser(UserData userData, String worldName) throws StorageException {
        try {
            userData.setLastModified(System.currentTimeMillis());

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

        } catch (Exception e) {
            throw new StorageException("Failed to save user " + userData.getUuid() + " in world " + worldName, e);
        }
    }

    @Override
    public boolean userExists(String uuid, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            return usersCollection.countDocuments(filter) > 0;
        } catch (Exception e) {
            throw new StorageException("Failed to check if user exists: " + uuid, e);
        }
    }

    @Override
    public Set<String> getAllUserIds(String worldName) throws StorageException {
        try {
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
        } catch (Exception e) {
            throw new StorageException("Failed to get all user IDs for world " + worldName, e);
        }
    }

    @Override
    public void deleteUser(String uuid, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildUserId(uuid, worldName));
            usersCollection.deleteOne(filter);

            // Log change
            logChange(worldName, CalculableType.USER, uuid, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MongoBackend] Deleted user " + uuid + " from world " + worldName);

        } catch (Exception e) {
            throw new StorageException("Failed to delete user " + uuid + " from world " + worldName, e);
        }
    }

    // ========== Group Operations ==========

    @Override
    public GroupData loadGroup(String groupName, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            Document doc = groupsCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToGroupData(doc);

        } catch (Exception e) {
            throw new StorageException("Failed to load group " + groupName + " in world " + worldName, e);
        }
    }

    @Override
    public void saveGroup(GroupData groupData, String worldName) throws StorageException {
        try {
            groupData.setLastModified(System.currentTimeMillis());

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

        } catch (Exception e) {
            throw new StorageException("Failed to save group " + groupData.getName() + " in world " + worldName, e);
        }
    }

    @Override
    public boolean groupExists(String groupName, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            return groupsCollection.countDocuments(filter) > 0;
        } catch (Exception e) {
            throw new StorageException("Failed to check if group exists: " + groupName, e);
        }
    }

    @Override
    public Set<String> getAllGroupNames(String worldName) throws StorageException {
        try {
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
        } catch (Exception e) {
            throw new StorageException("Failed to get all group names for world " + worldName, e);
        }
    }

    @Override
    public void deleteGroup(String groupName, String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", buildGroupId(groupName, worldName));
            groupsCollection.deleteOne(filter);

            // Log change
            logChange(worldName, CalculableType.GROUP, groupName, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MongoBackend] Deleted group " + groupName + " from world " + worldName);

        } catch (Exception e) {
            throw new StorageException("Failed to delete group " + groupName + " from world " + worldName, e);
        }
    }

    // ========== World Metadata Operations ==========

    @Override
    public WorldMetadata loadWorldMetadata(String worldName) throws StorageException {
        try {
            Document filter = new Document("_id", worldName);
            Document doc = worldsCollection.find(filter).first();

            if (doc == null) {
                return null;
            }

            return documentToWorldMetadata(doc);

        } catch (Exception e) {
            throw new StorageException("Failed to load world metadata for " + worldName, e);
        }
    }

    @Override
    public void saveWorldMetadata(WorldMetadata metadata, String worldName) throws StorageException {
        try {
            metadata.setLastModified(System.currentTimeMillis());

            Document doc = worldMetadataToDocument(metadata);
            doc.put("_id", worldName);

            // Upsert
            Document filter = new Document("_id", worldName);
            ReplaceOptions options = new ReplaceOptions().upsert(true);

            worldsCollection.replaceOne(filter, doc, options);

            Debugger.log("[MongoBackend] Saved world metadata for " + worldName);

        } catch (Exception e) {
            throw new StorageException("Failed to save world metadata for " + worldName, e);
        }
    }

    // ========== Change Detection ==========

    @Override
    public Set<ChangeRecord> getChangesSince(long timestamp, String worldName) throws StorageException {
        try {
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

        } catch (Exception e) {
            throw new StorageException("Failed to get changes since " + timestamp + " for world " + worldName, e);
        }
    }

    @Override
    public long getLastModifiedTimestamp(String worldName) throws StorageException {
        try {
            Document filter = new Document("world", worldName);
            Document result = changelogCollection.find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(1)
                    .first();

            if (result == null) {
                return 0L;
            }

            return result.getLong("timestamp");

        } catch (Exception e) {
            throw new StorageException("Failed to get last modified timestamp for world " + worldName, e);
        }
    }

    // ========== Batch Operations ==========

    @Override
    public Map<String, UserData> loadAllUsers(String worldName) throws StorageException {
        try {
            Map<String, UserData> users = new HashMap<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = usersCollection.find(filter);

            for (Document doc : results) {
                UserData userData = documentToUserData(doc);
                users.put(userData.getUuid(), userData);
            }

            Debugger.log("[MongoBackend] Loaded " + users.size() + " users for world " + worldName);
            return users;

        } catch (Exception e) {
            throw new StorageException("Failed to load all users for world " + worldName, e);
        }
    }

    @Override
    public Map<String, GroupData> loadAllGroups(String worldName) throws StorageException {
        try {
            Map<String, GroupData> groups = new HashMap<>();
            Document filter = new Document("world", worldName);
            FindIterable<Document> results = groupsCollection.find(filter);

            for (Document doc : results) {
                GroupData groupData = documentToGroupData(doc);
                groups.put(groupData.getName(), groupData);
            }

            Debugger.log("[MongoBackend] Loaded " + groups.size() + " groups for world " + worldName);
            return groups;

        } catch (Exception e) {
            throw new StorageException("Failed to load all groups for world " + worldName, e);
        }
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

    // ========== Conversion Methods: Document ↔ DTO ==========

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
