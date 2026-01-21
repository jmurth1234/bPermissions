package de.bananaco.bpermissions.imp;

import de.bananaco.bpermissions.api.*;
import de.bananaco.bpermissions.imp.storage.WorldFactory;
import de.bananaco.bpermissions.util.loadmanager.MainThread;
import de.bananaco.bpermissions.util.loadmanager.TaskRunnable;
import de.bananaco.bpermissions.util.Debugger;
import de.bananaco.permissions.ImportManager;
import de.bananaco.permissions.fornoobs.BackupPermissionsCommand;
import de.bananaco.permissions.fornoobs.ForNoobs;
import de.bananaco.permissions.interfaces.PromotionTrack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.Callable;

public class Permissions extends JavaPlugin {

    private final Map<String, String> mirrors = new HashMap<String, String>();
    private final Mirrors mrs = new Mirrors(mirrors);
    public SuperPermissionHandler handler;
    private Listener loader;

    // Change to public for people to hook into if they really need to
    public Map<String, Commands> commands;
    private WorldManager wm;
    private World world;  // Changed from DefaultWorld to World for flexibility
    private Config config;
    protected static JavaPlugin instance = null;
    private MainThread mt;
    private WorldFactory worldFactory;  // NEW: Factory for creating worlds

    @Override
    public void onDisable() {
        System.out.println(blankFormat("Waiting 30s to finish tasks..."));
        // try to finish previous tasks first
        for (int i = 0; i < 31; i++) {
            if (mt.hasTasks()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (i == 30) {
                    System.out.println(blankFormat("Tasks not finished - disabling anyway."));
                    System.out.println(blankFormat("Tasks remaining: " + mt.tasksCount()));

                    getServer().getScheduler().cancelTasks(Permissions.instance);

                    mt.clearTasks();
                }
            } else {
                System.out.println(blankFormat("All tasks finished after " + i + " seconds!"));
                i = 31;
            }
        }

        System.out.println(blankFormat("Saving worlds..."));

        //save all worlds
        for (World world : wm.getAllWorlds()) {
            world.save();
        }

        // then disable
        mt.schedule(new TaskRunnable() {
            public void run() {
                getServer().getScheduler().cancelTasks(Permissions.instance);

                // Shutdown world factory (closes database connections)
                if (worldFactory != null) {
                    worldFactory.shutdown();
                }

                mt.setRunning(false);
                System.out.println(blankFormat("Worlds saved, bPermissions disabled."));
            }

            public TaskRunnable.TaskType getType() {
                return TaskRunnable.TaskType.SERVER;
            }
        });

        while (mt.hasTasks()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onLoad() {
        // Load the world mirroring setup
        mrs.load();
    }

    @Override
    public void onEnable() {
        // start main thread
        mt = MainThread.getInstance();
        mt.start();

        instance = this;
        // Only happens after onEnable(), prevent NPE's
        config = new Config();
        // Load the config.yml
        config.load();
        // And test
        boolean onlineMode = getServer().getOnlineMode();
        if (!onlineMode) {
            System.err.println(blankFormat("== Warning: Offline mode is enabled on the server =="));
            System.err.println(blankFormat("bPermissions only recommends using offline mode in scenarios where a proxy such as BungeeCord is handling mojang auth"));
            System.err.println(blankFormat("If there's no user authentication, users could be spoofed."));
        }
        // Get the instance
        wm = WorldManager.getInstance();
        // Set the global file flag
        wm.setUseGlobalFiles(config.getUseGlobalFiles());
        wm.setUseGlobalUsers(config.getUseGlobalUsers());
        handler = new SuperPermissionHandler(this, config.useCustomPermissible());

        // Create world factory for creating appropriate world implementations
        worldFactory = new WorldFactory(this, config);

        // Create world loader (will use the factory)
        loader = new WorldLoader(this, mirrors, worldFactory);

        // Create default world using factory
        try {
            world = worldFactory.createWorld("global");
            // Set the default world to our defaults
            wm.setDefaultWorld(world);
            // load the default world
            world.load();
        } catch (Exception e) {
            getLogger().severe("Failed to create default world: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize bPermissions", e);
        }
        // Load the default Map for Commands
        commands = new HashMap<String, Commands>();
        // Register Commands
        OldUserGroupCommand oldUserGroupCommand = new OldUserGroupCommand(this, commands);
        GroupsTabCompleter tabCompleter = new GroupsTabCompleter(commands);
        this.getCommand("group").setExecutor(oldUserGroupCommand);
        this.getCommand("user").setExecutor(oldUserGroupCommand);
        this.getCommand("world").setExecutor(oldUserGroupCommand);

        this.getCommand("group").setTabCompleter(tabCompleter);
        this.getCommand("user").setTabCompleter(tabCompleter);
        this.getCommand("setgroup").setTabCompleter(tabCompleter);

        // Register changelog cleanup command
        ChangelogCleanupCommand changelogCleanupCommand = new ChangelogCleanupCommand(this);
        this.getCommand("changelog").setExecutor(changelogCleanupCommand);

        // Register loader events
        getServer().getPluginManager().registerEvents(loader, this);
        // Register handler events
        getServer().getPluginManager().registerEvents(handler, this);
        // Setup all online players
        //handler.setupAllPlayers();

        // Load our custom nodes (if any)
        CustomNodes.getInstance().load();

        // REMOVED
        // getServer().getScheduler().scheduleSyncRepeatingTask(this, new SuperPermissionHandler.SuperPermissionReloader(handler), 5, 5);
        // And print a nice little message ;)
        Debugger.log(blankFormat("Enabled"));
        // print dino
        //printDinosaurs();

        // set main thread enabled
        mt.setStarted(true);
        // setup all players
        final World world = this.world;
        mt.schedule(new TaskRunnable() {
            public void run() {
                Bukkit.getScheduler().callSyncMethod(Permissions.instance, new Callable() {
                    public Object call() throws Exception {
                        Debugger.log("Setting up all players...");
                        return Boolean.valueOf(Permissions.this.world.setupAll());
                    }
                });
            }

            public TaskRunnable.TaskType getType() {
                return TaskRunnable.TaskType.SERVER;
            }
        });
    }

    public static String blankFormat(String message) {
        return "[bPermissions] " + message;
    }

    public static String format(String message) {
        ChatColor vary = ChatColor.GREEN;
        if (message.contains("!")) {
            vary = ChatColor.RED;
        } else if (message.contains(":")) {
            vary = ChatColor.AQUA;
        }
        return ChatColor.BLUE + "[bPermissions] " + vary + message;
    }

    public static boolean hasPermission(Player player, String node) {
        return ApiLayer.hasPermission(player.getWorld().getName(), CalculableType.USER, player.getName(), node);
    }

    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(format(message));
    }

    public boolean has(CommandSender sender, String perm) {
        if (sender instanceof Player) {
            return sender.hasPermission(perm);
        } else {
            return true;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean allowed = true;

        if (sender instanceof Player) {
            allowed = hasPermission((Player) sender, "bPermissions.admin")
                    || sender.isOp();
        }

        /*
         * Promote/Demote shizzledizzle
         */
        if (args.length > 0 && (command.getName().equalsIgnoreCase("promote") || command.getName().equalsIgnoreCase("demote"))) {
            // Define some global variables
            String player = args[0];
            String name = "default";
            String world = null;
            if (args.length > 1) {
                name = args[1];
            }
            if (args.length > 2) {
                world = args[2];
            }
            // Check for permission
            if (!has(sender, "tracks." + name)) {
                sendMessage(sender, "You don't have permission to use promotion tracks!");
                return true;
            }
            if (command.getName().equalsIgnoreCase("promote")) {
                PromotionTrack track = config.getPromotionTrack();
                if (track.containsTrack(name)) {
                    // user validity check
                    if (config.trackLimit()) {
                        boolean isValid = true;
                        Player s = (sender instanceof Player) ? (Player) sender : null;
                        if (world == null) {
                            for (org.bukkit.World w : Bukkit.getWorlds()) {
                                boolean v = new ValidityCheck(s, track, name, player, w.getName()).isValid();
                                if (!v) {
                                    isValid = false;
                                }
                            }
                        } else {
                            isValid = new ValidityCheck(s, track, name, player, world).isValid();
                        }
                        if (!isValid) {
                            sender.sendMessage(ChatColor.RED + "Invalid promotion!");
                            return true;
                        }
                    }
                    // or the rest
                    track.promote(player, name, world);
                    sendMessage(sender, "Promoted along the track: " + name + " in " + (world == null ? "all worlds" : "world: " + world));
                    this.showPromoteOutput(sender, player);
                } else {
                    sendMessage(sender, "That track (" + name + ") does not exist");
                }
            } else if (command.getName().equalsIgnoreCase("demote")) {
                PromotionTrack track = config.getPromotionTrack();
                if (track.containsTrack(name)) {
                    // user validity check
                    if (config.trackLimit()) {
                        boolean isValid = true;
                        Player s = (sender instanceof Player) ? (Player) sender : null;
                        if (world == null) {
                            for (org.bukkit.World w : Bukkit.getWorlds()) {
                                boolean v = new ValidityCheck(s, track, name, player, w.getName()).isValid();
                                if (!v) {
                                    isValid = false;
                                }
                            }
                        } else {
                            isValid = new ValidityCheck(s, track, name, player, world).isValid();
                        }
                        if (!isValid) {
                            sender.sendMessage(ChatColor.RED + "Invalid promotion!");
                            return true;
                        }
                    }
                    // or the rest
                    track.demote(player, name, world);
                    sendMessage(sender, "Demoted along the track: " + name + " in " + (world == null ? "all worlds" : "world: " + world));
                    this.showPromoteOutput(sender, player);
                } else {
                    sendMessage(sender, "That track (" + name + ") does not exist");
                }
            }
            ApiLayer.update();
            return true;
        }

        if (!allowed) {
            sendMessage(sender, "You're not allowed to do that!");
            return true;
        }

        /*
         * Create an entry in the commands selection if one does not exist
         */
        if (!commands.containsKey(getName(sender))) {
            commands.put(getName(sender), new Commands());
        }

        Commands cmd = commands.get(getName(sender));


        if (command.getName().equalsIgnoreCase("exec")) {
            String name = "null";
            CalculableType type = CalculableType.USER;
            String action = "null";
            String value = "null";
            String world = null;
            for (String c : args) {
                if (c.startsWith("u:") || c.startsWith("g:")) {
                    if (c.startsWith("u:")) {
                        type = CalculableType.USER;
                    } else {
                        type = CalculableType.GROUP;
                    }
                    name = c.split(":")[1];
                } else if (c.startsWith("a:")) {
                    String[] actionArray = c.split(":");
                    if (actionArray.length == 3) {
                        action = actionArray[1] + ":" + actionArray[2];
                    } else {
                        action = actionArray[1];
                    }
                } else if (c.startsWith("v:")) {
                    value = c.split(":")[1];
                } else if (c.startsWith("w:")) {
                    world = c.split(":")[1];
                }
            }
            String actionMessage = (world == null ? "all worlds" : "world: " + world);

            String message = ChatColor.GOLD + "Executing action: " + ChatColor.GREEN + action + " " + value + ChatColor.GOLD + " in " + ChatColor.GREEN + (world == null ? "all worlds" : "world: " + world);
            sender.sendMessage(message);

            boolean executed = ActionExecutor.execute(name, type, action, value, world);
            if (executed) {
                String message2 = ChatColor.GOLD + "Action applied to " + ChatColor.GREEN + type.getName() + " " + name;

                sender.sendMessage(message2);
            } else {
                sender.sendMessage(format("Invalid exec command!"));
            }
        }
        /*
         * A new, easier way to set a players group!
         */
        if (command.getName().equalsIgnoreCase("setgroup")) {
            if (args.length < 2) {
                sendMessage(sender, "Not enough arguments!");
                return false;
            }
            String name = args[0];
            CalculableType type = CalculableType.USER;
            String action = "setgroup";
            String value = args[1];
            String world = null;

            ActionExecutor.execute(name, type, action, value, world);
            sendMessage(sender, "The player " + name + " is now " + value + "!");
        }
        /*
         * And now your standard "permissions" command
         */
        if (command.getName().equalsIgnoreCase("permissions")) {
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("import")) {
                    sender.sendMessage("Importing from " + args[1]);
                    try {
                        if (args[1].equalsIgnoreCase("yml")) {
                            new ImportManager(this).importYML();
                        }
                        if (args[1].equalsIgnoreCase("pex")) {
                            new ImportManager(this).pexImport();
                        }
                        if (args[1].equalsIgnoreCase("p3")) {
                            new ImportManager(this).importPermissions3();
                        }
                        if (args[1].equalsIgnoreCase("gm")) {
                            new ImportManager(this).importGroupManager();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            }
            if (args.length == 1) {
                if (sender instanceof ConsoleCommandSender) {
                    if (args[0].equalsIgnoreCase("debug")) {
                        if (Debugger.getDebug()) {
                            for (World world : wm.getAllWorlds()) {
                                Debugger.log(world);
                            }
                            return true;
                        } else {
                            sender.sendMessage("Please enable debug mode to use this command.");
                            return true;
                        }
                    }
                    if (args[0].equalsIgnoreCase("debugperms")) {
                        Collection<Player> players = (Collection<Player>) Bukkit.getOnlinePlayers();
                        if (players.size() == 0) {
                            System.err.println("You need some online players!");
                        } else {
                            // Old manual test removed - use automated JUnit tests instead
                            System.out.println("Manual PermissionsTest has been migrated to JUnit tests");
                            System.out.println("See bukkit/src/test/java/de/bananaco/bpermissions/imp/ApiLayerIntegrationTest.java");
                        }
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("debugset")) {
                        Collection<Player> players = (Collection<Player>) Bukkit.getOnlinePlayers();
                        if (players.size() == 0) {
                            System.err.println("You need some online players!");
                        } else {
                            Player player = (Player) players.toArray()[0];
                            BukkitCompat.runTest(player, this);
                        }
                        return true;
                    }
                }
                String action = args[0];
                if (action.equalsIgnoreCase("save")) {
                    sendMessage(sender, "All worlds saved!");
                    cmd.save();
                    return true;
                } else if (action.equalsIgnoreCase("reload")) {
                    // Reload config file
                    config.load();
                    // Get the instance
                    wm = WorldManager.getInstance();
                    // Set the global file flag
                    wm.setUseGlobalFiles(config.getUseGlobalFiles());
                    wm.setUseGlobalUsers(config.getUseGlobalUsers());

                    // reload custom nodes
                    CustomNodes.getInstance().reload();

                    // reload mirrors
                    mrs.load();

                    // finally reload the files
                    mt.schedule(new TaskRunnable() {
                        public void run() {
                            for (final World world : wm.getAllWorlds()) {
                                try {
                                    world.setFiles();
                                    ((YamlWorld) world).loadUnsafe();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                Bukkit.getScheduler().callSyncMethod(Permissions.instance, new Callable() {
                                    public Object call() throws Exception {
                                        return world.setupAll();
                                    }
                                });
                            }
                        }

                        public TaskRunnable.TaskType getType() {
                            return TaskRunnable.TaskType.LOAD;
                        }
                    });


                    sendMessage(sender, "Reloaded bPermissions!");
                    return true;
                } else if (action.equalsIgnoreCase("cleanup")) {
                    sendMessage(sender, "Cleaning up files!");
                    wm.cleanup();
                    return true;
                } else if (action.equalsIgnoreCase("examplefiles") || action.equalsIgnoreCase("helpme")) {
                    sendMessage(sender, "Created example files!");
                    // Create the example file
                    new ForNoobs(this).addAll();
                    return true;
                } else if (action.equalsIgnoreCase("backup")) {
                    sendMessage(sender, "Creating backup!");
                    new BackupPermissionsCommand(this).backup();
                    return true;
                } else if (args[0].equalsIgnoreCase("convert")) {
                    final ImportManager manager = new ImportManager(this);

                    ConvertRunnable runnable = new ConvertRunnable(manager);
                    runnable.start();
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public void showPromoteOutput(CommandSender sender, String player) {
        sender.sendMessage("The player: " + ChatColor.GREEN + player + ChatColor.WHITE + " now has these groups");
        for (World world : WorldManager.getInstance().getAllWorlds()) {
            List<String> groups = world.getUser(player).serialiseGroups();
            String[] g = groups.toArray(new String[groups.size()]);
            String gr = Arrays.toString(g);
            sender.sendMessage("In world: " + world.getName() + " " + gr);
        }
    }

    private String getName(CommandSender sender) {
        if (sender instanceof Player) {
            return sender.getName();
        }
        return "CONSOLE";
    }

    class ConvertRunnable implements Runnable {
        private final ImportManager manager;
        private String threadName = "converter";
        private Thread t;

        ConvertRunnable(ImportManager manager) {
            this.manager = manager;
        }

        public void run() {

            System.out.println("Running " + threadName);
            try {
                manager.importUuid();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void start() {
            System.out.println("Starting " + threadName);
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }

    }

}
