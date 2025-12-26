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

    // Connection configuration (stored for reconnection)
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private int maxReconnectAttempts = 3;
    private long reconnectDelayMs = 5000; // 5 seconds

    // Connection pool configuration
    private int maxPoolSize = 10;  // Default: suitable for single server
    private int minIdle = 2;       // Default: minimum idle connections

    // SSL/TLS configuration
    private boolean useSSL = false;           // Default: SSL disabled for backward compatibility
    private boolean requireSSL = false;       // Default: don't require SSL
    private boolean verifyServerCertificate = true;  // Default: verify cert if SSL is used

    // Transaction support - per-thread transaction connection
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    // Type tokens for Gson deserialization
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type MAP_STRING_OBJECT_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
    public void initialize(Map<String, Object> config) throws StorageException {
        try {
            // Extract and store configuration for reconnection
            this.host = (String) config.get("host");
            this.port = (Integer) config.getOrDefault("port", 3306);
            this.database = (String) config.get("database");
            this.username = (String) config.get("username");
            this.password = (String) config.get("password");
            this.serverId = (String) config.getOrDefault("server-id", UUID.randomUUID().toString());

            // Optional reconnection configuration
            if (config.containsKey("max-reconnect-attempts")) {
                this.maxReconnectAttempts = (Integer) config.get("max-reconnect-attempts");
            }
            if (config.containsKey("reconnect-delay-ms")) {
                this.reconnectDelayMs = ((Number) config.get("reconnect-delay-ms")).longValue();
            }

            // Optional connection pool configuration
            if (config.containsKey("max-pool-size")) {
                this.maxPoolSize = (Integer) config.get("max-pool-size");
            }
            if (config.containsKey("min-idle")) {
                this.minIdle = (Integer) config.get("min-idle");
            }

            // Optional SSL/TLS configuration
            if (config.containsKey("use-ssl")) {
                this.useSSL = (Boolean) config.get("use-ssl");
            }
            if (config.containsKey("require-ssl")) {
                this.requireSSL = (Boolean) config.get("require-ssl");
            }
            if (config.containsKey("verify-server-certificate")) {
                this.verifyServerCertificate = (Boolean) config.get("verify-server-certificate");
            }

            if (host == null || database == null || username == null || password == null) {
                throw new StorageException("MySQL configuration incomplete. Required: host, database, username, password");
            }

            Debugger.log("[MySQLBackend] Initializing with database: " + database + ", server-id: " + serverId);
            if (useSSL) {
                Debugger.log("[MySQLBackend] SSL/TLS enabled (require-ssl: " + requireSSL +
                        ", verify-certificate: " + verifyServerCertificate + ")");
            }

            // Create HikariCP data source
            dataSource = new HikariDataSource(createHikariConfig());

            // Create tables if they don't exist
            createTables();

            Debugger.log("[MySQLBackend] Successfully initialized MySQL backend");

        } catch (Exception e) {
            throw new StorageException.ConnectionFailedException("Failed to initialize MySQL backend", e);
        }
    }

    /**
     * Create HikariCP configuration for MySQL connection pool.
     * <p>
     * This method is extracted to allow reuse during reconnection.
     * Pool sizes are configurable via config.yml for optimal performance tuning.
     * </p>
     * <p>
     * SSL/TLS Configuration:
     * - useSSL: enables SSL/TLS encryption
     * - requireSSL: forces SSL (connection fails if SSL unavailable)
     * - verifyServerCertificate: validates server certificate against CA
     * </p>
     *
     * @return Configured HikariConfig instance
     */
    private HikariConfig createHikariConfig() {
        HikariConfig hikariConfig = new HikariConfig();

        // Build JDBC URL with SSL parameters
        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append(String.format("jdbc:mysql://%s:%d/%s", host, port, database));
        jdbcUrl.append("?serverTimezone=UTC");
        jdbcUrl.append("&useSSL=").append(useSSL);

        if (useSSL) {
            jdbcUrl.append("&requireSSL=").append(requireSSL);
            jdbcUrl.append("&verifyServerCertificate=").append(verifyServerCertificate);
        }

        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // Configurable pool sizes (defaults: max=10, min=2)
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setMinimumIdle(minIdle);

        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setIdleTimeout(600000);  // 10 minutes
        hikariConfig.setMaxLifetime(1800000); // 30 minutes

        // Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        return hikariConfig;
    }

    /**
     * Attempt to reconnect to the database.
     * <p>
     * This method closes the existing data source and creates a new one.
     * Used for automatic recovery when database connection is lost.
     * </p>
     *
     * @return true if reconnection succeeded, false otherwise
     */
    private synchronized boolean reconnect() {
        try {
            Debugger.log("[MySQLBackend] Attempting to reconnect to database...");

            // Close old data source
            if (dataSource != null && !dataSource.isClosed()) {
                try {
                    dataSource.close();
                } catch (Exception e) {
                    Debugger.log("[MySQLBackend] Error closing old data source: " + e.getMessage());
                }
            }

            // Wait before reconnecting to avoid connection storms
            Thread.sleep(reconnectDelayMs);

            // Create new data source
            dataSource = new HikariDataSource(createHikariConfig());

            // Verify connection
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    Debugger.log("[MySQLBackend] Successfully reconnected to database");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            Debugger.log("[MySQLBackend] Reconnection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a SQLException is a connection-related error that should trigger reconnection.
     * <p>
     * Connection errors typically have SQL state codes starting with '08'.
     * See: https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-error-sqlstates.html
     * </p>
     *
     * @param e The SQLException to check
     * @return true if this is a connection error, false otherwise
     */
    private boolean shouldRetryOnError(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState == null) {
            return false;
        }

        // SQL state 08xxx indicates connection exceptions
        // 08000: connection_exception
        // 08001: SQL client unable to establish SQL connection
        // 08003: connection does not exist
        // 08004: SQL server rejected establishment of SQL connection
        // 08006: connection failure
        // 08007: transaction resolution unknown
        return sqlState.startsWith("08");
    }

    /**
     * Execute a database operation with automatic retry on connection failure.
     * <p>
     * This method wraps database operations and automatically retries up to
     * maxReconnectAttempts times after attempting to reconnect if a connection
     * error is detected.
     * </p>
     *
     * @param operation The database operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws StorageException if the operation fails (even after retries)
     */
    private <T> T executeWithRetry(DatabaseOperation<T> operation) throws StorageException {
        for (int attempt = 0; attempt <= maxReconnectAttempts; attempt++) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                // Only retry if not the last attempt and it's a connection error
                if (attempt < maxReconnectAttempts && shouldRetryOnError(e)) {
                    Debugger.log("[MySQLBackend] Connection error detected (attempt " + (attempt + 1) +
                            "/" + maxReconnectAttempts + "), attempting reconnection...");
                    if (reconnect()) {
                        Debugger.log("[MySQLBackend] Reconnected, retrying operation...");
                        continue; // Retry the operation
                    } else {
                        Debugger.log("[MySQLBackend] Reconnection failed");
                    }
                }
                // Either not a connection error, out of retries, or reconnect failed
                throw new StorageException.ConnectionFailedException("Database operation failed after " +
                        (attempt + 1) + " attempt(s)", e);
            }
        }
        // Should never reach here, but satisfy compiler
        throw new StorageException("Database operation failed after " + maxReconnectAttempts + " retry attempts");
    }

    /**
     * Functional interface for database operations that can be retried.
     */
    @FunctionalInterface
    private interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }

    // ========== Transaction Support ==========

    /**
     * Wrapper for Connection that only closes if not in a transaction.
     * This allows using try-with-resources while respecting transaction boundaries.
     */
    private class TransactionAwareConnection implements AutoCloseable {
        private final Connection connection;
        private final boolean isTransactionConnection;

        public TransactionAwareConnection() throws SQLException {
            Connection txConn = transactionConnection.get();
            if (txConn != null) {
                this.connection = txConn;
                this.isTransactionConnection = true;
            } else {
                this.connection = dataSource.getConnection();
                this.isTransactionConnection = false;
            }
        }

        public Connection get() {
            return connection;
        }

        @Override
        public void close() throws SQLException {
            // Only close if this is NOT a transaction connection
            // Transaction connections are managed by commit/rollback
            if (!isTransactionConnection) {
                connection.close();
            }
        }
    }

    @Override
    public void beginTransaction() throws StorageException {
        if (transactionConnection.get() != null) {
            throw new StorageException("Transaction already active on this thread");
        }

        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            transactionConnection.set(conn);
            Debugger.log("[MySQLBackend] Transaction started on thread " + Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new StorageException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commitTransaction() throws StorageException {
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new StorageException("No active transaction to commit");
        }

        try {
            conn.commit();
            Debugger.log("[MySQLBackend] Transaction committed on thread " + Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new StorageException("Failed to commit transaction", e);
        } finally {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                Debugger.log("[MySQLBackend] Error cleaning up transaction connection: " + e.getMessage());
            }
            transactionConnection.remove();
        }
    }

    @Override
    public void rollbackTransaction() throws StorageException {
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new StorageException("No active transaction to rollback");
        }

        try {
            conn.rollback();
            Debugger.log("[MySQLBackend] Transaction rolled back on thread " + Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new StorageException("Failed to rollback transaction", e);
        } finally {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                Debugger.log("[MySQLBackend] Error cleaning up transaction connection: " + e.getMessage());
            }
            transactionConnection.remove();
        }
    }

    @Override
    public void shutdown() throws StorageException {
        // Clean up any lingering transaction connections
        Connection txConn = transactionConnection.get();
        if (txConn != null) {
            try {
                txConn.rollback();
                txConn.close();
            } catch (SQLException e) {
                Debugger.log("[MySQLBackend] Error cleaning up transaction during shutdown: " + e.getMessage());
            }
            transactionConnection.remove();
        }

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
     * <p>
     * Each table is created in a separate method with isolated error handling
     * to prevent connection leaks if one table creation fails.
     * </p>
     */
    private void createTables() throws SQLException {
        try (TransactionAwareConnection txConn = new TransactionAwareConnection()) {
            Connection conn = txConn.get();
            createUsersTable(conn);
            createGroupsTable(conn);
            createWorldsTable(conn);
            createChangelogTable(conn);
            Debugger.log("[MySQLBackend] Created/verified all database tables");
        }
    }

    /**
     * Create the permissions_users table.
     */
    private void createUsersTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_users (" +
                            "id VARCHAR(100) PRIMARY KEY, " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "username VARCHAR(16), " +
                            "world VARCHAR(50) NOT NULL, " +
                            "permissions TEXT, " +
                            "`groups` TEXT, " +  // Backticks to escape reserved keyword
                            "metadata TEXT, " +
                            "last_modified BIGINT NOT NULL, " +
                            "INDEX idx_uuid_world (uuid, world), " +
                            "INDEX idx_last_modified (last_modified)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            Debugger.log("[MySQLBackend] Created/verified permissions_users table");
        }
    }

    /**
     * Create the permissions_groups table.
     */
    private void createGroupsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_groups (" +
                            "id VARCHAR(100) PRIMARY KEY, " +
                            "name VARCHAR(50) NOT NULL, " +
                            "world VARCHAR(50) NOT NULL, " +
                            "permissions TEXT, " +
                            "`groups` TEXT, " +  // Backticks to escape reserved keyword
                            "metadata TEXT, " +
                            "last_modified BIGINT NOT NULL, " +
                            "INDEX idx_name_world (name, world), " +
                            "INDEX idx_last_modified (last_modified)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            Debugger.log("[MySQLBackend] Created/verified permissions_groups table");
        }
    }

    /**
     * Create the permissions_worlds table.
     */
    private void createWorldsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS permissions_worlds (" +
                            "world VARCHAR(50) PRIMARY KEY, " +
                            "default_group VARCHAR(50) NOT NULL, " +
                            "last_modified BIGINT NOT NULL, " +
                            "custom_settings TEXT" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            Debugger.log("[MySQLBackend] Created/verified permissions_worlds table");
        }
    }

    /**
     * Create the permissions_changelog table.
     */
    private void createChangelogTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
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
            Debugger.log("[MySQLBackend] Created/verified permissions_changelog table");
        }
    }

    // ========== User Operations ==========

    @Override
    public UserData loadUser(String uuid, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_users WHERE uuid = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, uuid);
                stmt.setString(2, worldName);

                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    return null;
                }

                return resultSetToUserData(rs);
            }
        });
    }

    @Override
    public void saveUser(UserData userData, String worldName) throws StorageException {
        userData.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            String sql = "INSERT INTO permissions_users (id, uuid, username, world, permissions, `groups`, metadata, last_modified) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), " +
                    "permissions = VALUES(permissions), " +
                    "`groups` = VALUES(`groups`), " +
                    "metadata = VALUES(metadata), " +
                    "last_modified = VALUES(last_modified)";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

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

                return null; // Void operation
            }
        });
    }

    @Override
    public boolean userExists(String uuid, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT COUNT(*) FROM permissions_users WHERE uuid = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, uuid);
                stmt.setString(2, worldName);

                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        });
    }

    @Override
    public Set<String> getAllUserIds(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT uuid FROM permissions_users WHERE world = ?";
            Set<String> uuids = new HashSet<>();

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    uuids.add(rs.getString("uuid"));
                }

                return uuids;
            }
        });
    }

    @Override
    public void deleteUser(String uuid, String worldName) throws StorageException {
        executeWithRetry(() -> {
            String sql = "DELETE FROM permissions_users WHERE uuid = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, uuid);
                stmt.setString(2, worldName);
                stmt.executeUpdate();

                // Log change
                logChange(worldName, CalculableType.USER, uuid, ChangeRecord.ChangeType.DELETE);

                Debugger.log("[MySQLBackend] Deleted user " + uuid + " from world " + worldName);

                return null; // Void operation
            }
        });
    }

    // ========== Group Operations ==========

    @Override
    public GroupData loadGroup(String groupName, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_groups WHERE name = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, groupName);
                stmt.setString(2, worldName);

                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    return null;
                }

                return resultSetToGroupData(rs);
            }
        });
    }

    @Override
    public void saveGroup(GroupData groupData, String worldName) throws StorageException {
        groupData.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            String sql = "INSERT INTO permissions_groups (id, name, world, permissions, `groups`, metadata, last_modified) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "permissions = VALUES(permissions), " +
                    "`groups` = VALUES(`groups`), " +
                    "metadata = VALUES(metadata), " +
                    "last_modified = VALUES(last_modified)";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

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

                return null; // Void operation
            }
        });
    }

    @Override
    public boolean groupExists(String groupName, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT COUNT(*) FROM permissions_groups WHERE name = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, groupName);
                stmt.setString(2, worldName);

                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        });
    }

    @Override
    public Set<String> getAllGroupNames(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT name FROM permissions_groups WHERE world = ?";
            Set<String> groupNames = new HashSet<>();

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    groupNames.add(rs.getString("name"));
                }

                return groupNames;
            }
        });
    }

    @Override
    public void deleteGroup(String groupName, String worldName) throws StorageException {
        executeWithRetry(() -> {
            String sql = "DELETE FROM permissions_groups WHERE name = ? AND world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, groupName);
                stmt.setString(2, worldName);
                stmt.executeUpdate();

                // Log change
                logChange(worldName, CalculableType.GROUP, groupName, ChangeRecord.ChangeType.DELETE);

                Debugger.log("[MySQLBackend] Deleted group " + groupName + " from world " + worldName);

                return null; // Void operation
            }
        });
    }

    // ========== World Metadata Operations ==========

    @Override
    public WorldMetadata loadWorldMetadata(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_worlds WHERE world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    return null;
                }

                return resultSetToWorldMetadata(rs);
            }
        });
    }

    @Override
    public void saveWorldMetadata(WorldMetadata metadata, String worldName) throws StorageException {
        metadata.setLastModified(System.currentTimeMillis());

        executeWithRetry(() -> {
            String sql = "INSERT INTO permissions_worlds (world, default_group, last_modified, custom_settings) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "default_group = VALUES(default_group), " +
                    "last_modified = VALUES(last_modified), " +
                    "custom_settings = VALUES(custom_settings)";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                stmt.setString(2, metadata.getDefaultGroup());
                stmt.setLong(3, metadata.getLastModified());
                stmt.setString(4, gson.toJson(metadata.getCustomSettings()));

                stmt.executeUpdate();

                Debugger.log("[MySQLBackend] Saved world metadata for " + worldName);

                return null; // Void operation
            }
        });
    }

    // ========== Change Detection ==========

    @Override
    public Set<ChangeRecord> getChangesSince(long timestamp, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_changelog " +
                    "WHERE world = ? AND timestamp > ? AND server_source != ? " +
                    "ORDER BY timestamp ASC";

            Set<ChangeRecord> changes = new HashSet<>();

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                stmt.setLong(2, timestamp);
                stmt.setString(3, serverId);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    changes.add(resultSetToChangeRecord(rs));
                }

                return changes;
            }
        });
    }

    @Override
    public long getLastModifiedTimestamp(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT MAX(timestamp) FROM permissions_changelog WHERE world = ?";

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        });
    }

    // ========== Batch Operations ==========

    @Override
    public Map<String, UserData> loadAllUsers(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_users WHERE world = ?";
            Map<String, UserData> users = new HashMap<>();

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    UserData userData = resultSetToUserData(rs);
                    users.put(userData.getUuid(), userData);
                }

                Debugger.log("[MySQLBackend] Loaded " + users.size() + " users for world " + worldName);
                return users;
            }
        });
    }

    @Override
    public Map<String, GroupData> loadAllGroups(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql = "SELECT * FROM permissions_groups WHERE world = ?";
            Map<String, GroupData> groups = new HashMap<>();

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    GroupData groupData = resultSetToGroupData(rs);
                    groups.put(groupData.getName(), groupData);
                }

                Debugger.log("[MySQLBackend] Loaded " + groups.size() + " groups for world " + worldName);
                return groups;
            }
        });
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

        try (TransactionAwareConnection txConn = new TransactionAwareConnection();
             PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

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

    // ========== Changelog Management ==========

    @Override
    public int deleteChangelogBefore(long timestamp, String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql;
            if (worldName == null) {
                sql = "DELETE FROM permissions_changelog WHERE timestamp < ?";
            } else {
                sql = "DELETE FROM permissions_changelog WHERE timestamp < ? AND world = ?";
            }

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                stmt.setLong(1, timestamp);
                if (worldName != null) {
                    stmt.setString(2, worldName);
                }

                int deletedRows = stmt.executeUpdate();
                Debugger.log("[MySQLBackend] Deleted " + deletedRows + " changelog entries older than " + timestamp +
                        (worldName != null ? " for world " + worldName : " (all worlds)"));

                return deletedRows;
            }
        });
    }

    @Override
    public int deleteAllChangelog(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql;
            if (worldName == null) {
                sql = "DELETE FROM permissions_changelog";
            } else {
                sql = "DELETE FROM permissions_changelog WHERE world = ?";
            }

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                if (worldName != null) {
                    stmt.setString(1, worldName);
                }

                int deletedRows = stmt.executeUpdate();
                Debugger.log("[MySQLBackend] Deleted ALL " + deletedRows + " changelog entries" +
                        (worldName != null ? " for world " + worldName : " (all worlds)"));

                return deletedRows;
            }
        });
    }

    @Override
    public long getChangelogCount(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql;
            if (worldName == null) {
                sql = "SELECT COUNT(*) FROM permissions_changelog";
            } else {
                sql = "SELECT COUNT(*) FROM permissions_changelog WHERE world = ?";
            }

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                if (worldName != null) {
                    stmt.setString(1, worldName);
                }

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        });
    }

    @Override
    public long getOldestChangelogTimestamp(String worldName) throws StorageException {
        return executeWithRetry(() -> {
            String sql;
            if (worldName == null) {
                sql = "SELECT MIN(timestamp) FROM permissions_changelog";
            } else {
                sql = "SELECT MIN(timestamp) FROM permissions_changelog WHERE world = ?";
            }

            try (TransactionAwareConnection txConn = new TransactionAwareConnection();
                 PreparedStatement stmt = txConn.get().prepareStatement(sql)) {

                if (worldName != null) {
                    stmt.setString(1, worldName);
                }

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        });
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
