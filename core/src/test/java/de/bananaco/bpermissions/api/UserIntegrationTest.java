package de.bananaco.bpermissions.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for User and Group using a REAL World implementation.
 * These tests verify actual behavior without mocking World, testing how
 * users and groups interact in a real world context.
 */
@DisplayName("User/Group Integration - Real World Tests")
class UserIntegrationTest {

    private WorldTest world;
    private WorldManager worldManager;

    @BeforeEach
    void setup() {
        worldManager = WorldManager.getInstance();
        world = new WorldTest("testworld");
        worldManager.worlds.put("testworld", world);
        MetaData.setSorting(true);
        worldManager.setAutoSave(false);
    }

    @AfterEach
    void teardown() {
        worldManager.worlds.clear();
        world.clear();
    }

    // ===== User Metadata Tests with Real World =====

    @Test
    @DisplayName("User setValue with multiple values - REAL WORLD")
    void userSetValue_multipleValues_allPersist() {
        // Arrange
        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.setValue("key1", "value1");
        user.setValue("key2", "value2");
        user.setValue("key3", "value3");

        // Assert
        Map<String, String> meta = user.getMeta();
        assertEquals(3, meta.size(), "All three values should be stored");
        assertEquals("value1", meta.get("key1"));
        assertEquals("value2", meta.get("key2"));
        assertEquals("value3", meta.get("key3"));
    }

    @Test
    @DisplayName("User setValue and getValue with real world")
    void userSetAndGetValue_realWorld_worksCorrectly() {
        // Arrange
        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.setValue("prefix", "[VIP]");
        user.setValue("suffix", "[Member]");

        // Assert
        assertEquals("[VIP]", user.getValue("prefix"));
        assertEquals("[Member]", user.getValue("suffix"));
    }

    @Test
    @DisplayName("User priority setting and retrieval")
    void userPriority_realWorld_parsesCorrectly() {
        // Arrange
        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.setValue("priority", "42");

        // Assert
        assertEquals(42, user.getPriority());
        assertEquals("42", user.getValue("priority"));
    }

    // ===== Group Metadata Tests with Real World =====

    @Test
    @DisplayName("Group setValue with multiple values - REAL WORLD")
    void groupSetValue_multipleValues_allPersist() {
        // Arrange
        Group group = new Group("testGroup", world);
        world.add(group);

        // Act
        group.setValue("key1", "value1");
        group.setValue("key2", "value2");
        group.setValue("priority", "10");

        // Assert
        Map<String, String> meta = group.getMeta();
        assertEquals(3, meta.size());
        assertEquals("value1", meta.get("key1"));
        assertEquals("value2", meta.get("key2"));
        assertEquals("10", meta.get("priority"));
    }

    // ===== User-Group Interaction Tests =====

    @Test
    @DisplayName("User inherits metadata from Group")
    void userInheritsMetadataFromGroup() throws RecursiveGroupException {
        // Arrange
        Group vipGroup = new Group("vip", world);
        vipGroup.setValue("prefix", "[VIP]");
        vipGroup.setValue("color", "gold");
        world.add(vipGroup);

        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.addGroup("vip");
        user.calculateGroups();
        user.calculateEffectiveMeta();

        // Assert
        Map<String, String> effectiveMeta = user.getEffectiveMeta();
        assertEquals("[VIP]", effectiveMeta.get("prefix"), "User should inherit prefix from VIP group");
        assertEquals("gold", effectiveMeta.get("color"), "User should inherit color from VIP group");
    }

    @Test
    @DisplayName("User metadata overrides Group metadata")
    void userMetadataOverridesGroupMetadata() throws RecursiveGroupException {
        // Arrange
        Group group = new Group("members", world);
        group.setValue("prefix", "[Member]");
        world.add(group);

        User user = new User("testUser", world);
        user.setValue("prefix", "[CustomPrefix]");
        world.add(user);

        // Act
        user.addGroup("members");
        user.calculateGroups();
        user.calculateEffectiveMeta();

        // Assert
        Map<String, String> effectiveMeta = user.getEffectiveMeta();
        assertEquals("[CustomPrefix]", effectiveMeta.get("prefix"),
                "User's own prefix should override group prefix");
    }

    @Test
    @DisplayName("User inherits from multiple groups - priority matters")
    void userInheritsFromMultipleGroups() throws RecursiveGroupException {
        // Arrange - Create groups with different priorities
        Group defaultGroup = new Group("default", world);
        defaultGroup.setValue("priority", "1");
        defaultGroup.setValue("prefix", "[Member]");
        world.add(defaultGroup);

        Group vipGroup = new Group("vip", world);
        vipGroup.setValue("priority", "10");
        vipGroup.setValue("prefix", "[VIP]");
        world.add(vipGroup);

        User user = new User("testUser", world);
        world.add(user);

        // Act - Add both groups
        user.addGroup("default");
        user.addGroup("vip");
        user.calculateGroups();
        user.calculateEffectiveMeta();

        // Assert - Higher priority group wins
        Map<String, String> effectiveMeta = user.getEffectiveMeta();
        assertEquals("[VIP]", effectiveMeta.get("prefix"),
                "VIP group has higher priority, its prefix should be used");
    }

    // ===== Permission Tests with Real World =====

    @Test
    @DisplayName("User has direct permission")
    void userHasDirectPermission() throws RecursiveGroupException {
        // Arrange
        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.addPermission("test.permission", true);
        user.calculateMappedPermissions();

        // Assert
        assertTrue(user.hasPermission("test.permission"), "User should have direct permission");
    }

    @Test
    @DisplayName("User inherits permission from Group")
    void userInheritsPermissionFromGroup() throws RecursiveGroupException {
        // Arrange
        Group builders = new Group("builders", world);
        builders.addPermission("build.place", true);
        builders.addPermission("build.break", true);
        world.add(builders);

        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.addGroup("builders");
        user.calculateGroups();
        user.calculateMappedPermissions();

        // Assert
        assertTrue(user.hasPermission("build.place"), "User should inherit build.place from builders group");
        assertTrue(user.hasPermission("build.break"), "User should inherit build.break from builders group");
    }

    @Test
    @DisplayName("User permission overrides Group permission")
    void userPermissionOverridesGroupPermission() throws RecursiveGroupException {
        // Arrange
        Group group = new Group("testers", world);
        group.addPermission("test.feature", true);
        world.add(group);

        User user = new User("testUser", world);
        user.addPermission("test.feature", false); // User explicitly denies
        world.add(user);

        // Act
        user.addGroup("testers");
        user.calculateGroups();
        user.calculateMappedPermissions();

        // Assert
        assertFalse(user.hasPermission("test.feature"),
                "User's explicit denial should override group's grant");
    }

    // ===== Group Inheritance Tests =====

    @Test
    @DisplayName("Group inherits from parent Group")
    void groupInheritsFromParentGroup() throws RecursiveGroupException {
        // Arrange
        Group parentGroup = new Group("moderator", world);
        parentGroup.addPermission("mod.kick", true);
        parentGroup.setValue("prefix", "[Mod]");
        world.add(parentGroup);

        Group childGroup = new Group("admin", world);
        childGroup.setValue("prefix", "[Admin]");
        world.add(childGroup);

        // Act
        childGroup.addGroup("moderator");
        childGroup.calculateGroups();
        childGroup.calculateMappedPermissions();
        childGroup.calculateEffectiveMeta();

        // Assert
        assertTrue(childGroup.hasPermission("mod.kick"), "Admin group should inherit mod.kick from moderator");
        Map<String, String> effectiveMeta = childGroup.getEffectiveMeta();
        assertEquals("[Admin]", effectiveMeta.get("prefix"),
                "Child group's own metadata should take precedence");
    }

    @Test
    @DisplayName("User inherits through nested group hierarchy")
    void userInheritsThroughNestedGroups() throws RecursiveGroupException {
        // Arrange - Create hierarchy: default -> moderator -> admin
        Group defaultGroup = new Group("default", world);
        defaultGroup.addPermission("basic.chat", true);
        defaultGroup.setValue("priority", "1");
        world.add(defaultGroup);

        Group moderator = new Group("moderator", world);
        moderator.addGroup("default");
        moderator.addPermission("mod.kick", true);
        moderator.setValue("priority", "5");
        world.add(moderator);

        Group admin = new Group("admin", world);
        admin.addGroup("moderator");
        admin.addPermission("admin.ban", true);
        admin.setValue("priority", "10");
        world.add(admin);

        User user = new User("testUser", world);
        world.add(user);

        // Act
        user.addGroup("admin");
        user.calculateGroups();
        user.calculateMappedPermissions();

        // Assert - User should have all permissions from entire hierarchy
        assertTrue(user.hasPermission("admin.ban"), "Should have admin permission");
        assertTrue(user.hasPermission("mod.kick"), "Should inherit moderator permission");
        assertTrue(user.hasPermission("basic.chat"), "Should inherit default permission through chain");
    }

    // ===== World Integration Tests =====

    @Test
    @DisplayName("World.get() creates and returns User with default group")
    void worldGet_createsUserWithDefaultGroup() {
        // Act
        User user = world.getUser("newPlayer");

        // Assert
        assertNotNull(user, "World should create user if not exists");
        assertTrue(user.hasGroup("default"), "User should have default group");
    }

    @Test
    @DisplayName("World.getGroup() retrieves existing Group")
    void worldGetGroup_retrievesExistingGroup() {
        // Arrange
        Group originalGroup = new Group("builders", world);
        world.add(originalGroup);

        // Act
        Group retrieved = world.getGroup("builders");

        // Assert
        assertNotNull(retrieved);
        assertEquals("builders", retrieved.getName());
        assertSame(originalGroup, retrieved, "Should return same instance");
    }

    @Test
    @DisplayName("World normalizes group names - dots to dashes")
    void worldNormalizesGroupNames() {
        // Arrange
        Group group = new Group("group.with.dots", world);
        world.add(group);

        // Act
        Group retrieved = world.getGroup("group-with-dashes");

        // Assert
        assertNotNull(retrieved, "World should normalize dots to dashes when retrieving");
    }

    // ===== Clear Operations with Real World =====

    @Test
    @DisplayName("User clearValues removes all metadata")
    void userClearValues_removesAllMetadata() {
        // Arrange
        User user = new User("testUser", world);
        user.setValue("key1", "value1");
        user.setValue("key2", "value2");
        user.setValue("key3", "value3");
        world.add(user);

        // Act
        user.clearValues();

        // Assert
        assertEquals(0, user.getMeta().size());
        assertEquals("", user.getValue("key1"));
    }

    @Test
    @DisplayName("User clear removes all data (groups, permissions, metadata)")
    void userClear_removesAllData() {
        // Arrange
        User user = new User("testUser", world);
        user.addGroup("group1");
        user.addGroup("group2");
        user.addPermission("perm1", true);
        user.addPermission("perm2", false);
        user.setValue("key1", "value1");
        world.add(user);

        // Act
        user.clear();

        // Assert
        assertEquals(0, user.getGroupsAsString().size(), "All groups should be cleared");
        assertEquals(0, user.getPermissions().size(), "All permissions should be cleared");
        assertEquals(0, user.getMeta().size(), "All metadata should be cleared");
    }

    // ===== Effective Meta Calculation Tests =====

    @Test
    @DisplayName("calculateEffectiveMeta combines user and group metadata correctly")
    void calculateEffectiveMeta_combinesCorrectly() throws RecursiveGroupException {
        // Arrange
        Group group = new Group("members", world);
        group.setValue("group_key", "from_group");
        group.setValue("shared_key", "group_value");
        world.add(group);

        User user = new User("testUser", world);
        user.setValue("user_key", "from_user");
        user.setValue("shared_key", "user_value"); // Should override group
        world.add(user);

        // Act
        user.addGroup("members");
        user.calculateGroups();
        user.calculateEffectiveMeta();

        // Assert
        Map<String, String> effectiveMeta = user.getEffectiveMeta();
        assertEquals("from_user", effectiveMeta.get("user_key"));
        assertEquals("from_group", effectiveMeta.get("group_key"));
        assertEquals("user_value", effectiveMeta.get("shared_key"),
                "User value should override group value for shared keys");
    }
}
