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

@DisplayName("User")
@ExtendWith(MockitoExtension.class)
class UserTest {

    @Mock
    private World mockWorld;

    @Test
    @DisplayName("constructor with name creates user with no groups")
    void constructor_withName_createsUserWithNoGroups() {
        // Arrange
        when(mockWorld.getName()).thenReturn("testworld");

        // Act
        User user = new User("testUser", mockWorld);

        // Assert
        assertNotNull(user);
        assertEquals("testUser", user.getName());
        assertNotNull(user.getGroupsAsString());
    }

    @Test
    @DisplayName("constructor with groups creates user with groups")
    void constructor_withGroups_createsUserWithGroups() {
        // Arrange
        List<String> groups = Arrays.asList("default", "member");
        Set<Permission> permissions = new HashSet<>();

        // Act
        User user = new User("testUser", groups, permissions, "testworld", mockWorld);

        // Assert
        assertNotNull(user);
        assertEquals("testUser", user.getName());
        assertTrue(user.hasGroup("default"));
        assertTrue(user.hasGroup("member"));
    }

    @Test
    @DisplayName("constructor with permissions creates user with permissions")
    void constructor_withPermissions_createsUserWithPermissions() {
        // Arrange
        Set<Permission> permissions = new HashSet<>();
        permissions.add(Permission.loadFromString("test.permission"));

        // Act
        User user = new User("testUser", null, permissions, "testworld", mockWorld);

        // Assert
        assertNotNull(user);
        Set<Permission> userPerms = user.getPermissions();
        assertEquals(1, userPerms.size());
    }

    @Test
    @DisplayName("constructor with null groups creates user without error")
    void constructor_nullGroups_createsUserWithoutError() {
        // Arrange & Act
        User user = new User("testUser", null, null, "testworld", mockWorld);

        // Assert
        assertNotNull(user);
        assertNotNull(user.getGroupsAsString());
    }

    @Test
    @DisplayName("getType returns USER")
    void getType_user_returnsUserType() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        CalculableType type = user.getType();

        // Assert
        assertEquals(CalculableType.USER, type);
    }

    @Test
    @DisplayName("getPriority with priority meta returns priority value")
    void getPriority_withPriorityMeta_returnsPriorityValue() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.setValue("priority", "10");

        // Act
        int priority = user.getPriority();

        // Assert
        assertEquals(10, priority);
    }

    @Test
    @DisplayName("getPriority without priority meta returns zero")
    void getPriority_noPriorityMeta_returnsZero() {
        // Arrange
        User user = new User("testUser", mockWorld);

        // Act
        int priority = user.getPriority();

        // Assert
        assertEquals(0, priority);
    }

    @Test
    @DisplayName("getPriority with invalid priority meta throws NumberFormatException")
    void getPriority_invalidPriorityMeta_throwsNumberFormatException() {
        // Arrange
        User user = new User("testUser", mockWorld);
        user.setValue("priority", "not-a-number");

        // Act & Assert
        assertThrows(NumberFormatException.class, user::getPriority);
    }
}
