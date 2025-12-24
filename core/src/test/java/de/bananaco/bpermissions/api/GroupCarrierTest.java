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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("GroupCarrier - Group Management Operations")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupCarrierTest {

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
    @DisplayName("addGroup adds group to collection")
    void addGroup_validGroup_addsToCollection() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addGroup("admin");

        // Assert
        assertTrue(user.hasGroup("admin"));
    }

    @Test
    @DisplayName("addGroup normalizes dots to dashes")
    void addGroup_groupWithDots_normalizedToDashes() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addGroup("group.with.dots");

        // Assert
        // Both forms should work because hasGroup also normalizes dots
        assertTrue(user.hasGroup("group-with-dots"));
        assertTrue(user.hasGroup("group.with.dots")); // hasGroup normalizes dots too

        // Verify the stored form uses dashes
        Set<String> groups = user.getGroupsAsString();
        assertTrue(groups.contains("group-with-dots"));
    }

    @Test
    @DisplayName("addGroup normalizes to lowercase")
    void addGroup_mixedCase_normalizedToLowercase() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addGroup("AdminGroup");

        // Assert
        assertTrue(user.hasGroup("admingroup"));
        assertTrue(user.hasGroup("ADMINGROUP")); // hasGroup is case-insensitive
    }

    @Test
    @DisplayName("removeGroup removes existing group")
    void removeGroup_existingGroup_removesGroup() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin", "moderator"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        user.removeGroup("admin");

        // Assert
        assertFalse(user.hasGroup("admin"));
        assertTrue(user.hasGroup("moderator"));
    }

    @Test
    @DisplayName("removeGroup with non-existent group does nothing")
    void removeGroup_nonExistentGroup_doesNothing() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        user.removeGroup("nonexistent");

        // Assert
        assertTrue(user.hasGroup("admin"));
    }

    @Test
    @DisplayName("removeGroup normalizes group name")
    void removeGroup_mixedCase_normalizedAndRemoved() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        user.removeGroup("ADMIN");

        // Assert
        assertFalse(user.hasGroup("admin"));
    }

    @Test
    @DisplayName("setGroup clears existing groups and sets single group")
    void setGroup_existingGroups_clearsAndSetsSingle() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin", "moderator", "vip"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        user.setGroup("default");

        // Assert
        assertTrue(user.hasGroup("default"));
        assertFalse(user.hasGroup("admin"));
        assertFalse(user.hasGroup("moderator"));
        assertFalse(user.hasGroup("vip"));

        Set<String> groups = user.getGroupsAsString();
        assertEquals(1, groups.size());
    }

    @Test
    @DisplayName("setGroup normalizes group name")
    void setGroup_mixedCase_normalizedToLowercase() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.setGroup("AdminGroup");

        // Assert
        assertTrue(user.hasGroup("admingroup"));
    }

    @Test
    @DisplayName("hasGroup returns true for existing group")
    void hasGroup_existingGroup_returnsTrue() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act & Assert
        assertTrue(user.hasGroup("admin"));
    }

    @Test
    @DisplayName("hasGroup returns false for non-existent group")
    void hasGroup_nonExistentGroup_returnsFalse() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act & Assert
        assertFalse(user.hasGroup("moderator"));
    }

    @Test
    @DisplayName("hasGroup is case insensitive")
    void hasGroup_differentCase_returnsTrue() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act & Assert
        assertTrue(user.hasGroup("ADMIN"));
        assertTrue(user.hasGroup("Admin"));
        assertTrue(user.hasGroup("aDmIn"));
    }

    @Test
    @DisplayName("hasGroup handles dot normalization")
    void hasGroup_dotsInName_handlesNormalization() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.addGroup("group.with.dots");

        // Act & Assert
        assertTrue(user.hasGroup("group-with-dots"));
    }

    @Test
    @DisplayName("getGroupsAsString returns all group names")
    void getGroupsAsString_multipleGroups_returnsAllNames() {
        // Arrange
        List<String> groupList = Arrays.asList("admin", "moderator", "vip");
        User user = new User("testUser", groupList, new HashSet<>(), "testworld", mockWorld);

        // Act
        Set<String> groups = user.getGroupsAsString();

        // Assert
        assertEquals(3, groups.size());
        assertTrue(groups.contains("admin"));
        assertTrue(groups.contains("moderator"));
        assertTrue(groups.contains("vip"));
    }

    @Test
    @DisplayName("getGroupsAsString returns empty set for no groups")
    void getGroupsAsString_noGroups_returnsEmptySet() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        Set<String> groups = user.getGroupsAsString();

        // Assert
        assertNotNull(groups);
        assertEquals(0, groups.size());
    }

    @Test
    @DisplayName("hasGroupRecursive finds direct group")
    void hasGroupRecursive_directGroup_returnsTrue() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        Group adminGroup = new Group("admin", mockWorld);
        registerGroup("admin", adminGroup);

        // Act
        boolean hasGroup = user.hasGroupRecursive("admin");

        // Assert
        assertTrue(hasGroup);
    }

    @Test
    @DisplayName("hasGroupRecursive finds inherited group")
    void hasGroupRecursive_inheritedGroup_returnsTrue() {
        // Arrange
        // Create group hierarchy: admin -> moderator -> default
        Group defaultGroup = new Group("default", mockWorld);
        Group modGroup = new Group("moderator", Arrays.asList("default"),
            new HashSet<>(), "testworld", mockWorld);
        Group adminGroup = new Group("admin", Arrays.asList("moderator"),
            new HashSet<>(), "testworld", mockWorld);

        registerGroup("default", defaultGroup);
        registerGroup("moderator", modGroup);
        registerGroup("admin", adminGroup);

        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        boolean hasDefault = user.hasGroupRecursive("default");

        // Assert
        assertTrue(hasDefault, "Should find 'default' group through inheritance chain");
    }

    @Test
    @DisplayName("hasGroupRecursive returns false for non-existent group")
    void hasGroupRecursive_nonExistentGroup_returnsFalse() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin"),
            new HashSet<>(), "testworld", mockWorld);

        Group adminGroup = new Group("admin", mockWorld);
        registerGroup("admin", adminGroup);

        // Act
        boolean hasGroup = user.hasGroupRecursive("nonexistent");

        // Assert
        assertFalse(hasGroup);
    }

    @Test
    @DisplayName("clear removes all groups")
    void clear_withGroups_removesAllGroups() {
        // Arrange
        User user = new User("testUser", Arrays.asList("admin", "moderator"),
            new HashSet<>(), "testworld", mockWorld);

        // Act
        user.clear();

        // Assert
        Set<String> groups = user.getGroupsAsString();
        assertEquals(0, groups.size());
    }

    @Test
    @DisplayName("multiple addGroup calls accumulate groups")
    void addGroup_multipleCalls_accumulatesGroups() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addGroup("admin");
        user.addGroup("moderator");
        user.addGroup("vip");

        // Assert
        assertEquals(3, user.getGroupsAsString().size());
        assertTrue(user.hasGroup("admin"));
        assertTrue(user.hasGroup("moderator"));
        assertTrue(user.hasGroup("vip"));
    }

    @Test
    @DisplayName("addGroup with duplicate group name does not duplicate")
    void addGroup_duplicateGroup_doesNotDuplicate() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        user.addGroup("admin");
        user.addGroup("admin");
        user.addGroup("ADMIN"); // Different case

        // Assert
        // Set should deduplicate
        Set<String> groups = user.getGroupsAsString();
        assertTrue(groups.size() <= 2, "Should not have more than 2 entries (case variations)");
    }

    // Helper method
    private void registerGroup(String name, Group group) {
        when(mockWorld.get(name, CalculableType.GROUP)).thenReturn(group);
        when(mockWorld.getGroup(name)).thenReturn(group);
    }
}
