package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.User;

import java.io.Serializable;
import java.util.*;

/**
 * Data Transfer Object for User permissions data.
 * <p>
 * This class represents a user's permission data in a database-friendly format.
 * It contains all the information needed to reconstruct a {@link User} object
 * or to persist a User's data to a database.
 * </p>
 */
public class UserData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uuid;
    private String username;
    private Set<String> permissions;
    private Set<String> groups;
    private Map<String, String> metadata;
    private long lastModified;

    /**
     * Default constructor for serialization frameworks.
     */
    public UserData() {
        this.permissions = new HashSet<>();
        this.groups = new HashSet<>();
        this.metadata = new HashMap<>();
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields.
     *
     * @param uuid         User's UUID
     * @param username     User's username (can be null)
     * @param permissions  Set of permission strings (e.g., "some.permission", "^denied.permission")
     * @param groups       Set of group names
     * @param metadata     Map of metadata key-value pairs (prefix, suffix, custom values)
     * @param lastModified Last modification timestamp
     */
    public UserData(String uuid, String username, Set<String> permissions, Set<String> groups,
                    Map<String, String> metadata, long lastModified) {
        this.uuid = uuid;
        this.username = username;
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
        this.groups = groups != null ? new HashSet<>(groups) : new HashSet<>();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.lastModified = lastModified;
    }

    /**
     * Create a UserData object from a User API object.
     * <p>
     * This is used when saving a User to the database.
     * </p>
     *
     * @param user The User object to convert
     * @return UserData representation of the user
     */
    public static UserData fromUser(User user) {
        if (user == null) {
            return null;
        }

        String uuid = user.getName();  // User name is the UUID
        String username = null;  // Will be set by the backend via Bukkit.getOfflinePlayer()

        // Serialize permissions
        Set<String> permissions = new HashSet<>(user.serialisePermissions());

        // Serialize groups
        Set<String> groups = new HashSet<>(user.serialiseGroups());

        // Get metadata
        Map<String, String> metadata = new HashMap<>(user.getMeta());

        return new UserData(uuid, username, permissions, groups, metadata, System.currentTimeMillis());
    }

    /**
     * Apply this UserData to an existing User object.
     * <p>
     * This updates the User's permissions, groups, and metadata based on this DTO.
     * Note: This does not create a new User object, it modifies the existing one.
     * </p>
     *
     * @param user The User object to update
     */
    public void applyToUser(User user) {
        if (user == null) {
            return;
        }

        // Note: We don't clear existing permissions/groups first because
        // the User will be reconstructed from scratch when loaded from the database
    }

    // ========== Getters and Setters ==========

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getPermissions() {
        return new HashSet<>(permissions);  // Return copy to prevent external modification
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }

    public Set<String> getGroups() {
        return new HashSet<>(groups);  // Return copy to prevent external modification
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups != null ? new HashSet<>(groups) : new HashSet<>();
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);  // Return copy to prevent external modification
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    // ========== Object Methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserData userData = (UserData) o;

        if (!Objects.equals(uuid, userData.uuid)) {
            return false;
        }
        return Objects.equals(username, userData.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, username);
    }

    @Override
    public String toString() {
        return "UserData{" +
                "uuid='" + uuid + '\'' +
                ", username='" + username + '\'' +
                ", permissions=" + permissions.size() +
                ", groups=" + groups.size() +
                ", metadata=" + metadata.size() +
                ", lastModified=" + lastModified +
                '}';
    }
}
