# Valoris

**Build cities, unite them into kingdoms, research your way from the Bronze Age to gunpowder, and take your rivals' cities by siege.**

Valoris is a medieval nation-building mod for multiplayer servers. Players found cities, claim land, join kingdoms, wage wars with allies, besiege enemy cities and decide the fate of conquered ones. A technology tree spread over four eras decides what each realm can craft. Professions, a hand-shaped world map, soil quality, climate, wild beasts and a slower, harsher survival make every region and every trade matter.

Everything is managed through one in-game menu (default key **K**) or the `/valoris` commands, and almost every rule is configurable.

---

## Contents

1. Cities
2. Kingdoms
3. Diplomacy and war
4. Sieges
5. Conquest
6. Technology tree and eras
7. Armory Table
8. Era balance: weapons and armor
9. Metallurgy: bronze, iron and steel
10. Professions (skills)
11. The Valoris world map
12. Soil quality, farming and ores
13. Healing
14. Climate: cold and heat
15. Wild animals: wolf packs, great wolves and bears
16. Roads, horses and wagons
17. Respawn and the first join
18. Survival rules
19. Chat
20. Moderation tools
21. Backups and data safety
22. Music
23. Mod compatibility
24. Configuration
25. Commands

---

## 1. Cities

- **Found a city** by placing the **City Banner**, a sword in the stone. The banner marks the heart of your city, and it is what besiegers must destroy.
- The banner drops nothing and does not need a block below it, so removing the ground under it does not destroy it.
- **Roles:** wanderer → resident → city ruler → king.
- **Residents:**
  - the ruler invites players, or players ask to join (`request`, `accept`, `decline`),
  - the ruler can kick residents, and residents can leave,
  - the ruler can hand the city over to another resident (`transfer`).
- **Rename and recolour.** Each city has a colour on the map. Every city of a kingdom wears the kingdom's colour.
- **Land claims** (built on FTB Chunks):
  - only the city ruler can claim or unclaim, and players outside a city cannot claim at all,
  - new chunks must touch the city's land,
  - the chunk holding the banner cannot be unclaimed, and neither can a chunk whose removal would cut the land in two,
  - claim limit: 49 chunks at the start (Expand Domain I is known from the start), raised by **Expand Domain II–IV** to 64 / 81 / 128,
  - chunk force-loading is disabled for everyone, so nothing stays loaded and lags the server.
- **Move the banner** to a new spot when you need to.
- **Notice board:** every city may pin one public notice (a title and a text). Only the ruler posts it, and it disappears after 24 hours.
- **Inactive residents** who have not joined the server for 60 hours are removed from their city automatically.
  - City rulers and kings are never removed.
  - Time the server was offline does not count.
- **City integrity:** a city's team can only change through Valoris. Leaving, kicking or disbanding from FTB screens, other mods or modified clients is refused, so cities and their technologies cannot vanish by accident.

## 2. Kingdoms

- **Found a kingdom** from a city of at least **3 residents**. That city becomes the capital, and its ruler becomes the king.
- **Grow the kingdom:**
  - the king invites independent cities, and cities can ask to join,
  - the king can expel cities,
  - a kingdom holds up to 10 cities.
- **Hand over the crown** to another city's ruler, rename the kingdom, change its colour, or disband it.
- **Shared land:** residents of any city of a kingdom may build, break and use things in every city of that kingdom, like its own residents.
- **Shared knowledge:** all cities of a kingdom know the same technologies. A city that later leaves keeps them.
- **Rebellion:**
  - a city can rise against its kingdom and become independent, which starts a war of rebellion,
  - a rebellion that is put down puts the city on a 24-hour cooldown before it can rebel again.
- **Fall of the capital:** if the capital is conquered, the kingdom breaks apart and its other cities become free.

## 3. Diplomacy and war

A **realm** is a kingdom, led by its king, or an independent city, led by its ruler. Realms deal with each other through diplomacy.

- **Relations:** neutral, alliance, war and peace.
  - Alliances and peace need the other side to accept.
  - Declaring war and breaking an alliance are one-sided.
- **Coalition wars:**
  - the realm that declares war leads the attackers, and the realm attacked leads the defenders,
  - allies of the attacked realm join its defence automatically,
  - allies can be **called to arms** for an offensive, and they accept or refuse,
  - a realm never fights against its own ally.
- **Peace:**
  - peace between the two leaders ends the whole war,
  - peace with a single member takes only that member out of the war.
- **Truce:** after peace, the same realms cannot fight each other again for 6 hours.
- **War screen:** both sides side by side, with every realm, its cities and its totals.
  - It includes a chronicle of the war, sieges under way and a list of enemy cities to target.
  - Casualties (players killed by the other side) are counted for every war.
- **Inbox:** every invitation, proposal, call to arms and conquest request appears as a card with a timer and Accept / Decline buttons. It opens by itself when something new arrives.

## 4. Sieges

- **City map:**
  - before a siege, the attacking leader studies the target city on a top-down map,
  - the map shows city walls in blue, buildings in green and the banner in red, with terrain shading and contour lines,
  - zoom with the scroll wheel and drag to move,
  - the siege is started from this screen.
- **When a siege can be declared:**
  - only against a realm you are at war with,
  - only when **more than 50% of the defending realm's residents are online** (no offline raids),
  - not against a city that has just survived a siege: it is protected for 2 hours.
- **Phases:**
  - **preparation (15 minutes):** both sides get ready,
  - **active siege (45 minutes):** the attackers must destroy the banner. If it survives until the end, the defenders win.
  - The countdown shows as a boss bar, and big on-screen titles mark every turning point.
- **Destroying the banner:** attackers bring it down by hitting it 10 times. The hits of all attackers count together, at most one hit per half second per player. The defenders are alerted when their banner is under attack.
- **Allies:**
  - other enemies of the defender may join a siege that is already under way,
  - everyone on a side fights in every siege of that side.
- **War zone rules during the active phase:**
  - the city's claim protection is lifted, and explosions (siege engines) can break its walls,
  - only the two sides may act inside the city: bystanders cannot build, break, use, fight or interact there,
  - both sides get Mining Fatigue II on the city's land,
  - both sides may place **one block every 10 seconds** to patch breaches, raise barricades and set ladders,
  - nobody may pour fluids or light fires there,
  - outside a siege, explosions never destroy city land, except a city's own residents blowing up their own land.
- **Siege Machines compatibility:** projectiles destroy blocks in and behind water too. Water no longer absorbs the blast, and the water itself is not removed.

## 5. Conquest

When a city falls, the conquering leader gets a decision screen with these options:

- **Appoint a ruler:** a resident of the fallen city, or one of the conqueror's own residents, who moves there. The city joins the conqueror's kingdom.
- **Subjugate:** force the city into the kingdom. Its current ruler keeps ruling it.
- **Release:** leave the city alone, and it is free again.
- **Destroy** the city completely.

While the conqueror decides, the fallen city is frozen: no disbanding, diplomacy, claims, joining a kingdom or kicking residents. If no decision is made in time, the city goes free. A ruler without a kingdom can only release or destroy.

## 6. Technology tree and eras

Technologies belong to the realm, so every city of a kingdom shares them.

- **Four eras:** Bronze Age → Iron Age → Steel Age → Gunpowder Age. Each era opens with its own technology.
- **The tree:**
  - Bronze Tools, Weapons, Armor and Shields, Siege Engines,
  - Iron Tools, Mechanics, Shipbuilding, Iron Weapons, Armor and Shields, Trebuchet,
  - Steel Tools and Weapons, Plate Armor, Steel Shields, Mortar, Automation,
  - Firearms and Culverin,
  - Expand Domain I to IV, which raise the claim limit.
- **Research:**
  - the realm's leader starts a research once the prerequisites are known,
  - the best skill levels among all the realm's members must meet the requirements (for example Blacksmith 5 and Miner 12),
  - research takes real time and keeps running while the server is off,
  - one research runs at a time per realm.
- **Crafting locks:** an item cannot be crafted on a crafting table, at the Armory Table or on a smithing table until the realm knows the technology that unlocks it.
- **Tech tree screen:**
  - pan by dragging,
  - known technologies have a green frame, the current research a blue one,
  - click a node to see what it unlocks and to start the research.
- The whole tree, with costs, requirements, unlocks and research times, lives in `config/valoris2/technology.json`. Reload it with `/valoris tech reload`.

## 7. Armory Table

A workbench with an anvil and a weapon rack. It is the forge of the mod: medieval weapons, armor, horse gear, shields, ranged weapons, siege engines and armor decorations are made here.

- **Categories:** Weapons, Helmets, Chestplates, Leggings, Boots, Horse, Shields, Ranged, Siege Engines and Decorations. Each is split into eras.
- **Item details:**
  - every item shows its real stats: damage, attack speed, reach, armor, toughness and durability,
  - every cost line shows what you have against what you need,
  - the details scroll when they are long, and the Craft button always stays at the bottom.
- **Search box** to find items by name.
- **Requirements:** items need the right technology, and some items can only be made by a king (crowns).
- **Costs grow with the era.** Armor also needs wolf fur (1 / 2 / 3 per piece by era), and Steel Age armor needs bear fur too.
- **Recipes from other mods are replaced:** the original recipes of Epic Knights and its add-ons, Siege Machines and Trotting Wagons are removed. Their items come from the Armory Table instead, priced in materials your world has.
- **Horse tab:**
  - saddle and leather horse armor in bronze,
  - iron and chainmail horse armor,
  - gold horse armor, barding and dark barding in steel,
  - Trotting Wagons wagons and wheels.
- **Fur armor**, made by this mod with its own models:
  - **Wolf Fur set** (Bronze Age): a hood with a wolf's head (long snout, pointed ears), a fur coat with a pelt down the back, leggings and boots,
  - **Bear Fur set** (Iron Age): the same, with a bear's head (short wide snout, round ears),
  - the two sets can be mixed, and both protect against the cold (see Climate).
- **Decorations:** 204 armor decorations from Epic Knights and its add-ons, spread over the four eras and unlocked by each era's opening technology. These include:
  - Greek and Roman crests and laurel wreaths,
  - Viking-era tunics, cloaks and boar crests,
  - heraldic crests, plumes and crusader cloaks,
  - plate pieces with gilded and dark variants,
  - Landsknecht hats and puffed sleeves, and hussar wings.
- **Crowns** are made of bronze, and only by kings.
- The whole catalog lives in `config/valoris2/armory.json`.

## 8. Era balance: weapons and armor

Gear from every supported mod is balanced by era, not by the mod it comes from.

- **Melee damage** = material value + weapon type.
  - A plain sword deals 5 in bronze, 6 in iron and 7 in steel.
  - Every weapon type has its own value: daggers lower, two-handed swords and polearms higher.
- **Axes** are tools: they always hit weaker than the weapons of their material (bronze 3, iron 4, steel 5).
- **Lances:**
  - they hit hard (bronze 6, iron 7, steel 8),
  - a mounted charge adds up to the same amount again, scaled by speed: a full gallop deals bronze 12, iron 14 and steel 16,
  - later-era lances charge proportionally harder.
- **Reach is capped** at +1.5 blocks. Weapons that lose reach to the cap get +0.5 damage and +0.1 attack speed for every block taken away.
- **Armor by era:**

| Era | Helmet | Chestplate | Leggings | Boots | Toughness | Horse armor |
|---|---|---|---|---|---|---|
| Bronze | 2 | 6 | 4 | 2 | 0 | 4 |
| Iron | 3 | 6 | 5 | 2 | 0 | 7 |
| Steel | 3 | 8 | 6 | 3 | 1.5 | 10 |
| Gunpowder | 4 | 8 | 7 | 3 | 2 | – |

- Every value is configurable in `armory.json` and `technology.json`.

## 9. Metallurgy: bronze, iron and steel

- **Bronze Furnace:** a crucible with two inputs, where copper and tin melt into bronze while fuel burns. Hoppers work with it: inputs from the top, fuel from the sides, output from the bottom.
- **Bloomery:** the only way to smelt iron.
- **Steel Furnace:** the only way to make steel.
- Vanilla furnaces, blast furnaces, smokers, campfires and Create's fans cannot produce iron or steel.
- **Bronze tools** sit between stone and iron. They are the first tools that can mine iron ore; stone tools no longer can.
- **Steel tools** are as good as diamond ones. Diamond and netherite tools cannot be crafted.

## 10. Professions (skills)

There are four professions, each with levels 1 to 30:

- **Blacksmith:** crafting tools, weapons and armor, and smelting metal. Better materials give more XP.
- **Farmer:** harvesting ripe crops, flax, melons, pumpkins, cane, berries and medicinal herbs, and breeding animals.
- **Miner:** mining stone and ores. Rarer ores and harder stone give more XP.
- **Hunter:** hunting wolf packs and wild animals. Pack leaders give the most XP.

How it works:

- **Pace:** about level 10 after the first evening, level 20 after a week and level 30 after about two weeks of focused play.
- **What skills are for:** the realm's best levels unlock research in the technology tree.
- **No farming XP by replacing blocks:** breaking blocks you placed yourself gives no XP for a while (no place-and-break loops). Mobs from spawners give less Hunter XP.
- **Skills are kept on death** and are backed up outside the world.

## 11. The Valoris world map

A world type with a fixed, hand-shaped map of 4000 × 4000 blocks in an endless ocean (`level-type=valoris2:valoris_map`).

- **The Heart:** the biggest continent, in the middle. Plains and forests (soil tier 1–2), with meadow and cherry grove mountains.
- **The warm continent** in the south-east: forests, savanna, jungle and swamps (tier 2–3).
- **The cold continent** in the north and north-west: taiga, windswept hills, snow and ice (tier 3–4).
- **Islands**, each of one kind: icy, taiga, green, cherry, swamp, jungle and desert, including a large desert island in the south.
- **Terrain:** continuous mountain ranges with rocky, steep slopes.
- **No structures:** no villages, temples, mineshafts or strongholds.
- The map does not depend on the seed. Caves and ores still do.
- Regions are made to differ, so players from different regions have to trade.

## 12. Soil quality, farming and ores

- **Soil tiers:** every biome has one of four soil tiers, and plants grow at a fraction of the vanilla speed:
  - tier 1 (plains): 40%,
  - tier 2 (forests): 30%,
  - tier 3 (jungle, taiga, savanna, swamp): 20%,
  - tier 4 (desert, badlands, snow and ice, peaks): 10%.
- **Hoe:** right-click with a hoe to see the soil tier.
- **Crops need claimed land:** crops on farmland grow only inside a city's claims.
- **Slower growth elsewhere:**
  - tree saplings grow at half speed,
  - mushrooms spread at soil speed,
  - bamboo cannot grow on claimed land,
  - bees no longer grow crops.
- **Ore zones:** each ore has a tier, and it generates only under land of the same or a higher tier.
  - Plains hold only coal, copper and tin.
  - Iron, gold and redstone need tier 3.
  - Diamonds and emeralds need tier 4.
  - This works with vanilla, All The Ores, Create and any ore with a `c:ores` tag.
- **Overworld quartz ore** is added.
- **Flax:** a new crop, used for linen bandages.

## 13. Healing

Natural regeneration is off. You heal with:

- **Medicinal herbs**, picked from medicinal herb bushes (which can be planted). A dry relative of the bush grows in deserts and badlands and gives medicinal roots.
- **Linen bandages** and **honey poultices**, plus combined poultices with herbs and roots.
- **Resting by a campfire:** with a full hunger bar near a lit campfire you slowly regain health, also while moving around it.

Healing items are applied over a short time, with their own animation (wrapping a bandage, rubbing herbs on the chest) that every player can see.

## 14. Climate: cold and heat

- **Two new meters** appear where the air bubbles are: **snowflakes** in icy and snowy biomes and **flames** in deserts and badlands.
- A full meter lasts **15 minutes**. When it runs out, you take damage until you fix it.
- **Cold:**
  - each piece of fur armor slows the cold down,
  - a full fur set (wolf and bear pieces mix) warms you up quickly,
  - so does a heat source nearby: fire, a campfire, lava, magma, a lit furnace (torches, lanterns and candles do not count).
- **Heat:**
  - each piece of metal armor (bronze excepted) makes it worse, and fur armor twice as much,
  - shade under a roof slowly cools you down,
  - **a bottle of water** refills the meter at once.
- Leaving those biomes refills the meters.

## 15. Wild animals: wolf packs, great wolves and bears

- **Wolf packs** of 6–8 wolves roam and hunt together:
  - each pack follows its leader, and a new leader takes over when the old one dies,
  - they spot players from 48 blocks away and attack as one,
  - they avoid a player in full armor,
  - a player who comes crouching is left alone until they strike,
  - pack wolves hit 1.5× harder than vanilla wolves,
  - packs never spawn inside cities.
- **Great wolves:** in tier 4 lands the packs are made of great wolves, 1.5× bigger, with double health and 1.5× damage.
- **Brown bears:**
  - they live alone in forests, taiga, savanna, swamps and hills,
  - they have 60 health and a crushing 10-damage blow, but poor senses,
  - a bear rears up and roars before it charges,
  - they are fully animated (walking, breathing, roaring, paw swipes),
  - they drop bear meat, the most filling meat, and bear fur.
- **Animal products:** wolves drop fur and wolf meat, and horses drop horse meat. All of them can be cooked.
- **Animals spawn once**, with the land. Breeding is the way to get more, and an animal can breed again only after 2 hours. Looking at an animal shows whether it can breed. Wolves do not breed.
- **No hostile spawning:** hostile mobs do not spawn naturally. Spawners, structures and raids still work.

## 16. Roads, horses and wagons

- **Dirt paths are roads:** players, horses, donkeys, mules and camels move **30% faster** on dirt paths made with a shovel, and so do Trotting Wagons.
- **Trotting Wagons** handle like a horse with Horse Combat Controls. No whip is needed:
  - W/S raise or lower the throttle, which stays when you release the key,
  - A/D turn, more sharply at low speed.
- **Bell:** craftable from bronze.

## 17. Respawn and the first join

- **After death** you choose where to rise:
  - in the **wilderness**, at a random place, or
  - at a **city banner** of your kingdom, after a short cooldown that is longer when that city is under siege.
- **First join:** new players get the same choice of where to begin, and they are safe until they pick.

## 18. Survival rules

- **Nether and End are closed:** no portals, no travel by any means.
- **Obsidian:** it cannot be collected or placed.
- **Lava:**
  - it cannot be picked up with a bucket, and lava sources cannot be scooped up by anything,
  - a lava bucket made any other way vanishes.
- **Bone meal:** it does not exist in survival.
- **Food:**
  - thrown eggs never hatch chicks,
  - mooshrooms give stew only once every 20 minutes,
  - suspicious stew gives no Regeneration.
- **Map and minimap** (FTB Chunks):
  - they show no other players and no mobs,
  - waypoints are disabled,
  - the map keeps only what the claim system needs.
- **FTB commands:** players cannot use FTB Teams or FTB Chunks commands. Cities and claims are handled only by Valoris.
- **Anti-logout:**
  - taking damage holds you in combat for 60 seconds, and the Disconnect button is greyed out with a countdown,
  - leaving anyway gets you a 30-minute ban.

## 19. Chat

- **Private messages:** `/msg` and `/r`.
- **Emotes:** `/me`.
- **City and kingdom chat:** `/cc` and `/kc`. Use them with a message, or alone to switch your chat to that channel.
- **Messages to another city or kingdom:** `/cmsg` and `/kmsg`.

## 20. Moderation tools

Staff commands under `/vx`:

- **Bans, temp bans and IP bans:** they use the vanilla ban lists and expire on their own.
- **Punishments:** mute and temp mute, kick, warn, and freeze (no moving, building, fighting or commands).
- **Player records:** check, punishment history, and an **alt-account check**. Staff are warned when someone joins from the IP of a banned or muted player.
- **Staff chat,** clear chat, and **spy** on private and cross-realm messages.
- **Vanish:** fully invisible to non-staff, including the world, the tab list and messages, with fake leave and join messages.
- **Invsee and ecsee:** look into a player's inventory or ender chest live. Admins can also edit it.
- **Durations** like `30m`, `2h`, `7d`, `1w` or `1d12h`.

## 21. Backups and data safety

- **Automatic backups** every 30 minutes and on shutdown, stored outside the world folder. A backup covers:
  - cities, kingdoms, technologies, wars, sieges and conquests,
  - moderation data,
  - player skills,
  - FTB Teams and Chunks data.
- **The last 48 backups are kept.** Restore one with `/valoris admin restore`.
- **Archive:** every removed city or kingdom is recorded, and its technologies can be given back.
- **Recovery:** skills and city memberships that a player lost are restored automatically when they join.

## 22. Music

- **Custom soundtrack:** every `.mp3` in `assets/valoris2/music/`, in the mod or in a resource pack, becomes a track.
- **Random shuffle:** random tracks play one after another, never the same one twice in a row.
- **Vanilla music** stays silent while custom tracks are present.
- **Volume** follows the Music slider.

## 23. Mod compatibility

**Required:**

- FTB Library
- FTB Teams
- FTB Chunks

**Supported** (balanced, crafted at the Armory Table and tied into the technology tree):

- Epic Knights and its Add-on
- Epic Knights: Antique Legacy
- Epic Knights: Dark Ages
- Epic Knights: Slavic Armory
- Siege Machines
- Trotting Wagons
- Horse Combat Controls
- All The Ores
- Create

## 24. Configuration

- `config/valoris2-common.toml` holds the general rules:
  - claim limits, siege timings, banner hits, siege cooldowns, truce length,
  - the online requirement for sieges, block placing during sieges,
  - the minimum residents for a kingdom, inactivity removal,
  - skill XP multipliers, soil speeds and ore tiers,
  - wolves, great wolves and bears,
  - climate, lances, roads,
  - healing, food rules, respawn, anti-logout, backups and dimensions.
- `config/valoris2/technology.json` holds the technology tree, the crafting locks and weapon damage.
- `config/valoris2/armory.json` holds the Armory Table catalog, the costs and armor values per era.
- Reload the JSON files in game with `/valoris tech reload`.

## 25. Commands

The in-game menu (**K**) covers everything. Each button runs one of these commands.

- **General:** `/valoris gui`, `/valoris requests`
- **Residents:** `/valoris resident info | cities | request | accept | decline | leave`
- **City rulers:** `/valoris city create | info | rename | color | claim | unclaim | banner | move | disband | invite | kick | accept | decline | transfer | kingdom join/accept/decline/rebel`
- **Kings:** `/valoris kingdom create | info | list | rename | color | invite | accept | decline | expel | crown | disband`
- **Diplomacy:** `/valoris diplomacy status | propose | accept | decline | war | call | join | refuse | withdraw | break`
- **War and conquest:** `/valoris war siege | status | conquest appoint/subjugate/release/destroy | accept | decline`
- **Technology:** `/valoris tech research | cancel`. Admins also have `finish | grant | revoke | era | reload`.
- **Skills (admin):** `/valoris skills set`
- **Backups (admin):** `/valoris admin backup | backups | restore | archive`
- **Chat:** `/msg`, `/r`, `/me`, `/cc`, `/kc`, `/cmsg`, `/kmsg`
- **Staff:** `/vx ...` (see Moderation tools)
