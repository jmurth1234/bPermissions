package de.bananaco.bpermissions.imp;

import de.bananaco.bpermissions.api.*;
import de.bananaco.bpermissions.api.storage.PollingSync;
import de.bananaco.bpermissions.api.storage.StorageBackend;
import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.api.storage.dto.GroupData;
import de.bananaco.bpermissions.api.storage.dto.UserData;
import de.bananaco.bpermissions.api.storage.dto.WorldMetadata;
import de.bananaco.bpermissions.util.Debugger;
import de.bananaco.bpermissions.util.loadmanager.MainThread;
import de.bananaco.bpermissions.util.loadmanager.TaskRunnable;
import de.bananaco.bpermissions.util.loadmanager.TaskRunnable.TaskType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Database-backed implementation of World.
 * <p>
 * This class extends {@link World} and uses a {@link StorageBackend} to persist
 * permission data to a database (MongoDB, MySQL, etc.) instead of YAML files.
 * </p>
 * <p>
 * Like {@link YamlWorld}, this implementation uses async loading/saving via
 * {@link MainThread} and supports lazy loading of offline users. It also
 * includes a {@link PollingSync} component to detect changes made by external
 * sources (other servers, web interfaces).
 * </p>
 */
public class DatabaseWorld extends World {

    private final StorageBackend backend;
    private final Permissions permissions;
    private final WorldManager wm = WorldManager.getInstance();
    private final PollingSync pollingSync;

    private String defaultGroup = "default";
    private boolean error = false;

    /**
     * Create a new DatabaseWorld.
     *
     * @param worldName      The world name
     * @param permissions    The Permissions plugin instance
     * @param backend        The storage backend to use
     * @param pollInterval   Polling interval in seconds for change detection
     */
    public DatabaseWorld(String worldName, Permissions permissions, StorageBackend backend, int pollInterval) {
        super(worldName);
        this.permissions = permissions;
        this.backend = backend;
        this.pollingSync = new PollingSync(this, backend, pollInterval);
    }

    @Override
    public void setFiles() {
        // No files for database backend
        // This method exists for compatibility with the World interface
    }

    @Override
    public String getDefaultGroup() {
        return defaultGroup;
    }

    @Override
    public void setDefaultGroup(String group) {
        this.defaultGroup = group;

        // Save to database
        try {
            WorldMetadata metadata = backend.loadWorldMetadata(getName());
            if (metadata == null) {
                metadata = new WorldMetadata();
                metadata.setWorldName(getName());
            }
            metadata.setDefaultGroup(group);
            backend.saveWorldMetadata(metadata, getName());
        } catch (StorageException e) {
            Debugger.log("[DatabaseWorld] Failed to save default group: " + e.getMessage());
        }
    }

    @Override
    public boolean load() {
        if (MainThread.getInstance() == null) {
            Debugger.log("[DatabaseWorld] MainThread not available");
            return false;
        }

        try {
            // Schedule async load
            TaskRunnable loadTask = new TaskRunnable() {
                @Override
                public TaskType getType() {
                    return TaskType.LOAD;
                }

                @Override
                public void run() {
                    try {
                        loadUnsafe();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            MainThread.getInstance().schedule(loadTask);

            // Start polling for external changes
            pollingSync.start();

            error = false;
            return true;

        } catch (Exception e) {
            error = true;
            Bukkit.getServer().broadcastMessage(ChatColor.RED +
                    "Permissions for world: " + getName() + " did not load correctly! Please consult server.log");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load data from the database (called async).
     */
    private synchronized void loadUnsafe() throws Exception {
        long startTime = System.currentTimeMillis();

        // Temporarily disable auto-save during load
        boolean autoSave = wm.getAutoSave();
        wm.setAutoSave(false);

        try {
            // Load world metadata
            WorldMetadata metadata = backend.loadWorldMetadata(getName());
            if (metadata != null) {
                defaultGroup = metadata.getDefaultGroup();
            } else {
                // Create default metadata
                metadata = new WorldMetadata();
                metadata.setWorldName(getName());
                metadata.setDefaultGroup(defaultGroup);
                backend.saveWorldMetadata(metadata, getName());
            }

            // Load ALL groups (groups are small and needed for permission calculation)
            Map<String, GroupData> groupsData = backend.loadAllGroups(getName());
            clearGroups();

            for (GroupData groupData : groupsData.values()) {
                Group group = createGroupFromData(groupData);
                add(group);
                group.setLoaded();
            }

            Debugger.log("[DatabaseWorld] Loaded " + groupsData.size() + " groups for " + getName());

            // Load ONLY ONLINE users (optimization - same as YamlWorld)
            int usersLoaded = 0;
            for (Player player : permissions.getServer().getOnlinePlayers()) {
                if (player.getWorld().getName().equalsIgnoreCase(getName())) {
                    String uuid = player.getUniqueId().toString();
                    if (loadUserFromBackend(uuid)) {
                        usersLoaded++;
                    }
                }
            }

            Debugger.log("[DatabaseWorld] Loaded " + usersLoaded + " users for " + getName());

            // Setup online players
            for (Player player : permissions.getServer().getOnlinePlayers()) {
                if (player.getWorld().getName().equalsIgnoreCase(getName())) {
                    String uuid = player.getUniqueId().toString();
                    setupPlayer(uuid);
                }
            }

            long endTime = System.currentTimeMillis();
            Debugger.log("[DatabaseWorld] Loading " + getName() + " took " + (endTime - startTime) + "ms");

            Bukkit.getLogger().info("[bPermissions] Permissions for world " + getName() + " has loaded from database!");

        } finally {
            // Restore auto-save setting
            wm.setAutoSave(autoSave);
        }
    }

    /**
     * Load a single user from the database.
     *
     * @param uuid User's UUID
     * @return true if loaded, false if not found
     */
    private boolean loadUserFromBackend(String uuid) throws StorageException {
        UserData userData = backend.loadUser(uuid, getName());

        if (userData == null) {
            return false;
        }

        User user = createUserFromData(userData);
        add(user);
        user.setLoaded();

        try {
            user.calculateMappedPermissions();
            user.calculateEffectiveMeta();
        } catch (RecursiveGroupException e) {
            Debugger.log("[DatabaseWorld] Recursive group error for user " + uuid + ": " + e.getMessage());
        }

        return true;
    }

    /**
     * Create a User object from UserData.
     */
    private User createUserFromData(UserData userData) {
        List<String> groupsList = new ArrayList<>(userData.getGroups());
        Set<Permission> permissions = Permission.loadFromString(new ArrayList<>(userData.getPermissions()));

        User user = new User(userData.getUuid(), groupsList, permissions, getName(), this);

        // Load metadata
        for (Map.Entry<String, String> entry : userData.getMetadata().entrySet()) {
            user.setValue(entry.getKey(), entry.getValue());
        }

        return user;
    }

    /**
     * Create a Group object from GroupData.
     */
    private Group createGroupFromData(GroupData groupData) {
        List<String> groupsList = new ArrayList<>(groupData.getGroups());
        Set<Permission> permissions = Permission.loadFromString(new ArrayList<>(groupData.getPermissions()));

        Group group = new Group(groupData.getName(), groupsList, permissions, getName(), this);

        // Load metadata
        for (Map.Entry<String, String> entry : groupData.getMetadata().entrySet()) {
            group.setValue(entry.getKey(), entry.getValue());
        }

        return group;
    }

    @Override
    public boolean save() {
        if (MainThread.getInstance() == null) {
            Debugger.log("[DatabaseWorld] MainThread not available");
            return false;
        }

        if (error) {
            Bukkit.getServer().broadcastMessage(ChatColor.RED +
                    "Permissions for world: " + getName() + " did not load correctly, please consult server.log.");
            return false;
        }

        try {
            // Schedule async save
            TaskRunnable saveTask = new TaskRunnable() {
                @Override
                public TaskType getType() {
                    return TaskType.SAVE;
                }

                @Override
                public void run() {
                    try {
                        saveUnsafe();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            MainThread.getInstance().schedule(saveTask);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save data to the database (called async).
     */
    private synchronized void saveUnsafe() throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            // Save world metadata
            WorldMetadata metadata = new WorldMetadata();
            metadata.setWorldName(getName());
            metadata.setDefaultGroup(defaultGroup);
            backend.saveWorldMetadata(metadata, getName());

            // Save all loaded users
            Set<Calculable> users = getAll(CalculableType.USER);
            int usersSaved = 0;
            for (Calculable calc : users) {
                User user = (User) calc;
                UserData userData = UserData.fromUser(user);

                // Set username (fetched from Bukkit)
                if (isUUID(user.getName())) {
                    String username = Bukkit.getOfflinePlayer(UUID.fromString(user.getName())).getName();
                    userData.setUsername(username);
                }

                // Don't save users with only default settings (same optimization as YamlWorld)
                if (user.getMeta().size() == 0 &&
                        user.getPermissions().size() == 0 &&
                        (user.getGroupsAsString().size() == 0 ||
                                (user.getGroupsAsString().size() == 1 &&
                                        user.getGroupsAsString().iterator().next().equals(getDefaultGroup())))) {
                    continue;
                }

                backend.saveUser(userData, getName());
                usersSaved++;
            }

            // Save all groups
            Set<Calculable> groups = getAll(CalculableType.GROUP);
            int groupsSaved = 0;
            for (Calculable calc : groups) {
                Group group = (Group) calc;
                GroupData groupData = GroupData.fromGroup(group);
                backend.saveGroup(groupData, getName());
                groupsSaved++;
            }

            long endTime = System.currentTimeMillis();
            Debugger.log("[DatabaseWorld] Saved " + usersSaved + " users and " + groupsSaved +
                    " groups for " + getName() + " in " + (endTime - startTime) + "ms");

        } catch (StorageException e) {
            Debugger.log("[DatabaseWorld] Error saving world " + getName() + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean loadOne(String name, CalculableType type) {
        return loadCalculableWithLookup(name, name, type);
    }

    @Override
    public boolean loadCalculableWithLookup(String lookupName, String name, CalculableType type) {
        long startTime = System.currentTimeMillis();

        if (contains(lookupName, type)) {
            return true;
        }

        if (!storeContains(lookupName, type)) {
            return false;
        }

        try {
            if (type == CalculableType.USER) {
                // Load user from database
                UserData userData = backend.loadUser(lookupName, getName());
                if (userData == null) {
                    return false;
                }

                User user = createUserFromData(userData);

                // If lookup name differs from actual name (username vs UUID migration)
                if (!lookupName.equals(name)) {
                    user = new User(name, new ArrayList<>(userData.getGroups()),
                            Permission.loadFromString(new ArrayList<>(userData.getPermissions())),
                            getName(), this);

                    // Load metadata
                    for (Map.Entry<String, String> entry : userData.getMetadata().entrySet()) {
                        user.setValue(entry.getKey(), entry.getValue());
                    }
                }

                user.setLoaded();
                remove(user);  // Remove if exists
                add(user);

                try {
                    user.calculateMappedPermissions();
                    user.calculateEffectiveMeta();
                } catch (RecursiveGroupException e) {
                    Debugger.log("[DatabaseWorld] Recursive group error: " + e.getMessage());
                }

            } else if (type == CalculableType.GROUP) {
                // Load group from database
                GroupData groupData = backend.loadGroup(lookupName, getName());
                if (groupData == null) {
                    return false;
                }

                Group group = createGroupFromData(groupData);
                group.setLoaded();
                remove(group);  // Remove if exists
                add(group);
            }

            long endTime = System.currentTimeMillis();
            Debugger.log("[DatabaseWorld] Loading single calculable " + name + " took " + (endTime - startTime) + "ms");
            return true;

        } catch (StorageException e) {
            Debugger.log("[DatabaseWorld] Error loading " + type + " " + name + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean storeContains(String name, CalculableType type) {
        try {
            if (type == CalculableType.USER) {
                return backend.userExists(name, getName());
            } else if (type == CalculableType.GROUP) {
                return backend.groupExists(name, getName());
            }
        } catch (StorageException e) {
            Debugger.log("[DatabaseWorld] Error checking if " + type + " " + name + " exists: " + e.getMessage());
        }
        return false;
    }

    @Override
    public UUID getUUID(String player) {
        return Bukkit.getOfflinePlayer(player).getUniqueId();
    }

    @Override
    public boolean setupPlayer(String player) {
        permissions.handler.setupPlayer(Bukkit.getPlayer(UUID.fromString(player)), true);
        return true;
    }

    @Override
    public boolean setupAll() {
        for (final Player player : permissions.getServer().getOnlinePlayers()) {
            setupPlayer(player.getUniqueId().toString());
        }
        return true;
    }

    @Override
    public boolean isOnline(User user) {
        return Bukkit.getPlayer(UUID.fromString(user.getName())) != null;
    }

    /**
     * Cleanup and shutdown the database world.
     * <p>
     * This stops the polling sync and closes the storage backend connection.
     * </p>
     */
    public void shutdown() {
        // Stop polling
        pollingSync.stop();

        // Close storage backend
        try {
            backend.shutdown();
        } catch (StorageException e) {
            Debugger.log("[DatabaseWorld] Error shutting down storage backend: " + e.getMessage());
        }
    }
}
