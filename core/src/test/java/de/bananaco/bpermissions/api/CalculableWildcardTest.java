package de.bananaco.bpermissions.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Calculable - Wildcard Permission Algorithm")
class CalculableWildcardTest {

    @Test
    @DisplayName("exact permission match returns permission value")
    void hasPermission_exactMatch_returnsValue() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.permission", true);

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("exact permission match returns false for negated permission")
    void hasPermission_exactMatchNegated_returnsFalse() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.permission", false);

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("permission check is case insensitive")
    void hasPermission_mixedCase_isCaseInsensitive() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.permission", true);

        // Act
        boolean result = Calculable.hasPermission("Test.Permission", perms);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("wildcard permission matches child permission")
    void hasPermission_wildcardMatch_returnsTrue() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.*", true);

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("wildcard permission matches deep child permission")
    void hasPermission_wildcardMatchDeep_returnsTrue() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.*", true);

        // Act
        boolean result = Calculable.hasPermission("test.sub.deep.permission", perms);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("closer wildcard takes precedence over farther wildcard")
    void hasPermission_multipleWildcards_closerTakesPrecedence() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.*", false);
        perms.put("test.sub.*", true);

        // Act
        boolean result = Calculable.hasPermission("test.sub.permission", perms);

        // Assert
        assertTrue(result, "Closer wildcard test.sub.* should win over test.*");
    }

    @Test
    @DisplayName("exact permission takes precedence over wildcard")
    void hasPermission_exactAndWildcard_exactTakesPrecedence() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.*", true);
        perms.put("test.permission", false);

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertFalse(result, "Exact permission should take precedence over wildcard");
    }

    @Test
    @DisplayName("root wildcard matches any permission")
    void hasPermission_rootWildcard_matchesAnyPermission() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);

        // Act
        boolean result = Calculable.hasPermission("any.random.permission", perms);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("specific permission overrides root wildcard")
    void hasPermission_specificAndRootWildcard_specificWins() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);
        perms.put("blocked.permission", false);

        // Act
        boolean result = Calculable.hasPermission("blocked.permission", perms);

        // Assert
        assertFalse(result, "Specific permission should override root wildcard");
    }

    @Test
    @DisplayName("no matching permission returns false")
    void hasPermission_noMatch_returnsFalse() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("other.permission", true);

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("null node returns false")
    void hasPermission_nullNode_returnsFalse() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("test.permission", true);

        // Act
        boolean result = Calculable.hasPermission(null, perms);

        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @CsvSource({
        "admin.*, admin.kick, true",
        "admin.*, admin.ban.player, true",
        "bukkit.*, bukkit.command.help, true",
        "essentials.*, essentials.teleport, true",
        "world.*, world.build, true",
        "test.*, test, false",  // test.* does not match "test" itself
    })
    @DisplayName("wildcard permission matching - parameterized")
    void hasPermission_wildcardPatterns_matchesCorrectly(String wildcardPerm, String checkPerm, boolean expected) {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put(wildcardPerm, expected);

        // Act
        boolean result = Calculable.hasPermission(checkPerm, perms);

        // Assert
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("empty permission map returns false")
    void hasPermission_emptyMap_returnsFalse() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();

        // Act
        boolean result = Calculable.hasPermission("test.permission", perms);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("single segment permission with wildcard")
    void hasPermission_singleSegmentWithWildcard_matchesCorrectly() {
        // Arrange
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);

        // Act
        boolean result = Calculable.hasPermission("singlepermission", perms);

        // Assert
        assertTrue(result, "Root wildcard should match single-segment permissions");
    }
}
