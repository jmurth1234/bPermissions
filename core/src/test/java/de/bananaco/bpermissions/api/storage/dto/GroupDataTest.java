package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.Group;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroupData DTO.
 */
class GroupDataTest {

    private GroupData groupData;
    private static final String TEST_GROUP_NAME = "admin";

    @BeforeEach
    void setUp() {
        Set<String> permissions = new HashSet<>(Arrays.asList("admin.*", "^admin.dangerous"));
        Set<String> inheritedGroups = new HashSet<>(Arrays.asList("moderator", "default"));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("prefix", "&c[Admin]");
        metadata.put("priority", "100");

        groupData = new GroupData(TEST_GROUP_NAME, permissions, inheritedGroups, metadata, System.currentTimeMillis());
    }

    @Test
    void testConstructor() {
        assertNotNull(groupData);
        assertEquals(TEST_GROUP_NAME, groupData.getName());
        assertEquals(2, groupData.getPermissions().size());
        assertEquals(2, groupData.getGroups().size());
        assertEquals(2, groupData.getMetadata().size());
    }

    @Test
    void testDefaultConstructor() {
        GroupData emptyGroup = new GroupData();
        assertNotNull(emptyGroup);
        assertNull(emptyGroup.getName());
        assertNotNull(emptyGroup.getPermissions());
        assertNotNull(emptyGroup.getGroups());
        assertNotNull(emptyGroup.getMetadata());
    }

    @Test
    void testGettersReturnCopies() {
        Set<String> permissions = groupData.getPermissions();
        permissions.add("should.not.affect.original");

        assertEquals(2, groupData.getPermissions().size());
    }

    @Test
    void testFromGroup() {
        Group mockGroup = Mockito.mock(Group.class);
        Mockito.when(mockGroup.getName()).thenReturn(TEST_GROUP_NAME);
        Mockito.when(mockGroup.serialisePermissions()).thenReturn(Arrays.asList("perm1", "perm2"));
        Mockito.when(mockGroup.serialiseGroups()).thenReturn(Arrays.asList("parent1"));

        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("priority", "50");
        Mockito.when(mockGroup.getMeta()).thenReturn(metaMap);

        GroupData result = GroupData.fromGroup(mockGroup);

        assertNotNull(result);
        assertEquals(TEST_GROUP_NAME, result.getName());
        assertEquals(2, result.getPermissions().size());
        assertEquals(1, result.getGroups().size());
        assertEquals("50", result.getMetadata().get("priority"));
    }

    @Test
    void testFromGroupWithNull() {
        GroupData result = GroupData.fromGroup(null);
        assertNull(result);
    }

    @Test
    void testEqualsAndHashCode() {
        GroupData group1 = new GroupData(TEST_GROUP_NAME, new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);
        GroupData group2 = new GroupData(TEST_GROUP_NAME, new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);
        GroupData group3 = new GroupData("different", new HashSet<>(), new HashSet<>(), new HashMap<>(), 0);

        assertEquals(group1, group2);
        assertEquals(group1.hashCode(), group2.hashCode());
        assertNotEquals(group1, group3);
    }

    @Test
    void testToString() {
        String str = groupData.toString();
        assertNotNull(str);
        assertTrue(str.contains(TEST_GROUP_NAME));
        assertTrue(str.contains("GroupData"));
    }

    @Test
    void testNullSafety() {
        GroupData nullSafe = new GroupData("test", null, null, null, 0);
        assertNotNull(nullSafe.getPermissions());
        assertNotNull(nullSafe.getGroups());
        assertNotNull(nullSafe.getMetadata());
    }
}
