package de.bananaco.bpermissions.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Permission")
class PermissionTest {

    @Test
    @DisplayName("loadFromString with true permission returns true permission")
    void loadFromString_truePermission_returnsTrue() {
        // Arrange & Act
        Permission perm = Permission.loadFromString("test.permission");

        // Assert
        assertNotNull(perm);
        assertEquals("test.permission", perm.name());
        assertEquals("test.permission", perm.nameLowerCase());
        assertTrue(perm.isTrue());
    }

    @Test
    @DisplayName("loadFromString with negated permission returns false permission")
    void loadFromString_negatedPermission_returnsFalse() {
        // Arrange & Act
        Permission perm = Permission.loadFromString("-test.permission");

        // Assert
        assertNotNull(perm);
        assertEquals("test.permission", perm.name());
        assertEquals("test.permission", perm.nameLowerCase());
        assertFalse(perm.isTrue());
    }

    @Test
    @DisplayName("loadFromString with caret permission returns false permission")
    void loadFromString_caretPermission_returnsFalse() {
        // Arrange & Act
        Permission perm = Permission.loadFromString("^test.permission");

        // Assert
        assertNotNull(perm);
        assertEquals("test.permission", perm.name());
        assertEquals("test.permission", perm.nameLowerCase());
        assertFalse(perm.isTrue());
    }

    @Test
    @DisplayName("loadFromString with null input returns null")
    void loadFromString_nullInput_returnsNull() {
        // Arrange & Act
        Permission perm = Permission.loadFromString((String) null);

        // Assert
        assertNull(perm);
    }

    @Test
    @DisplayName("loadFromString with empty string returns null")
    void loadFromString_emptyString_returnsNull() {
        // Arrange & Act
        Permission perm = Permission.loadFromString("");

        // Assert
        assertNull(perm);
    }

    @Test
    @DisplayName("loadFromString normalizes to lowercase")
    void loadFromString_mixedCase_normalizesToLowercase() {
        // Arrange & Act
        Permission perm = Permission.loadFromString("Test.Permission");

        // Assert
        assertEquals("Test.Permission", perm.name());
        assertEquals("test.permission", perm.nameLowerCase());
    }

    @Test
    @DisplayName("loadWithChildren creates permission with children")
    void loadWithChildren_withChildren_createsPermissionWithChildren() {
        // Arrange
        Map<String, Boolean> children = new HashMap<>();
        children.put("child.one", true);
        children.put("child.two", false);

        // Act
        Permission perm = Permission.loadWithChildren("parent.node", true, children);

        // Assert
        assertNotNull(perm);
        assertEquals("parent.node", perm.name());
        assertTrue(perm.isTrue());

        Map<String, Boolean> resultChildren = perm.getChildren();
        assertNotNull(resultChildren);
        assertEquals(2, resultChildren.size());
        assertTrue(resultChildren.get("child.one"));
        assertFalse(resultChildren.get("child.two"));
    }

    @Test
    @DisplayName("getChildren with no children returns empty map")
    void getChildren_noChildren_returnsEmptyMap() {
        // Arrange
        Permission perm = Permission.loadFromString("test.node");

        // Act
        Map<String, Boolean> children = perm.getChildren();

        // Assert
        assertNotNull(children);
        assertTrue(children.isEmpty());
    }

    @Test
    @DisplayName("equals returns true for same permission")
    void equals_samePermission_returnsTrue() {
        // Arrange
        Permission perm1 = Permission.loadFromString("test.permission");
        Permission perm2 = Permission.loadFromString("test.permission");

        // Act & Assert
        assertEquals(perm1, perm2);
    }

    @Test
    @DisplayName("equals returns false for different permissions")
    void equals_differentPermission_returnsFalse() {
        // Arrange
        Permission perm1 = Permission.loadFromString("test.one");
        Permission perm2 = Permission.loadFromString("test.two");

        // Act & Assert
        assertNotEquals(perm1, perm2);
    }

    @Test
    @DisplayName("toString returns correct string representation")
    void toString_permission_returnsCorrectFormat() {
        // Arrange
        Permission permTrue = Permission.loadFromString("test.permission");
        Permission permFalse = Permission.loadFromString("-test.permission");

        // Act
        String strTrue = permTrue.toString();
        String strFalse = permFalse.toString();

        // Assert
        assertTrue(strTrue.contains("test.permission"));
        assertTrue(strTrue.contains("true"));
        assertTrue(strFalse.contains("test.permission"));
        assertTrue(strFalse.contains("false"));
    }
}
