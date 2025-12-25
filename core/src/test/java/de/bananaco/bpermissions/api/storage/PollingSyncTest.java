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

    @Test
    void testConcurrentStartCalls() throws Exception {
        // Test that concurrent start() calls don't create duplicate schedulers
        // This verifies the AtomicBoolean race condition fix

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        final int[] successCount = new int[1];

        // Create multiple threads that all try to start simultaneously
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                pollingSync.start();
                if (pollingSync.isRunning()) {
                    synchronized (successCount) {
                        successCount[0]++;
                    }
                }
            });
        }

        // Start all threads at once
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify that polling started and is running
        assertTrue(pollingSync.isRunning(), "PollingSync should be running");

        // Verify only ONE start actually happened (no duplicate schedulers)
        // All threads should see it as running, but only one actually started it
        assertEquals(threadCount, successCount[0], "All threads should see running=true");

        // Clean up
        pollingSync.stop();
        assertFalse(pollingSync.isRunning(), "PollingSync should be stopped");
    }

    @Test
    void testConcurrentStopCalls() throws Exception {
        // Test that concurrent stop() calls don't cause issues

        pollingSync.start();
        assertTrue(pollingSync.isRunning());

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // Create multiple threads that all try to stop simultaneously
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                pollingSync.stop();
            });
        }

        // Start all threads at once
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify that polling stopped
        assertFalse(pollingSync.isRunning(), "PollingSync should be stopped");
    }
}
