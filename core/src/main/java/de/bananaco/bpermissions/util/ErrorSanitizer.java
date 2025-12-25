package de.bananaco.bpermissions.util;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing error messages to prevent credential leakage.
 * <p>
 * This class removes sensitive information like passwords from error messages,
 * stack traces, and log output. This is critical for security as database
 * connection errors often include full connection strings with embedded
 * credentials.
 * </p>
 * <p>
 * Example transformations:
 * <ul>
 *   <li>"jdbc:mysql://user:secretpass@localhost/db" → "jdbc:mysql://user:***@localhost/db"</li>
 *   <li>"password=secretpass&host=db" → "password=***&host=db"</li>
 *   <li>"mongodb://admin:pass123@server:27017" → "mongodb://admin:***@server:27017"</li>
 * </ul>
 * </p>
 */
public final class ErrorSanitizer {

    // Pattern to match password= query parameters
    private static final Pattern PASSWORD_PARAM_PATTERN =
            Pattern.compile("password=([^&\\s]+)", Pattern.CASE_INSENSITIVE);

    // Pattern to match credentials in URLs (user:password@host format)
    private static final Pattern AUTH_URL_PATTERN =
            Pattern.compile("://([^:@]+):([^@]+)@");

    // Pattern to match password fields in JSON-like structures
    private static final Pattern PASSWORD_JSON_PATTERN =
            Pattern.compile("\"password\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    // Pattern to match MySQL JDBC URL password parameter
    private static final Pattern JDBC_PASSWORD_PATTERN =
            Pattern.compile("&password=([^&\\s]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private ErrorSanitizer() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /**
     * Sanitize an error message by removing sensitive credentials.
     * <p>
     * This method applies multiple regex patterns to mask passwords and other
     * sensitive information in the input string.
     * </p>
     *
     * @param message The message to sanitize (can be null)
     * @return Sanitized message with credentials replaced by "***", or null if input was null
     */
    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }

        // Apply all sanitization patterns
        message = PASSWORD_PARAM_PATTERN.matcher(message).replaceAll("password=***");
        message = AUTH_URL_PATTERN.matcher(message).replaceAll("://$1:***@");
        message = PASSWORD_JSON_PATTERN.matcher(message).replaceAll("\"password\":\"***\"");
        message = JDBC_PASSWORD_PATTERN.matcher(message).replaceAll("&password=***");

        return message;
    }

    /**
     * Sanitize a Throwable's message and cause chain.
     * <p>
     * This method sanitizes the main exception message and all caused-by messages
     * in the exception chain.
     * </p>
     *
     * @param throwable The throwable to sanitize
     * @return A sanitized error message string, or null if throwable was null
     */
    public static String sanitize(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringBuilder sanitized = new StringBuilder();
        sanitized.append(sanitize(throwable.getMessage()));

        // Sanitize cause chain
        Throwable cause = throwable.getCause();
        while (cause != null) {
            sanitized.append(" | Caused by: ").append(sanitize(cause.getMessage()));
            cause = cause.getCause();
        }

        return sanitized.toString();
    }

    /**
     * Sanitize both a custom message and an exception.
     * <p>
     * Useful for log statements like: log("Database error: " + sanitize("connect failed", exception))
     * </p>
     *
     * @param message   The custom message
     * @param throwable The exception
     * @return Combined sanitized message
     */
    public static String sanitize(String message, Throwable throwable) {
        String sanitizedMessage = sanitize(message);
        String sanitizedException = sanitize(throwable);

        if (sanitizedMessage == null && sanitizedException == null) {
            return null;
        } else if (sanitizedMessage == null) {
            return sanitizedException;
        } else if (sanitizedException == null) {
            return sanitizedMessage;
        } else {
            return sanitizedMessage + ": " + sanitizedException;
        }
    }
}
