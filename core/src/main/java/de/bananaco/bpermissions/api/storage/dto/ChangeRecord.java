package de.bananaco.bpermissions.api.storage.dto;

import de.bananaco.bpermissions.api.CalculableType;

import java.io.Serializable;
import java.util.Objects;

/**
 * Data Transfer Object for Change tracking.
 * <p>
 * This class represents a change to a User or Group in the database.
 * It is used by the polling system to detect external changes made by
 * other servers or web interfaces.
 * </p>
 */
public class ChangeRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Type of change that occurred.
     */
    public enum ChangeType {
        /**
         * A new User or Group was created.
         */
        INSERT,

        /**
         * An existing User or Group was updated.
         */
        UPDATE,

        /**
         * A User or Group was deleted.
         */
        DELETE
    }

    private String worldName;
    private CalculableType calculableType;  // USER or GROUP
    private String calculableName;  // UUID for users, name for groups
    private ChangeType changeType;
    private long timestamp;
    private String serverSource;  // Which server made the change

    /**
     * Default constructor for serialization frameworks.
     */
    public ChangeRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields.
     *
     * @param worldName       The world name
     * @param calculableType  Type of calculable (USER or GROUP)
     * @param calculableName  UUID for users, name for groups
     * @param changeType      Type of change (INSERT, UPDATE, DELETE)
     * @param timestamp       When the change occurred
     * @param serverSource    Which server made the change
     */
    public ChangeRecord(String worldName, CalculableType calculableType, String calculableName,
                        ChangeType changeType, long timestamp, String serverSource) {
        this.worldName = worldName;
        this.calculableType = calculableType;
        this.calculableName = calculableName;
        this.changeType = changeType;
        this.timestamp = timestamp;
        this.serverSource = serverSource;
    }

    // ========== Getters and Setters ==========

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public CalculableType getCalculableType() {
        return calculableType;
    }

    public void setCalculableType(CalculableType calculableType) {
        this.calculableType = calculableType;
    }

    public String getCalculableName() {
        return calculableName;
    }

    public void setCalculableName(String calculableName) {
        this.calculableName = calculableName;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getServerSource() {
        return serverSource;
    }

    public void setServerSource(String serverSource) {
        this.serverSource = serverSource;
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

        ChangeRecord that = (ChangeRecord) o;

        if (timestamp != that.timestamp) {
            return false;
        }
        if (!Objects.equals(worldName, that.worldName)) {
            return false;
        }
        if (calculableType != that.calculableType) {
            return false;
        }
        if (!Objects.equals(calculableName, that.calculableName)) {
            return false;
        }
        if (changeType != that.changeType) {
            return false;
        }
        return Objects.equals(serverSource, that.serverSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, calculableType, calculableName, changeType, timestamp, serverSource);
    }

    @Override
    public String toString() {
        return "ChangeRecord{" +
                "worldName='" + worldName + '\'' +
                ", calculableType=" + calculableType +
                ", calculableName='" + calculableName + '\'' +
                ", changeType=" + changeType +
                ", timestamp=" + timestamp +
                ", serverSource='" + serverSource + '\'' +
                '}';
    }
}
