package de.bananaco.bpermissions.api.storage;

/**
 * Base exception for storage backend operations.
 * <p>
 * This exception is thrown when a storage operation fails (connection errors,
 * query failures, data corruption, etc.).
 * </p>
 */
public class StorageException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new StorageException with the specified message.
     *
     * @param message The error message
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates a new StorageException with the specified message and cause.
     *
     * @param message The error message
     * @param cause   The underlying cause of the exception
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new StorageException with the specified cause.
     *
     * @param cause The underlying cause of the exception
     */
    public StorageException(Throwable cause) {
        super(cause);
    }

    /**
     * Exception thrown when a connection to the storage backend fails.
     */
    public static class ConnectionFailedException extends StorageException {

        private static final long serialVersionUID = 1L;

        public ConnectionFailedException(String message) {
            super(message);
        }

        public ConnectionFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConnectionFailedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown when data in the storage backend is corrupted or invalid.
     */
    public static class DataCorruptedException extends StorageException {

        private static final long serialVersionUID = 1L;

        public DataCorruptedException(String message) {
            super(message);
        }

        public DataCorruptedException(String message, Throwable cause) {
            super(message, cause);
        }

        public DataCorruptedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown when a conflict occurs (e.g., optimistic locking failure).
     */
    public static class ConflictException extends StorageException {

        private static final long serialVersionUID = 1L;

        public ConflictException(String message) {
            super(message);
        }

        public ConflictException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConflictException(Throwable cause) {
            super(cause);
        }
    }
}
