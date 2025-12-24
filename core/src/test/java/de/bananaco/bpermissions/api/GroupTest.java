package de.bananaco.bpermissions.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Group")
@ExtendWith(MockitoExtension.class)
class GroupTest {

    @Mock
    private World mockWorld;

    @Test
    @DisplayName("constructor with name creates group with no parent groups")
    void constructor_withName_createsGroupWithNoParents() {
        // Arrange
        when(mockWorld.getName()).thenReturn("testworld");

        // Act
        Group group = new Group("admin", mockWorld);

        // Assert
        assertNotNull(group);
        assertEquals("admin", group.getName());
        assertNotNull(group.getGroupsAsString());
    }

    @Test
    @DisplayName("constructor with parent groups creates group with parents")
    void constructor_withParentGroups_createsGroupWithParents() {
        // Arrange
        List<String> parentGroups = Arrays.asList("default", "member");
        Set<Permission> permissions = new HashSet<>();

        // Act
        Group group = new Group("admin", parentGroups, permissions, "testworld", mockWorld);

        // Assert
        assertNotNull(group);
        assertEquals("admin", group.getName());
        assertTrue(group.hasGroup("default"));
        assertTrue(group.hasGroup("member"));
    }

    @Test
    @DisplayName("constructor with permissions creates group with permissions")
    void constructor_withPermissions_createsGroupWithPermissions() {
        // Arrange
        Set<Permission> permissions = new HashSet<>();
        permissions.add(Permission.loadFromString("admin.*"));

        // Act
        Group group = new Group("admin", null, permissions, "testworld", mockWorld);

        // Assert
        assertNotNull(group);
        Set<Permission> groupPerms = group.getPermissions();
        assertEquals(1, groupPerms.size());
    }

    @Test
    @DisplayName("constructor with null parent groups creates group without error")
    void constructor_nullParentGroups_createsGroupWithoutError() {
        // Arrange & Act
        Group group = new Group("admin", null, null, "testworld", mockWorld);

        // Assert
        assertNotNull(group);
        assertNotNull(group.getGroupsAsString());
    }

    @Test
    @DisplayName("getType returns GROUP")
    void getType_group_returnsGroupType() {
        // Arrange
        Group group = new Group("admin", mockWorld);

        // Act
        CalculableType type = group.getType();

        // Assert
        assertEquals(CalculableType.GROUP, type);
    }

    @Test
    @DisplayName("groups can inherit from other groups")
    void addGroup_parentGroup_addsToInheritanceChain() {
        // Arrange
        Group group = new Group("admin", mockWorld);

        // Act
        group.addGroup("moderator");

        // Assert
        assertTrue(group.hasGroup("moderator"));
    }

    @Test
    @DisplayName("group names are normalized to lowercase")
    void addGroup_mixedCase_normalizesToLowercase() {
        // Arrange
        Group group = new Group("admin", mockWorld);

        // Act
        group.addGroup("ModeraTor");

        // Assert
        assertTrue(group.hasGroup("moderator"));
        assertTrue(group.hasGroup("MODERATOR")); // Case insensitive check
    }

    @Test
    @DisplayName("group can have multiple parent groups")
    void addGroup_multipleParents_addsAllParents() {
        // Arrange
        Group group = new Group("admin", mockWorld);

        // Act
        group.addGroup("moderator");
        group.addGroup("builder");
        group.addGroup("vip");

        // Assert
        assertTrue(group.hasGroup("moderator"));
        assertTrue(group.hasGroup("builder"));
        assertTrue(group.hasGroup("vip"));
    }
}
