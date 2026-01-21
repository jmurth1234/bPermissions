package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.Permission;
import de.bananaco.bpermissions.api.User;
import de.bananaco.bpermissions.api.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserData DTO.
 */
class UserDataTest {

    private UserData userData;
    private static final String TEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_USERNAME = "TestPlayer";

    @BeforeEach
    void setUp() {
        Set<String> permissions = new HashSet<>(Arrays.asList("test.permission", "^denied.permission"));
        Set<String> groups = new HashSet<>(Arrays.asList("admin", "moderator"));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("prefix", "&5[Admin]");
        metadata.put("suffix", "");

        userData = new UserData(TEST_UUID, TEST_USERNAME, permissions, groups, metadata, System.currentTimeMillis());
    }

    @Test
    void testConstructor() {
        assertNotNull(userData);
        assertEquals(TEST_UUID, userData.getUuid());
        assertEquals(TEST_USERNAME, userData.getUsername());
        assertEquals(2, userData.getPermissions().size());
        assertEquals(2, userData.getGroups().size());
        assertEquals(2, userData.getMetadata().size());
    }

    @Test
    void testDefaultConstructor() {
        UserData emptyUser = new UserData();
        assertNotNull(emptyUser);
        assertNull(emptyUser.getUuid());
        assertNotNull(emptyUser.getPermissions());
        assertNotNull(emptyUser.getGroups());
        assertNotNull(emptyUser.getMetadata());
        assertTrue(emptyUser.getPermissions().isEmpty());
        assertTrue(emptyUser.getGroups().isEmpty());
        assertTrue(emptyUser.getMetadata().isEmpty());
    }

    @Test
    void testGettersReturnCopies() {
        // Verify that getters return defensive copies
        Set<String> permissions = userData.getPermissions();
        permissions.add("should.not.affect.original");

        assertEquals(2, userData.getPermissions().size());
        assertFalse(userData.getPermissions().contains("should.not.affect.original"));
    }

    @Test
    void testSettersCreateCopies() {
        Set<String> newPerms = new HashSet<>(Arrays.asList("new.perm"));
        userData.setPermissions(newPerms);

        // Modify original set
        newPerms.add("should.not.affect.dto");

        assertEquals(1, userData.getPermissions().size());
        assertFalse(userData.getPermissions().contains("should.not.affect.dto"));
    }

    @Test
    void testFromUser() {
        // Create a mock User
        User mockUser = Mockito.mock(User.class);
        Mockito.when(mockUser.getName()).thenReturn(TEST_UUID);
        Mockito.when(mockUser.serialisePermissions()).thenReturn(Arrays.asList("perm1", "^perm2"));
        Mockito.when(mockUser.serialiseGroups()).thenReturn(Arrays.asList("group1", "group2"));

        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("prefix", "&c[Test]");
        Mockito.when(mockUser.getMeta()).thenReturn(metaMap);

        UserData result = UserData.fromUser(mockUser);

        assertNotNull(result);
        assertEquals(TEST_UUID, result.getUuid());
        assertEquals(2, result.getPermissions().size());
        assertEquals(2, result.getGroups().size());
        assertEquals(1, result.getMetadata().size());
        assertEquals("&c[Test]", result.getMetadata().get("prefix"));
    }

    @Test
    void testFromUserWithNull() {
        UserData result = UserData.fromUser(null);
        assertNull(result);
    }

    @Test
    void testEqualsAndHashCode() {
        UserData user1 = new UserData(TEST_UUID, TEST_USERNAME, new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);
        UserData user2 = new UserData(TEST_UUID, TEST_USERNAME, new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);
        UserData user3 = new UserData("different-uuid", TEST_USERNAME, new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
        assertNotEquals(user1, user3);
    }

    @Test
    void testToString() {
        String str = userData.toString();
        assertNotNull(str);
        assertTrue(str.contains(TEST_UUID));
        assertTrue(str.contains("UserData"));
    }

    @Test
    void testLastModified() {
        long timestamp = System.currentTimeMillis();
        userData.setLastModified(timestamp);
        assertEquals(timestamp, userData.getLastModified());
    }

    @Test
    void testNullSafety() {
        UserData nullSafe = new UserData(TEST_UUID, null, null, null, null, 0);
        assertNotNull(nullSafe.getPermissions());
        assertNotNull(nullSafe.getGroups());
        assertNotNull(nullSafe.getMetadata());
    }
}
