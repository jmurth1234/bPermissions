package de.bananaco.bpermissions.api.storage;

import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;

import java.util.Map;
import java.util.Set;

/**
 * Interface for database storage backends.
 * <p>
 * Implementations of this interface provide the low-level storage operations
 * for persisting and retrieving permission data from various database systems
 * (MongoDB, MySQL, etc.).
 * </p>
 * <p>
 * This abstraction allows bPermissions to support multiple database backends
 * while maintaining a consistent API for the {@link de.bananaco.bpermissions.api.World}
 * implementation.
 * </p>
 */
public interface StorageBackend {

    /**
     * Initialize the storage backend with the given configuration.
     * <p>
     * This method should establish database connections, create connection pools,
     * and perform any necessary setup (creating tables/collections, indexes, etc.).
     * </p>
     *
     * @param config Configuration map containing backend-specific settings
     *               (e.g., connection strings, credentials, pool sizes)
     * @throws StorageException if initialization fails
     */
    void initialize(Map<String, Object> config) throws StorageException;

    /**
     * Shutdown the storage backend and release all resources.
     * <p>
     * This method should close database connections, shutdown connection pools,
     * and perform any necessary cleanup.
     * </p>
     *
     * @throws StorageException if shutdown fails
     */
    void shutdown() throws StorageException;

    /**
     * Check if the backend is currently connected to the database.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    // ========== User Operations ==========

    /**
     * Load a user's data from storage.
     *
     * @param uuid      The user's UUID (as a string)
     * @param worldName The world name
     * @return UserData object containing the user's permissions, groups, and metadata,
     *         or null if the user doesn't exist in storage
     * @throws StorageException if the load operation fails
     */
    UserData loadUser(String uuid, String worldName) throws StorageException;

    /**
     * Save a user's data to storage.
     * <p>
     * This operation should perform an upsert (insert if not exists, update if exists).
     * The lastModified timestamp should be set automatically before saving.
     * </p>
     *
     * @param userData  The user data to save
     * @param worldName The world name
     * @throws StorageException if the save operation fails
     */
    void saveUser(UserData userData, String worldName) throws StorageException;

    /**
     * Check if a user exists in storage.
     *
     * @param uuid      The user's UUID (as a string)
     * @param worldName The world name
     * @return true if the user exists, false otherwise
     * @throws StorageException if the check operation fails
     */
    boolean userExists(String uuid, String worldName) throws StorageException;

    /**
     * Get all user UUIDs for a world.
     * <p>
     * This is useful for enumerating all users without loading their full data.
     * </p>
     *
     * @param worldName The world name
     * @return Set of UUIDs (as strings)
     * @throws StorageException if the operation fails
     */
    Set<String> getAllUserIds(String worldName) throws StorageException;

    /**
     * Delete a user from storage.
     *
     * @param uuid      The user's UUID (as a string)
     * @param worldName The world name
     * @throws StorageException if the delete operation fails
     */
    void deleteUser(String uuid, String worldName) throws StorageException;

    // ========== Group Operations ==========

    /**
     * Load a group's data from storage.
     *
     * @param groupName The group name
     * @param worldName The world name
     * @return GroupData object containing the group's permissions and metadata,
     *         or null if the group doesn't exist in storage
     * @throws StorageException if the load operation fails
     */
    GroupData loadGroup(String groupName, String worldName) throws StorageException;

    /**
     * Save a group's data to storage.
     * <p>
     * This operation should perform an upsert (insert if not exists, update if exists).
     * The lastModified timestamp should be set automatically before saving.
     * </p>
     *
     * @param groupData The group data to save
     * @param worldName The world name
     * @throws StorageException if the save operation fails
     */
    void saveGroup(GroupData groupData, String worldName) throws StorageException;

    /**
     * Check if a group exists in storage.
     *
     * @param groupName The group name
     * @param worldName The world name
     * @return true if the group exists, false otherwise
     * @throws StorageException if the check operation fails
     */
    boolean groupExists(String groupName, String worldName) throws StorageException;

    /**
     * Get all group names for a world.
     *
     * @param worldName The world name
     * @return Set of group names
     * @throws StorageException if the operation fails
     */
    Set<String> getAllGroupNames(String worldName) throws StorageException;

    /**
     * Delete a group from storage.
     *
     * @param groupName The group name
     * @param worldName The world name
     * @throws StorageException if the delete operation fails
     */
    void deleteGroup(String groupName, String worldName) throws StorageException;

    // ========== World Metadata Operations ==========

    /**
     * Load world metadata (default group, settings).
     *
     * @param worldName The world name
     * @return WorldMetadata object, or null if not found
     * @throws StorageException if the load operation fails
     */
    WorldMetadata loadWorldMetadata(String worldName) throws StorageException;

    /**
     * Save world metadata.
     *
     * @param metadata  The world metadata to save
     * @param worldName The world name
     * @throws StorageException if the save operation fails
     */
    void saveWorldMetadata(WorldMetadata metadata, String worldName) throws StorageException;

    // ========== Change Detection (for polling-based sync) ==========

    /**
     * Get all changes that occurred after the given timestamp.
     * <p>
     * This method is used by the polling system to detect changes made by
     * external sources (other servers, web interfaces, etc.).
     * </p>
     * <p>
     * Implementations should filter out changes made by this server instance
     * to avoid unnecessary reload operations.
     * </p>
     *
     * @param timestamp The Unix timestamp (milliseconds) to get changes since
     * @param worldName The world name
     * @return Set of ChangeRecord objects representing changes
     * @throws StorageException if the query fails
     */
    Set<ChangeRecord> getChangesSince(long timestamp, String worldName) throws StorageException;

    /**
     * Get the timestamp of the most recent change for a world.
     *
     * @param worldName The world name
     * @return Unix timestamp (milliseconds) of last modification
     * @throws StorageException if the query fails
     */
    long getLastModifiedTimestamp(String worldName) throws StorageException;

    // ========== Batch Operations (for efficiency) ==========

    /**
     * Load all users for a world in a single operation.
     * <p>
     * This is more efficient than loading users one-by-one during world initialization.
     * </p>
     *
     * @param worldName The world name
     * @return Map of UUID to UserData
     * @throws StorageException if the load operation fails
     */
    Map<String, UserData> loadAllUsers(String worldName) throws StorageException;

    /**
     * Load all groups for a world in a single operation.
     *
     * @param worldName The world name
     * @return Map of group name to GroupData
     * @throws StorageException if the load operation fails
     */
    Map<String, GroupData> loadAllGroups(String worldName) throws StorageException;

    /**
     * Save multiple users in a single batch operation.
     * <p>
     * This is useful for bulk migrations or world saves.
     * Default implementation saves users one-by-one.
     * </p>
     *
     * @param users     Collection of UserData objects to save
     * @param worldName The world name
     * @throws StorageException if the save operation fails
     */
    default void saveAllUsers(Iterable<UserData> users, String worldName) throws StorageException {
        for (UserData user : users) {
            saveUser(user, worldName);
        }
    }

    /**
     * Save multiple groups in a single batch operation.
     * <p>
     * Default implementation saves groups one-by-one.
     * </p>
     *
     * @param groups    Collection of GroupData objects to save
     * @param worldName The world name
     * @throws StorageException if the save operation fails
     */
    default void saveAllGroups(Iterable<GroupData> groups, String worldName) throws StorageException {
        for (GroupData group : groups) {
            saveGroup(group, worldName);
        }
    }

    // ========== Transaction Support (optional, for SQL backends) ==========

    /**
     * Begin a transaction.
     * <p>
     * Default implementation does nothing (no-op).
     * SQL-based backends may override this to support transactions.
     * </p>
     *
     * @throws StorageException if the transaction cannot be started
     */
    default void beginTransaction() throws StorageException {
        // No-op by default
    }

    /**
     * Commit the current transaction.
     * <p>
     * Default implementation does nothing (no-op).
     * </p>
     *
     * @throws StorageException if the commit fails
     */
    default void commitTransaction() throws StorageException {
        // No-op by default
    }

    /**
     * Rollback the current transaction.
     * <p>
     * Default implementation does nothing (no-op).
     * </p>
     *
     * @throws StorageException if the rollback fails
     */
    default void rollbackTransaction() throws StorageException {
        // No-op by default
    }

    // ========== Changelog Management (optional) ==========

    /**
     * Delete all changelog entries older than the specified timestamp.
     * <p>
     * This is useful for cleaning up old changelog entries to prevent unbounded
     * table growth.
     * </p>
     *
     * @param timestamp  Unix timestamp (milliseconds) - entries older than this will be deleted
     * @param worldName  The world name (null to delete from all worlds)
     * @return Number of entries deleted
     * @throws StorageException if the deletion fails
     */
    default int deleteChangelogBefore(long timestamp, String worldName) throws StorageException {
        // No-op by default - backends without changelog can ignore this
        return 0;
    }

    /**
     * Delete all changelog entries for a specific world.
     * <p>
     * <b>Warning:</b> This deletes ALL changelog entries for the world, including recent ones.
     * Use with caution!
     * </p>
     *
     * @param worldName The world name (null to delete all changelog entries)
     * @return Number of entries deleted
     * @throws StorageException if the deletion fails
     */
    default int deleteAllChangelog(String worldName) throws StorageException {
        // No-op by default
        return 0;
    }

    /**
     * Get the count of changelog entries for a world.
     * <p>
     * Useful for monitoring changelog table size.
     * </p>
     *
     * @param worldName The world name (null for total count across all worlds)
     * @return Number of changelog entries
     * @throws StorageException if the query fails
     */
    default long getChangelogCount(String worldName) throws StorageException {
        // No-op by default
        return 0;
    }

    /**
     * Get the oldest changelog entry timestamp for a world.
     * <p>
     * Useful for determining how far back changelog history goes.
     * </p>
     *
     * @param worldName The world name (null for oldest across all worlds)
     * @return Unix timestamp (milliseconds) of oldest entry, or 0 if no entries exist
     * @throws StorageException if the query fails
     */
    default long getOldestChangelogTimestamp(String worldName) throws StorageException {
        // No-op by default
        return 0;
    }
}
