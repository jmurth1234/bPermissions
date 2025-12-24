package de.bananaco.bpermissions.api;

import de.bananaco.bpermissions.util.Debugger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds a super easy to use static interface to bPermissions 2
 *
 * Needed imports:
 *
 * de.bananaco.bpermisisons.api.util.CalculableType Can be CalculableType.GROUP
 * or CalculableType.USER
 *
 * de.bananaco.bpermisisons.api.util.Permission Carries a String and a Boolean,
 * can be created when needed (new Permission(String, Boolean)) and will
 * override any existing permission by that name.
 */
public final class ApiLayer {

    private ApiLayer() {
        // Utility class
    }

    // This should never null, and if it does something horrible has gone wrong and that should be the least of our worries
    private static WorldManager wm = WorldManager.getInstance();

    /*
     * DEPRACATED FIX METHODS
     */
    // CalculableType
    @Deprecated
    public static CalculableType getType(de.bananaco.bpermissions.api.util.CalculableType type) {
        return (type == de.bananaco.bpermissions.api.util.CalculableType.GROUP) ? CalculableType.GROUP : CalculableType.USER;
    }

    @Deprecated
    public static String[] getGroups(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name) {
        return getGroups(world, getType(type), name);
    }

    @Deprecated
    public static Permission[] getPermissions(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name) {
        return getPermissions(world, getType(type), name);
    }

    @Deprecated
    public static synchronized Map<String, Boolean> getEffectivePermissions(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name) {
        return getEffectivePermissions(world, getType(type), name);
    }

    @Deprecated
    public static String getValue(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String key) {
        return getValue(world, getType(type), name, key);
    }

    @Deprecated
    public static void addGroup(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String groupToAdd) {
        addGroup(world, getType(type), name, groupToAdd);
    }

    @Deprecated
    public static void setGroup(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String groupToAdd) {
        setGroup(world, getType(type), name, groupToAdd);
    }

    @Deprecated
    public static void removeGroup(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String groupToRemove) {
        removeGroup(world, getType(type), name, groupToRemove);
    }

    @Deprecated
    public static boolean hasGroup(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String group) {
        return hasGroup(world, getType(type), name, group);
    }

    @Deprecated
    public static boolean hasGroupRecursive(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String group) {
        return hasGroupRecursive(world, getType(type), name, group);
    }

    @Deprecated
    public static void addPermission(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, Permission permissionToAdd) {
        addPermission(world, getType(type), name, permissionToAdd);
    }

    @Deprecated
    public static void removePermission(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String permissionToRemove) {
        removePermission(world, getType(type), name, permissionToRemove);
    }

    @Deprecated
    public static boolean hasPermission(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String node) {
        return hasPermission(world, getType(type), name, node);
    }

    @Deprecated
    public static void setValue(String world, de.bananaco.bpermissions.api.util.CalculableType type, String name, String key, String value) {
        setValue(world, getType(type), name, key, value);
    }

    /*
     * Used for getting values
     */

    /**
     * Used to get the groups of a user or a group as a String[] array
     *
     * @param world
     * @param type
     * @param name
     * @return String[]
     */
    public static String[] getGroups(String world, CalculableType type, String name) {
        World w;

        if (wm.getUseGlobalFiles()) {
            w = wm.getDefaultWorld();
        } else {
            w = wm.getWorld(world);
        }

        // Null checks everywhere!
        if (w == null || type == null || name == null) {
            return new String[0];
        }

        Calculable c = w.get(name, type);
        List<String> g = c.serialiseGroups();
        String[] groups = g.toArray(new String[g.size()]);
        return groups;
    }

    /**
     * Used to get the permissions of a user or a group as a Permission[] array
     * Remember, Permission can be true or false
     *
     * @param world
     * @param type
     * @param name
     * @return Permission[]
     */
    public static Permission[] getPermissions(String world, CalculableType type, String name) {
        World w = wm.getWorld(world);
        // Null checks everywhere!
        if (w == null || type == null || name == null) {
            return new Permission[0];
        }
        Calculable c = w.get(name, type);
        Set<Permission> p = c.getPermissions();
        Permission[] permissions = p.toArray(new Permission[p.size()]);
        return permissions;
    }

    public static synchronized Map<String, Boolean> getEffectivePermissions(String world, CalculableType type, String name) {
        return getEffectivePermissions(world, type, name, false);
    }

    /**
     * Returns an effective set of the permissions including calculated
     * inheritance from global files!
     * <p/>
     * Used internally and is also accessible to the world
     *
     * @param world
     * @param type
     * @param name
     * @param recalculate
     * @return Map<String, Boolean> permissions
     */
    public static synchronized Map<String, Boolean> getEffectivePermissions(String world, CalculableType type, String name, boolean recalculate) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        // our two thingies
        World global;
        World w;
        // define them
        global = wm.getWorld(null);
        w = world == null ? null : wm.getWorld(world);
        // do we apply globals?
        if (wm.getUseGlobalFiles()) {
            Calculable c = global.get(name, type);
            if (recalculate) {
                c.setDirty(true);
            }
            permissions.putAll(((MapCalculable) c).getMappedPermissions());
        }
        // now we apply the per-world stuff (or globals if w==null)
        if (w != null) {
            Calculable c = w.get(name, type);
            if (recalculate) {
                c.setDirty(true);
            }
            permissions.putAll(((MapCalculable) c).getMappedPermissions());
        }
        // custom node checking
        for (String key : new HashSet<String>(permissions.keySet())) {
            if (CustomNodes.contains(key)) {
                Map<String, Boolean> children = CustomNodes.getChildren(key);
                // if negative
                if (!permissions.get(key)) {
                    children = Permission.reverse(children);
                }
                // push into Map
                permissions.putAll(children);
            }
        }
        return permissions;
    }

    /**
     * Static access to WorldManager.getInstance().update();
     *
     * @return success
     */
    public static boolean update() {
        return wm.update();
    }

    /**
     * Used to return the metadata value for a user or a group. Will never
     * return null but may return ""
     *
     * @param world
     * @param type
     * @param name
     * @param key
     * @return String
     */
    public static String getValue(String world, CalculableType type, String name, String key) {
        World w = wm.getWorld(world);
        String v = "";
        Calculable c;
        // Fix for Vault bug 112 https://github.com/MilkBowl/Vault/issues/112
        if (w == null || type == null || name == null || key == null) {
            return v;
        }
        
        // Add support for prefix/suffix from global files
        if (wm.getUseGlobalFiles()) {
            World global = wm.getDefaultWorld();
            c = global.get(name, type);
            v = c.getEffectiveValue(key);
        } 

        // per world meta overrides global meta if it exists
        if (w.get(name, type).containsEffectiveValue(key)) {
            c = w.get(name, type);
            v = c.getEffectiveValue(key);
        }
        return v;
    }

     /*
     * Used for setting values
     */

    /**
     * Used to add a single group to a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param groupToAdd
     */
    public static void addGroup(String world, CalculableType type, String name, String groupToAdd) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || groupToAdd == null) {
            return;
        }

        ActionExecutor.execute(name, type, ActionType.ADD_GROUP.getName(), groupToAdd, world);
    }

    /**
     * Used to set the group of a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param groupToAdd
     */
    public static void setGroup(String world, CalculableType type, String name, String groupToAdd) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || groupToAdd == null) {
            return;
        }

        ActionExecutor.execute(name, type, ActionType.SET_GROUP.getName(), groupToAdd, world);
    }

    /**
     * Used to remove a single group from a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param groupToRemove
     */
    public static void removeGroup(String world, CalculableType type, String name, String groupToRemove) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || groupToRemove == null) {
            return;
        }

        ActionExecutor.execute(name, type, ActionType.REMOVE_GROUP.getName(), groupToRemove, world);
    }

    /**
     * Returns true if the user or group directly carries the named group as a
     * child group
     *
     * @param world
     * @param type
     * @param name
     * @param group
     * @return boolean
     */
    public static boolean hasGroup(String world, CalculableType type, String name, String group) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || group == null) {
            return false;
        }
        Calculable c = w.get(name, type);
        return c.hasGroup(group);
    }

    /**
     * Returns true if the user or group or any inherited groups carry the named
     * group as a child group
     *
     * @param world
     * @param type
     * @param name
     * @param group
     * @return boolean
     */
    public static boolean hasGroupRecursive(String world, CalculableType type, String name, String group) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || group == null) {
            return false;
        }
        Calculable c = w.get(name, type);
        return c.hasGroupRecursive(group);
    }

    /**
     * Adds a single permission (String, Boolean) to a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param permissionToAdd
     */
    public static void addPermission(String world, CalculableType type, String name, Permission permissionToAdd) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || permissionToAdd == null) {
            return;
        }
        ActionExecutor.execute(name, type, ActionType.ADD_PERMISSION.getName(), permissionToAdd.serialize(), world);
    }

    /**
     * Removes a single permission (String, Boolean) from a user or a group The
     * permission object is instead a String, the boolean does not matter here.
     *
     * @param world
     * @param type
     * @param name
     * @param permissionToRemove
     */
    public static void removePermission(String world, CalculableType type, String name, String permissionToRemove) {
        World w = wm.getWorld(world);
        if (w == null || type == null || name == null || permissionToRemove == null) {
            return;
        }
        ActionExecutor.execute(name, type, ActionType.REMOVE_PERMISSION.getName(), permissionToRemove, world);
    }

    /**
     * Returns whether the user or group has the permission node
     *
     * @param world
     * @param type
     * @param name
     * @param node
     * @return boolean
     */
    public static boolean hasPermission(String world, CalculableType type, String name, String node) {
        long t = System.currentTimeMillis();
        World w = wm.getWorld(world);

        if (w == null || type == null || name == null || node == null) {
            return false;
        }

        Map<String, Boolean> permissions = getEffectivePermissions(world, type, name);
        boolean b = Calculable.hasPermission(node, permissions);

        long f = System.currentTimeMillis();
        Debugger.log("Elapsed milliseconds for hasPermission " + name + " in " + world +" - " + node + ":" + b +" :" + (f - t) + "ms");
        return b;
    }

    /**
     * Used to set the metadata value for a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param key
     * @param value
     */
    public static void setValue(String world, CalculableType type, String name, String key, String value) {
        World w = wm.getWorld(world);
        // NPE FIX
        if (w == null || type == null || name == null || key == null) {
            return;
        }
        ActionExecutor.execute(name, type, ActionType.ADD_META.getName() + ":" + key, value, world);
    }

    /**
     * Used to remove a metadata value from a user or a group
     *
     * @param world
     * @param type
     * @param name
     * @param key
     * @param value
     */
    public static void removeValue(String world, CalculableType type, String name, String key, String value) {
        World w = wm.getWorld(world);
        // NPE FIX
        if (w == null || type == null || name == null || key == null) {
            return;
        }
        ActionExecutor.execute(name, type, ActionType.REMOVE_META.getName() + ":" + key, value, world);
    }

    /**
     * Used to add a listener to a world that runs when a calculable changes
     *
     * @param world the world to add the listener to
     * @param listener An instance of CalculableChangeListener
     */
    public static void addChangeListener(String world, CalculableChangeListener listener) {
        World w = wm.getWorld(world);
        // NPE FIX
        if (w == null || listener == null) {
            return;
        }

        w.addChangeListener(listener);
    }
}