# AmethystTools

AmethystTools is a Paper plugin that adds configurable custom mining tools, starting with the Amethyst Pickaxe.

It is built for server owners who want powerful utility tools with safe defaults, clean permissions, and simple config-based expansion.

## Why AmethystTools

- Area mining with configurable radius and depth
- Expiring tools with automatic lore countdown updates
- Automatic removal of expired tools
- Per-item config system for easy future tool additions
- Block protection via configurable blocked-materials list
- Area-mining exclusions via `mining.excluded-materials`
- Separate messages.yml for fully editable text
- Config/message version checks to help with safe updates

## Features

- 3x3 style mining by default (`radius: 1`, `depth: 1`)
- Blocks in `mining.excluded-materials` are only mineable directly (not as 3x3 side-breaks)
- Expiry system (`expiry.days`) with `%time%` placeholder in lore
- Dynamic `/at give` item input from config aliases
- Admin reload command for config and messages
- LuckPerms-friendly permission nodes

## Commands

- /at give <player> [item] [amount]
- /at reload

## Permissions

- amethysttools.use
- amethysttools.admin

## Config Structure

The plugin uses an item map so you can add more tools later without editing Java code.

```yml
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

Add more tools by creating new keys under `items` and giving each key aliases.

## Protected Blocks (Default)

- BEDROCK
- SPAWNER
- CRYING_OBSIDIAN
- END_PORTAL_FRAME
- END_PORTAL
- END_GATEWAY
- NETHER_PORTAL
- REINFORCED_DEEPSLATE

## Area-Mining Exclusions (Default)

- OBSIDIAN

## Messages

All user-facing text is editable in messages.yml:

- no-permission
- usage
- invalid-player
- invalid-item
- gave-item
- received-item
- reloaded
- tool-expired

## Compatibility

- Server software: Paper
- Minecraft API target: 1.21.x
- Java: 21

## Install

1. Build or download the release jar.
2. Place it in your server plugins folder.
3. Start server once to generate config files.
4. Edit config.yml and messages.yml.
5. Run /at reload.
