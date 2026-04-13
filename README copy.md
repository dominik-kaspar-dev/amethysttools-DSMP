# AmethystTools (Paper 1.21.x)

AmethystTools adds a custom Amethyst Pickaxe with configurable mining behavior.

## Features

- Configurable area mining (`radius` and `depth`)
- Pickaxe expiry timer with auto-updating lore
- Automatic removal when expired
- Configurable blocked materials
- Configurable command/player messages in a separate file

## Permissions

- `amethysttools.use` - use the Amethyst Pickaxe ability
- `amethysttools.admin` - use admin commands

## Commands

- `/at give <player> [item] [amount]`
- `/at reload`

## Generated Plugin Files

On first startup, the plugin creates:

- `plugins/AmethystTools/config.yml`
- `plugins/AmethystTools/messages.yml`

## Config Version Checks

Both configuration files are versioned:

- `config.yml` uses `config-version`
- `messages.yml` uses `messages-version`

Both values should match the plugin version from `plugin.yml` (example: `1.1.0`).

On startup and on `/at reload`, the plugin checks these values and logs a warning if a file is outdated. This makes upgrades safer and makes it clear when you need to merge new keys.

## Main Config Options

- `mining.radius` (default `1`) - 3x3 area when set to 1
- `mining.depth` (default `1`) - one layer deep
- `expiry.days` (default `3`) - tool lifetime
- `items.<item_key>` - one section per configurable custom item
- `items.<item_key>.aliases` - names accepted by `/at give <player> <item>`
- `items.<item_key>.enchantments` - map of enchantment to level
- `blocked-materials` - materials that can never be mined by the tool

Default blocked list includes: `BEDROCK`, `SPAWNER`, `OBSIDIAN`, `CRYING_OBSIDIAN`, `END_PORTAL_FRAME`, `END_PORTAL`, `END_GATEWAY`, `NETHER_PORTAL`, `REINFORCED_DEEPSLATE`.

## Messages Config

All user-facing messages are in `messages.yml` under the `messages` section. Example keys:

- `no-permission`
- `usage`
- `invalid-player`
- `invalid-item`
- `gave-item`
- `received-item`
- `reloaded`
- `tool-expired`

## Per-Item Config Example

```yaml
items:
	amethyst_pickaxe:
		aliases:
			- pickaxe
			- amethystpickaxe
			- amethyst_pickaxe
		material: NETHERITE_PICKAXE
		name: "&dAmethyst Pickaxe"
		lore:
			- "&7Mines in a configurable area"
			- "&7Expires in: &e%time%"
		unbreakable: false
		glow: true
		enchantments:
			EFFICIENCY: 5
			UNBREAKING: 3
			SILKTOUCH: 1
```

Add more items by creating additional keys under `items`.

## Build (Maven)

Requirements:

- Java 21
- Maven 3.9+

Build:

```bash
mvn clean package
```

Output jar:

- `target/amethysttools-1.1.0.jar`

## Install

1. Stop your Paper server.
2. Put `target/amethysttools-1.1.0.jar` into server `plugins/`.
3. Start server.
4. Edit `plugins/AmethystTools/config.yml` and `plugins/AmethystTools/messages.yml`.
5. Run `/amethysttools reload` after changes.

## Version Update Checklist (for new releases)

When updating plugin version (example: `1.1.0` -> `1.1.1`), use this checklist:

1. Update `<version>` in `pom.xml`.
2. Update `version` in `src/main/resources/plugin.yml`.
3. Update jar references in this README.
4. If `config.yml` gets new/changed keys, set `config-version` to the plugin version and document the new keys.
5. If `messages.yml` gets new/changed keys, set `messages-version` to the plugin version and document the new keys.
6. Build and test with `mvn clean package`.

## LuckPerms Example

```bash
/lp group default permission set amethysttools.use true
/lp user YourName permission set amethysttools.admin true
```
