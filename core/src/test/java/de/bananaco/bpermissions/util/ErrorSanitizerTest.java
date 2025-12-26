package de.bananaco.bpermissions.util;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for ErrorSanitizer utility class.
 */
class ErrorSanitizerTest {

    @Test
    void testSanitizePasswordParam() {
        String input = "Connection failed: password=secretpass123&host=localhost";
        String expected = "Connection failed: password=***&host=localhost";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeAuthUrl() {
        String input = "Failed to connect to jdbc:mysql://admin:supersecret@localhost:3306/db";
        String expected = "Failed to connect to jdbc:mysql://admin:***@localhost:3306/db";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeMongoDbUrl() {
        String input = "Connection error: mongodb://dbuser:mypassword@mongo.example.com:27017/database";
        String expected = "Connection error: mongodb://dbuser:***@mongo.example.com:27017/database";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizePasswordJson() {
        String input = "Config: {\"username\":\"admin\",\"password\":\"secret123\",\"host\":\"localhost\"}";
        String expected = "Config: {\"username\":\"admin\",\"password\":\"***\",\"host\":\"localhost\"}";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeJdbcPasswordParam() {
        String input = "jdbc:mysql://localhost/db?useSSL=false&password=mypass&serverTimezone=UTC";
        String expected = "jdbc:mysql://localhost/db?useSSL=false&password=***&serverTimezone=UTC";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeMultiplePasswords() {
        String input = "Error: password=first123 and also password=second456";
        String expected = "Error: password=*** and also password=***";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeCaseInsensitive() {
        String input = "PASSWORD=secret123 and Password=secret456";
        // Regex replacement normalizes to lowercase "password=***"
        String expected = "password=*** and password=***";
        assertEquals(expected, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeNullMessage() {
        assertNull(ErrorSanitizer.sanitize((String) null));
    }

    @Test
    void testSanitizeEmptyMessage() {
        assertEquals("", ErrorSanitizer.sanitize(""));
    }

    @Test
    void testSanitizeNoCredentials() {
        String input = "Connection timeout after 30 seconds";
        assertEquals(input, ErrorSanitizer.sanitize(input));
    }

    @Test
    void testSanitizeThrowable() {
        Exception ex = new SQLException("Access denied for user 'admin'@'localhost' (using password: YES) password=secret123");
        String sanitized = ErrorSanitizer.sanitize(ex);
        assertTrue(sanitized.contains("password=***"));
        assertFalse(sanitized.contains("secret123"));
    }

    @Test
    void testSanitizeThrowableWithCause() {
        Exception cause = new SQLException("Connection error: jdbc:mysql://user:pass123@localhost/db");
        Exception ex = new RuntimeException("Database connection failed", cause);

        String sanitized = ErrorSanitizer.sanitize(ex);
        assertTrue(sanitized.contains("jdbc:mysql://user:***@localhost/db"));
        assertFalse(sanitized.contains("pass123"));
        assertTrue(sanitized.contains("Caused by:"));
    }

    @Test
    void testSanitizeNullThrowable() {
        assertNull(ErrorSanitizer.sanitize((Throwable) null));
    }

    @Test
    void testSanitizeMessageAndThrowable() {
        Exception ex = new SQLException("password=secret");
        String message = "Failed to connect: password=another";

        String sanitized = ErrorSanitizer.sanitize(message, ex);
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("another"));
        assertTrue(sanitized.contains("password=***"));
    }

    @Test
    void testSanitizeComplexConnectionString() {
        String input = "HikariPool-1 - Exception during pool initialization.\n" +
                "java.sql.SQLException: Access denied for user 'bperms'@'localhost'\n" +
                "Connection string: jdbc:mysql://bperms:SuperSecret123@localhost:3306/bpermissions?useSSL=false&password=SuperSecret123&serverTimezone=UTC";

        String sanitized = ErrorSanitizer.sanitize(input);
        assertFalse(sanitized.contains("SuperSecret123"));
        assertTrue(sanitized.contains("jdbc:mysql://bperms:***@localhost"));
        assertTrue(sanitized.contains("password=***"));
    }

    @Test
    void testSanitizePreservesStructure() {
        String input = "Error at line 42: connection to mongodb://user:pass@host:27017 failed with code 401";
        String sanitized = ErrorSanitizer.sanitize(input);

        // Verify structure is preserved
        assertTrue(sanitized.startsWith("Error at line 42:"));
        assertTrue(sanitized.contains("mongodb://user:***@host:27017"));
        assertTrue(sanitized.contains("failed with code 401"));
    }

    @Test
    void testConstructorThrowsException() {
        try {
            // Use reflection to invoke private constructor
            java.lang.reflect.Constructor<ErrorSanitizer> constructor =
                    ErrorSanitizer.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Expected exception to be thrown when invoking private constructor");
        } catch (Exception e) {
            // Reflection wraps the AssertionError in InvocationTargetException
            // Verify that some exception was thrown (constructor is private)
            assertTrue(e.getCause() != null || e.getMessage() != null,
                    "Expected exception but got: " + e.getClass());
        }
    }
}
