package de.bananaco.bpermissions.api.storage;

import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.User;
import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.storage.dto.ChangeRecord;
import de.bananaco.bpermissions.util.Debugger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polling-based synchronization component for detecting external changes.
 * <p>
 * This class periodically polls the database for changes made by other servers
 * or external sources (like web interfaces). When changes are detected, it
 * reloads the affected users/groups and updates online players.
 * </p>
 * <p>
 * This approach provides eventual consistency across multiple servers with a
 * configurable delay (typically 5-10 seconds).
 * </p>
 */
public class PollingSync {

    private final World world;
    private final StorageBackend backend;
    private final int pollIntervalSeconds;
    private final ScheduledExecutorService scheduler;

    private long lastPollTimestamp;
    private volatile boolean running = false;

    /**
     * Create a new PollingSync instance.
     *
     * @param world                The world to sync
     * @param backend              The storage backend to poll
     * @param pollIntervalSeconds  How often to check for changes (in seconds)
     */
    public PollingSync(World world, StorageBackend backend, int pollIntervalSeconds) {
        this.world = world;
        this.backend = backend;
        this.pollIntervalSeconds = Math.max(1, pollIntervalSeconds);  // Minimum 1 second
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new PollingThreadFactory());
        this.lastPollTimestamp = System.currentTimeMillis();
    }

    /**
     * Start polling for changes.
     * <p>
     * This method schedules periodic polls at the configured interval.
     * If already running, this method does nothing.
     * </p>
     */
    public void start() {
        if (running) {
            Debugger.log("[PollingSync] Already running for world: " + world.getName());
            return;
        }

        running = true;
        scheduler.scheduleAtFixedRate(
                this::pollForChanges,
                pollIntervalSeconds,  // Initial delay
                pollIntervalSeconds,  // Period
                TimeUnit.SECONDS
        );

        Debugger.log("[PollingSync] Started for world " + world.getName() +
                " with interval " + pollIntervalSeconds + " seconds");
    }

    /**
     * Stop polling for changes and shutdown the scheduler.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Debugger.log("[PollingSync] Stopped for world: " + world.getName());
    }

    /**
     * Check if the polling sync is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Poll the database for changes and apply them to the world.
     * <p>
     * This method is called periodically by the scheduler.
     * </p>
     */
    private void pollForChanges() {
        try {
            // Get changes since last poll
            Set<ChangeRecord> changes = backend.getChangesSince(lastPollTimestamp, world.getName());

            if (!changes.isEmpty()) {
                Debugger.log("[PollingSync] Detected " + changes.size() + " changes in world " + world.getName());
                applyChanges(changes);
            }

            // Update timestamp for next poll
            lastPollTimestamp = System.currentTimeMillis();

        } catch (Exception e) {
            Debugger.log("[PollingSync] Error polling for changes in world " + world.getName() + ": " + e.getMessage());
            // Don't rethrow - we want polling to continue despite errors
        }
    }

    /**
     * Apply a set of changes to the world.
     *
     * @param changes Set of ChangeRecord objects representing changes
     */
    private void applyChanges(Set<ChangeRecord> changes) {
        for (ChangeRecord change : changes) {
            try {
                applyChange(change);
            } catch (Exception e) {
                Debugger.log("[PollingSync] Error applying change: " + change + " - " + e.getMessage());
            }
        }
    }

    /**
     * Apply a single change to the world.
     *
     * @param change The change to apply
     */
    private void applyChange(ChangeRecord change) {
        String name = change.getCalculableName();
        CalculableType type = change.getCalculableType();
        ChangeRecord.ChangeType changeType = change.getChangeType();

        Debugger.log("[PollingSync] Applying change: " + change);

        // Check if this calculable is currently loaded in memory
        boolean isLoaded = world.contains(name, type);

        switch (changeType) {
            case UPDATE:
            case INSERT:
                if (isLoaded) {
                    // Reload from database to get latest data
                    Debugger.log("[PollingSync] Reloading " + type + " " + name);
                    world.loadOne(name, type);

                    // If it's a user change, update the online player
                    if (type == CalculableType.USER) {
                        updateOnlinePlayer(name);
                    } else if (type == CalculableType.GROUP) {
                        // Group changed - update ALL players that have this group
                        Debugger.log("[PollingSync] Group " + name + " changed, updating all online players");
                        world.setupAll();
                    }
                } else {
                    // Not loaded in memory - will be loaded on-demand when needed
                    Debugger.log("[PollingSync] " + type + " " + name + " not loaded, skipping reload");
                }
                break;

            case DELETE:
                if (isLoaded) {
                    // Remove from memory
                    Debugger.log("[PollingSync] Removing " + type + " " + name + " from memory");
                    if (type == CalculableType.USER) {
                        world.remove(world.getUser(UUID.fromString(name)));
                        updateOnlinePlayer(name);
                    } else if (type == CalculableType.GROUP) {
                        world.remove(world.getGroup(name));
                        world.setupAll();  // Update all players
                    }
                }
                break;

            default:
                Debugger.log("[PollingSync] Unknown change type: " + changeType);
                break;
        }
    }

    /**
     * Update an online player's permissions after their data has changed.
     *
     * @param uuid The player's UUID (as a string)
     */
    private void updateOnlinePlayer(String uuid) {
        try {
            User user = world.getUser(UUID.fromString(uuid));
            if (user != null && world.isOnline(user)) {
                Debugger.log("[PollingSync] Updating online player: " + uuid);
                world.setupPlayer(uuid);
            }
        } catch (Exception e) {
            Debugger.log("[PollingSync] Error updating online player " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Custom thread factory for polling threads.
     * <p>
     * This ensures polling threads have descriptive names for easier debugging.
     * </p>
     */
    private static class PollingThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final int poolId;

        PollingThreadFactory() {
            this.poolId = poolNumber.getAndIncrement();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("bPermissions-PollingSync-" + poolId);
            thread.setDaemon(true);  // Don't prevent JVM shutdown
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
