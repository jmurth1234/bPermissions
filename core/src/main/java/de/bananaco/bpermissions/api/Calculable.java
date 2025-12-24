package de.bananaco.bpermissions.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class contains the main calculations for a
 * GroupCarrier/PermissionCarrier.
 *
 * It calculates inherited permissions all the way down the line of the object.
 * This does not include checking for infinite loops, you can break this if you
 * want to.
 */
public abstract class Calculable extends CalculableMeta {

    List<Permission> effectivePermissions;
    String name;
    boolean hasCalculated = false;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Calculable(String name, Set<String> groups,
            Set<Permission> permissions, String world) {
        super(groups, permissions, world);
        // TODO does this remove the ChatColor?
        this.name = name;
        this.effectivePermissions = new ArrayList<>();
    }

    /**
     * Debugging code: used to print the total effective Permissions of an
     * object at a specified point in time
     */
    protected void print() {
        String[] perms = new String[effectivePermissions.size()];
        int i = 0;
        for (Permission perm : effectivePermissions) {
            if (perm == null) {
                System.err.println("PERM IS NULL?");
            } else if (perm.isTrue()) {
                perms[i] = perm.name();
            } else {
                perms[i] = "^" + perm.name();
            }
            i++;
        }
        System.out.println(getName() + ": " + Arrays.toString(perms));
    }

    /**
     * Used to calculate the total permissions gained by the object
     *
     * @throws RecursiveGroupException
     */
    public void calculateEffectivePermissions() throws RecursiveGroupException {
        calculateGroups();
        List<Permission> calculatedPermissions = new ArrayList<>();
        try {
            Map<String, Integer> priorities = new HashMap<>();
            //System.out.println(serialiseGroups());
            for (String gr : serialiseGroups()) {
                Group group = getWorldObject().getGroup(gr);
                group.calculateMappedPermissions();
                for (Permission perm : group.getEffectivePermissions()) {
                    if (!priorities.containsKey(perm.nameLowerCase()) || priorities.get(perm.nameLowerCase()) < group.getPriority()) {
                        priorities.put(perm.nameLowerCase(), group.getPriority());
                        if (calculatedPermissions.contains(perm)) {
                            calculatedPermissions.remove(perm);
                        }
                        calculatedPermissions.add(perm);
                    }
                }
            }
            priorities.clear();
            for (Permission perm : this.getPermissions()) {
                if (calculatedPermissions.contains(perm)) {
                    calculatedPermissions.remove(perm);
                }
                calculatedPermissions.add(perm);
            }
            hasCalculated = true;
            effectivePermissions = calculatedPermissions;
            //print();
        } catch (StackOverflowError e) {
            throw new RecursiveGroupException(this);
        }
    }

    /**
     * Return the total permissions gained by the object
     *
     * @return Set<Permission>
     */
    public List<Permission> getEffectivePermissions() {
        try {
            if (!hasCalculated || isDirty()) {
                this.calculateEffectivePermissions();
            }

            if (effectivePermissions.size() == 0) {
                this.calculateEffectivePermissions();
            }

            return effectivePermissions;
        } catch (RecursiveGroupException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the name of the calculable object
     *
     * @return String
     */
    public String getName() {
        return name.replace(".", "-");
    }

    /**
     * Returns the lowercased name of the calculable object
     *
     * @return String
     */
    public String getNameLowerCase() {
        return name.toLowerCase().replace(".", "-");
}

    @Override
    public int hashCode() {
        return getNameLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name.toLowerCase();
    }

    /**
     * Another way of checking the type besides instanceof
     *
     * @return CalculableType
     */
    public abstract CalculableType getType();

    protected abstract boolean isDirty();

    public abstract boolean hasPermission(String node);

    protected abstract World getWorldObject();

    @Override
    public void clear() {
        this.effectivePermissions.clear();
        super.clear();
    }

    public static boolean hasPermission(String node, Map<String, Boolean> perms) {
        if (node == null) {
            System.err.println("NODE IS NULL");
            return false;
        }

        node = node.toLowerCase();
        if (perms.containsKey(node)) {
            return perms.get(node);
        }

        String permission = node;
        int index = permission.lastIndexOf('.');
        while (index >= 0) {
            permission = permission.substring(0, index);
            String wildcard = permission + ".*";
            if (perms.containsKey(wildcard)) {
                return perms.get(wildcard);
            }
            index = permission.lastIndexOf('.');
        }
        if (perms.containsKey("*")) {
            return perms.get("*");
        }
        return false;
    }

    public abstract void setDirty(boolean dirty);
}
