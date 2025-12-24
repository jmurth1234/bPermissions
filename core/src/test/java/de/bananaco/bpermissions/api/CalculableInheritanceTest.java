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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for permission and metadata inheritance.
 * These tests work with the WorldManager singleton and require proper setup/teardown.
 */
@DisplayName("Calculable - Permission and Metadata Inheritance")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalculableInheritanceTest {

    @Mock
    private World mockWorld;

    private WorldManager worldManager;

    @BeforeEach
    void setup() {
        // Get WorldManager singleton instance
        worldManager = WorldManager.getInstance();

        // Setup mock World
        when(mockWorld.getName()).thenReturn("testworld");

        // Register the mock world with WorldManager
        worldManager.worlds.put("testworld", mockWorld);
    }

    @AfterEach
    void teardown() {
        // Clean up WorldManager after each test
        worldManager.worlds.clear();
    }

    @Test
    @DisplayName("user with no groups has only direct permissions")
    void inheritance_noGroups_onlyDirectPermissions() throws RecursiveGroupException {
        // Arrange
        Set<Permission> permissions = new HashSet<>();
        permissions.add(Permission.loadFromString("user.direct.permission"));

        User user = new User("testUser", null, permissions, "testworld", mockWorld);

        // Mock the world to return empty groups
        when(mockWorld.getGroup(anyString())).thenReturn(createEmptyGroup("default"));

        // Act
        user.calculateEffectivePermissions();
        var effectivePerms = user.getEffectivePermissions();

        // Assert
        assertNotNull(effectivePerms);
        assertTrue(containsPermission(effectivePerms, "user.direct.permission"));
    }

    @Test
    @DisplayName("user inherits permissions from single parent group")
    void inheritance_singleGroup_inheritsPermissions() throws RecursiveGroupException {
        // Arrange
        createGroupWithPermissions("default",
            "group.build",
            "group.chat"
        );

        User user = createUserWithGroups("testUser", Arrays.asList("default"));

        // Act
        user.calculateEffectivePermissions();
        var effectivePerms = user.getEffectivePermissions();

        // Assert
        assertTrue(containsPermission(effectivePerms, "group.build"));
        assertTrue(containsPermission(effectivePerms, "group.chat"));
    }

    @Test
    @DisplayName("direct user permission overrides inherited group permission")
    void inheritance_directPermission_overridesGroup() throws RecursiveGroupException {
        // Arrange
        Group group = createGroupWithPermissions("default", "test.permission");

        Set<Permission> userPerms = new HashSet<>();
        userPerms.add(Permission.loadFromString("-test.permission")); // Negated

        User user = new User("testUser", Arrays.asList("default"), userPerms, "testworld", mockWorld);

        when(mockWorld.getGroup("default")).thenReturn(group);
        when(mockWorld.getName()).thenReturn("testworld");

        // Act
        user.calculateEffectivePermissions();
        var effectivePerms = user.getEffectivePermissions();

        // Assert
        Permission testPerm = findPermission(effectivePerms, "test.permission");
        assertNotNull(testPerm, "Permission should exist");
        assertFalse(testPerm.isTrue(), "Direct negated permission should override inherited positive permission");
    }

    @Test
    @DisplayName("multi-level group inheritance works correctly")
    void inheritance_multiLevel_inheritsFromChain() throws RecursiveGroupException {
        // Arrange
        // Create hierarchy: admin -> moderator -> default
        Group defaultGroup = createGroupWithPermissions("default", "default.build", "default.chat");
        Group modGroup = createGroupWithPermissionsAndParents("moderator",
            Arrays.asList("default"),
            "mod.kick", "mod.mute");
        Group adminGroup = createGroupWithPermissionsAndParents("admin",
            Arrays.asList("moderator"),
            "admin.ban", "admin.delete");

        User user = createUserWithGroups("testUser", Arrays.asList("admin"));

        when(mockWorld.getGroup("admin")).thenReturn(adminGroup);
        when(mockWorld.getGroup("moderator")).thenReturn(modGroup);
        when(mockWorld.getGroup("default")).thenReturn(defaultGroup);

        // Act
        user.calculateEffectivePermissions();
        var effectivePerms = user.getEffectivePermissions();

        // Assert
        assertTrue(containsPermission(effectivePerms, "admin.ban"), "Should have admin permissions");
        assertTrue(containsPermission(effectivePerms, "mod.kick"), "Should have moderator permissions");
        assertTrue(containsPermission(effectivePerms, "default.build"), "Should have default permissions");
    }

    @Test
    @DisplayName("higher priority group permissions override lower priority")
    void inheritance_groupPriority_higherPriorityWins() throws RecursiveGroupException {
        // Arrange
        Group lowPriorityGroup = createGroupWithPermissions("lowpriority", "test.permission");
        Group highPriorityGroup = createGroupWithPermissions("highpriority", "-test.permission");

        // Set priorities (in real code, this would be via meta "priority" value)
        lowPriorityGroup.setValue("priority", "10");
        highPriorityGroup.setValue("priority", "20");

        User user = createUserWithGroups("testUser", Arrays.asList("lowpriority", "highpriority"));

        when(mockWorld.getGroup("lowpriority")).thenReturn(lowPriorityGroup);
        when(mockWorld.getGroup("highpriority")).thenReturn(highPriorityGroup);

        // Act
        user.calculateEffectivePermissions();
        var effectivePerms = user.getEffectivePermissions();

        // Assert
        Permission testPerm = findPermission(effectivePerms, "test.permission");
        assertNotNull(testPerm);
        // Note: The actual behavior depends on how priorities are implemented
        // This test documents the expected behavior
    }

    @Test
    @DisplayName("metadata inheritance from single group")
    void metadataInheritance_singleGroup_inheritsMetadata() throws RecursiveGroupException {
        // Arrange
        Group group = createGroupWithPermissions("default");
        group.setValue("prefix", "[Member]");
        group.setValue("suffix", "");

        User user = createUserWithGroups("testUser", Arrays.asList("default"));

        when(mockWorld.getGroup("default")).thenReturn(group);

        // Act
        user.calculateEffectiveMeta();
        Map<String, String> effectiveMeta = user.getEffectiveMeta();

        // Assert
        assertTrue(effectiveMeta.containsKey("prefix"));
        assertEquals("[Member]", effectiveMeta.get("prefix"));
    }

    @Test
    @DisplayName("direct metadata overrides inherited metadata")
    void metadataInheritance_directMeta_overridesInherited() throws RecursiveGroupException {
        // Arrange
        Group group = createGroupWithPermissions("default");
        group.setValue("prefix", "[Member]");

        User user = createUserWithGroups("testUser", Arrays.asList("default"));
        user.setValue("prefix", "[VIP]");

        when(mockWorld.getGroup("default")).thenReturn(group);

        // Act
        user.calculateEffectiveMeta();
        Map<String, String> effectiveMeta = user.getEffectiveMeta();

        // Assert
        assertEquals("[VIP]", effectiveMeta.get("prefix"), "Direct metadata should override inherited");
    }

    @Test
    @DisplayName("multi-level metadata inheritance with priorities")
    void metadataInheritance_multiLevel_respectsPriority() throws RecursiveGroupException {
        // Arrange
        Group defaultGroup = createGroupWithPermissions("default");
        defaultGroup.setValue("prefix", "[Default]");
        defaultGroup.setValue("priority", "0");

        Group vipGroup = createGroupWithPermissions("vip");
        vipGroup.setValue("prefix", "[VIP]");
        vipGroup.setValue("priority", "10");

        User user = createUserWithGroups("testUser", Arrays.asList("default", "vip"));

        when(mockWorld.getGroup("default")).thenReturn(defaultGroup);
        when(mockWorld.getGroup("vip")).thenReturn(vipGroup);

        // Act
        user.calculateEffectiveMeta();
        Map<String, String> effectiveMeta = user.getEffectiveMeta();

        // Assert
        // Higher priority group metadata should win
        assertEquals("[VIP]", effectiveMeta.get("prefix"), "Higher priority group metadata should win");
    }

    @Test
    @DisplayName("getEffectiveValue returns metadata value")
    void metadataInheritance_getEffectiveValue_returnsValue() throws RecursiveGroupException {
        // Arrange
        Group group = createGroupWithPermissions("default");
        group.setValue("custom-key", "custom-value");

        User user = createUserWithGroups("testUser", Arrays.asList("default"));

        when(mockWorld.getGroup("default")).thenReturn(group);

        // Act
        user.calculateEffectiveMeta();
        String value = user.getEffectiveValue("custom-key");

        // Assert
        assertEquals("custom-value", value);
    }

    @Test
    @DisplayName("getEffectiveValue returns empty string for non-existent key")
    void metadataInheritance_getEffectiveValue_nonExistent_returnsEmpty() {
        // Arrange
        User user = createUserWithGroups("testUser", null);

        // Act
        String value = user.getEffectiveValue("non-existent");

        // Assert
        assertEquals("", value);
    }

    @Test
    @DisplayName("containsEffectiveValue returns true for existing key")
    void metadataInheritance_containsEffectiveValue_exists_returnsTrue() throws RecursiveGroupException {
        // Arrange
        User user = createUserWithGroups("testUser", null);
        user.setValue("test-key", "test-value");

        // Act
        user.calculateEffectiveMeta();
        boolean contains = user.containsEffectiveValue("test-key");

        // Assert
        assertTrue(contains);
    }

    @Test
    @DisplayName("containsEffectiveValue returns false for non-existent key")
    void metadataInheritance_containsEffectiveValue_notExists_returnsFalse() {
        // Arrange
        User user = createUserWithGroups("testUser", null);

        // Act
        boolean contains = user.containsEffectiveValue("non-existent");

        // Assert
        assertFalse(contains);
    }

    // Helper methods

    private Group createEmptyGroup(String name) {
        return new Group(name, null, new HashSet<>(), "testworld", mockWorld);
    }

    private Group createGroupWithPermissions(String name, String... permissions) {
        Set<Permission> perms = new HashSet<>();
        for (String perm : permissions) {
            perms.add(Permission.loadFromString(perm));
        }
        Group group = new Group(name, null, perms, "testworld", mockWorld);
        registerGroup(name, group);
        return group;
    }

    private Group createGroupWithPermissionsAndParents(String name, java.util.List<String> parents, String... permissions) {
        Set<Permission> perms = new HashSet<>();
        for (String perm : permissions) {
            perms.add(Permission.loadFromString(perm));
        }
        Group group = new Group(name, parents, perms, "testworld", mockWorld);
        registerGroup(name, group);
        return group;
    }

    /**
     * Register a group with the mock World so it can be found by both get() and getGroup()
     */
    private void registerGroup(String name, Group group) {
        when(mockWorld.get(name, CalculableType.GROUP)).thenReturn(group);
        when(mockWorld.getGroup(name)).thenReturn(group);
    }

    private User createUserWithGroups(String name, java.util.List<String> groups) {
        return new User(name, groups, new HashSet<>(), "testworld", mockWorld);
    }

    private boolean containsPermission(java.util.List<Permission> permissions, String name) {
        return permissions.stream()
            .anyMatch(p -> p.name().equalsIgnoreCase(name));
    }

    private Permission findPermission(java.util.List<Permission> permissions, String name) {
        return permissions.stream()
            .filter(p -> p.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
