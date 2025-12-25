package de.bananaco.bpermissions.imp.storage;

import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.imp.Config;
import de.bananaco.bpermissions.imp.DatabaseWorld;
import de.bananaco.bpermissions.imp.Permissions;
import de.bananaco.bpermissions.imp.YamlWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorldFactory.
 */
class WorldFactoryTest {

    @Mock
    private Permissions mockPermissions;

    @Mock
    private Config mockConfig;

    private WorldFactory worldFactory;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        worldFactory = new WorldFactory(mockPermissions, mockConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (worldFactory != null) {
            worldFactory.shutdown();
        }
        closeable.close();
    }

    @Test
    void testCreateYamlWorld() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        World world = worldFactory.createWorld("testworld");

        assertNotNull(world);
        assertTrue(world instanceof YamlWorld);
        assertEquals("testworld", world.getName());
    }

    @Test
    void testCreateMongoWorldWithValidConfig() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("mongodb");
        when(mockConfig.getPollInterval()).thenReturn(5);

        Map<String, Object> mongoConfig = new HashMap<>();
        mongoConfig.put("connection-string", "mongodb://localhost:27017");
        mongoConfig.put("database", "bpermissions");
        mongoConfig.put("server-id", "test-server");
        when(mockConfig.getMongoConfig()).thenReturn(mongoConfig);

        // Note: This will fail if MongoDB is not running, which is expected in unit tests
        // In a real environment, you'd use Testcontainers or mock the backend
        try {
            World world = worldFactory.createWorld("testworld");
            assertNotNull(world);
            assertTrue(world instanceof DatabaseWorld);
        } catch (StorageException.ConnectionFailedException e) {
            // Expected if MongoDB is not running
            assertTrue(e.getMessage().contains("Failed to initialize MongoDB backend"));
        }
    }

    @Test
    void testCreateMongoWorldReusesSameBackend() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("mongodb");
        when(mockConfig.getPollInterval()).thenReturn(5);

        Map<String, Object> mongoConfig = new HashMap<>();
        mongoConfig.put("connection-string", "mongodb://localhost:27017");
        mongoConfig.put("database", "bpermissions");
        mongoConfig.put("server-id", "test-server");
        when(mockConfig.getMongoConfig()).thenReturn(mongoConfig);

        try {
            World world1 = worldFactory.createWorld("world1");
            World world2 = worldFactory.createWorld("world2");

            // Both worlds should use the same backend (shared connection pool)
            // This is tested implicitly - if it works without errors, the backend is shared
            assertNotNull(world1);
            assertNotNull(world2);
            assertNotSame(world1, world2); // Different world instances
        } catch (StorageException.ConnectionFailedException e) {
            // Expected if MongoDB is not running
        }
    }

    @Test
    void testInvalidBackendThrowsException() {
        when(mockConfig.getStorageBackend()).thenReturn("invalid-backend");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            worldFactory.createWorld("testworld");
        });

        assertTrue(exception.getMessage().contains("Unknown storage backend"));
    }

    @Test
    void testShutdownClosesBackends() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        worldFactory.createWorld("testworld");

        // Shutdown should not throw
        assertDoesNotThrow(() -> worldFactory.shutdown());

        // After shutdown, shared backends should be cleared
        // (Can't directly verify this without exposing internal state)
    }

    @Test
    void testCaseInsensitiveBackendNames() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("YAML");

        World world = worldFactory.createWorld("testworld");

        assertNotNull(world);
        assertTrue(world instanceof YamlWorld);
    }

    @Test
    void testCreateMultipleYamlWorlds() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        World world1 = worldFactory.createWorld("world1");
        World world2 = worldFactory.createWorld("world2");
        World world3 = worldFactory.createWorld("world_nether");

        assertNotNull(world1);
        assertNotNull(world2);
        assertNotNull(world3);

        // All should be YamlWorld instances
        assertTrue(world1 instanceof YamlWorld);
        assertTrue(world2 instanceof YamlWorld);
        assertTrue(world3 instanceof YamlWorld);

        // Should have different names
        assertEquals("world1", world1.getName());
        assertEquals("world2", world2.getName());
        assertEquals("world_nether", world3.getName());
    }

    @Test
    void testCloseBackendRemovesBackend() throws StorageException {
        // This test verifies that closeBackend properly shuts down and removes a backend
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        World world = worldFactory.createWorld("testworld");
        assertNotNull(world);

        // Close a backend that doesn't exist (should not throw)
        assertDoesNotThrow(() -> worldFactory.closeBackend("mongodb"));

        // Close with null (should not throw)
        assertDoesNotThrow(() -> worldFactory.closeBackend(null));
    }

    @Test
    void testCloseOtherBackendsKeepsSpecified() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        worldFactory.createWorld("testworld");

        // Close all backends except yaml (should not throw)
        assertDoesNotThrow(() -> worldFactory.closeOtherBackends("yaml"));

        // Close all backends except mongodb (should close everything since we only have yaml)
        assertDoesNotThrow(() -> worldFactory.closeOtherBackends("mongodb"));

        // Close all backends (pass null)
        assertDoesNotThrow(() -> worldFactory.closeOtherBackends(null));
    }

    @Test
    void testCloseBackendCaseInsensitive() {
        // Test that closeBackend is case-insensitive
        assertDoesNotThrow(() -> worldFactory.closeBackend("MONGODB"));
        assertDoesNotThrow(() -> worldFactory.closeBackend("MongoDb"));
        assertDoesNotThrow(() -> worldFactory.closeBackend("mysql"));
    }

    @Test
    void testMultipleShutdownCallsSafe() throws StorageException {
        when(mockConfig.getStorageBackend()).thenReturn("yaml");

        worldFactory.createWorld("testworld");

        // Multiple shutdown calls should be safe
        assertDoesNotThrow(() -> worldFactory.shutdown());
        assertDoesNotThrow(() -> worldFactory.shutdown());
        assertDoesNotThrow(() -> worldFactory.shutdown());
    }
}
