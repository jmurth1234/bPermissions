package de.bananaco.bpermissions.api.storage;

import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.User;
import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PollingSync.
 */
class PollingSyncTest {

    @Mock
    private World mockWorld;

    @Mock
    private StorageBackend mockBackend;

    @Mock
    private User mockUser;

    private PollingSync pollingSync;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        when(mockWorld.getName()).thenReturn("testworld");
        when(mockWorld.isOnline(any())).thenReturn(false);

        pollingSync = new PollingSync(mockWorld, mockBackend, 1); // 1 second interval for faster tests
    }

    @AfterEach
    void tearDown() throws Exception {
        if (pollingSync != null && pollingSync.isRunning()) {
            pollingSync.stop();
        }
        closeable.close();
    }

    @Test
    void testStartAndStop() {
        assertFalse(pollingSync.isRunning());

        pollingSync.start();
        assertTrue(pollingSync.isRunning());

        pollingSync.stop();
        assertFalse(pollingSync.isRunning());
    }

    @Test
    void testStartTwiceDoesNothing() {
        pollingSync.start();
        assertTrue(pollingSync.isRunning());

        pollingSync.start(); // Should do nothing
        assertTrue(pollingSync.isRunning());

        pollingSync.stop();
    }

    @Test
    void testPollDetectsChanges() throws Exception {
        // Create a change record
        Set<ChangeRecord> changes = new HashSet<>();
        changes.add(new ChangeRecord(
                "testworld",
                CalculableType.USER,
                "uuid-123",
                ChangeRecord.ChangeType.UPDATE,
                System.currentTimeMillis(),
                "other-server"
        ));

        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(changes);
        when(mockWorld.contains("uuid-123", CalculableType.USER)).thenReturn(true);

        pollingSync.start();

        // Wait for at least one poll
        Thread.sleep(1500);

        pollingSync.stop();

        // Verify that changes were requested
        verify(mockBackend, atLeastOnce()).getChangesSince(anyLong(), eq("testworld"));
    }

    @Test
    void testPollReloadsChangedUser() throws Exception {
        String testUuid = UUID.randomUUID().toString();

        Set<ChangeRecord> changes = new HashSet<>();
        changes.add(new ChangeRecord(
                "testworld",
                CalculableType.USER,
                testUuid,
                ChangeRecord.ChangeType.UPDATE,
                System.currentTimeMillis(),
                "other-server"
        ));

        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(changes);
        when(mockWorld.contains(testUuid, CalculableType.USER)).thenReturn(true);
        when(mockWorld.getUser(UUID.fromString(testUuid))).thenReturn(mockUser);
        when(mockWorld.isOnline(mockUser)).thenReturn(true);

        pollingSync.start();
        Thread.sleep(1500);
        pollingSync.stop();

        // Verify that the user was reloaded
        verify(mockWorld, atLeastOnce()).loadOne(testUuid, CalculableType.USER);
        // Verify that the online player was updated
        verify(mockWorld, atLeastOnce()).setupPlayer(testUuid);
    }

    @Test
    void testPollUpdatesAllPlayersOnGroupChange() throws Exception {
        Set<ChangeRecord> changes = new HashSet<>();
        changes.add(new ChangeRecord(
                "testworld",
                CalculableType.GROUP,
                "admin",
                ChangeRecord.ChangeType.UPDATE,
                System.currentTimeMillis(),
                "other-server"
        ));

        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(changes);
        when(mockWorld.contains("admin", CalculableType.GROUP)).thenReturn(true);

        pollingSync.start();
        Thread.sleep(1500);
        pollingSync.stop();

        // Verify that the group was reloaded
        verify(mockWorld, atLeastOnce()).loadOne("admin", CalculableType.GROUP);
        // Verify that all players were updated
        verify(mockWorld, atLeastOnce()).setupAll();
    }

    @Test
    void testPollSkipsUnloadedCalculables() throws Exception {
        Set<ChangeRecord> changes = new HashSet<>();
        changes.add(new ChangeRecord(
                "testworld",
                CalculableType.USER,
                "unloaded-uuid",
                ChangeRecord.ChangeType.UPDATE,
                System.currentTimeMillis(),
                "other-server"
        ));

        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(changes);
        when(mockWorld.contains("unloaded-uuid", CalculableType.USER)).thenReturn(false);

        pollingSync.start();
        Thread.sleep(1500);
        pollingSync.stop();

        // Verify that unloaded users are NOT reloaded (lazy loading)
        verify(mockWorld, never()).loadOne("unloaded-uuid", CalculableType.USER);
    }

    @Test
    void testPollHandlesErrors() throws Exception {
        // Backend throws exception
        when(mockBackend.getChangesSince(anyLong(), eq("testworld")))
                .thenThrow(new StorageException("Test error"));

        pollingSync.start();
        Thread.sleep(1500);

        // Polling should continue despite error
        assertTrue(pollingSync.isRunning());

        pollingSync.stop();
    }

    @Test
    void testPollHandlesEmptyChanges() throws Exception {
        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(new HashSet<>());

        pollingSync.start();
        Thread.sleep(1500);
        pollingSync.stop();

        // Should still poll but not try to apply any changes
        verify(mockBackend, atLeastOnce()).getChangesSince(anyLong(), eq("testworld"));
        verify(mockWorld, never()).loadOne(anyString(), any(CalculableType.class));
    }

    @Test
    void testDeleteChange() throws Exception {
        String testUuid = UUID.randomUUID().toString();

        Set<ChangeRecord> changes = new HashSet<>();
        changes.add(new ChangeRecord(
                "testworld",
                CalculableType.USER,
                testUuid,
                ChangeRecord.ChangeType.DELETE,
                System.currentTimeMillis(),
                "other-server"
        ));

        when(mockBackend.getChangesSince(anyLong(), eq("testworld"))).thenReturn(changes);
        when(mockWorld.contains(testUuid, CalculableType.USER)).thenReturn(true);
        when(mockWorld.getUser(UUID.fromString(testUuid))).thenReturn(mockUser);

        pollingSync.start();
        Thread.sleep(1500);
        pollingSync.stop();

        // Verify that the user was removed
        verify(mockWorld, atLeastOnce()).remove(mockUser);
    }
}
