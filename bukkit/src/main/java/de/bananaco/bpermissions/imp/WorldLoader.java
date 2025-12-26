package de.bananaco.bpermissions.imp;

import java.util.Map;

import de.bananaco.bpermissions.api.storage.StorageException;
import de.bananaco.bpermissions.imp.storage.WorldFactory;
import de.bananaco.bpermissions.util.Debugger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import de.bananaco.bpermissions.api.WorldManager;

/**
 * This class handles world mirroring and world loading.
 * <p>
 * World instances are created using a {@link WorldFactory} which determines
 * the appropriate World implementation (YamlWorld, DatabaseWorld) based on
 * the storage backend configuration.
 * </p>
 */
public class WorldLoader implements Listener {

    private WorldManager wm = WorldManager.getInstance();
    private Map<String, String> mirrors;
    private Permissions permissions;
    private WorldFactory worldFactory;

    protected WorldLoader(Permissions permissions, Map<String, String> mirrors, WorldFactory worldFactory) {
        this.mirrors = mirrors;
        this.permissions = permissions;
        this.worldFactory = worldFactory;

        // Load all existing worlds
        for (World world : Bukkit.getServer().getWorlds()) {
            createWorld(world);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        createWorld(event.getWorld());
    }

    public void createWorld(World w) {
        String worldName = w.getName().toLowerCase();

        // Skip mirrored worlds
        if (!mirrors.containsKey(worldName)) {
            Debugger.log("Loading world: " + w.getName());

            try {
                // Create world using the factory (respects storage backend config)
                de.bananaco.bpermissions.api.World bpWorld = worldFactory.createWorld(worldName);
                wm.createWorld(worldName, bpWorld);
            } catch (StorageException e) {
                permissions.getLogger().severe("Failed to create world " + worldName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
