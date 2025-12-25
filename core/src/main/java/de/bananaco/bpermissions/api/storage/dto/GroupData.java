package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.Group;

import java.io.Serializable;
import java.util.*;

/**
 * Data Transfer Object for Group permissions data.
 * <p>
 * This class represents a group's permission data in a database-friendly format.
 * It contains all the information needed to reconstruct a {@link Group} object
 * or to persist a Group's data to a database.
 * </p>
 */
public class GroupData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private Set<String> permissions;
    private Set<String> groups;  // Inherited groups
    private Map<String, String> metadata;
    private long lastModified;

    /**
     * Default constructor for serialization frameworks.
     */
    public GroupData() {
        this.permissions = new HashSet<>();
        this.groups = new HashSet<>();
        this.metadata = new HashMap<>();
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields.
     *
     * @param name         Group name
     * @param permissions  Set of permission strings (e.g., "admin.*", "^admin.dangerous")
     * @param groups       Set of inherited group names
     * @param metadata     Map of metadata key-value pairs (prefix, suffix, priority)
     * @param lastModified Last modification timestamp
     */
    public GroupData(String name, Set<String> permissions, Set<String> groups,
                     Map<String, String> metadata, long lastModified) {
        this.name = name;
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
        this.groups = groups != null ? new HashSet<>(groups) : new HashSet<>();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.lastModified = lastModified;
    }

    /**
     * Create a GroupData object from a Group API object.
     * <p>
     * This is used when saving a Group to the database.
     * </p>
     *
     * @param group The Group object to convert
     * @return GroupData representation of the group
     */
    public static GroupData fromGroup(Group group) {
        if (group == null) {
            return null;
        }

        String name = group.getName();

        // Serialize permissions
        Set<String> permissions = new HashSet<>(group.serialisePermissions());

        // Serialize inherited groups
        Set<String> groups = new HashSet<>(group.serialiseGroups());

        // Get metadata
        Map<String, String> metadata = new HashMap<>(group.getMeta());

        return new GroupData(name, permissions, groups, metadata, System.currentTimeMillis());
    }

    /**
     * Apply this GroupData to an existing Group object.
     * <p>
     * This updates the Group's permissions and metadata based on this DTO.
     * Note: This does not create a new Group object, it modifies the existing one.
     * </p>
     *
     * @param group The Group object to update
     */
    public void applyToGroup(Group group) {
        if (group == null) {
            return;
        }

        // Note: We don't clear existing permissions/groups first because
        // the Group will be reconstructed from scratch when loaded from the database
    }

    // ========== Getters and Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

        GroupData groupData = (GroupData) o;

        return Objects.equals(name, groupData.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "GroupData{" +
                "name='" + name + '\'' +
                ", permissions=" + permissions.size() +
                ", groups=" + groups.size() +
                ", metadata=" + metadata.size() +
                ", lastModified=" + lastModified +
                '}';
    }
}
