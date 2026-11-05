# Valoris

**A medieval nation-building mod for multiplayer servers.** Found cities, unite them into kingdoms, research your way from the Bronze Age to gunpowder and take your rivals' cities by siege.

## What's inside

- **Cities and kingdoms** - claim land around a City Banner, invite residents, unite cities under a king.
- **Diplomacy and war** - alliances, wars, truces, peace treaties, coups and rebellions.
- **Sieges and conquest** - timed sieges with siege engines, gates and wall damage; the winner decides the fate of the city.
- **Technology tree** - four ages (Bronze, Iron, Steel, Gunpowder); a realm can only craft what it has researched.
- **Professions** - skills that level up as you play and unlock technologies.
- **Harsher survival** - no natural regeneration, healing items, climate, wolf packs and bears, soil quality and regional ores.
- **Armory Table** - all weapons and armor of the supported mods are crafted in one place, balanced per age.
- **Discord integration** (optional) - account confirmation, realm roles, a news channel and Rich Presence.

## Requirements

- Minecraft **1.21.1**, **NeoForge**
- **FTB Library**, **FTB Teams**, **FTB Chunks**

Optional, supported when installed: Epic Knights (and its add-ons), Siege Machines, Trotting Wagons, Horse Combat Controls, All The Ores, Create, Cuffed, DrinkBeer Refill.

The mod must be installed on **both the server and the clients**.

## Playing

- Press **K** to open the Valoris menu. Everything - your city, kingdom, diplomacy, technology, skills, the notice board and the leaderboard - is managed from there.
- **Found a city:** create a city in the menu, place the City Banner you receive, then claim land around it.
- **Research:** the Technology tab shows the tree, what each technology unlocks and what it needs.
- **Gear:** craft an Armory Table and right click it to make weapons, armor and shields.

## Server setup

1. Put the mod and its requirements into the `mods` folder and start the server once.
2. Edit the generated configs and restart:
   - `config/valoris2-common.toml` - claims, sieges, skills, survival, climate, login and Discord.
   - `config/valoris2/technology.json` - the technology tree and crafting locks.
   - `config/valoris2/armory.json` - the Armory Table catalog and costs.
3. The JSON files can be reloaded in game with `/valoris tech reload`.

### Login (offline-mode servers)

`auth.enabled` adds password login (`/register`, `/login`). Turn it off on online-mode servers.

### Discord bot (optional)

1. Create an application at the Discord Developer Portal, add a bot and invite it to your Discord server.
2. In `valoris2-common.toml` set `discord.enabled = true`, the bot's `discord.token` and your `discord.guildId`.
3. Optional: `discord.newsChannel` for realm news, `discord.realmRoles` for city and kingdom roles (needs Manage Roles).

Keep the config file private - the token controls the bot.

### Custom music

Every `.mp3` in `assets/valoris2/music/` of a resource pack is played as the game music, replacing the vanilla soundtrack.

## Commands

The menu (**K**) covers everything for players. Useful extras:

- **Chat:** `/cc` city chat, `/kc` kingdom chat, `/msg`, `/r`
- **Admins:** `/valoris tech grant | revoke | era | reload`, `/valoris admin backup | restore`
- **Staff:** `/vx` - bans, mutes, warnings, freeze, inventory view and vanish

## Credits

Japanese armor and weapons are a port of *Epic Knights: Japanese Armory* by chivas477 (Academic Free License 3.0).
