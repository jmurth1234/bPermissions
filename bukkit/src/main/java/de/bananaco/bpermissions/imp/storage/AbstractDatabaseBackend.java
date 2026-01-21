package de.bananaco.bpermissions.imp.storage;

import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.util.Debugger;

/**
 * Abstract base class for database-based storage backends.
 * <p>
 * Provides common functionality for database backends including:
 * <ul>
 *   <li>Server ID management for multi-server setups</li>
 *   <li>Reconnection configuration and automatic retry logic</li>
 *   <li>Common helper methods</li>
 * </ul>
 * </p>
 */
public abstract class AbstractDatabaseBackend implements StorageBackend {

    // ========== Common Fields ==========

    /** Server identifier for multi-server setups */
    protected String serverId;

    // Reconnection configuration
    protected int maxReconnectAttempts = 3;
    protected long reconnectDelayMs = 5000; // 5 seconds

    // ========== Abstract Methods (Backend-Specific) ==========

    /**
     * Get the name of this backend for logging purposes.
     *
     * @return Backend name (e.g., "MySQLBackend", "MongoBackend")
     */
    protected abstract String getBackendName();

    /**
     * Attempt to reconnect to the database.
     * <p>
     * This method should close existing connections, reinitialize the
     * connection pool, and verify connectivity.
     * </p>
     *
     * @return true if reconnection succeeded, false otherwise
     */
    protected abstract boolean reconnect();

    /**
     * Determine if an exception should trigger a retry attempt.
     * <p>
     * Subclasses must implement this to identify backend-specific connection errors
     * that warrant a reconnection attempt.
     * </p>
     *
     * @param e The exception to check
     * @return true if the operation should be retried, false otherwise
     */
    protected abstract boolean shouldRetryOnError(Exception e);

    // ========== Retry Logic ==========

    /**
     * Functional interface for database operations that can be retried.
     * <p>
     * This interface allows wrapping database operations (SQL queries, MongoDB commands, etc.)
     * in a common retry mechanism that handles connection failures gracefully.
     * </p>
     *
     * @param <T> The return type of the operation
     */
    @FunctionalInterface
    protected interface DatabaseOperation<T> {
        /**
         * Execute the database operation.
         *
         * @return The result of the operation
         * @throws Exception if the operation fails (database-specific exception)
         */
        T execute() throws Exception;
    }

    /**
     * Execute a database operation with automatic retry on connection failure.
     * <p>
     * This method wraps database operations and automatically retries up to
     * {@link #maxReconnectAttempts} times after attempting to reconnect if a
     * connection error is detected via {@link #shouldRetryOnError(Exception)}.
     * </p>
     * <p>
     * The retry flow is:
     * <ol>
     *   <li>Execute the operation</li>
     *   <li>If a connection error occurs, attempt to reconnect</li>
     *   <li>If reconnection succeeds, retry the operation</li>
     *   <li>Repeat until success or max attempts reached</li>
     * </ol>
     * </p>
     *
     * @param operation The database operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws StorageException if the operation fails (even after retries)
     */
    protected <T> T executeWithRetry(DatabaseOperation<T> operation) throws StorageException {
        for (int attempt = 0; attempt <= maxReconnectAttempts; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                // Only retry if not the last attempt and it's a connection error
                if (attempt < maxReconnectAttempts && shouldRetryOnError(e)) {
                    Debugger.log("[" + getBackendName() + "] Connection error detected (attempt " +
                            (attempt + 1) + "/" + maxReconnectAttempts + "), attempting reconnection...");
                    if (reconnect()) {
                        Debugger.log("[" + getBackendName() + "] Reconnected, retrying operation...");
                        continue; // Retry the operation
                    } else {
                        Debugger.log("[" + getBackendName() + "] Reconnection failed");
                    }
                }
                // Either not a connection error, out of retries, or reconnect failed
                throw new StorageException.ConnectionFailedException(
                        getBackendName() + " operation failed after " + (attempt + 1) + " attempt(s)", e);
            }
        }
        // Should never reach here, but satisfy compiler
        throw new StorageException(getBackendName() + " operation failed after " +
                maxReconnectAttempts + " retry attempts");
    }

    // ========== Helper Methods ==========

    /**
     * Build a unique user ID from UUID and world name.
     *
     * @param uuid      The user's UUID
     * @param worldName The world name
     * @return Composite ID string
     */
    protected String buildUserId(String uuid, String worldName) {
        return uuid + "_" + worldName;
    }

    /**
     * Build a unique group ID from group name and world name.
     *
     * @param groupName The group name
     * @param worldName The world name
     * @return Composite ID string
     */
    protected String buildGroupId(String groupName, String worldName) {
        return groupName + "_" + worldName;
    }
}
