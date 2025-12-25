package de.bananaco.bpermissions.api.storage.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for World metadata.
 * <p>
 * This class represents a world's configuration and settings in a database-friendly format.
 * It stores information like the default group and any custom world-specific settings.
 * </p>
 */
public class WorldMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private String worldName;
    private String defaultGroup;
    private long lastModified;
    private Map<String, Object> customSettings;

    /**
     * Default constructor for serialization frameworks.
     */
    public WorldMetadata() {
        this.defaultGroup = "default";
        this.lastModified = System.currentTimeMillis();
        this.customSettings = new HashMap<>();
    }

    /**
     * Constructor with all fields.
     *
     * @param worldName      The world name
     * @param defaultGroup   The default group for new users
     * @param lastModified   Last modification timestamp
     * @param customSettings Map of custom world-specific settings
     */
    public WorldMetadata(String worldName, String defaultGroup, long lastModified,
                         Map<String, Object> customSettings) {
        this.worldName = worldName;
        this.defaultGroup = defaultGroup != null ? defaultGroup : "default";
        this.lastModified = lastModified;
        this.customSettings = customSettings != null ? new HashMap<>(customSettings) : new HashMap<>();
    }

    // ========== Getters and Setters ==========

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getDefaultGroup() {
        return defaultGroup;
    }

    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public Map<String, Object> getCustomSettings() {
        return new HashMap<>(customSettings);  // Return copy to prevent external modification
    }

    public void setCustomSettings(Map<String, Object> customSettings) {
        this.customSettings = customSettings != null ? new HashMap<>(customSettings) : new HashMap<>();
    }

    /**
     * Get a custom setting value.
     *
     * @param key The setting key
     * @return The setting value, or null if not found
     */
    public Object getCustomSetting(String key) {
        return customSettings.get(key);
    }

    /**
     * Set a custom setting value.
     *
     * @param key   The setting key
     * @param value The setting value
     */
    public void setCustomSetting(String key, Object value) {
        customSettings.put(key, value);
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

        WorldMetadata that = (WorldMetadata) o;

        return Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName);
    }

    @Override
    public String toString() {
        return "WorldMetadata{" +
                "worldName='" + worldName + '\'' +
                ", defaultGroup='" + defaultGroup + '\'' +
                ", lastModified=" + lastModified +
                ", customSettings=" + customSettings.size() +
                '}';
    }
}
