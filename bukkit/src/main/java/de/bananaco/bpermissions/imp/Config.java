package de.bananaco.bpermissions.imp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import de.bananaco.bpermissions.util.Debugger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import de.bananaco.bpermissions.api.WorldManager;
import de.bananaco.permissions.interfaces.PromotionTrack;

public class Config {

    private final File file = new File("plugins/bPermissions/config.yml");
    private YamlConfiguration config = new YamlConfiguration();
    private String trackType = "multi";
    private PromotionTrack track = null;
    private boolean useGlobalFiles = false;
    private boolean autoSave = true;
    private boolean offlineMode = false;
    private boolean trackLimit = false;
    private boolean useGlobalUsers = false;
    private boolean useCustomPermissible = false;

    // Storage configuration
    private String storageBackend = "yaml";
    private int pollInterval = 5;
    private Map<String, Object> mongoConfig = new HashMap<>();
    private Map<String, Object> mysqlConfig = new HashMap<>();

    public void load() {
        try {
            loadUnsafe();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadUnsafe() throws Exception {
        // Your standard create if not exist shizzledizzle
        if (!file.exists()) {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        }
        config.load(file);
        // set the value to default
        config.set("auto-save", config.get("auto-save", autoSave));
        config.set("track-type", config.get("track-type", trackType));
        // set the debugger value to default
        config.set("debug-mode", Debugger.setDebug(config.getBoolean("debug-mode", Debugger.getDebug())));
        // config.set("allow-offline-mode", config.get("allow-offline-mode", offlineMode));
        config.set("use-global-files", config.get("use-global-files", useGlobalFiles));
        config.set("use-global-users", config.get("use-global-users", useGlobalUsers));
        config.set("track-limit", config.get("track-limit", trackLimit));
        config.set("use-custom-permissible", config.get("use-custom-permissible", useCustomPermissible));
        // then load it into memory
        useGlobalFiles = config.getBoolean("use-global-files");
        useGlobalUsers = config.getBoolean("use-global-users");
        autoSave = config.getBoolean("auto-save");
        trackType = config.getString("track-type");
        offlineMode = config.getBoolean("allow-offline-mode");
        trackLimit = config.getBoolean("track-limit");
        useCustomPermissible = config.getBoolean("use-custom-permissible");
        // then load our PromotionTrack
        if (trackType.equalsIgnoreCase("multi")) {
            track = new MultiGroupPromotion();
        } else if (trackType.equalsIgnoreCase("lump")) {
            track = new LumpGroupPromotion();
        } else if (trackType.equalsIgnoreCase("single")) {
            track = new SingleGroupPromotion();
        } else {
            track = new ReplaceGroupPromotion();
        }
        // Load storage configuration
        ConfigurationSection storageSection = config.getConfigurationSection("storage");
        if (storageSection == null) {
            // Create default storage configuration
            config.set("storage.backend", "yaml");
            config.set("storage.poll-interval", 5);
            config.set("storage.mongodb.connection-string", "mongodb://localhost:27017");
            config.set("storage.mongodb.database", "bpermissions");
            config.set("storage.mongodb.server-id", "server-1");
            config.set("storage.mysql.host", "localhost");
            config.set("storage.mysql.port", 3306);
            config.set("storage.mysql.database", "bpermissions");
            config.set("storage.mysql.username", "root");
            config.set("storage.mysql.password", "password");
            config.set("storage.mysql.server-id", "server-1");
            storageSection = config.getConfigurationSection("storage");
        }

        // Load storage backend type
        storageBackend = config.getString("storage.backend", "yaml");
        pollInterval = config.getInt("storage.poll-interval", 5);

        // Load MongoDB configuration
        ConfigurationSection mongoSection = config.getConfigurationSection("storage.mongodb");
        if (mongoSection != null) {
            mongoConfig.put("connection-string", mongoSection.getString("connection-string", "mongodb://localhost:27017"));
            mongoConfig.put("database", mongoSection.getString("database", "bpermissions"));
            mongoConfig.put("server-id", mongoSection.getString("server-id", "server-1"));
        }

        // Load MySQL configuration
        ConfigurationSection mysqlSection = config.getConfigurationSection("storage.mysql");
        if (mysqlSection != null) {
            mysqlConfig.put("host", mysqlSection.getString("host", "localhost"));
            mysqlConfig.put("port", mysqlSection.getInt("port", 3306));
            mysqlConfig.put("database", mysqlSection.getString("database", "bpermissions"));
            mysqlConfig.put("username", mysqlSection.getString("username", "root"));
            mysqlConfig.put("password", mysqlSection.getString("password", "password"));
            mysqlConfig.put("server-id", mysqlSection.getString("server-id", "server-1"));
        }

        // Then set the worldmanager
        WorldManager.getInstance().setAutoSave(autoSave);
        // Load the track
        track.load();
        // finally save the config
        config.save(file);
    }

    public boolean trackLimit() {
        return trackLimit;
    }

    public boolean getUseGlobalFiles() {
        return useGlobalFiles;
    }

    public boolean getUseGlobalUsers() { return useGlobalUsers; }

    public PromotionTrack getPromotionTrack() {
        return track;
    }

    public boolean getAllowOfflineMode() {
        return offlineMode;
    }

    public boolean useCustomPermissible() {
        return useCustomPermissible;
    }

    public String getStorageBackend() {
        return storageBackend;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public Map<String, Object> getMongoConfig() {
        return mongoConfig;
    }

    public Map<String, Object> getMysqlConfig() {
        return mysqlConfig;
    }
}
