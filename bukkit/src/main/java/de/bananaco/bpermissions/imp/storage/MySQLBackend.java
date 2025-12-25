package de.bananaco.bpermissions.imp.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;
import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import de.bananaco.bpermissions.util.Debugger;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;

/**
 * MySQL implementation of the StorageBackend interface.
 * <p>
 * This backend stores permissions data in MySQL/MariaDB tables with the following schema:
 * <ul>
 *   <li>permissions_users - User permissions and group memberships</li>
 *   <li>permissions_groups - Group definitions and inherited groups</li>
 *   <li>permissions_worlds - World metadata (default groups, settings)</li>
 *   <li>permissions_changelog - Change tracking for multi-server sync</li>
 * </ul>
 * </p>
 * <p>
 * Uses HikariCP for connection pooling and Gson for JSON serialization.
 * </p>
 */
public class MySQLBackend implements StorageBackend {

    private HikariDataSource dataSource;
    private final Gson gson = new Gson();
    private String serverId;

    // Type tokens for Gson deserialization
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type MAP_STRING_OBJECT_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
    public void initialize(Map<String, Object> config) throws StorageException {
        try {
            // Extract configuration
            String host = (String) config.get("host");
            int port = (Integer) config.getOrDefault("port", 3306);
            String database = (String) config.get("database");
            String username = (String) config.get("username");
            String password = (String) config.get("password");
            this.serverId = (String) config.getOrDefault("server-id", UUID.randomUUID().toString());

            if (host == null || database == null || username == null || password == null) {
                throw new StorageException("MySQL configuration incomplete. Required: host, database, username, password");
            }

            Debugger.log("[MySQLBackend] Initializing with database: " + database + ", server-id: " + serverId);

            // Configure HikariCP connection pool
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, database));
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(20);  // Max connections for 6+ servers
            hikariConfig.setMinimumIdle(5);       // Min idle connections
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setIdleTimeout(600000);  // 10 minutes
            hikariConfig.setMaxLifetime(1800000); // 30 minutes

            // Performance optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(hikariConfig);

            // Create tables if they don't exist
            createTables();

            Debugger.log("[MySQLBackend] Successfully initialized MySQL backend");

        } catch (Exception e) {
            throw new StorageException.ConnectionFailedException("Failed to initialize MySQL backend", e);
        }
    }

    @Override
    public void shutdown() throws StorageException {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                Debugger.log("[MySQLBackend] Closed MySQL connection pool");
            }
        } catch (Exception e) {
            throw new StorageException("Failed to shutdown MySQL backend", e);
        }
    }

    @Override
    public boolean isConnected() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                return false;
            }
            try (Connection conn = dataSource.getConnection()) {
                return conn.isValid(2);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Users table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_users (" +
                            "id VARCHAR(100) PRIMARY KEY, " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "username VARCHAR(16), " +
                            "world VARCHAR(50) NOT NULL, " +
                            "permissions TEXT, " +
                            "groups TEXT, " +
                            "metadata TEXT, " +
                            "last_modified BIGINT NOT NULL, " +
                            "INDEX idx_uuid_world (uuid, world), " +
                            "INDEX idx_last_modified (last_modified)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Groups table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_groups (" +
                            "id VARCHAR(100) PRIMARY KEY, " +
                            "name VARCHAR(50) NOT NULL, " +
                            "world VARCHAR(50) NOT NULL, " +
                            "permissions TEXT, " +
                            "groups TEXT, " +
                            "metadata TEXT, " +
                            "last_modified BIGINT NOT NULL, " +
                            "INDEX idx_name_world (name, world), " +
                            "INDEX idx_last_modified (last_modified)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Worlds table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_worlds (" +
                            "world VARCHAR(50) PRIMARY KEY, " +
                            "default_group VARCHAR(50) NOT NULL, " +
                            "last_modified BIGINT NOT NULL, " +
                            "custom_settings TEXT" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Changelog table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_changelog (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "world VARCHAR(50) NOT NULL, " +
                            "calculable_type ENUM('USER', 'GROUP') NOT NULL, " +
                            "calculable_name VARCHAR(100) NOT NULL, " +
                            "change_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL, " +
                            "timestamp BIGINT NOT NULL, " +
                            "server_source VARCHAR(100) NOT NULL, " +
                            "INDEX idx_world_timestamp (world, timestamp), " +
                            "INDEX idx_timestamp (timestamp)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            Debugger.log("[MySQLBackend] Created/verified database tables");
        }
    }

    // ========== User Operations ==========

    @Override
    public UserData loadUser(String uuid, String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_users WHERE uuid = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            return resultSetToUserData(rs);

        } catch (SQLException e) {
            throw new StorageException("Failed to load user " + uuid + " in world " + worldName, e);
        }
    }

    @Override
    public void saveUser(UserData userData, String worldName) throws StorageException {
        userData.setLastModified(System.currentTimeMillis());

        String sql = "INSERT INTO permissions_users (id, uuid, username, world, permissions, groups, metadata, last_modified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "username = VALUES(username), " +
                "permissions = VALUES(permissions), " +
                "groups = VALUES(groups), " +
                "metadata = VALUES(metadata), " +
                "last_modified = VALUES(last_modified)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String id = buildUserId(userData.getUuid(), worldName);
            stmt.setString(1, id);
            stmt.setString(2, userData.getUuid());
            stmt.setString(3, userData.getUsername());
            stmt.setString(4, worldName);
            stmt.setString(5, gson.toJson(new ArrayList<>(userData.getPermissions())));
            stmt.setString(6, gson.toJson(new ArrayList<>(userData.getGroups())));
            stmt.setString(7, gson.toJson(userData.getMetadata()));
            stmt.setLong(8, userData.getLastModified());

            stmt.executeUpdate();

            // Log change
            logChange(worldName, CalculableType.USER, userData.getUuid(), ChangeRecord.ChangeType.UPDATE);

            Debugger.log("[MySQLBackend] Saved user " + userData.getUuid() + " in world " + worldName);

        } catch (SQLException e) {
            throw new StorageException("Failed to save user " + userData.getUuid() + " in world " + worldName, e);
        }
    }

    @Override
    public boolean userExists(String uuid, String worldName) throws StorageException {
        String sql = "SELECT COUNT(*) FROM permissions_users WHERE uuid = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to check if user exists: " + uuid, e);
        }
    }

    @Override
    public Set<String> getAllUserIds(String worldName) throws StorageException {
        String sql = "SELECT uuid FROM permissions_users WHERE world = ?";
        Set<String> uuids = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                uuids.add(rs.getString("uuid"));
            }

            return uuids;

        } catch (SQLException e) {
            throw new StorageException("Failed to get all user IDs for world " + worldName, e);
        }
    }

    @Override
    public void deleteUser(String uuid, String worldName) throws StorageException {
        String sql = "DELETE FROM permissions_users WHERE uuid = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid);
            stmt.setString(2, worldName);
            stmt.executeUpdate();

            // Log change
            logChange(worldName, CalculableType.USER, uuid, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MySQLBackend] Deleted user " + uuid + " from world " + worldName);

        } catch (SQLException e) {
            throw new StorageException("Failed to delete user " + uuid + " from world " + worldName, e);
        }
    }

    // ========== Group Operations ==========

    @Override
    public GroupData loadGroup(String groupName, String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_groups WHERE name = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupName);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            return resultSetToGroupData(rs);

        } catch (SQLException e) {
            throw new StorageException("Failed to load group " + groupName + " in world " + worldName, e);
        }
    }

    @Override
    public void saveGroup(GroupData groupData, String worldName) throws StorageException {
        groupData.setLastModified(System.currentTimeMillis());

        String sql = "INSERT INTO permissions_groups (id, name, world, permissions, groups, metadata, last_modified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "permissions = VALUES(permissions), " +
                "groups = VALUES(groups), " +
                "metadata = VALUES(metadata), " +
                "last_modified = VALUES(last_modified)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String id = buildGroupId(groupData.getName(), worldName);
            stmt.setString(1, id);
            stmt.setString(2, groupData.getName());
            stmt.setString(3, worldName);
            stmt.setString(4, gson.toJson(new ArrayList<>(groupData.getPermissions())));
            stmt.setString(5, gson.toJson(new ArrayList<>(groupData.getGroups())));
            stmt.setString(6, gson.toJson(groupData.getMetadata()));
            stmt.setLong(7, groupData.getLastModified());

            stmt.executeUpdate();

            // Log change
            logChange(worldName, CalculableType.GROUP, groupData.getName(), ChangeRecord.ChangeType.UPDATE);

            Debugger.log("[MySQLBackend] Saved group " + groupData.getName() + " in world " + worldName);

        } catch (SQLException e) {
            throw new StorageException("Failed to save group " + groupData.getName() + " in world " + worldName, e);
        }
    }

    @Override
    public boolean groupExists(String groupName, String worldName) throws StorageException {
        String sql = "SELECT COUNT(*) FROM permissions_groups WHERE name = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupName);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to check if group exists: " + groupName, e);
        }
    }

    @Override
    public Set<String> getAllGroupNames(String worldName) throws StorageException {
        String sql = "SELECT name FROM permissions_groups WHERE world = ?";
        Set<String> groupNames = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                groupNames.add(rs.getString("name"));
            }

            return groupNames;

        } catch (SQLException e) {
            throw new StorageException("Failed to get all group names for world " + worldName, e);
        }
    }

    @Override
    public void deleteGroup(String groupName, String worldName) throws StorageException {
        String sql = "DELETE FROM permissions_groups WHERE name = ? AND world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupName);
            stmt.setString(2, worldName);
            stmt.executeUpdate();

            // Log change
            logChange(worldName, CalculableType.GROUP, groupName, ChangeRecord.ChangeType.DELETE);

            Debugger.log("[MySQLBackend] Deleted group " + groupName + " from world " + worldName);

        } catch (SQLException e) {
            throw new StorageException("Failed to delete group " + groupName + " from world " + worldName, e);
        }
    }

    // ========== World Metadata Operations ==========

    @Override
    public WorldMetadata loadWorldMetadata(String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_worlds WHERE world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            return resultSetToWorldMetadata(rs);

        } catch (SQLException e) {
            throw new StorageException("Failed to load world metadata for " + worldName, e);
        }
    }

    @Override
    public void saveWorldMetadata(WorldMetadata metadata, String worldName) throws StorageException {
        metadata.setLastModified(System.currentTimeMillis());

        String sql = "INSERT INTO permissions_worlds (world, default_group, last_modified, custom_settings) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "default_group = VALUES(default_group), " +
                "last_modified = VALUES(last_modified), " +
                "custom_settings = VALUES(custom_settings)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            stmt.setString(2, metadata.getDefaultGroup());
            stmt.setLong(3, metadata.getLastModified());
            stmt.setString(4, gson.toJson(metadata.getCustomSettings()));

            stmt.executeUpdate();

            Debugger.log("[MySQLBackend] Saved world metadata for " + worldName);

        } catch (SQLException e) {
            throw new StorageException("Failed to save world metadata for " + worldName, e);
        }
    }

    // ========== Change Detection ==========

    @Override
    public Set<ChangeRecord> getChangesSince(long timestamp, String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_changelog " +
                "WHERE world = ? AND timestamp > ? AND server_source != ? " +
                "ORDER BY timestamp ASC";

        Set<ChangeRecord> changes = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            stmt.setLong(2, timestamp);
            stmt.setString(3, serverId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                changes.add(resultSetToChangeRecord(rs));
            }

            return changes;

        } catch (SQLException e) {
            throw new StorageException("Failed to get changes since " + timestamp + " for world " + worldName, e);
        }
    }

    @Override
    public long getLastModifiedTimestamp(String worldName) throws StorageException {
        String sql = "SELECT MAX(timestamp) FROM permissions_changelog WHERE world = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;

        } catch (SQLException e) {
            throw new StorageException("Failed to get last modified timestamp for world " + worldName, e);
        }
    }

    // ========== Batch Operations ==========

    @Override
    public Map<String, UserData> loadAllUsers(String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_users WHERE world = ?";
        Map<String, UserData> users = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UserData userData = resultSetToUserData(rs);
                users.put(userData.getUuid(), userData);
            }

            Debugger.log("[MySQLBackend] Loaded " + users.size() + " users for world " + worldName);
            return users;

        } catch (SQLException e) {
            throw new StorageException("Failed to load all users for world " + worldName, e);
        }
    }

    @Override
    public Map<String, GroupData> loadAllGroups(String worldName) throws StorageException {
        String sql = "SELECT * FROM permissions_groups WHERE world = ?";
        Map<String, GroupData> groups = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                GroupData groupData = resultSetToGroupData(rs);
                groups.put(groupData.getName(), groupData);
            }

            Debugger.log("[MySQLBackend] Loaded " + groups.size() + " groups for world " + worldName);
            return groups;

        } catch (SQLException e) {
            throw new StorageException("Failed to load all groups for world " + worldName, e);
        }
    }

    // ========== Helper Methods ==========

    private String buildUserId(String uuid, String worldName) {
        return uuid + "_" + worldName;
    }

    private String buildGroupId(String groupName, String worldName) {
        return groupName + "_" + worldName;
    }

    private void logChange(String worldName, CalculableType type, String name, ChangeRecord.ChangeType changeType) {
        String sql = "INSERT INTO permissions_changelog (world, calculable_type, calculable_name, change_type, timestamp, server_source) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, worldName);
            stmt.setString(2, type.name());
            stmt.setString(3, name);
            stmt.setString(4, changeType.name());
            stmt.setLong(5, System.currentTimeMillis());
            stmt.setString(6, serverId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            Debugger.log("[MySQLBackend] Warning: Failed to log change: " + e.getMessage());
        }
    }

    // ========== Conversion Methods: ResultSet <-> DTO ==========

    private UserData resultSetToUserData(ResultSet rs) throws SQLException {
        String uuid = rs.getString("uuid");
        String username = rs.getString("username");
        long lastModified = rs.getLong("last_modified");

        List<String> permissionsList = gson.fromJson(rs.getString("permissions"), LIST_STRING_TYPE);
        Set<String> permissions = permissionsList != null ? new HashSet<>(permissionsList) : new HashSet<>();

        List<String> groupsList = gson.fromJson(rs.getString("groups"), LIST_STRING_TYPE);
        Set<String> groups = groupsList != null ? new HashSet<>(groupsList) : new HashSet<>();

        Map<String, String> metadata = gson.fromJson(rs.getString("metadata"), MAP_STRING_STRING_TYPE);
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        return new UserData(uuid, username, permissions, groups, metadata, lastModified);
    }

    private GroupData resultSetToGroupData(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        long lastModified = rs.getLong("last_modified");

        List<String> permissionsList = gson.fromJson(rs.getString("permissions"), LIST_STRING_TYPE);
        Set<String> permissions = permissionsList != null ? new HashSet<>(permissionsList) : new HashSet<>();

        List<String> groupsList = gson.fromJson(rs.getString("groups"), LIST_STRING_TYPE);
        Set<String> groups = groupsList != null ? new HashSet<>(groupsList) : new HashSet<>();

        Map<String, String> metadata = gson.fromJson(rs.getString("metadata"), MAP_STRING_STRING_TYPE);
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        return new GroupData(name, permissions, groups, metadata, lastModified);
    }

    private WorldMetadata resultSetToWorldMetadata(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        String defaultGroup = rs.getString("default_group");
        long lastModified = rs.getLong("last_modified");

        Map<String, Object> customSettings = gson.fromJson(rs.getString("custom_settings"), MAP_STRING_OBJECT_TYPE);
        if (customSettings == null) {
            customSettings = new HashMap<>();
        }

        return new WorldMetadata(worldName, defaultGroup, lastModified, customSettings);
    }

    private ChangeRecord resultSetToChangeRecord(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        String calculableTypeStr = rs.getString("calculable_type");
        String calculableName = rs.getString("calculable_name");
        String changeTypeStr = rs.getString("change_type");
        long timestamp = rs.getLong("timestamp");
        String serverSource = rs.getString("server_source");

        CalculableType calculableType = CalculableType.valueOf(calculableTypeStr);
        ChangeRecord.ChangeType changeType = ChangeRecord.ChangeType.valueOf(changeTypeStr);

        return new ChangeRecord(worldName, calculableType, calculableName, changeType, timestamp, serverSource);
    }
}
