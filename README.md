# bPermissions

bPermissions is an advanced permissions plugin for Bukkit/Spigot servers (with incomplete sponge port), providing fine-grained control over user and group permissions across multiple worlds.

## Features

- Define permissions for users and groups
- Per-world permissions - define different permissions in each Minecraft world
- Permission inheritance through group memberships
- Supports "tracks" for easy promotion/demotion of users through a group hierarchy
- Configurable via YAML files
- Commands to modify users, groups, and worlds
- Ability to import permissions from other permissions plugins
- Extensible API for developers

## Installation

1. Download the latest bPermissions jar file
2. Place the jar file in your server's `plugins` directory
3. Restart your server
4. Configure the plugin (see Configuration section)

## Configuration

### config.yml

The `config.yml` file contains general settings for bPermissions:

- `use-global-files`: Set true to make all worlds use permissions defined in global files
- `use-global-users`: Set true to share online user permissions/groups across worlds
- `auto-save`: Set true to automatically save permission changes to file  
- `track-type`: Set the promotion track type (multi, single, lump, replace)

### Permissions Files

Permissions and groups for each world are stored in YAML files under `plugins/bPermissions/worlds/<worldname>`. Global files are under `plugins/bPermissions/global`.  

- `groups.yml` defines permission groups
- `users.yml` defines user permissions and groups

See the [Bukkit page](https://dev.bukkit.org/projects/bpermissions) for more details on the file formats.

## Commands

- `/user`, `/group` - Manage user and group permissions
- `/world` - Manage per-world permissions  
- `/promote`, `/demote` - Promote/demote users along a track
- `/setgroup` - Add a user to a single group
- `/permissions` - Manage bPermissions

See the [Command Reference](https://dev.bukkit.org/projects/bpermissions/pages/bpermissions-command-list) for detailed command usage.

## Permissions

- `bPermissions.admin` - Grants access to all bPermissions commands
- `tracks.<trackname>` - Grants access to `/promote` and `/demote` for a specific track

## API

bPermissions provides a Java API for developers to integrate with other plugins. See the [API Documentation](https://dev.bukkit.org/projects/bpermissions/pages/bpermissions-api) for usage details.

## Support

If you encounter any issues or have questions, please open an issue on the [issue tracker](https://github.com/rymate1234/bPermissions/issues).

We also have an irc channel on irc.esper.net #bananacode which I rarely check but you can try your luck there.

## License

bPermissions is open-source software released under the [AOL license](LICENSE.md).