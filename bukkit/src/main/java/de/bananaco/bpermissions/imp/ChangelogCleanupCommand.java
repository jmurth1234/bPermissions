package de.bananaco.bpermissions.imp;

import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.WorldManager;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Command for manually cleaning up old changelog entries.
 * <p>
 * This command allows administrators to manually trigger changelog cleanup
 * for database-backed worlds, providing control over data retention.
 * </p>
 * <p>
 * Usage: /changelog cleanup [days] [world]
 * - days: Number of days to retain (default: 30)
 * - world: Specific world name or "all" for all worlds (default: all)
 * </p>
 * <p>
 * Permission: bPermissions.admin.changelog
 * </p>
 */
public class ChangelogCleanupCommand extends BaseCommand implements CommandExecutor {

    private final Permissions plugin;

    public ChangelogCleanupCommand(Permissions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("bPermissions.admin.changelog") && !sender.isOp()) {
            sendMessage(sender, "You don't have permission to use this command!");
            return true;
        }

        // Parse arguments
        int retentionDays = 30;  // Default: 30 days
        String worldFilter = "all";  // Default: all worlds

        if (args.length >= 1) {
            try {
                retentionDays = Integer.parseInt(args[0]);
                if (retentionDays < 0) {
                    sendMessage(sender, "Retention days must be a positive number!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sendMessage(sender, "Invalid number: " + args[0]);
                return true;
            }
        }

        if (args.length >= 2) {
            worldFilter = args[1];
        }

        // Calculate cutoff timestamp
        long cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String cutoffDate = dateFormat.format(new Date(cutoffTimestamp));

        sendMessage(sender, "Starting changelog cleanup...");
        sendMessage(sender, "Retention: " + retentionDays + " days (before " + cutoffDate + ")");
        sendMessage(sender, "World filter: " + worldFilter);

        // Get worlds to process
        WorldManager wm = WorldManager.getInstance();
        java.util.Set<World> worldsToProcess = new java.util.HashSet<>();

        if (worldFilter.equalsIgnoreCase("all")) {
            worldsToProcess.addAll(wm.getAllWorlds());
        } else {
            World world = wm.getWorld(worldFilter);
            if (world == null) {
                sendMessage(sender, "World not found: " + worldFilter);
                return true;
            }
            worldsToProcess.add(world);
        }

        // Process each world
        int totalDeleted = 0;
        int worldsProcessed = 0;
        int worldsSkipped = 0;

        for (World world : worldsToProcess) {
            // Only process DatabaseWorld instances
            if (!(world instanceof DatabaseWorld)) {
                worldsSkipped++;
                continue;
            }

            DatabaseWorld dbWorld = (DatabaseWorld) world;
            StorageBackend backend = dbWorld.getBackend();

            try {
                // Get statistics before cleanup
                long countBefore = backend.getChangelogCount(world.getName());
                long oldestTimestamp = backend.getOldestChangelogTimestamp(world.getName());

                if (countBefore == 0) {
                    sender.sendMessage(ChatColor.GRAY + "  " + world.getName() + ": No changelog entries");
                    worldsProcessed++;
                    continue;
                }

                // Perform cleanup
                int deleted = backend.deleteChangelogBefore(cutoffTimestamp, world.getName());
                long countAfter = backend.getChangelogCount(world.getName());

                // Format output
                String oldestDate = oldestTimestamp > 0 ? dateFormat.format(new Date(oldestTimestamp)) : "N/A";
                sender.sendMessage(ChatColor.GREEN + "  " + world.getName() + ":");
                sender.sendMessage(ChatColor.GRAY + "    Before: " + countBefore + " entries (oldest: " + oldestDate + ")");
                sender.sendMessage(ChatColor.GRAY + "    Deleted: " + deleted + " entries");
                sender.sendMessage(ChatColor.GRAY + "    After: " + countAfter + " entries");

                totalDeleted += deleted;
                worldsProcessed++;

            } catch (StorageException e) {
                sender.sendMessage(ChatColor.RED + "  " + world.getName() + ": Error - " + e.getMessage());
            }
        }

        // Summary
        sendMessage(sender, "Changelog cleanup completed!");
        sendMessage(sender, "Worlds processed: " + worldsProcessed);
        if (worldsSkipped > 0) {
            sendMessage(sender, "Worlds skipped (YAML): " + worldsSkipped);
        }
        sendMessage(sender, "Total entries deleted: " + totalDeleted);

        return true;
    }
}
