package de.bananaco.bpermissions.api;

import de.bananaco.bpermissions.util.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * This is the class to extend for new implementations of bPermissions.
 * <p/>
 * With this class, other ways to load/save permissions will become easily
 * available (hopefully)...
 */
public abstract class World {

    private final Map<String, Group> groups;
    private final Map<String, User> users;
    private final String world;
    private char COLOR_CHAR = '\u00A7';
    private Pattern stripColorPattern;
    private final List<CalculableChangeListener> changeListeners;

    public World(String world) {
        this.world = world;
        this.users = new HashMap<>();
        this.groups = new HashMap<>();
        this.changeListeners = new ArrayList<>();

        stripColorPattern = Pattern.compile("(?i)" + String.valueOf(COLOR_CHAR) + "[0-9A-FK-OR]");
    }

    public abstract void setFiles();

    /**
     * Make sure you call .calculateMappedPermissions for all the users once
     * this is done!
     * <p/>
     * You can just call add(Calculable) here with the objects you create.
     *
     * @return boolean
     */
    public abstract boolean load();

    /**
     * This should be as efficient as possible, can even be threaded if you
     * really desire. This is an attempt to increase compatibility with
     * everything!
     *
     * @return boolean
     */
    public abstract boolean save();

    /**
     * This loads a single Calculable into the API
     * <p/>
     * Make sure you call .calculateMappedPermissions for all the users once
     * this is done!
     * <p/>
     * You can just call add(Calculable) here with the objects you create.
     *
     * @return boolean
     */
    public abstract boolean loadOne(String name, CalculableType type);

    /**
     * This loads a single Calculable into the API
     * <p/>
     * Similar to loadOne() except this one allows an additional lookup string
     * <p/>
     * You can just call add(Calculable) here with the objects you create.
     *
     * @return boolean
     */
    public abstract boolean loadCalculableWithLookup(String lookupName, String name, CalculableType type);

    /**
     * This saves a single Calculable into the storage
     *
     * This should be as efficient as possible, can even be threaded if you
     * really desire. This is an attempt to increase compatibility with
     * everything!
     *
     * [REMOVED FOR NOW - UNUSED]
     *
     * @return boolean
     * public abstract boolean saveOne(String name, CalculableType type);
     */

    /**
     * This is a way of checking whether a Calculable is stored within
     * the storage that this world uses.
     * <p/>
     * Will return true if the store cointains the Calculable
     *
     * @return boolean
     */
    public abstract boolean storeContains(String name, CalculableType type);


    /**
     * Used to check if the World contains an entry for said Calculable
     *
     * @param name
     * @param type
     * @return boolean
     */
    public boolean contains(String name, CalculableType type) {
        name = stripColor(name);
        // A quick lowercase here
        name = name.toLowerCase();
        // And now we check
        if (type == CalculableType.USER) {
            return users.containsKey(name);
        } else if (type == CalculableType.GROUP) {
            return groups.containsKey(name);
        }
        return false;
    }

    /**
     * Used to get a Group from this world via the group name
     *
     * @param name The name of a group
     * @return Calculable (Group)
     */
    public Group getGroup(String name) {
        name = stripColor(name);
        return (Group) get(name, CalculableType.GROUP);
    }


    /**
     * Used to get a User from this world via their username.
     * This has been depreciated and may not exist in a future release,
     * it's recommended to use getUser() with a UUID instead
     *
     * @param name The username of a player, or UUID as a string
     * @return Calculable (User)
     */
    @Deprecated
    public User getUser(String name) {
        name = stripColor(name);
        return (User) get(name, CalculableType.USER);
    }

    /**
     * Used to get a User from this world via their UUID
     * This is the preferred method of doing so.
     *
     * @param uuid A users UUID
     * @return Calculable (User)
     */
    public User getUser(UUID uuid) {
        return (User) get(uuid.toString(), CalculableType.USER);
    }

    /**
     * Used to get the contained Calculable (contains should be used first)
     *
     * @param name
     * @param type
     * @return Calculable (Group/User)
     */
    public Calculable get(String name, CalculableType type) {
        long t = System.currentTimeMillis();
        MapCalculable c = null;

        name = stripColor(name);
        // A quick lowercase here
        name = name.toLowerCase();
        name = name.replace(".", "-");

        // make sure we get the users UUID, not their nick
        if (type == CalculableType.USER) {
            if (!isUUID(name)) {
                name = getUUID(name).toString();
            }
        }

        // load them
        if (storeContains(name, type)) {
            loadIfExists(name, type);
        }

        // set them up
        if (type == CalculableType.USER) {
            if (!users.containsKey(name)) {
                User user = new User(name, null, null, getName(), this);
                add(user);
                // Don't forget to add the default group!
                user.addGroup(getDefaultGroup());
                // And calculate the effective Permissions!
                try {
                    user.setLoaded();
                    user.calculateGroups();
                    user.calculateMappedPermissions();
                    user.calculateEffectiveMeta();
                } catch (RecursiveGroupException e) {
                    System.err.println(e.getMessage());
                }
            }

            c = users.get(name);

            if (c.getGroups().isEmpty()) {
                c.addGroup(getDefaultGroup());
                try {
                    c.calculateGroups();
                    c.calculateMappedPermissions();
                    c.calculateEffectiveMeta();
                } catch (RecursiveGroupException e) {
                    System.err.println(e.getMessage());
                }
            }
        } else if (type == CalculableType.GROUP) {
            if (!groups.containsKey(name)) {
                Group g = new Group(name, null, null, getName(), this);
                g.setLoaded();
                add(g);
            }
            c = groups.get(name);
        }

        long f = System.currentTimeMillis();
        Debugger.log("Getting calculable " + name + " in " + getName() + " took " + (f - t) + "ms");
        return c;
    }

    /**
     * Used to grab a complete set of the contained Calculable from the World.
     * Should never return null but may return an empty Set<Calculable>
     * Returns a new Set with direct references to the object.
     *
     * @param type
     * @return Set<Calculable>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Set<Calculable> getAll(CalculableType type) {
        Set<Calculable> entries = new HashSet();
        Map<String, Calculable> data = getAllAsMap(type);
        // catch null
        if (data == null) {
            return null;
        }
        // And now we grab
        if (type == CalculableType.USER) {
            for (String key : data.keySet()) {
                entries.add(data.get(key));
            }
            return entries;
        } else if (type == CalculableType.GROUP) {
            for (String key : data.keySet()) {
                entries.add(data.get(key));
            }
            return entries;
        }
        data.clear();
        return entries;
    }

    /**
     * Used to grab a complete Map of the contained Calculable from the World.
     * This may return null if there are no Calculable in the world
     *
     * @param type
     * @return Set<Calculable>
     */
    private Map<String, Calculable> getAllAsMap(CalculableType type) {
        try {
            if (type == CalculableType.USER) {
                return new HashMap<>(users);
            } else if (type == CalculableType.GROUP) {
                return new HashMap<>(groups);
            }
        } catch (Exception e) {
            Debugger.log("Error getting " + type.name());
        }
        return null;
    }

    /**
     * This adds the Calculable to either groups or users depending on if the
     * calculable is an instance of either. This is not directly checked and
     * instead getType() is relied upon to be correct. If the calculable is not
     * an instance of a group or a user, it is not added. This means you cannot
     * add base calculables (or any other class which extends calculable) to
     * this.
     *
     * @param calculable
     */
    public void add(Calculable calculable) {
        if (calculable.getType() == CalculableType.USER) {
            users.put(calculable.getNameLowerCase(), (User) calculable);
        } else if (calculable.getType() == CalculableType.GROUP) {
            groups.put(calculable.getNameLowerCase(), (Group) calculable);
        } else {
            System.err.println("Calculable not instance of User or Group!");
        }
    }

    /**
     * This removes the Calculable from either groups or users depending on if the
     * calculable is an instance of either. This is not directly checked and
     * instead getType() is relied upon to be correct. If the calculable is not
     * an instance of a group or a user, it is not added. This means you cannot
     * add base calculables (or any other class which extends calculable) to
     * this.
     *
     * @param calculable
     */
    public void remove(Calculable calculable) {
        if (calculable.getType() == CalculableType.USER) {
            users.remove(calculable.getNameLowerCase());
        } else if (calculable.getType() == CalculableType.GROUP) {
            groups.remove(calculable.getNameLowerCase());
        } else {
            System.err.println("Calculable not instance of User or Group!");
        }
    }

    /**
     * Returns the world name
     *
     * @return String
     */
    public String getName() {
        return world;
    }

    /**
     * Used to clear the Maps containing User and Group object (useful for doing
     * a clean load)
     */
    public void clear() {
        groups.clear();
        users.clear();
    }

    /**
     * Used to clear the Map containing Group objects (useful for doing
     * a clean load)
     */
    public void clearGroups() {
        groups.clear();
    }


    /**
     * Used to clear the Map containing User objects (useful for doing
     * a clean load)
     */
    public void clearUsers() {
        users.clear();
    }


    /**
     * Shows if the world is THIS world
     *
     * @param world
     * @return boolean
     */
    public boolean equalsWorld(String world) {
        return world.equalsIgnoreCase(this.world);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        return o.hashCode() == hashCode();
    }

    /**
     * This is the implementation of the .cleanup() in WorldManager
     * <p/>
     * Removes any empty groups and any users with just the default group
     */
    protected void cleanup() {
        List<String> removal = new ArrayList<>();
        // Iterate through the users
        for (String user : users.keySet()) {
            User u = users.get(user);


            if (u.getMeta().size() == 0
                    && u.getPermissions().size() == 0
                    && (u.getGroupsAsString().size() == 0
                    || (u.getGroupsAsString().size() == 1
                    && u.getGroupsAsString().iterator().next().equals(getDefaultGroup())))) {
                removal.add(user);
            }
        }
        // Remove the user if it's been flagged
        for (String user : removal) {
            users.remove(user);
        }
        removal.clear();
        // Iterate through the groups
        for (String group : groups.keySet()) {
            Group g = groups.get(group);
            if (g.getMeta().size() == 0
                    && g.getPermissions().size() == 0
                    && g.getGroupsAsString().size() == 0) {
                removal.add(group);
            }
        }
        // Remove the group if it's been flagged
        for (String group : removal) {
            groups.remove(group);
        }
        // And finally save the cleaned up files
        save();
    }

    public abstract void setDefaultGroup(String group);

    public abstract String getDefaultGroup();

    public abstract boolean setupPlayer(String player);

    public boolean setupAll() {
        // override to return true
        return false;
    }

    public boolean isOnline(User user) {
        // override to return true;
        return false;
    }

    public void clearPlayers() {
        users.clear();
    }

    public boolean isUUID(String string) {
        // use the uuid check from the java source code
        String[] components = string.split("-");
        return components.length == 5;
    }

    /**
     * Method to load a Calculable into the memory if it exists and isn't loaded already
     *
     * @param name The name of the Calculable
     * @param type The type of the Calculable
     */
    public void loadIfExists(String name, CalculableType type) {
        if (!contains(name, type) && storeContains(name, type)) {
            loadOne(name, type);
        }
    }

    /**
     * Method to load a User into the memory if it exists and isn't loaded already, falling back to a username
     * if the UUID doesn't exist in the config
     *
     * @param username The name of the User
     * @param uuid The UUID of the user
     */
    public void loadUserWithFallback(String username, String uuid) {
        if (!contains(uuid, CalculableType.USER) && storeContains(uuid, CalculableType.USER)) {
            loadOne(uuid, CalculableType.USER);
        }

        if (storeContains(username, CalculableType.USER)) {
            loadCalculableWithLookup(username, uuid, CalculableType.USER);
        }
    }

    /**
     * Abstract method to get a players UUID given their username using whatever
     * method is used in the implementation of a World
     *
     * @param player The name of the Player
     * @return uuid The UUID of the Player
     */
    public abstract UUID getUUID(String player);

    /**
     * Strips the given message of all color codes - taken from the Bukkit source
     * code.
     * <p/>
     * This allows the bPermissions core to be separate from an implementation
     *
     * @param input String to strip of color
     * @return A copy of the input string, without any coloring
     */
    public String stripColor(final String input) {
        if (input == null) {
            return null;
        }
        char COLOR_CHAR = '\u00A7';
        return stripColorPattern.matcher(input).replaceAll("");
    }

    public void addChangeListener(CalculableChangeListener listener) {
        changeListeners.add(listener);
    }

    public void runChangeListeners(CalculableChange change) {
        for (CalculableChangeListener listener : changeListeners) {
            listener.onChange(change);
        }
    }
}
