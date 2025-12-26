package de.bananaco.bpermissions.imp.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.util.Debugger;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for SQL-based storage backends.
 * <p>
 * Provides common functionality for SQL databases including:
 * <ul>
 *   <li>Transaction management with thread-local connections</li>
 *   <li>Connection pooling support</li>
 *   <li>JSON serialization/deserialization with Gson</li>
 * </ul>
 * </p>
 * <p>
 * Inherits automatic retry logic from {@link AbstractDatabaseBackend}.
 * </p>
 */
public abstract class AbstractSQLBackend extends AbstractDatabaseBackend {

    // ========== Common Fields ==========

    /** JSON serialization */
    protected final Gson gson = new Gson();

    // Transaction support - per-thread transaction connection
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    // Type tokens for Gson deserialization
    protected static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {}.getType();
    protected static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    protected static final Type MAP_STRING_OBJECT_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    // ========== Abstract Methods (Backend-Specific) ==========

    /**
     * Get a database connection from the connection pool.
     * <p>
     * This method should return a new connection from the backend's
     * connection pool (e.g., HikariCP for MySQL).
     * </p>
     *
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    protected abstract Connection getConnection() throws SQLException;

    /**
     * Determine if a SQLException should trigger a retry attempt.
     * <p>
     * Subclasses must implement to specify which SQL states or error codes
     * should trigger automatic retry with reconnection.
     * </p>
     *
     * @param e The SQLException to check
     * @return true if the operation should be retried, false otherwise
     */
    protected abstract boolean shouldRetryOnSQLError(SQLException e);

    // ========== AbstractDatabaseBackend Implementation ==========

    /**
     * Implements the parent's shouldRetryOnError by delegating to SQL-specific check.
     * <p>
     * Only SQLException instances are considered for retry; other exceptions are not retried.
     * </p>
     *
     * @param e The exception to check
     * @return true if the operation should be retried, false otherwise
     */
    @Override
    protected boolean shouldRetryOnError(Exception e) {
        if (e instanceof SQLException) {
            return shouldRetryOnSQLError((SQLException) e);
        }
        return false;
    }

    // ========== Transaction Management ==========

    @Override
    public void beginTransaction() throws StorageException {
        try {
            if (transactionConnection.get() != null) {
                throw new StorageException("Transaction already active on this thread");
            }

            Connection conn = getConnection();
            conn.setAutoCommit(false);
            transactionConnection.set(conn);

            Debugger.log("[" + getBackendName() + "] Transaction started");
        } catch (SQLException e) {
            throw new StorageException.ConnectionFailedException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commitTransaction() throws StorageException {
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new StorageException("No active transaction on this thread");
        }

        try {
            conn.commit();
            Debugger.log("[" + getBackendName() + "] Transaction committed");
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                Debugger.log("[" + getBackendName() + "] Failed to rollback after commit failure: " +
                        rollbackEx.getMessage());
            }
            throw new StorageException("Failed to commit transaction", e);
        } finally {
            closeTransactionConnection();
        }
    }

    @Override
    public void rollbackTransaction() throws StorageException {
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new StorageException("No active transaction on this thread");
        }

        try {
            conn.rollback();
            Debugger.log("[" + getBackendName() + "] Transaction rolled back");
        } catch (SQLException e) {
            throw new StorageException("Failed to rollback transaction", e);
        } finally {
            closeTransactionConnection();
        }
    }

    /**
     * Close and clear the transaction connection for the current thread.
     */
    private void closeTransactionConnection() {
        Connection conn = transactionConnection.get();
        if (conn != null) {
            try {
                conn.setAutoCommit(true); // Restore auto-commit
                conn.close();
            } catch (SQLException e) {
                Debugger.log("[" + getBackendName() + "] Error closing transaction connection: " +
                        e.getMessage());
            } finally {
                transactionConnection.remove();
            }
        }
    }

    // ========== Transaction-Aware Connection Wrapper ==========

    /**
     * Wrapper for Connection that only closes if not in a transaction.
     * <p>
     * This allows using try-with-resources while respecting transaction boundaries.
     * If a transaction is active, the connection is reused and not closed.
     * Otherwise, a new connection is obtained and closed after use.
     * </p>
     */
    protected class TransactionAwareConnection implements AutoCloseable {
        private final Connection connection;
        private final boolean isTransactionConnection;

        public TransactionAwareConnection() throws SQLException {
            Connection txConn = transactionConnection.get();
            if (txConn != null) {
                this.connection = txConn;
                this.isTransactionConnection = true;
            } else {
                this.connection = getConnection();
                this.isTransactionConnection = false;
            }
        }

        public Connection get() {
            return connection;
        }

        @Override
        public void close() throws SQLException {
            // Only close if not part of a transaction
            if (!isTransactionConnection) {
                connection.close();
            }
        }
    }
}
