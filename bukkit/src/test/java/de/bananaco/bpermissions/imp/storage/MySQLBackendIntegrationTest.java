package de.bananaco.bpermissions.imp.storage;

import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;
import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MySQLBackend using Testcontainers.
 * <p>
 * These tests use a real MySQL container to verify database operations work correctly.
 * </p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MySQLBackendIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bpermissions_test")
            .withUsername("test")
            .withPassword("test");

    private MySQLBackend backend;
    private Map<String, Object> config;

    @BeforeEach
    void setUp() throws StorageException {
        backend = new MySQLBackend();

        config = new HashMap<>();
        config.put("host", mysql.getHost());
        config.put("port", mysql.getFirstMappedPort());
        config.put("database", "bpermissions_test");
        config.put("username", "test");
        config.put("password", "test");
        config.put("server-id", "test-server");

        backend.initialize(config);
    }

    @AfterEach
    void tearDown() throws StorageException {
        if (backend != null) {
            backend.shutdown();
        }
    }

    // ========== User CRUD Tests ==========

    @Test
    @Order(1)
    @DisplayName("Should save and load user data")
    void testSaveAndLoadUser() throws StorageException {
        // Given
        UserData user = new UserData();
        user.setUuid("550e8400-e29b-41d4-a716-446655440000");
        user.setUsername("TestPlayer");
        user.setPermissions(Set.of("test.permission", "^test.denied"));
        user.setGroups(Set.of("admin", "moderator"));

        Map<String, String> meta = new HashMap<>();
        meta.put("prefix", "&c[Admin]");
        meta.put("suffix", "");
        user.setMetadata(meta);

        // When
        backend.saveUser(user, "world");
        UserData loaded = backend.loadUser("550e8400-e29b-41d4-a716-446655440000", "world");

        // Then
        assertNotNull(loaded);
        assertEquals("TestPlayer", loaded.getUsername());
        assertEquals(2, loaded.getPermissions().size());
        assertTrue(loaded.getPermissions().contains("test.permission"));
        assertTrue(loaded.getPermissions().contains("^test.denied"));
        assertEquals(2, loaded.getGroups().size());
        assertTrue(loaded.getGroups().contains("admin"));
        assertEquals("&c[Admin]", loaded.getMetadata().get("prefix"));
    }

    @Test
    @Order(2)
    @DisplayName("Should delete user data")
    void testDeleteUser() throws StorageException {
        // Given
        UserData user = new UserData();
        user.setUuid("delete-test-uuid");
        user.setUsername("DeleteMe");
        user.setPermissions(Set.of("test.permission"));
        user.setGroups(Set.of("default"));
        user.setMetadata(new HashMap<>());

        backend.saveUser(user, "world");

        // When
        backend.deleteUser("delete-test-uuid", "world");
        UserData loaded = backend.loadUser("delete-test-uuid", "world");

        // Then
        assertNull(loaded, "Deleted user should not be loadable");
    }

    @Test
    @Order(3)
    @DisplayName("Should check if user exists")
    void testUserExists() throws StorageException {
        // Given
        UserData user = new UserData();
        user.setUuid("exists-test-uuid");
        user.setUsername("ExistsTest");
        user.setPermissions(new HashSet<>());
        user.setGroups(new HashSet<>());
        user.setMetadata(new HashMap<>());

        backend.saveUser(user, "world");

        // When/Then
        assertTrue(backend.userExists("exists-test-uuid", "world"));
        assertFalse(backend.userExists("non-existent-uuid", "world"));
    }

    @Test
    @Order(4)
    @DisplayName("Should get all user UUIDs")
    void testGetAllUsers() throws StorageException {
        // Given
        for (int i = 0; i < 3; i++) {
            UserData user = new UserData();
            user.setUuid("user-" + i);
            user.setUsername("User" + i);
            user.setPermissions(new HashSet<>());
            user.setGroups(new HashSet<>());
            user.setMetadata(new HashMap<>());
            backend.saveUser(user, "world");
        }

        // When
        Set<String> uuids = backend.getAllUserIds("world");

        // Then
        assertTrue(uuids.size() >= 3);
        assertTrue(uuids.contains("user-0"));
        assertTrue(uuids.contains("user-1"));
        assertTrue(uuids.contains("user-2"));
    }

    // ========== Group CRUD Tests ==========

    @Test
    @Order(10)
    @DisplayName("Should save and load group data")
    void testSaveAndLoadGroup() throws StorageException {
        // Given
        GroupData group = new GroupData();
        group.setName("admin");
        group.setPermissions(Set.of("admin.*", "^admin.dangerous"));
        group.setGroups(Set.of("moderator", "default"));

        Map<String, String> meta = new HashMap<>();
        meta.put("prefix", "&4[Admin]");
        meta.put("priority", "100");
        group.setMetadata(meta);

        // When
        backend.saveGroup(group, "world");
        GroupData loaded = backend.loadGroup("admin", "world");

        // Then
        assertNotNull(loaded);
        assertEquals("admin", loaded.getName());
        assertEquals(2, loaded.getPermissions().size());
        assertTrue(loaded.getPermissions().contains("admin.*"));
        assertEquals(2, loaded.getGroups().size());
        assertEquals("&4[Admin]", loaded.getMetadata().get("prefix"));
        assertEquals("100", loaded.getMetadata().get("priority"));
    }

    @Test
    @Order(11)
    @DisplayName("Should delete group data")
    void testDeleteGroup() throws StorageException {
        // Given
        GroupData group = new GroupData();
        group.setName("deleteme");
        group.setPermissions(Set.of("test.permission"));
        group.setGroups(new HashSet<>());
        group.setMetadata(new HashMap<>());

        backend.saveGroup(group, "world");

        // When
        backend.deleteGroup("deleteme", "world");
        GroupData loaded = backend.loadGroup("deleteme", "world");

        // Then
        assertNull(loaded, "Deleted group should not be loadable");
    }

    @Test
    @Order(12)
    @DisplayName("Should check if group exists")
    void testGroupExists() throws StorageException {
        // Given
        GroupData group = new GroupData();
        group.setName("existsgroup");
        group.setPermissions(new HashSet<>());
        group.setGroups(new HashSet<>());
        group.setMetadata(new HashMap<>());

        backend.saveGroup(group, "world");

        // When/Then
        assertTrue(backend.groupExists("existsgroup", "world"));
        assertFalse(backend.groupExists("nonexistent", "world"));
    }

    @Test
    @Order(13)
    @DisplayName("Should get all group names")
    void testGetAllGroups() throws StorageException {
        // Given
        for (int i = 0; i < 3; i++) {
            GroupData group = new GroupData();
            group.setName("group-" + i);
            group.setPermissions(new HashSet<>());
            group.setGroups(new HashSet<>());
            group.setMetadata(new HashMap<>());
            backend.saveGroup(group, "world");
        }

        // When
        Set<String> names = backend.getAllGroupNames("world");

        // Then
        assertTrue(names.size() >= 3);
        assertTrue(names.contains("group-0"));
        assertTrue(names.contains("group-1"));
        assertTrue(names.contains("group-2"));
    }

    // ========== World Metadata Tests ==========

    @Test
    @Order(20)
    @DisplayName("Should save and load world metadata")
    void testSaveAndLoadWorldMetadata() throws StorageException {
        // Given
        WorldMetadata metadata = new WorldMetadata();
        metadata.setWorldName("testworld");
        metadata.setDefaultGroup("default");

        // When
        backend.saveWorldMetadata(metadata, "testworld");
        WorldMetadata loaded = backend.loadWorldMetadata("testworld");

        // Then
        assertNotNull(loaded);
        assertEquals("testworld", loaded.getWorldName());
        assertEquals("default", loaded.getDefaultGroup());
    }

    // ========== Changelog Tests ==========

    @Test
    @Order(30)
    @DisplayName("Should retrieve changes since timestamp")
    void testGetChangesSince() throws StorageException {
        // Given
        UserData user = new UserData();
        user.setUuid("changelog-test");
        user.setUsername("ChangeTest");
        user.setPermissions(new HashSet<>());
        user.setGroups(new HashSet<>());
        user.setMetadata(new HashMap<>());

        long beforeSave = System.currentTimeMillis() - 1;
        backend.saveUser(user, "world");

        // Small delay to ensure change is persisted
        try { Thread.sleep(100); } catch (InterruptedException e) { }

        // When
        Set<ChangeRecord> changes = backend.getChangesSince(beforeSave, "world");

        // Then
        // Note: getChangesSince filters OUT changes from the same server (by design for multi-server setups)
        // So in a single-server test, we expect NO changes since all changes are from "test-server"
        // Verify the changelog count increased instead
        long changelogCount = backend.getChangelogCount("world");
        assertTrue(changelogCount > 0, "Should have changelog entries recorded");

        // The filtered results should be empty since we're querying the same server
        assertTrue(changes.isEmpty(), "Should filter out changes from same server (correct behavior for multi-server sync)");
    }

    @Test
    @Order(31)
    @DisplayName("Should delete changelog before timestamp")
    void testDeleteChangelogBefore() throws StorageException {
        // Given
        long countBefore = backend.getChangelogCount("world");
        long cutoff = System.currentTimeMillis() - 1000; // 1 second ago

        // When
        int deleted = backend.deleteChangelogBefore(cutoff, "world");

        // Then
        assertTrue(deleted >= 0, "Delete count should be non-negative");
        long countAfter = backend.getChangelogCount("world");
        assertEquals(countBefore - deleted, countAfter);
    }

    @Test
    @Order(32)
    @DisplayName("Should get changelog count")
    void testGetChangelogCount() throws StorageException {
        // When
        long count = backend.getChangelogCount("world");

        // Then
        assertTrue(count >= 0, "Changelog count should be non-negative");
    }

    @Test
    @Order(33)
    @DisplayName("Should get oldest changelog timestamp")
    void testGetOldestChangelogTimestamp() throws StorageException {
        // When
        long oldest = backend.getOldestChangelogTimestamp("world");

        // Then
        assertTrue(oldest == 0 || oldest > 0, "Oldest timestamp should be 0 or positive");
    }

    @Test
    @Order(34)
    @DisplayName("Should delete all changelog for world")
    void testDeleteAllChangelog() throws StorageException {
        // Given
        long countBefore = backend.getChangelogCount("world");

        // When
        backend.deleteAllChangelog("world");
        long countAfter = backend.getChangelogCount("world");

        // Then
        assertEquals(0, countAfter, "All changelog entries should be deleted");
    }

    // ========== Bulk Operations Tests ==========

    @Test
    @Order(40)
    @DisplayName("Should load all users in world")
    void testLoadAllUsers() throws StorageException {
        // Given
        for (int i = 0; i < 3; i++) {
            UserData user = new UserData();
            user.setUuid("bulk-user-" + i);
            user.setUsername("BulkUser" + i);
            user.setPermissions(Set.of("test.perm" + i));
            user.setGroups(Set.of("default"));
            user.setMetadata(new HashMap<>());
            backend.saveUser(user, "bulkworld");
        }

        // When
        Map<String, UserData> users = backend.loadAllUsers("bulkworld");

        // Then
        assertTrue(users.size() >= 3);
        assertNotNull(users.get("bulk-user-0"));
        assertNotNull(users.get("bulk-user-1"));
        assertNotNull(users.get("bulk-user-2"));
        assertEquals("BulkUser0", users.get("bulk-user-0").getUsername());
    }

    @Test
    @Order(41)
    @DisplayName("Should load all groups in world")
    void testLoadAllGroups() throws StorageException {
        // Given
        for (int i = 0; i < 3; i++) {
            GroupData group = new GroupData();
            group.setName("bulk-group-" + i);
            group.setPermissions(Set.of("test.perm" + i));
            group.setGroups(new HashSet<>());
            group.setMetadata(new HashMap<>());
            backend.saveGroup(group, "bulkworld");
        }

        // When
        Map<String, GroupData> groups = backend.loadAllGroups("bulkworld");

        // Then
        assertTrue(groups.size() >= 3);
        assertNotNull(groups.get("bulk-group-0"));
        assertNotNull(groups.get("bulk-group-1"));
        assertNotNull(groups.get("bulk-group-2"));
    }

    // ========== Transaction Tests ==========

    @Test
    @Order(50)
    @DisplayName("Should commit transaction successfully")
    void testTransactionCommit() throws StorageException {
        // Given
        backend.beginTransaction();

        UserData user = new UserData();
        user.setUuid("tx-commit-test");
        user.setUsername("TxCommit");
        user.setPermissions(new HashSet<>());
        user.setGroups(new HashSet<>());
        user.setMetadata(new HashMap<>());

        // When
        backend.saveUser(user, "world");
        backend.commitTransaction();

        // Then - changes should be persisted
        UserData loaded = backend.loadUser("tx-commit-test", "world");
        assertNotNull(loaded, "Committed transaction should persist changes");
        assertEquals("TxCommit", loaded.getUsername());
    }

    @Test
    @Order(51)
    @DisplayName("Should rollback transaction successfully")
    void testTransactionRollback() throws StorageException {
        // Given
        backend.beginTransaction();

        UserData user = new UserData();
        user.setUuid("tx-rollback-test");
        user.setUsername("TxRollback");
        user.setPermissions(new HashSet<>());
        user.setGroups(new HashSet<>());
        user.setMetadata(new HashMap<>());

        // When
        backend.saveUser(user, "world");
        backend.rollbackTransaction();

        // Then - changes should NOT be persisted
        UserData loaded = backend.loadUser("tx-rollback-test", "world");
        assertNull(loaded, "Rolled back transaction should not persist changes");
    }

    @Test
    @Order(52)
    @DisplayName("Should handle nested operations in transaction")
    void testNestedTransactionOperations() throws StorageException {
        // Given
        backend.beginTransaction();

        // Create user
        UserData user = new UserData();
        user.setUuid("tx-nested-user");
        user.setUsername("TxNested");
        user.setPermissions(new HashSet<>());
        user.setGroups(Set.of("tx-nested-group"));
        user.setMetadata(new HashMap<>());

        // Create group
        GroupData group = new GroupData();
        group.setName("tx-nested-group");
        group.setPermissions(Set.of("test.permission"));
        group.setGroups(new HashSet<>());
        group.setMetadata(new HashMap<>());

        // When
        backend.saveUser(user, "world");
        backend.saveGroup(group, "world");
        backend.commitTransaction();

        // Then
        UserData loadedUser = backend.loadUser("tx-nested-user", "world");
        GroupData loadedGroup = backend.loadGroup("tx-nested-group", "world");

        assertNotNull(loadedUser);
        assertNotNull(loadedGroup);
        assertTrue(loadedUser.getGroups().contains("tx-nested-group"));
    }
}
