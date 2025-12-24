package de.bananaco.bpermissions.imp;

import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.World;

import java.util.UUID;

/**
 * Test implementation of World for use in unit and integration tests.
 * Provides no-op implementations of all abstract methods.
 */
public class WorldTest extends World {

    public WorldTest(String world) {
        super(world);
    }

    @Override
    public void setFiles() {
        return;
    }

    @Override
    public boolean load() {
        return true;
    }

    @Override
    public boolean save() {
        return true;
    }

    @Override
    public boolean loadOne(String name, CalculableType type) {
        return true;
    }

    @Override
    public boolean loadCalculableWithLookup(String lookupName, String name, CalculableType type) {
        return true;
    }

    //@Override
    //public boolean saveOne(String name, CalculableType type) {
    //    return true;
    //}

    @Override
    public boolean storeContains(String name, CalculableType type) {
        return false;
    }

    @Override
    public String getDefaultGroup() {
        return "default";
    }

    @Override
    public boolean setupPlayer(String player) {
        return false;
    }

    @Override
    public UUID getUUID(String player) {
        // Return a fake UUID based on player name for testing
        return UUID.nameUUIDFromBytes(player.getBytes());
    }

    @Override
    public void setDefaultGroup(String group) {
        // No-op for tests
    }
}
