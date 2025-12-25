package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.CalculableType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChangeRecord DTO.
 */
class ChangeRecordTest {

    @Test
    void testConstructor() {
        ChangeRecord record = new ChangeRecord(
                "world",
                CalculableType.USER,
                "uuid-123",
                ChangeRecord.ChangeType.UPDATE,
                123456789L,
                "server-1"
        );

        assertEquals("world", record.getWorldName());
        assertEquals(CalculableType.USER, record.getCalculableType());
        assertEquals("uuid-123", record.getCalculableName());
        assertEquals(ChangeRecord.ChangeType.UPDATE, record.getChangeType());
        assertEquals(123456789L, record.getTimestamp());
        assertEquals("server-1", record.getServerSource());
    }

    @Test
    void testDefaultConstructor() {
        ChangeRecord record = new ChangeRecord();
        assertNotNull(record);
        assertTrue(record.getTimestamp() > 0);
    }

    @Test
    void testChangeTypes() {
        ChangeRecord insert = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.INSERT, 0, "server");
        ChangeRecord update = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.UPDATE, 0, "server");
        ChangeRecord delete = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.DELETE, 0, "server");

        assertEquals(ChangeRecord.ChangeType.INSERT, insert.getChangeType());
        assertEquals(ChangeRecord.ChangeType.UPDATE, update.getChangeType());
        assertEquals(ChangeRecord.ChangeType.DELETE, delete.getChangeType());
    }

    @Test
    void testCalculableTypes() {
        ChangeRecord userChange = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.UPDATE, 0, "server");
        ChangeRecord groupChange = new ChangeRecord("world", CalculableType.GROUP, "test",
                ChangeRecord.ChangeType.UPDATE, 0, "server");

        assertEquals(CalculableType.USER, userChange.getCalculableType());
        assertEquals(CalculableType.GROUP, groupChange.getCalculableType());
    }

    @Test
    void testEqualsAndHashCode() {
        ChangeRecord record1 = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.UPDATE, 100L, "server-1");
        ChangeRecord record2 = new ChangeRecord("world", CalculableType.USER, "test",
                ChangeRecord.ChangeType.UPDATE, 100L, "server-1");
        ChangeRecord record3 = new ChangeRecord("world", CalculableType.GROUP, "test",
                ChangeRecord.ChangeType.UPDATE, 100L, "server-1");

        assertEquals(record1, record2);
        assertEquals(record1.hashCode(), record2.hashCode());
        assertNotEquals(record1, record3);
    }

    @Test
    void testToString() {
        ChangeRecord record = new ChangeRecord("world", CalculableType.USER, "uuid-123",
                ChangeRecord.ChangeType.UPDATE, 123456789L, "server-1");

        String str = record.toString();
        assertNotNull(str);
        assertTrue(str.contains("world"));
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("UPDATE"));
    }

    @Test
    void testSetters() {
        ChangeRecord record = new ChangeRecord();

        record.setWorldName("testworld");
        record.setCalculableType(CalculableType.GROUP);
        record.setCalculableName("admin");
        record.setChangeType(ChangeRecord.ChangeType.DELETE);
        record.setTimestamp(999L);
        record.setServerSource("test-server");

        assertEquals("testworld", record.getWorldName());
        assertEquals(CalculableType.GROUP, record.getCalculableType());
        assertEquals("admin", record.getCalculableName());
        assertEquals(ChangeRecord.ChangeType.DELETE, record.getChangeType());
        assertEquals(999L, record.getTimestamp());
        assertEquals("test-server", record.getServerSource());
    }
}
