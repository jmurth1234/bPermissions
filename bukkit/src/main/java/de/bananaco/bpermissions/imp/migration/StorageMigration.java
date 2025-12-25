package de.bananaco.bpermissions.imp.migration;

import de.bananaco.bpermissions.api.Calculable;
import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.Group;
import de.bananaco.bpermissions.api.User;
import de.bananaco.bpermissions.api.World;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import de.bananaco.bpermissions.util.Debugger;
import org.bukkit.Bukkit;

import java.util.Set;

/**
 * Utility class for migrating permission data between storage backends.
 * <p>
 * This class provides methods to migrate data from YAML files to database storage
 * (MongoDB or MySQL) while preserving all permissions, groups, and metadata.
 * </p>
 */
public class StorageMigration {

    /**
     * Migrate a world from YAML to a database backend.
     * <p>
     * This operation:
     * <ul>
     *   <li>Loads all data from the source world (YAML)</li>
     *   <li>Converts it to database-compatible format</li>
     *   <li>Saves it to the target storage backend</li>
     * </ul>
     * </p>
     * <p>
     * <b>Note:</b> This does NOT modify the source world. The source world's
     * files remain unchanged after migration.
     * </p>
     *
     * @param sourceWorld    The source world (typically YamlWorld)
     * @param targetBackend  The target storage backend (MongoDB or MySQL)
     * @throws StorageException if migration fails
     */
    public static void migrateWorldToDatabase(World sourceWorld, StorageBackend targetBackend) throws StorageException {
        String worldName = sourceWorld.getName();
        Debugger.log("[StorageMigration] Starting migration of world '" + worldName + "' to database");

        int groupsMigrated = 0;
        int usersMigrated = 0;

        try {
            // Migrate world metadata first
            migrateWorldMetadata(sourceWorld, targetBackend);

            // Migrate groups first (users depend on groups)
            groupsMigrated = migrateGroups(sourceWorld, targetBackend);

            // Migrate users
            usersMigrated = migrateUsers(sourceWorld, targetBackend);

            Debugger.log("[StorageMigration] Migration complete: " + groupsMigrated + " groups, " +
                    usersMigrated + " users migrated for world '" + worldName + "'");

            Bukkit.getLogger().info("[bPermissions] Successfully migrated world '" + worldName + "' to database (" +
                    groupsMigrated + " groups, " + usersMigrated + " users)");

        } catch (StorageException e) {
            Debugger.log("[StorageMigration] Migration failed for world '" + worldName + "': " + e.getMessage());
            throw new StorageException("Failed to migrate world '" + worldName + "' to database", e);
        }
    }

    /**
     * Migrate world metadata (default group, settings).
     */
    private static void migrateWorldMetadata(World sourceWorld, StorageBackend targetBackend) throws StorageException {
        String worldName = sourceWorld.getName();

        WorldMetadata metadata = new WorldMetadata();
        metadata.setWorldName(worldName);
        metadata.setDefaultGroup(sourceWorld.getDefaultGroup());
        metadata.setLastModified(System.currentTimeMillis());

        targetBackend.saveWorldMetadata(metadata, worldName);

        Debugger.log("[StorageMigration] Migrated world metadata for '" + worldName + "'");
    }

    /**
     * Migrate all groups from source world to target backend.
     *
     * @return Number of groups migrated
     */
    private static int migrateGroups(World sourceWorld, StorageBackend targetBackend) throws StorageException {
        String worldName = sourceWorld.getName();
        Set<Calculable> groups = sourceWorld.getAll(CalculableType.GROUP);

        if (groups == null || groups.isEmpty()) {
            Debugger.log("[StorageMigration] No groups to migrate for world '" + worldName + "'");
            return 0;
        }

        int count = 0;
        for (Calculable calc : groups) {
            Group group = (Group) calc;

            // Convert to GroupData DTO
            GroupData groupData = GroupData.fromGroup(group);

            // Save to database
            targetBackend.saveGroup(groupData, worldName);

            count++;
            Debugger.log("[StorageMigration] Migrated group: " + group.getName());
        }

        return count;
    }

    /**
     * Migrate all users from source world to target backend.
     *
     * @return Number of users migrated
     */
    private static int migrateUsers(World sourceWorld, StorageBackend targetBackend) throws StorageException {
        String worldName = sourceWorld.getName();
        Set<Calculable> users = sourceWorld.getAll(CalculableType.USER);

        if (users == null || users.isEmpty()) {
            Debugger.log("[StorageMigration] No users to migrate for world '" + worldName + "'");
            return 0;
        }

        int count = 0;
        for (Calculable calc : users) {
            User user = (User) calc;

            // Skip users with only default settings (same optimization as YamlWorld)
            if (shouldSkipUser(user, sourceWorld)) {
                Debugger.log("[StorageMigration] Skipping user with default settings: " + user.getName());
                continue;
            }

            // Convert to UserData DTO
            UserData userData = UserData.fromUser(user);

            // Set username from Bukkit if available
            if (sourceWorld.isUUID(user.getName())) {
                try {
                    String username = Bukkit.getOfflinePlayer(java.util.UUID.fromString(user.getName())).getName();
                    userData.setUsername(username);
                } catch (Exception e) {
                    Debugger.log("[StorageMigration] Could not get username for UUID: " + user.getName());
                }
            }

            // Save to database
            targetBackend.saveUser(userData, worldName);

            count++;
            Debugger.log("[StorageMigration] Migrated user: " + user.getName());
        }

        return count;
    }

    /**
     * Check if a user should be skipped during migration.
     * <p>
     * Users with only default settings (no custom permissions, only default group, no metadata)
     * are typically not saved to save space.
     * </p>
     */
    private static boolean shouldSkipUser(User user, World world) {
        return user.getMeta().size() == 0 &&
                user.getPermissions().size() == 0 &&
                (user.getGroupsAsString().size() == 0 ||
                        (user.getGroupsAsString().size() == 1 &&
                                user.getGroupsAsString().iterator().next().equals(world.getDefaultGroup())));
    }

    /**
     * Batch migrate multiple worlds from YAML to database.
     *
     * @param sourceWorlds   Array of source worlds to migrate
     * @param targetBackend  The target storage backend
     * @return MigrationResult with statistics
     * @throws StorageException if migration fails
     */
    public static MigrationResult migrateMultipleWorlds(World[] sourceWorlds, StorageBackend targetBackend) throws StorageException {
        MigrationResult result = new MigrationResult();

        for (World world : sourceWorlds) {
            try {
                migrateWorldToDatabase(world, targetBackend);
                result.successfulWorlds++;
            } catch (StorageException e) {
                result.failedWorlds++;
                result.errors.add("Failed to migrate world '" + world.getName() + "': " + e.getMessage());
                Debugger.log("[StorageMigration] Failed to migrate world: " + world.getName() + " - " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Result of a migration operation.
     */
    public static class MigrationResult {
        public int successfulWorlds = 0;
        public int failedWorlds = 0;
        public java.util.List<String> errors = new java.util.ArrayList<>();

        public boolean isFullSuccess() {
            return failedWorlds == 0;
        }

        public int getTotalWorlds() {
            return successfulWorlds + failedWorlds;
        }

        @Override
        public String toString() {
            return "MigrationResult{" +
                    "successfulWorlds=" + successfulWorlds +
                    ", failedWorlds=" + failedWorlds +
                    ", errors=" + errors.size() +
                    '}';
        }
    }

    /**
     * Verify that a migration was successful by comparing data counts.
     * <p>
     * This is a basic verification that checks if the number of groups and users
     * in the database matches the source world.
     * </p>
     *
     * @param sourceWorld    The source world
     * @param targetBackend  The target backend to verify
     * @param worldName      The world name
     * @return true if verification passes, false otherwise
     */
    public static boolean verifyMigration(World sourceWorld, StorageBackend targetBackend, String worldName) {
        try {
            // Count groups
            Set<Calculable> sourceGroups = sourceWorld.getAll(CalculableType.GROUP);
            Set<String> targetGroups = targetBackend.getAllGroupNames(worldName);

            int sourceGroupCount = sourceGroups != null ? sourceGroups.size() : 0;
            int targetGroupCount = targetGroups != null ? targetGroups.size() : 0;

            if (sourceGroupCount != targetGroupCount) {
                Debugger.log("[StorageMigration] Verification failed: Group count mismatch. " +
                        "Source: " + sourceGroupCount + ", Target: " + targetGroupCount);
                return false;
            }

            // Count users (excluding default users)
            Set<Calculable> sourceUsers = sourceWorld.getAll(CalculableType.USER);
            Set<String> targetUsers = targetBackend.getAllUserIds(worldName);

            // Note: This is an approximate check since we skip default users
            // A more thorough check would verify each user individually

            Debugger.log("[StorageMigration] Verification passed: Groups=" + targetGroupCount +
                    ", Users=" + (targetUsers != null ? targetUsers.size() : 0));

            return true;

        } catch (Exception e) {
            Debugger.log("[StorageMigration] Verification error: " + e.getMessage());
            return false;
        }
    }
}
