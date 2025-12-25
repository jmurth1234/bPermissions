# CLAUDE.md - bPermissions Codebase Guide for AI Assistants

This document provides a comprehensive guide to the bPermissions codebase, development workflows, and conventions for AI assistants working on this project.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Repository Structure](#repository-structure)
3. [Architecture](#architecture)
4. [Module Internals](#module-internals)
   - [Core API Module](#core-api-module-bpermissions-api)
   - [Bukkit Module](#bukkit-module-bpermissions-bukkit)
   - [Sponge Module](#sponge-module-bpermissions-sponge)
5. [Development Environment](#development-environment)
6. [Build System](#build-system)
7. [Code Conventions](#code-conventions)
8. [Testing](#testing)
9. [CI/CD Workflows](#cicd-workflows)
10. [Release Process](#release-process)
11. [Common Tasks](#common-tasks)
12. [Important Files](#important-files)

---

## Project Overview

**bPermissions** is an advanced permissions plugin for Minecraft servers, supporting both Bukkit/Spigot and Sponge platforms (Sponge implementation is incomplete/alpha).

### Key Features
- Fine-grained permission control for users and groups
- Per-world permissions (different permissions in each Minecraft world)
- Permission inheritance through group memberships
- Promotion/demotion tracks for group hierarchies
- YAML-based configuration
- Extensible API for developers

### Current Status
- **Version**: 2.13.0 (as of last commit)
- **Java Version**: 17
- **Build Tool**: Gradle
- **License**: AOL (Attribute Only License)
- **Repository**: https://github.com/jmurth1234/bPermissions

---

## Repository Structure

```
bPermissions/
├── .github/
│   └── workflows/           # GitHub Actions CI/CD
│       ├── build.yml        # Continuous build and test
│       └── release.yml      # Release workflow
├── bukkit/                  # Bukkit/Spigot implementation
│   ├── src/
│   │   ├── main/java/de/bananaco/bpermissions/
│   │   │   └── imp/         # Implementation classes
│   │   └── test/            # Unit tests
│   └── build.gradle
├── core/                    # Core API module (bPermissions-API)
│   ├── src/
│   │   ├── main/java/de/bananaco/bpermissions/
│   │   │   ├── api/         # Public API
│   │   │   └── util/        # Utility classes
│   │   └── test/            # Unit tests
│   └── build.gradle
├── sponge/                  # Sponge implementation (ALPHA)
│   ├── src/
│   │   ├── main/java/de/bananaco/bpermissions/
│   │   │   └── imp/         # Implementation classes
│   │   └── test/            # Unit tests
│   └── build.gradle
├── config/
│   └── checkstyle/          # Checkstyle configuration
│       └── checkstyle.xml
├── gradle/                  # Gradle wrapper
├── build.gradle             # Root build configuration
├── settings.gradle          # Multi-module project settings
├── .editorconfig            # Code formatting rules
├── README.md                # User documentation
├── RELEASING.md             # Release process guide
└── TODO                     # Development todo list
```

### Module Breakdown

#### 1. **core** (bPermissions-API)
- **Artifact ID**: `bpermissions-api`
- **Group ID**: `de.bananaco`
- **Purpose**: Platform-agnostic API and core functionality
- **Key Packages**:
  - `de.bananaco.bpermissions.api`: Public API classes
  - `de.bananaco.bpermissions.util`: Utility classes
- **Dependencies**: Minimal (json-simple, Guava 17.0)

#### 2. **bukkit** (bPermissions-Bukkit)
- **Artifact ID**: `bpermissions-bukkit`
- **Group ID**: `de.bananaco` (also published as legacy `de.banaco` - deprecated)
- **Purpose**: Bukkit/Spigot platform implementation
- **Key Packages**:
  - `de.bananaco.bpermissions.imp`: Implementation classes
  - `de.bananaco.permissions`: Legacy API compatibility
- **Dependencies**: core, Spigot API 1.20.4, OkHttp3, JSON
- **Shadow JAR**: Available with `-all` classifier (includes dependencies)

#### 3. **sponge** (bPermissions-Sponge)
- **Artifact ID**: `bpermissions-sponge`
- **Group ID**: `de.bananaco`
- **Purpose**: Sponge platform implementation
- **Status**: ALPHA - incomplete port
- **Version Suffix**: Always includes `-alpha`
- **Dependencies**: core, SpongeAPI 4.1.0
- **Shadow JAR**: Available with `-all` classifier

---

## Architecture

### API Hierarchy

The core API uses a hierarchical inheritance structure:

```
WorldCarrier (base)
  └── PermissionCarrier (adds permission tracking)
      └── GroupCarrier (adds group management)
          └── CalculableMeta (adds metadata)
              └── Calculable (adds permission calculation)
                  ├── Group (via CalculableWrapper)
                  └── User (via MapCalculable)
```

### Core Concepts

1. **World**: Represents a Minecraft world with its own permission set
2. **User**: A player with permissions and group memberships
3. **Group**: A permission template that users can inherit from
4. **Permission**: A permission node (e.g., `bukkit.command.help`)
5. **Calculable**: Objects that can calculate effective permissions
6. **WorldManager**: Central manager for all worlds and permissions

### Platform Implementation Pattern

Each platform implementation (Bukkit, Sponge) follows this pattern:
1. Bootstrap through platform plugin loader
2. Initialize `WorldManager` and `ApiLayer`
3. Inject into platform's permission system
4. Load permissions from YAML files
5. Register commands and listeners

---

## Module Internals

This section provides in-depth documentation on how each module works internally, including class hierarchies, data flow, and key implementation details.

### Core API Module (bPermissions-API)

The core module provides the platform-agnostic permission system foundation that all implementations build upon.

#### Class Hierarchy Deep Dive

**Complete Inheritance Tree:**

```
MetaData (abstract)
  ├── WorldCarrier (abstract) - adds world association
  │   └── PermissionCarrier (abstract) - adds permission storage
  │       └── GroupCarrier (abstract) - adds group membership
  │           └── CalculableMeta - adds metadata inheritance
  │               └── Calculable (abstract) - adds permission calculation
  │                   └── MapCalculable (abstract) - adds Map-based caching
  │                       └── CalculableWrapper (abstract) - adds events & auto-save
  │                           ├── User - player permissions
  │                           └── Group - permission templates

World - manages Users and Groups for a world
ApiLayer - static API facade for external plugins
WorldManager - singleton managing all World instances
ActionExecutor - utility for executing permission actions
```

**Key Responsibilities by Class:**

1. **MetaData** (`core/src/main/java/de/bananaco/bpermissions/api/MetaData.java`)
   - Stores key-value metadata (prefix, suffix, custom values)
   - Provides `getValue()`, `setValue()`, `getPriority()`
   - Base for all permission objects

2. **WorldCarrier** (`core/src/main/java/de/bananaco/bpermissions/api/WorldCarrier.java`)
   - Associates object with a specific world
   - Stores world name reference

3. **PermissionCarrier** (`core/src/main/java/de/bananaco/bpermissions/api/PermissionCarrier.java`)
   - Manages `Set<Permission>` for direct permissions
   - Methods: `addPermission()`, `removePermission()`, `getPermissions()`

4. **GroupCarrier** (`core/src/main/java/de/bananaco/bpermissions/api/GroupCarrier.java`)
   - Manages group memberships (`Set<String>` names)
   - Resolves group names to Group objects
   - Methods: `addGroup()`, `removeGroup()`, `hasGroupRecursive()`

5. **CalculableMeta** (`core/src/main/java/de/bananaco/bpermissions/api/CalculableMeta.java`)
   - Calculates effective metadata from group hierarchy
   - Priority-based metadata inheritance
   - Higher priority groups override lower priority

6. **Calculable** (`core/src/main/java/de/bananaco/bpermissions/api/Calculable.java`)
   - **Core permission calculation engine**
   - Computes effective permissions from groups and direct permissions
   - Handles wildcard permission resolution (`*`, `admin.*`)
   - Detects recursive group dependencies

7. **MapCalculable** (`core/src/main/java/de/bananaco/bpermissions/api/MapCalculable.java`)
   - Optimizes permission checks by caching as `Map<String, Boolean>`
   - Dirty flag system for lazy recalculation
   - Fast permission lookups

8. **CalculableWrapper** (`core/src/main/java/de/bananaco/bpermissions/api/CalculableWrapper.java`)
   - Adds change notification system
   - Triggers auto-save when enabled
   - Cascades dirty flags to dependent objects

#### Permission Calculation Algorithm

**Step-by-Step Process:**

```java
// When user.hasPermission("admin.panel") is called:

1. Check if calculations are dirty
   └─ If dirty: recalculate effective permissions

2. calculateEffectivePermissions()
   ├─ Resolve group names to Group objects
   ├─ For each group (sorted by priority):
   │  ├─ Get group's effective permissions
   │  └─ Add to list (higher priority overwrites)
   ├─ Add direct permissions (always override groups)
   └─ Store in effectivePermissions List

3. Convert to Map for fast lookup
   └─ calculateMappedPermissions()
      └─ permissions.put(node.toLowerCase(), isTrue)

4. Check permission in Map
   ├─ Exact match: "admin.panel" → return true/false
   ├─ Wildcard match: "admin.*" → return true/false
   ├─ Root wildcard: "*" → return true/false
   └─ Default: return false (deny)
```

**Priority System Example:**

```java
// Group hierarchy with priorities
Group defaultGroup = new Group("default", world);
defaultGroup.setValue("priority", "1");
defaultGroup.setValue("prefix", "[Member]");

Group vipGroup = new Group("vip", world);
vipGroup.setValue("priority", "10");
vipGroup.setValue("prefix", "[VIP]");

Group adminGroup = new Group("admin", world);
adminGroup.setValue("priority", "100");
adminGroup.setValue("prefix", "[Admin]");

User user = new User("PlayerName", world);
user.addGroup("default");
user.addGroup("vip");
user.addGroup("admin");
user.calculateEffectiveMeta();

// Result: prefix = "[Admin]" (highest priority wins)
```

#### Event System Architecture

**CalculableChangeListener Interface:**

```java
public interface CalculableChangeListener {
    void onChange(CalculableChange change);
}
```

**ChangeType Enum:**
- `ADD_GROUP` - Group added to user/group
- `REMOVE_GROUP` - Group removed
- `REPLACE_GROUP` - Group replaced (promotion)
- `SET_GROUP` - Set single group (clear others)
- `ADD_PERMISSION` - Permission added
- `REMOVE_PERMISSION` - Permission removed
- `SET_VALUE` - Metadata key set
- `REMOVE_VALUE` - Metadata key removed

**Event Flow:**

```
User.addPermission("admin.panel", true)
  ↓
CalculableWrapper intercepts
  ↓
CalculableChange created with:
  - calculable: User object
  - type: ADD_PERMISSION
  - permission: "admin.panel"
  - world: "world"
  ↓
World.runChangeListeners(change)
  ↓
All registered listeners notified
  ↓
Auto-save triggered (if enabled)
  ↓
Dirty flag set for recalculation
  ↓
Cascaded to dependent calculables
```

#### WorldManager and ApiLayer

**WorldManager** (`core/src/main/java/de/bananaco/bpermissions/api/WorldManager.java`)

```java
public class WorldManager {
    private static WorldManager instance = null;
    private World defaultWorld = null;
    private Map<String, String> mirrors = new HashMap<>();  // World aliases
    private Map<String, World> worlds = new HashMap<>();
    private boolean autoSave = false;
    private boolean useGlobalFiles = false;
    private boolean useGlobalUsers = false;

    // Singleton access
    public static WorldManager getInstance() {
        if (instance == null) {
            instance = new WorldManager();
        }
        return instance;
    }

    // Key methods
    public World getWorld(String name) {
        // Handles mirrors and default world
    }

    public void createWorld(String name, World world) {
        worlds.put(name.toLowerCase(), world);
    }
}
```

**ApiLayer** (`core/src/main/java/de/bananaco/bpermissions/api/ApiLayer.java`)

Static utility interface for external plugins:

```java
public final class ApiLayer {
    // Permission queries
    public static boolean hasPermission(String world, CalculableType type,
                                       String name, String node)
    public static Permission[] getPermissions(String world, CalculableType type,
                                             String name)
    public static Map<String, Boolean> getEffectivePermissions(String world,
                                                               CalculableType type,
                                                               String name)

    // Modifications
    public static void addPermission(String world, CalculableType type,
                                    String name, Permission perm)
    public static void addGroup(String world, CalculableType type,
                               String name, String group)
    public static void setValue(String world, CalculableType type,
                               String name, String key, String value)

    // Listeners
    public static void addChangeListener(String world,
                                        CalculableChangeListener listener)
}
```

---

### Bukkit Module (bPermissions-Bukkit)

The Bukkit module implements the core API for Bukkit/Spigot servers, integrating with Bukkit's permission system and providing YAML-based configuration.

#### Plugin Lifecycle

**Initialization Sequence:**

```
Server Startup
  │
  ├─ onLoad()
  │  └─ Mirrors.load()
  │     └─ Load mirrors.yml (world aliases)
  │
  ├─ onEnable()
  │  ├─ MainThread.getInstance().start()
  │  │  └─ Start async task processor
  │  │
  │  ├─ Config.load()
  │  │  ├─ Load config.yml
  │  │  └─ Initialize promotion track system
  │  │
  │  ├─ SuperPermissionHandler.init()
  │  │  └─ Register with Bukkit permission system
  │  │
  │  ├─ WorldLoader (listener)
  │  │  └─ Handle world initialization events
  │  │
  │  ├─ DefaultWorld.load()
  │  │  └─ Load global world configuration
  │  │
  │  ├─ Register Commands
  │  │  ├─ /user, /group, /world
  │  │  ├─ /promote, /demote
  │  │  └─ /permissions
  │  │
  │  ├─ CustomNodes.load()
  │  │  └─ Register custom permission definitions
  │  │
  │  └─ Setup online players
  │     └─ Load permissions and inject Permissibles
  │
  └─ onDisable()
     ├─ Wait for async tasks to complete
     ├─ Save all worlds
     └─ Shutdown MainThread
```

#### Bukkit Permission Integration

**SuperPermissionHandler** (`bukkit/src/main/java/de/bananaco/bpermissions/imp/SuperPermissionHandler.java`)

Bridges bPermissions with Bukkit's SuperPerms system:

```java
@EventHandler(priority = EventPriority.LOWEST)
public void onPlayerLogin(PlayerLoginEvent event) {
    setupPlayer(event.getPlayer(), false);
}

private void setupPlayer(Player player, boolean recalculate) {
    // 1. Get effective permissions from bPermissions
    UUID uuid = player.getUniqueId();
    String world = player.getWorld().getName();
    Map<String, Boolean> perms = ApiLayer.getEffectivePermissions(
        world, CalculableType.USER, uuid.toString()
    );

    // 2. Inject custom Permissible (if enabled)
    if (useCustomPermissible) {
        PermissibleBase base = new bPermissible(player, perms);
        Injector.inject(player, base);
    }

    // 3. Set SuperPerms permissions
    setPermissions(permissible, plugin, perms);

    // 4. Set metadata (prefix, suffix)
    String prefix = ApiLayer.getValue(world, CalculableType.USER,
                                     uuid.toString(), "prefix");
    player.setMetadata("prefix", new FixedMetadataValue(plugin, prefix));
}
```

**bPermissible** (`bukkit/src/main/java/de/bananaco/bpermissions/imp/bPermissible.java`)

Custom Permissible with dual-check system:

```java
public class bPermissible extends PermissibleBase {
    private final Map<String, Boolean> bpermissions;
    private final PermissibleBase oldpermissible;

    @Override
    public boolean hasPermission(String perm) {
        // Step 1: Check Bukkit SuperPerms
        boolean ret = hasSuperPerm(perm);

        // Step 2: If not set, check bPermissions
        if (!ret && !isPermissionSet(perm)) {
            ret = Calculable.hasPermission(perm, bpermissions);
        }

        return ret;
    }

    private void updatePerms() {
        // Expand Bukkit plugin permissions with children
        for (String perm : new HashSet<>(bpermissions.keySet())) {
            Permission bukkit = Bukkit.getPluginManager().getPermission(perm);
            if (bukkit != null) {
                bpermissions.putAll(bukkit.getChildren());
            }
        }
    }
}
```

**Injector** (`bukkit/src/main/java/de/bananaco/bpermissions/imp/Injector.java`)

Reflection-based Permissible injection:

```java
public static PermissibleBase inject(CommandSender sender,
                                     Permissible newpermissible) {
    try {
        // Get versioned class name (e.g., v1_20_R3)
        String className = getVersionedClassName("entity.CraftHumanEntity");
        Class<?> craftClass = Class.forName(className);

        // Access private "perm" field via reflection
        Field permField = craftClass.getDeclaredField("perm");
        permField.setAccessible(true);

        // Save old permissible
        PermissibleBase oldperm = (PermissibleBase) permField.get(sender);

        // Copy attachments
        for (PermissionAttachment attach : oldperm.getAttachments()) {
            newpermissible.addAttachment(attach.getPlugin());
        }

        // Replace permissible
        permField.set(sender, newpermissible);

        return oldperm;
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}
```

#### YAML Configuration System

**File Structure:**

```
plugins/bPermissions/
├── config.yml           # Main configuration
├── mirrors.yml          # World aliases
├── tracks.yml           # Promotion tracks
├── custom_nodes.yml     # Custom permissions
├── world/               # Per-world config
│   ├── users.yml
│   └── groups.yml
├── world_nether/
│   ├── users.yml
│   └── groups.yml
└── global/              # Global config
    ├── users.yml
    └── groups.yml
```

**YamlWorld** (`bukkit/src/main/java/de/bananaco/bpermissions/imp/YamlWorld.java`)

Handles persistence for a world:

```java
public class YamlWorld extends World {
    private File ufile;  // users.yml
    private File gfile;  // groups.yml

    @Override
    public void load() {
        // Schedule async load
        MainThread.getInstance().schedule(new TaskRunnable() {
            public void run() {
                loadUnsafe();
            }
            public TaskType getType() {
                return TaskType.LOAD;
            }
        });
    }

    private void loadUnsafe() {
        // 1. Load YAML files
        YamlConfiguration usersYaml = new YamlConfiguration();
        usersYaml.load(ufile);

        // 2. Parse users section
        ConfigurationSection users = usersYaml.getConfigurationSection("users");
        for (String uuid : users.getKeys(false)) {
            User user = getUser(uuid);

            // Load permissions
            List<String> perms = users.getStringList(uuid + ".permissions");
            for (String perm : perms) {
                Permission p = Permission.loadFromString(perm);
                user.addPermission(p.name(), p.isTrue());
            }

            // Load groups
            List<String> groups = users.getStringList(uuid + ".groups");
            for (String group : groups) {
                user.addGroup(group);
            }

            // Load metadata
            ConfigurationSection meta = users.getConfigurationSection(uuid + ".meta");
            for (String key : meta.getKeys(false)) {
                user.setValue(key, meta.getString(key));
            }
        }

        // 3. Calculate effective permissions
        for (Calculable c : getAll(CalculableType.USER)) {
            c.calculateMappedPermissions();
            c.calculateEffectiveMeta();
        }
    }

    @Override
    public void save() {
        // Schedule async save
        MainThread.getInstance().schedule(new TaskRunnable() {
            public void run() {
                saveUnsafe(false);
            }
            public TaskType getType() {
                return TaskType.SAVE;
            }
        });
    }
}
```

**users.yml Format:**

```yaml
users:
  550e8400-e29b-41d4-a716-446655440000:
    username: PlayerName
    permissions:
      - some.permission
      - ^denied.permission
    groups:
      - admin
      - moderator
    meta:
      prefix: "&5[Admin]"
      suffix: ""
      custom_key: custom_value
```

**groups.yml Format:**

```yaml
groups:
  admin:
    permissions:
      - admin.*
      - ^admin.dangerous
    groups:
      - moderator
    meta:
      prefix: "&c[Admin]"
      priority: "100"

  moderator:
    permissions:
      - mod.*
    groups:
      - default
    meta:
      prefix: "&7[Mod]"
      priority: "50"
```

#### Command System

**Command Architecture:**

```
Plugin.onCommand()
  ├─ Check permissions
  ├─ Route to handler:
  │  ├─ /user, /group, /world → OldUserGroupCommand
  │  ├─ /promote, /demote → PromotionTrack
  │  ├─ /exec → ActionExecutor
  │  └─ /permissions → Config management
  └─ Execute action
```

**Command Session Management** (`bukkit/src/main/java/de/bananaco/bpermissions/imp/Commands.java`)

Stores per-player command context:

```java
public class Commands {
    private String world;              // Selected world
    private CalculableType calc;       // USER or GROUP
    private String name;               // Selected object name

    // Command chaining example:
    // /user PlayerName          → setCalculable(USER, "PlayerName")
    // /group admin addgroup     → addGroup("admin")
    // /user list permissions    → listPermissions()
}
```

**Example Command Flow:**

```
/user PlayerName
  └─ OldUserGroupCommand.onCommand()
     └─ cmd.setCalculable(USER, uuid, sender)

/user addgroup admin
  └─ OldUserGroupCommand.onCommand()
     └─ Commands.addGroup("admin", sender)
        └─ ActionExecutor.execute(uuid, USER, "addgroup", "admin", world)
           └─ User.addGroup("admin")
              ├─ CalculableChange event fired
              ├─ Auto-save triggered
              └─ Player permissions updated
```

#### Promotion Track System

**Four Track Types:**

1. **MultiGroupPromotion** (default)
   - Additive: promotes by adding groups
   - User accumulates groups as they progress
   - Example: default → default+moderator → default+moderator+admin

2. **SingleGroupPromotion**
   - Replaces: only one group from track at a time
   - Clear all track groups, set new one
   - Example: default → moderator → admin

3. **ReplaceGroupPromotion**
   - Replace current group with next
   - Example: default → moderator (removes default)

4. **LumpGroupPromotion**
   - Promote: add ALL groups in track
   - Demote: remove ALL groups in track

**tracks.yml:**

```yaml
default:
  - default
  - moderator
  - admin

vip:
  - vip
  - vipplus
  - vipdiamond
```

**Promotion Flow:**

```
/promote PlayerName default
  ├─ Check permission: "tracks.default"
  ├─ Get PromotionTrack implementation
  └─ track.promote("PlayerName", "default", "world")
     │
     └─ ReplaceGroupPromotion example:
        ├─ Get track groups: [default, moderator, admin]
        ├─ Find user's current position
        ├─ Remove current group
        ├─ Add next group
        └─ Save world
```

#### World Mirroring

**mirrors.yml:**

```yaml
world_nether: world
world_end: world
economy_world: main_world
```

**Purpose:** Multiple worlds share the same permission files

**Implementation:**

```java
// When world loads
String worldName = "world_nether";
if (mirrors.containsKey(worldName)) {
    worldName = mirrors.get(worldName);  // Use "world" instead
}
// world_nether and world share users.yml and groups.yml
```

#### Custom Nodes

**custom_nodes.yml:**

```yaml
permissions:
  admin.panel:
    - admin.panel.view: true
    - admin.panel.edit: true
    - admin.panel.delete: true

  moderator.tools:
    - moderator.kick: true
    - moderator.ban: true
```

**Purpose:** Define permission hierarchies that expand automatically

**Registration:**

```java
CustomNodes.load()
  ├─ For each custom node:
  │  ├─ Create Bukkit Permission with children
  │  ├─ Register with PluginManager
  │  └─ Load into core API
  └─ When player has "admin.panel":
     └─ Automatically gets all child permissions
```

#### Legacy Import System

Supports importing from other permission plugins:

```
/permissions import pex    → PermissionsEx
/permissions import p3     → Permissions 3
/permissions import gm     → GroupManager
/permissions import yml    → Generic YAML
/permissions convert       → Convert usernames to UUIDs
```

---

### Sponge Module (bPermissions-Sponge)

The Sponge module is an incomplete alpha implementation for the Sponge platform. Current status: **ALPHA - Not Production Ready**

**Key Files:**
- `sponge/src/main/java/de/bananaco/bpermissions/imp/BPermissionsPlugin.java` - Main plugin class
- `sponge/src/main/java/de/bananaco/bpermissions/imp/service/` - Sponge permission service implementation
- `sponge/src/main/java/de/bananaco/bpermissions/imp/commands/` - Sponge command implementations

**Note:** The Sponge module requires significant development before production use. Contributors should focus on bringing it up to feature parity with the Bukkit implementation.

---

## Development Environment

### Prerequisites

- **Java Development Kit**: JDK 17 (Temurin distribution recommended)
- **Build Tool**: Gradle (use included wrapper `./gradlew`)
- **IDE**: Any Java IDE (IntelliJ IDEA, Eclipse, VS Code)

### Setup

```bash
# Clone the repository
git clone https://github.com/jmurth1234/bPermissions.git
cd bPermissions

# Build the project
./gradlew build

# Run tests
./gradlew test

# Generate coverage reports
./gradlew jacocoTestReport
```

### IDE Configuration

The project uses `.editorconfig` for consistent formatting:
- **Indent**: 4 spaces for Java, Gradle, XML
- **Indent**: 2 spaces for YAML
- **Line Ending**: LF (Unix-style)
- **Charset**: UTF-8
- **Max Line Length**: 120 characters (Java)

---

## Build System

### Gradle Structure

The project uses a multi-module Gradle build:

```gradle
// Root build.gradle
ext {
    buildNumber = System.getenv('GITHUB_RUN_NUMBER') ?: 'dev'
    baseVersion = '2.13.0'
    fullVersion = buildNumber == 'release' ? baseVersion : "${baseVersion}-${buildNumber}"
}
```

### Version Strategy

1. **Development Builds**: `2.13.0-{BUILD_NUMBER}` (e.g., `2.13.0-123`)
   - Automatically published on every push to `master`
   - Build number from GitHub Actions run number

2. **Release Builds**: `2.13.0` (clean version)
   - Triggered by git tags (`v*.*.*`)
   - No build number suffix (set via `GITHUB_RUN_NUMBER=release`)

3. **Sponge Special Case**: Always appends `-alpha`
   - Development: `2.13.0-123-alpha`
   - Release: `2.13.0-alpha`

### Key Gradle Tasks

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Generate code coverage reports (JaCoCo)
./gradlew jacocoTestReport

# Run static analysis (Checkstyle + SpotBugs)
./gradlew check

# Build shadow (fat) JARs
./gradlew shadowJar

# Publish to GitHub Packages
./gradlew publish  # Requires GITHUB_ACTOR and GITHUB_TOKEN env vars

# Clean build outputs
./gradlew clean
```

### Publishing

All artifacts are published to **GitHub Packages**:
- Repository: `https://maven.pkg.github.com/jmurth1234/bPermissions`
- Authentication required (GITHUB_TOKEN with package read permissions)

**Published Artifacts**:
- `de.bananaco:bpermissions-api:{version}`
- `de.bananaco:bpermissions-bukkit:{version}` + `-all` shadow JAR
- `de.banaco:bpermissions-bukkit:{version}` (legacy, deprecated)
- `de.bananaco:bpermissions-sponge:{version}-alpha` + `-all` shadow JAR

---

## Code Conventions

### Style Guide

The project enforces code style through **Checkstyle** (`config/checkstyle/checkstyle.xml`):

#### Naming Conventions
- **Classes**: PascalCase (e.g., `WorldManager`, `ApiLayer`)
- **Methods**: camelCase (e.g., `getPermissions()`, `calculateEffective()`)
- **Variables**: camelCase
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: lowercase

#### Code Quality Rules
- **Method Length**: Max 150 lines
- **Parameter Count**: Max 7 parameters
- **Indentation**: 4 spaces (NO tabs)
- **Braces**: Required for all blocks (even single-line if/while)
- **Imports**: No wildcard imports (`import java.util.*` not allowed)
- **Whitespace**: Consistent spacing around operators and after commas

#### Common Patterns
1. **Null Safety**: Check for null before dereferencing
2. **equals() and hashCode()**: Always override together
3. **Utility Classes**: Final class with private constructor
4. **Interface Design**: Interfaces should define types, not just constants

### Java Version
- **Source Compatibility**: Java 17
- **Target Compatibility**: Java 17
- Use modern Java features where appropriate (var, records, switch expressions)

### Package Organization

```
de.bananaco.bpermissions.api        # Public API - stable interface
de.bananaco.bpermissions.imp        # Platform implementations - internal
de.bananaco.bpermissions.util       # Utilities - internal
de.bananaco.permissions             # Legacy API - backward compatibility
```

---

## Testing

### Test Framework
- **JUnit 5** (Jupiter)
- **Mockito** for mocking
- **JaCoCo** for coverage

### Test Organization
- Tests mirror source structure: `src/test/java` matches `src/main/java`
- Test classes named `{ClassName}Test.java`
- Shared test utilities in `core` module (e.g., `WorldTest`)

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :bukkit:test
./gradlew :core:test

# Generate coverage report
./gradlew jacocoTestReport

# View coverage report
open bukkit/build/reports/jacoco/test/html/index.html
```

### Coverage Reports
- Generated in `{module}/build/reports/jacoco/test/html/`
- XML reports for CI in `{module}/build/reports/jacoco/test/jacocoTestReport.xml`

---

## CI/CD Workflows

### 1. Build Workflow (`.github/workflows/build.yml`)

**Triggers**:
- Every push to `master` branch
- Pull requests to `master`

**Steps**:
1. Checkout code
2. Set up JDK 17
3. Build with Gradle
4. Run tests
5. Generate JaCoCo coverage report
6. Upload build artifacts (retention: 30 days)
7. Upload coverage reports (retention: 30 days)
8. **Publish to GitHub Packages** (only on `master` push)

**Environment Variables**:
- `GITHUB_RUN_NUMBER`: Build number appended to version
- `GITHUB_ACTOR`: GitHub username for publishing
- `GITHUB_TOKEN`: Authentication token for GitHub Packages

### 2. Release Workflow (`.github/workflows/release.yml`)

**Triggers**:
1. Git tag push matching `v*.*.*` (e.g., `v2.13.0`, `v2.14.0-beta`)
2. Manual workflow dispatch with version input

**Steps**:
1. Determine version from tag or input
2. Update `baseVersion` in `build.gradle`
3. Build with `GITHUB_RUN_NUMBER=release` (produces clean version)
4. Run tests
5. Generate coverage report
6. **Publish to GitHub Packages**
7. **Create GitHub Release**
   - Attach JAR files
   - Auto-generate release notes
   - Mark as pre-release if version contains `alpha`, `beta`, or `rc`
8. Upload artifacts to workflow (retention: 90 days)

---

## Release Process

### Version Numbering

Follow **Semantic Versioning** (`MAJOR.MINOR.PATCH`):
- **MAJOR**: Breaking changes, incompatible API changes
- **MINOR**: New features, backward-compatible
- **PATCH**: Bug fixes, backward-compatible

### Pre-release Versions
- Alpha: `v2.14.0-alpha`, `v2.14.0-alpha.1`
- Beta: `v2.14.0-beta`, `v2.14.0-beta.1`
- Release Candidate: `v2.14.0-rc.1`

### Creating a Release

#### Method 1: Git Tag (Recommended)

```bash
# 1. Update version in build.gradle
vim build.gradle  # Change baseVersion = '2.14.0'

# 2. Commit the version bump
git add build.gradle
git commit -m "chore: Bump version to 2.14.0"
git push origin master

# 3. Create and push tag
git tag v2.14.0
git push origin v2.14.0

# 4. Monitor release in GitHub Actions
```

#### Method 2: Manual Workflow Dispatch

1. Go to GitHub → Actions → "Release" workflow
2. Click "Run workflow"
3. Enter version (e.g., `2.14.0`) - NO 'v' prefix
4. Click "Run workflow"
5. Monitor progress

### Post-Release Checklist

- [ ] Verify packages published to GitHub Packages
- [ ] Check GitHub Release created with correct assets
- [ ] Test downloading and using published artifacts
- [ ] Update documentation if API changed
- [ ] Announce release (if applicable)

---

## Common Tasks

This section provides guidance for AI assistants on common development tasks.

### 1. Adding a New Permission Node

**Location**: Define in implementation modules (`bukkit` or `sponge`)

```java
// In Bukkit: bukkit/src/main/java/de/bananaco/bpermissions/imp/Permissions.java
// Register permission in plugin.yml or dynamically
```

### 2. Adding a New Command

**Bukkit Implementation**:
1. Create command class in `bukkit/src/main/java/de/bananaco/bpermissions/imp/`
2. Extend `BaseCommand`
3. Register in `Permissions.java` plugin initialization
4. Add tab completion in `GroupsTabCompleter.java` if needed

### 3. Modifying the API

**IMPORTANT**: API changes affect all dependent code!

1. Make changes in `core/src/main/java/de/bananaco/bpermissions/api/`
2. Consider backward compatibility
3. Update both `bukkit` and `sponge` implementations
4. Add tests in `core/src/test/java/`
5. Update version following semver:
   - Breaking change → increment MAJOR
   - New feature → increment MINOR
   - Bug fix → increment PATCH

### 4. Adding Dependencies

```gradle
// In module build.gradle (core, bukkit, or sponge)
dependencies {
    implementation 'group:artifact:version'
}

// For Bukkit/Sponge shadow JARs, also include in shadowJar block:
shadowJar {
    dependencies {
        include(dependency('group:artifact'))
    }
}
```

**Note**: All dependency changes must use **lowercase artifact IDs** for GitHub Packages compatibility!

### 5. Fixing Build Issues

```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Check for common issues
./gradlew check

# View detailed error output
./gradlew build --stacktrace --debug
```

### 6. Updating to New Minecraft Version

**Bukkit Module**:
1. Update Spigot API version in `bukkit/build.gradle`
2. Test for API compatibility changes
3. Update `BukkitCompat.java` if needed for version-specific code
4. Run tests thoroughly

**Sponge Module**:
1. Update SpongeAPI version in `sponge/build.gradle`
2. Check for breaking changes in Sponge API
3. Update implementation code as needed

### 7. Working with YAML Configuration

**File Locations** (runtime):
- `plugins/bPermissions/config.yml` - Main configuration
- `plugins/bPermissions/worlds/{worldname}/groups.yml` - Per-world groups
- `plugins/bPermissions/worlds/{worldname}/users.yml` - Per-world users
- `plugins/bPermissions/global/` - Global permissions

**Implementation**:
- YAML handling in `YamlWorld.java`, `YamlConfiguration.java`
- Uses SnakeYAML (via Bukkit/Sponge APIs)

### 8. Running Static Analysis

```bash
# Checkstyle
./gradlew checkstyleMain checkstyleTest

# SpotBugs
./gradlew spotbugsMain spotbugsTest

# View reports
open build/reports/checkstyle/main.html
open build/reports/spotbugs/main.html
```

### 9. Git Workflow for Feature Development

```bash
# Create feature branch
git checkout -b feature/my-feature

# Make changes and commit
git add .
git commit -m "feat: Add new feature description"

# Push to remote
git push origin feature/my-feature

# Create Pull Request on GitHub
# After approval, squash and merge to master
```

### 10. Debugging Permission Issues

Key classes to investigate:
- `SuperPermissionHandler.java` - Bukkit permission integration
- `Calculable.java` - Permission calculation logic
- `bPermissible.java` - Bukkit PermissibleBase wrapper
- `World.java` - Per-world permission storage

Enable debug logging in `config.yml` for runtime debugging.

---

## Important Files

### Configuration Files

| File | Purpose |
|------|---------|
| `build.gradle` | Root Gradle configuration, version management |
| `settings.gradle` | Multi-module project settings |
| `{module}/build.gradle` | Module-specific build configuration |
| `.editorconfig` | Code formatting rules for IDEs |
| `config/checkstyle/checkstyle.xml` | Checkstyle rules |
| `gradlew`, `gradlew.bat` | Gradle wrapper scripts |

### Documentation

| File | Purpose |
|------|---------|
| `README.md` | User-facing documentation |
| `RELEASING.md` | Detailed release process guide |
| `TODO` | Development tasks and ideas |
| `LICENSE.md` | AOL license text |
| `CLAUDE.md` | This file - AI assistant guide |

### Source Code Entry Points

| File | Purpose |
|------|---------|
| `core/src/main/java/de/bananaco/bpermissions/api/ApiLayer.java` | API facade |
| `core/src/main/java/de/bananaco/bpermissions/api/WorldManager.java` | Central manager |
| `bukkit/src/main/java/de/bananaco/bpermissions/imp/Permissions.java` | Bukkit plugin main class |
| `bukkit/src/main/java/de/bananaco/bpermissions/imp/Commands.java` | Command handling |
| `bukkit/src/main/java/de/bananaco/bpermissions/imp/YamlWorld.java` | YAML persistence |

### CI/CD

| File | Purpose |
|------|---------|
| `.github/workflows/build.yml` | Continuous build and test |
| `.github/workflows/release.yml` | Release automation |

---

## Tips for AI Assistants

### Do's ✅

1. **Read before modifying**: Always examine existing code before making changes
2. **Follow conventions**: Match existing code style and patterns
3. **Test thoroughly**: Run `./gradlew test` before committing
4. **Check style**: Run `./gradlew checkstyleMain` to validate
5. **Update both implementations**: If changing API, update both Bukkit and Sponge
6. **Consider backward compatibility**: API changes affect plugin developers
7. **Use lowercase artifact IDs**: Required for GitHub Packages
8. **Write descriptive commits**: Follow conventional commits (feat:, fix:, chore:)
9. **Add tests**: New features and bug fixes should include tests
10. **Document public APIs**: Add JavaDoc for public API methods

### Don'ts ❌

1. **Don't skip tests**: Never commit without running tests
2. **Don't use tabs**: Always use spaces (4 for Java, 2 for YAML)
3. **Don't break API**: Avoid breaking changes without major version bump
4. **Don't use wildcard imports**: Explicit imports only
5. **Don't ignore Checkstyle**: Fix warnings before committing
6. **Don't hardcode versions**: Use properties in build.gradle
7. **Don't commit IDE files**: Use .gitignore
8. **Don't push to master directly**: Use feature branches and PRs
9. **Don't skip release process**: Follow RELEASING.md for releases
10. **Don't modify generated files**: Edit source, not build outputs

### When Unsure

1. Check `RELEASING.md` for release process questions
2. Check existing code for patterns and conventions
3. Run `./gradlew tasks` to see available Gradle tasks
4. Examine recent commits for context: `git log --oneline -20`
5. Check GitHub Issues for related discussions
6. Ask for clarification rather than making assumptions

---

## Appendix: Quick Reference

### Useful Commands

```bash
# Build and test
./gradlew clean build test

# Run specific module tests
./gradlew :core:test
./gradlew :bukkit:test

# Generate all reports
./gradlew check jacocoTestReport

# Publish (requires GitHub credentials)
GITHUB_ACTOR=username GITHUB_TOKEN=token ./gradlew publish

# Create release build locally
GITHUB_RUN_NUMBER=release ./gradlew clean build
```

### Version Locations

- **Source**: `build.gradle` → `baseVersion = '2.13.0'`
- **Built**: JAR manifests and Maven POMs
- **Published**: GitHub Packages and GitHub Releases

### Key URLs

- **Repository**: https://github.com/jmurth1234/bPermissions
- **Packages**: https://github.com/jmurth1234/bPermissions/packages
- **Issues**: https://github.com/jmurth1234/bPermissions/issues
- **Actions**: https://github.com/jmurth1234/bPermissions/actions
- **Bukkit Page**: https://dev.bukkit.org/projects/bpermissions

---

**Last Updated**: 2025-12-25
**Current Version**: 2.13.0
**Maintained by**: bPermissions Team

For questions or issues with this guide, please open an issue on GitHub.
