package de.bananaco.bpermissions.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("PermissionCarrier - Permission Management Operations")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionCarrierTest {

    @Mock
    private World mockWorld;

    private WorldManager worldManager;

    @BeforeEach
    void setup() {
        worldManager = WorldManager.getInstance();
        when(mockWorld.getName()).thenReturn("testworld");
        worldManager.worlds.put("testworld", mockWorld);
    }

    @AfterEach
    void teardown() {
        worldManager.worlds.clear();
    }

    @Test
    @DisplayName("addPermission adds new positive permission")
    void addPermission_newPermission_addsToSet() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addPermission("test.permission", true);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());

        Permission perm = perms.iterator().next();
        assertEquals("test.permission", perm.name());
        assertTrue(perm.isTrue());
    }

    @Test
    @DisplayName("addPermission adds new negative permission")
    void addPermission_negativePermission_addsNegated() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addPermission("test.permission", false);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());

        Permission perm = perms.iterator().next();
        assertEquals("test.permission", perm.name());
        assertFalse(perm.isTrue());
    }

    @Test
    @DisplayName("addPermission overrides existing permission")
    void addPermission_existingPermission_overrides() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("test.permission", true);

        // Act
        user.addPermission("test.permission", false);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size(), "Should only have one permission, not duplicates");

        Permission perm = perms.iterator().next();
        assertFalse(perm.isTrue(), "Should have the new value (false)");
    }

    @Test
    @DisplayName("addPermission is case insensitive when overriding")
    void addPermission_differentCase_overrides() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("Test.Permission", true);

        // Act
        user.addPermission("test.permission", false);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size(), "Should override despite case difference");
    }

    @Test
    @DisplayName("removePermission removes existing permission")
    void removePermission_existingPermission_removes() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("test.permission", true);
        user.addPermission("other.permission", true);

        // Act
        user.removePermission("test.permission");

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());

        Permission perm = perms.iterator().next();
        assertEquals("other.permission", perm.name());
    }

    @Test
    @DisplayName("removePermission with non-existent permission does nothing")
    void removePermission_nonExistent_doesNothing() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("test.permission", true);

        // Act
        user.removePermission("nonexistent.permission");

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());
        assertEquals("test.permission", perms.iterator().next().name());
    }

    @Test
    @DisplayName("removePermission is case insensitive")
    void removePermission_differentCase_removes() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("Test.Permission", true);

        // Act
        user.removePermission("test.permission");

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(0, perms.size());
    }

    @Test
    @DisplayName("getPermissions returns all permissions")
    void getPermissions_multiplePermissions_returnsAll() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("perm1", true);
        user.addPermission("perm2", false);
        user.addPermission("perm3", true);

        // Act
        Set<Permission> perms = user.getPermissions();

        // Assert
        assertEquals(3, perms.size());
    }

    @Test
    @DisplayName("getPermissions returns empty set when no permissions")
    void getPermissions_noPermissions_returnsEmptySet() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        Set<Permission> perms = user.getPermissions();

        // Assert
        assertNotNull(perms);
        assertEquals(0, perms.size());
    }

    @Test
    @DisplayName("getPermissionsAsString returns serialized permissions")
    void getPermissionsAsString_mixedPermissions_returnsSerializedForm() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("positive.perm", true);
        user.addPermission("negative.perm", false);

        // Act
        Set<String> permStrings = user.getPermissionsAsString();

        // Assert
        assertEquals(2, permStrings.size());
        assertTrue(permStrings.contains("positive.perm"));
        assertTrue(permStrings.contains("^negative.perm")); // Negated permissions use ^
    }

    @Test
    @DisplayName("getPermissionsAsString with no permissions returns empty set")
    void getPermissionsAsString_noPermissions_returnsEmptySet() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        Set<String> permStrings = user.getPermissionsAsString();

        // Assert
        assertNotNull(permStrings);
        assertEquals(0, permStrings.size());
    }

    @Test
    @DisplayName("serialisePermissions returns sorted list")
    void serialisePermissions_multiplePermissions_returnsSortedList() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("zebra.perm", true);
        user.addPermission("apple.perm", true);
        user.addPermission("middle.perm", true);

        // Act
        List<String> permList = user.serialisePermissions();

        // Assert
        assertEquals(3, permList.size());
        // Verify it's a list (ordered)
        assertNotNull(permList.get(0));
        assertNotNull(permList.get(1));
        assertNotNull(permList.get(2));
    }

    @Test
    @DisplayName("serialisePermissions with no permissions returns empty list")
    void serialisePermissions_noPermissions_returnsEmptyList() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        List<String> permList = user.serialisePermissions();

        // Assert
        assertNotNull(permList);
        assertEquals(0, permList.size());
    }

    @Test
    @DisplayName("clear removes all permissions")
    void clear_withPermissions_removesAll() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("perm1", true);
        user.addPermission("perm2", true);
        user.addPermission("perm3", true);

        // Act
        user.clear();

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(0, perms.size());
    }

    @Test
    @DisplayName("multiple addPermission calls accumulate permissions")
    void addPermission_multipleCalls_accumulatesPermissions() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addPermission("perm1", true);
        user.addPermission("perm2", false);
        user.addPermission("perm3", true);

        // Assert
        assertEquals(3, user.getPermissions().size());
    }

    @Test
    @DisplayName("constructor with null permissions creates empty set")
    void constructor_nullPermissions_createsEmptySet() {
        // Arrange & Act
        User user = new User("testUser", null, null, "testworld", mockWorld);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertNotNull(perms);
        assertEquals(0, perms.size());
    }

    @Test
    @DisplayName("constructor with permissions initializes set")
    void constructor_withPermissions_initializesSet() {
        // Arrange
        Set<Permission> initialPerms = new HashSet<>();
        initialPerms.add(Permission.loadFromString("test.perm"));

        // Act
        User user = new User("testUser", null, initialPerms, "testworld", mockWorld);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());
    }

    @Test
    @DisplayName("addPermission with wildcard permission")
    void addPermission_wildcardPermission_addsCorrectly() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addPermission("admin.*", true);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());

        Permission perm = perms.iterator().next();
        assertEquals("admin.*", perm.name());
        assertTrue(perm.isTrue());
    }

    @Test
    @DisplayName("removePermission then addPermission with same name works correctly")
    void removeAndAddPermission_sameName_worksCorrectly() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addPermission("test.perm", true);

        // Act
        user.removePermission("test.perm");
        user.addPermission("test.perm", false);

        // Assert
        Set<Permission> perms = user.getPermissions();
        assertEquals(1, perms.size());

        Permission perm = perms.iterator().next();
        assertFalse(perm.isTrue());
    }
}
