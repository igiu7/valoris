package com.valoris;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CITY_CLAIM_LIMIT = BUILDER
            .comment("Chunks a city may claim without any Expand Domain technology (Expand Domain I-IV raise it to 49/64/81/128;",
                    "Expand Domain I is known from the start)")
            .defineInRange("cityClaimLimit", 25, 1, 1000);

    public static final ModConfigSpec.IntValue MAX_CITIES_PER_KINGDOM = BUILDER
            .comment("Maximum number of cities in one kingdom")
            .defineInRange("maxCitiesPerKingdom", 10, 2, 1000);

    public static final ModConfigSpec.IntValue MIN_RESIDENTS_FOR_KINGDOM = BUILDER
            .comment("A city needs at least this many residents (its ruler included) to found a kingdom")
            .defineInRange("minResidentsForKingdom", 3, 1, 1000);

    public static final ModConfigSpec.IntValue REQUEST_EXPIRY_SECONDS = BUILDER
            .comment("How long invitations, join requests and diplomatic proposals stay valid")
            .defineInRange("requestExpirySeconds", 600, 30, 86400);

    public static final ModConfigSpec.IntValue SIEGE_PREP_MINUTES = BUILDER
            .comment("Minutes between announcing a siege and its start")
            .defineInRange("siegePrepMinutes", 15, 1, 1440);

    public static final ModConfigSpec.IntValue SIEGE_DURATION_MINUTES = BUILDER
            .comment("How long a siege lasts. If the city banner survives this long, the defenders win")
            .defineInRange("siegeDurationMinutes", 45, 1, 1440);

    public static final ModConfigSpec.IntValue TRUCE_HOURS = BUILDER
            .comment("After peace, realms that fought each other may not go to war with each other again for this many hours:",
                    "no declaring war, no joining a war against them (also not through an ally). 0 = no truce")
            .defineInRange("truceHours", 6, 0, 720);

    public static final ModConfigSpec.IntValue SIEGE_BANNER_HITS = BUILDER
            .comment("During an active siege the attackers destroy the city banner by hitting it (left click) this many times",
                    "(hits of every attacker count together)")
            .defineInRange("siegeBannerHits", 10, 1, 1000);

    public static final ModConfigSpec.IntValue SIEGE_COOLDOWN_MINUTES = BUILDER
            .comment("After a city survives a siege it cannot be besieged again for this long")
            .defineInRange("siegeCooldownMinutes", 120, 0, 100000);

    public static final ModConfigSpec.IntValue SIEGE_PLACE_SECONDS = BUILDER
            .comment("During an active siege both sides may place blocks in the besieged city, one block every this many",
                    "seconds per player (bystanders cannot build there at all). 0 = no limit")
            .defineInRange("siegeBlockPlaceSeconds", 10, 0, 3600);

    public static final ModConfigSpec.IntValue LOOTING_MINUTES = BUILDER
            .comment("Looting time, one of the choices after a won siege: the winning side may do anything on the fallen city's land (break,",
                    "build, open chests and doors...) for this many minutes, then the city is free. 0 = no looting option")
            .defineInRange("lootingMinutes", 5, 0, 1440);

    public static final ModConfigSpec.DoubleValue SIEGE_MIN_ONLINE = BUILDER
            .comment("A siege can only be declared on a city when more than this fraction of the residents of the defending",
                    "realm (the city's kingdom, or the city alone if it is in no kingdom) are online. 0 = no requirement")
            .defineInRange("siegeMinOnlineFraction", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue INACTIVE_KICK_HOURS = BUILDER
            .comment("Residents who have not joined the server for this many hours (real time) are removed from their city,",
                    "and so from its kingdom. City rulers and kings are never removed. 0 = never")
            .defineInRange("inactiveResidentKickHours", 60, 0, 100000);

    public static final ModConfigSpec.IntValue CONQUEST_RULER_RESPONSE_MINUTES = BUILDER
            .comment("How long the ruler of a conquered city has to answer a request to join the conquering kingdom")
            .defineInRange("conquestRulerResponseMinutes", 10, 1, 1440);

    public static final ModConfigSpec.IntValue CONQUEST_DECISION_MINUTES = BUILDER
            .comment("How long (minutes of server uptime) the conqueror has to decide the fate of a fallen city.",
                    "The city cannot act meanwhile; if no decision is made in time, it goes free")
            .defineInRange("conquestDecisionMinutes", 2880, 10, 100000);
    public static final ModConfigSpec.IntValue REBEL_COOLDOWN_HOURS = BUILDER
            .comment("After a rebellion is put down (the rebel city is back in the kingdom it rose against, e.g. after",
                    "being conquered), how many hours (real time) until that city may rebel again")
            .defineInRange("rebelCooldownHours", 24, 0, 100000);

    // ---------------------------------------------------------------- skills

    public static final ModConfigSpec.DoubleValue XP_MULT_BLACKSMITH = BUILDER
            .comment("Skills: multiplier for all Blacksmith XP (1.0 = default pace, level 30 after ~2 weeks of play)")
            .defineInRange("skills.blacksmithXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_FARMER = BUILDER
            .comment("Skills: multiplier for all Farmer XP")
            .defineInRange("skills.farmerXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_MINER = BUILDER
            .comment("Skills: multiplier for all Miner XP")
            .defineInRange("skills.minerXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_HUNTER = BUILDER
            .comment("Skills: multiplier for all Hunter XP")
            .defineInRange("skills.hunterXpMultiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue SPAWNER_MOB_MULTIPLIER = BUILDER
            .comment("Skills: Hunter XP multiplier for mobs that came from a spawner")
            .defineInRange("skills.spawnerMobMultiplier", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PLACED_MEMORY_MINUTES = BUILDER
            .comment("Skills: blocks placed by players give no Miner/Farmer XP when broken within this many minutes")
            .defineInRange("skills.placedBlockMemoryMinutes", 120, 0, 10080);
    public static final ModConfigSpec.BooleanValue SHOW_XP_GAIN = BUILDER
            .comment("Skills: show '+XP' messages above the hotbar")
            .define("skills.showXpGain", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKSMITH_NAMESPACES = BUILDER
            .comment("Skills: mod ids whose items always count as Blacksmith gear when crafted on a crafting table",
                    "(the Armory Table gives Blacksmith XP for everything it makes)")
            .defineListAllowEmpty("skills.blacksmithModIds", List.of("magistuarmory", "magistuarmoryaddon",
                    "antiquelegacy", "darkagesarmory", "siegemachines"), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SIEGE_MAP_BUILT_KEYWORDS = BUILDER
            .comment("Siege map: a block counts as player-built when its id contains one of these words",
                    "(logs count too, unless they belong to a tree - leaves above them)")
            .defineListAllowEmpty("siegeMap.builtKeywords", List.of("brick", "planks", "polished", "smooth", "cut_",
                    "chiseled", "cobble", "_wall", "fence", "door", "stairs", "slab", "glass", "concrete", "wool",
                    "glazed", "tiles", "pillar", "quartz", "stripped_", "iron_bars", "palisade", "timber", "beam",
                    "casing", "shingle", "roof", "thatch", "plaster", "daub", "bookshelf", "copper_block",
                    "cut_copper", "lantern", "hay_block", "packed_mud"), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue DISABLE_NATURAL_REGENERATION = BUILDER
            .comment("Turn the naturalRegeneration game rule off when the server starts (heal with herbs, bandages, poultices)")
            .define("healing.disableNaturalRegeneration", true);

    public static final ModConfigSpec.BooleanValue DISABLE_HOSTILE_SPAWNS = BUILDER
            .comment("Hostile mobs do not spawn naturally (also world generation, patrols, reinforcements).",
                    "Spawners, trial spawners, structures, raids, spawn eggs and commands still work.")
            .define("mobs.disableHostileSpawns", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HOSTILE_SPAWN_ALLOW_LIST = BUILDER
            .comment("Hostile mobs that still spawn naturally, e.g. \"minecraft:slime\"")
            .defineListAllowEmpty("mobs.hostileSpawnAllowList", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue ANIMALS_RESPAWN = BUILDER
            .comment("Whether land animals (cows, pigs, sheep, chickens, horses...) keep spawning naturally. When false they",
                    "only appear once, with the land when it is generated; breeding still works")
            .define("mobs.animalsRespawn", false);
    public static final ModConfigSpec.BooleanValue WOLF_PACKS_ENABLED = BUILDER
            .comment("Wolves spawn and hunt in packs of 6-8 instead of the vanilla wolf spawning and behaviour")
            .define("mobs.wolfPacks", true);
    public static final ModConfigSpec.DoubleValue WOLF_PACK_CHANCE = BUILDER
            .comment("Chance, checked every 30 seconds for every player, that a wolf pack spawns 48-80 blocks away",
                    "(only when there are fewer than 8 wild wolves within 96 blocks)")
            .defineInRange("mobs.wolfPackChance", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WOLF_PACK_DAMAGE = BUILDER
            .comment("Attack damage of wild pack wolves compared to a vanilla wolf (4 -> 6 with 1.5; great wolves",
                    "get this on top of mobs.greatWolfDamage)")
            .defineInRange("mobs.wolfPackDamage", 1.5, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue GREAT_WOLF_SCALE = BUILDER
            .comment("Size of the great wolves living in tier 4 land (deserts, badlands, snow and ice, peaks)",
                    "compared to a normal wolf. Great wolf packs replace normal ones there; each drops 2 fur and 2 meat.")
            .defineInRange("mobs.greatWolfScale", 1.5, 1.0, 3.0);
    public static final ModConfigSpec.DoubleValue GREAT_WOLF_HEALTH = BUILDER
            .comment("Health of a great wolf compared to a normal wolf (wild 8 -> 16 with 2.0; tamed 40 -> 80)")
            .defineInRange("mobs.greatWolfHealth", 2.0, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue GREAT_WOLF_DAMAGE = BUILDER
            .comment("Attack damage of a great wolf compared to a normal wolf (4 -> 6 with 1.5)")
            .defineInRange("mobs.greatWolfDamage", 1.5, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue BEAR_CHANCE = BUILDER
            .comment("Chance, checked every 30 seconds for every player, that a lone bear spawns 40-72 blocks away",
                    "in tier 2 and 3 land (forests, taiga, savanna, swamps, hills...); at most one bear within 128 blocks.",
                    "0 = no bears")
            .defineInRange("mobs.bearChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEAR_HEALTH = BUILDER
            .comment("Bear health (a player has 20)")
            .defineInRange("mobs.bearHealth", 60.0, 1.0, 1024.0);
    public static final ModConfigSpec.DoubleValue BEAR_DAMAGE = BUILDER
            .comment("Bear attack damage (a wolf deals 4)")
            .defineInRange("mobs.bearDamage", 10.0, 0.0, 200.0);
    public static final ModConfigSpec.DoubleValue BEAR_DETECTION = BUILDER
            .comment("How far a bear notices players and keeps chasing them (blocks); a wolf sees 48")
            .defineInRange("mobs.bearDetection", 10.0, 2.0, 64.0);

    public static final ModConfigSpec.BooleanValue SOIL_QUALITY_ENABLED = BUILDER
            .comment("Plants grow slower depending on the soil quality (tier) of the biome")
            .define("soil.enabled", true);
    public static final ModConfigSpec.BooleanValue CROPS_NEED_CLAIM = BUILDER
            .comment("Crops on farmland (also melon/pumpkin stems, flax) grow only on claimed land (a city's chunks)")
            .define("soil.cropsNeedClaimedLand", true);
    public static final ModConfigSpec.DoubleValue SOIL_TIER1_SPEED = BUILDER
            .comment("Growth speed (fraction of vanilla) in tier 1 biomes: plains")
            .defineInRange("soil.tier1Speed", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SOIL_TIER2_SPEED = BUILDER
            .comment("Growth speed in tier 2 biomes: forests, birch forests, dark forests, meadows, rivers, mushroom fields")
            .defineInRange("soil.tier2Speed", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SOIL_TIER3_SPEED = BUILDER
            .comment("Growth speed in tier 3 biomes: jungles, taigas, savannas, swamps, hills, coasts (and unknown biomes)")
            .defineInRange("soil.tier3Speed", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SOIL_TIER4_SPEED = BUILDER
            .comment("Growth speed in tier 4 biomes: deserts, badlands, snowy and icy biomes, peaks, Nether, End")
            .defineInRange("soil.tier4Speed", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SAPLING_SPEED = BUILDER
            .comment("Growth speed of every tree sapling (fraction of vanilla; 0.5 = takes twice as long). Bone meal is not affected.")
            .defineInRange("soil.saplingSpeed", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue BAMBOO_GROWS_IN_CLAIMS = BUILDER
            .comment("Whether bamboo can grow (by itself or with bone meal) on a city's claimed land")
            .define("soil.bambooGrowsInClaims", false);
    public static final ModConfigSpec.DoubleValue DEAD_HERB_DRY_SPEED = BUILDER
            .comment("Growth speed of the dead medicinal herb in deserts and badlands (it does not grow outside them and savannas)")
            .defineInRange("soil.deadHerbDesertSpeed", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DEAD_HERB_SAVANNA_SPEED = BUILDER
            .comment("Growth speed of the dead medicinal herb in savannas")
            .defineInRange("soil.deadHerbSavannaSpeed", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOIL_BIOME_OVERRIDES = BUILDER
            .comment("Biome tiers set by hand, e.g. \"minecraft:cherry_grove=1\" or \"biomesoplenty:lavender_field=2\"")
            .defineListAllowEmpty("soil.biomeTiers", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOIL_EXEMPT_BLOCKS = BUILDER
            .comment("Plants that keep the vanilla growth speed")
            .defineListAllowEmpty("soil.exemptPlants", List.of("minecraft:cactus", "minecraft:bamboo",
                    "minecraft:bamboo_sapling", "minecraft:weeping_vines", "minecraft:twisting_vines",
                    "minecraft:nether_wart"), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue ORE_ZONES_ENABLED = BUILDER
            .comment("Ores by soil tier: an ore stays in newly generated land only under biomes of the same or a higher",
                    "soil tier (tier 1 land holds only tier 1 ores, tier 4 land holds all of them)")
            .define("ores.enabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_TIERS = BUILDER
            .comment("Ore tiers, \"name=tier\"; the name is the one in the \"c:ores/<name>\" tag. Ores not listed are left alone.",
                    "\"name=none\" removes the ore from newly generated land everywhere.")
            .defineListAllowEmpty("ores.tiers", List.of(
                    "coal=1", "copper=1", "tin=1",
                    "quartz=2", "zinc=2", "lapis=2",
                    "iron=3", "gold=3", "redstone=3",
                    "sulfur=4", "diamond=4", "emerald=4", "ruby=4", "sapphire=4", "peridot=4",
                    "salt=none", "silver=none", "lead=none", "nickel=none", "aluminum=none", "platinum=none",
                    "osmium=none", "uranium=none", "iridium=none", "fluorite=none", "cinnabar=none"),
                    () -> "", o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue ORE_ZONES_DEBUG = BUILDER
            .comment("Log how many ore blocks were removed from each new chunk")
            .define("ores.debug", false);

    public static final ModConfigSpec.IntValue BACKUP_INTERVAL_MINUTES = BUILDER
            .comment("Backups: every this many minutes (and on start / shutdown) everything important - cities, kingdoms,",
                    "technologies, wars, sieges, moderation, skills, FTB Teams / Chunks data - is copied to valoris_data/backups")
            .defineInRange("backups.intervalMinutes", 30, 5, 1440);
    public static final ModConfigSpec.IntValue BACKUP_KEEP = BUILDER
            .comment("Backups: how many of the newest backups are kept")
            .defineInRange("backups.keep", 48, 2, 10000);

    public static final ModConfigSpec.BooleanValue DISABLE_NETHER = BUILDER
            .comment("The Nether is closed: nothing travels there (portals, commands, other mods), nether portals do not",
                    "light, and players who are there are sent back to the overworld")
            .define("rules.disableNether", true);
    public static final ModConfigSpec.BooleanValue DISABLE_END = BUILDER
            .comment("The End is closed: nothing travels there, eyes of ender cannot be put into end portal frames, and",
                    "players who are there are sent back to the overworld")
            .define("rules.disableEnd", true);

    public static final ModConfigSpec.BooleanValue BLOCK_OBSIDIAN = BUILDER
            .comment("Obsidian drops nothing when broken, cannot be picked up and cannot be placed (except in creative)")
            .define("rules.blockObsidian", true);
    public static final ModConfigSpec.BooleanValue BLOCK_LAVA_BUCKET = BUILDER
            .comment("Lava cannot be picked up with a bucket (except in creative)")
            .define("rules.blockLavaBucket", true);
    public static final ModConfigSpec.BooleanValue CAMPFIRE_REST_ENABLED = BUILDER
            .comment("Being near a lit campfire with a full hunger bar (also while moving) slowly heals")
            .define("healing.campfireRest", true);
    public static final ModConfigSpec.IntValue CAMPFIRE_REST_SECONDS = BUILDER
            .comment("Resting by a campfire heals half a heart every this many seconds")
            .defineInRange("healing.campfireRestSeconds", 4, 1, 120);
    public static final ModConfigSpec.IntValue CAMPFIRE_REST_RADIUS = BUILDER
            .comment("How close (blocks) the lit campfire must be")
            .defineInRange("healing.campfireRestRadius", 4, 1, 8);
    public static final ModConfigSpec.DoubleValue CAMPFIRE_REST_EXHAUSTION = BUILDER
            .comment("Food exhaustion per half heart healed by the fire (vanilla regeneration uses 6)")
            .defineInRange("healing.campfireRestExhaustion", 2.0, 0.0, 40.0);

    public static final ModConfigSpec.IntValue BREEDING_COOLDOWN_MINUTES = BUILDER
            .comment("After breeding, an animal cannot breed again for this many minutes (server time)")
            .defineInRange("mobs.breedingCooldownMinutes", 120, 0, 100000);

    public static final ModConfigSpec.BooleanValue BAN_BONE_MEAL = BUILDER
            .comment("Bone meal does nothing, cannot be crafted, picked up or kept in survival")
            .define("food.banBoneMeal", true);
    public static final ModConfigSpec.BooleanValue EGGS_NO_CHICKS = BUILDER
            .comment("Thrown eggs never hatch chicks")
            .define("food.eggsNoChicks", true);
    public static final ModConfigSpec.IntValue MOOSHROOM_STEW_MINUTES = BUILDER
            .comment("A mooshroom gives stew (or suspicious stew) only once every this many minutes (server time)")
            .defineInRange("food.mooshroomStewMinutes", 20, 0, 10000);
    public static final ModConfigSpec.DoubleValue FISH_MOB_DROP_CHANCE = BUILDER
            .comment("Chance that a fish killed in the water (cod, salmon, tropical fish, pufferfish) drops anything;",
                    "fish are caught with a fishing rod (which also gives Hunter XP). 1 = always, as in vanilla")
            .defineInRange("food.fishMobDropChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue STEW_NO_REGENERATION = BUILDER
            .comment("Suspicious stew never gives Regeneration")
            .define("food.stewNoRegeneration", true);

    public static final ModConfigSpec.BooleanValue RESPAWN_CHOICE = BUILDER
            .comment("After death the player chooses: a random place in the world or a city banner of their kingdom")
            .define("respawn.enabled", true);
    public static final ModConfigSpec.IntValue RESPAWN_RADIUS = BUILDER
            .comment("Random respawn: within this many blocks of the world spawn.",
                    "0 = detect it: inside the world border (if a world border is set), otherwise respawn.fallbackRadius")
            .defineInRange("respawn.randomRadius", 0, 0, 30_000_000);
    public static final ModConfigSpec.IntValue RESPAWN_FALLBACK_RADIUS = BUILDER
            .comment("Random respawn radius around the world spawn when no world border is set and randomRadius is 0")
            .defineInRange("respawn.fallbackRadius", 5000, 16, 30_000_000);
    public static final ModConfigSpec.IntValue RESPAWN_COOLDOWN = BUILDER
            .comment("Seconds after death before the player can respawn at a city banner")
            .defineInRange("respawn.bannerCooldownSeconds", 20, 0, 3600);
    public static final ModConfigSpec.IntValue RESPAWN_SIEGE_COOLDOWN = BUILDER
            .comment("Seconds after death before the player can respawn at the banner of a city under siege")
            .defineInRange("respawn.besiegedBannerCooldownSeconds", 180, 0, 3600);

    public static final ModConfigSpec.BooleanValue ANTI_LOGOUT_ENABLED = BUILDER
            .comment("Anti-logout: a player who takes damage cannot log out for a while (the Disconnect button is greyed out)")
            .define("antiLogout.enabled", true);
    public static final ModConfigSpec.IntValue ANTI_LOGOUT_SECONDS = BUILDER
            .comment("Anti-logout: seconds after the last damage before the player may log out")
            .defineInRange("antiLogout.seconds", 60, 1, 3600);
    public static final ModConfigSpec.IntValue ANTI_LOGOUT_BAN_MINUTES = BUILDER
            .comment("Anti-logout: a player who leaves anyway (closing the game, losing the connection...) is banned for",
                    "this many minutes, counted from the moment they join again")
            .defineInRange("antiLogout.banMinutes", 30, 1, 100000);
    public static final ModConfigSpec.ConfigValue<String> ANTI_LOGOUT_TRIGGER = BUILDER
            .comment("Anti-logout: which damage starts it: \"any\" (also falling, fire...), \"entity\" (mobs, players,",
                    "projectiles) or \"player\" (only damage from other players)")
            // a plain validator: defineInList with List.of(...) throws on the null value NeoForge checks while the
            // entry is still missing from the file, which crashes the game at start
            .define("antiLogout.trigger", "any",
                    o -> o instanceof String s && (s.equals("any") || s.equals("entity") || s.equals("player")));
    public static final ModConfigSpec.BooleanValue ANTI_LOGOUT_TAG_ATTACKER = BUILDER
            .comment("Anti-logout: a player who hurts another player is held in combat too")
            .define("antiLogout.tagAttacker", true);

    public static final ModConfigSpec.BooleanValue CLIMATE_ENABLED = BUILDER
            .comment("Cold and heat: a cold meter in icy and snowy biomes and a heat meter in deserts and badlands, shown",
                    "where the air bubbles are. When a meter runs out the player takes damage")
            .define("climate.enabled", true);
    public static final ModConfigSpec.DoubleValue CLIMATE_MINUTES = BUILDER
            .comment("Minutes for a full meter to run out at the base pace (no armor that changes it)")
            .defineInRange("climate.minutes", 15.0, 0.5, 1000.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_DAMAGE = BUILDER
            .comment("Damage (2 = one heart) a player with an empty meter takes every climate.damageSeconds")
            .defineInRange("climate.damage", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CLIMATE_DAMAGE_SECONDS = BUILDER
            .comment("Seconds between hits while a meter is empty")
            .defineInRange("climate.damageSeconds", 2, 1, 600);
    public static final ModConfigSpec.DoubleValue CLIMATE_FAST_REFILL_SECONDS = BUILDER
            .comment("Cold: seconds to warm up from empty to full with a full set of fur armor (wolf and bear pieces mix)",
                    "or near a heat source (fire, campfire, lava, magma, a lit furnace...; not torches, lanterns or candles)")
            .defineInRange("climate.warmUpSeconds", 60.0, 1.0, 100000.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_FUR_SLOWDOWN = BUILDER
            .comment("Cold: each piece of fur armor (less than a full set) slows the cold meter by this fraction")
            .defineInRange("climate.furSlowdownPerPiece", 0.2, 0.0, 0.25);
    public static final ModConfigSpec.IntValue CLIMATE_HEAT_SOURCE_RADIUS = BUILDER
            .comment("Cold: how close (blocks) a heat source must be")
            .defineInRange("climate.heatSourceRadius", 4, 1, 8);
    public static final ModConfigSpec.DoubleValue CLIMATE_METAL_PIECE = BUILDER
            .comment("Heat: each piece of metal armor (not bronze) makes the heat meter run out this much faster",
                    "(0.25 = a full metal set doubles the pace)")
            .defineInRange("climate.metalPiece", 0.25, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_FUR_PIECE = BUILDER
            .comment("Heat: the same for each piece of fur armor (twice the metal value by default)")
            .defineInRange("climate.furPiece", 0.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_SHADE_REFILL_SECONDS = BUILDER
            .comment("Heat: seconds to cool down from empty to full under a roof (whatever armor); a water bottle refills it at once")
            .defineInRange("climate.shadeCoolSeconds", 180.0, 1.0, 100000.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_OUTSIDE_REFILL_SECONDS = BUILDER
            .comment("Seconds for a meter to refill from empty to full once the player has left its biomes")
            .defineInRange("climate.outsideRefillSeconds", 30.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue LANCE_CHARGE_BONUS = BUILDER
            .comment("Lances of the steel age (steel, diamond, netherite): a full mounted charge adds this many times the lance's",
                    "era damage on top of the hit (1.0 = a full charge doubles it). 0.95: 4 hearts through full steel armor")
            .defineInRange("lances.chargeBonus", 0.95, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue LANCE_CHARGE_BONUS_IRON = BUILDER
            .comment("The same for iron age lances (iron, silver). 0.31: 4 hearts through full iron armor")
            .defineInRange("lances.chargeBonusIron", 0.31, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue LANCE_CHARGE_BONUS_BRONZE = BUILDER
            .comment("The same for bronze age lances (bronze, copper, tin, gold, wood, stone). 0.5: 4 hearts through full bronze armor")
            .defineInRange("lances.chargeBonusBronze", 0.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue LANCE_FULL_CHARGE = BUILDER
            .comment("Lances: charge impulse (3 x mass of rider and horse x speed, as Epic Knights counts it) that gives the",
                    "full bonus; slower charges give a part of it. About 5 is a galloping horse")
            .defineInRange("lances.fullChargeImpulse", 5.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue PATH_SPEED_BONUS = BUILDER
            .comment("Roads: players, horses (donkeys, mules, camels) and Trotting Wagons on dirt paths move this much faster",
                    "(0.3 = +30%). 0 = no bonus")
            .defineInRange("roads.dirtPathSpeedBonus", 0.3, 0.0, 5.0);

    static final ModConfigSpec SPEC = BUILDER.build();
}
