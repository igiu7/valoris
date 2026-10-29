package com.valoris.skill;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.valoris.Config;
import com.valoris.armory.ArmoryEntry;
import com.valoris.mobs.WolfPacks;
import com.valoris.plant.DeadMedicinalHerbBlock;
import com.valoris.plant.MedicinalHerbBushBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * How much base XP each action is worth (before the per-skill config multipliers).
 * Tuned for about 4,000 XP per hour of focused work (see SkillCurve: level 30 = ~96,000 XP, about two weeks).
 */
public final class XpRules {
    private XpRules() {}

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    private static TagKey<Block> blockTag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ blacksmith

    /** XP per unit of a material, most valuable first (an item counts for the first tag it matches). */
    private static final Map<String, Double> MATERIALS = new LinkedHashMap<>();
    static {
        MATERIALS.put("c:ingots/netherite", 80.0);
        MATERIALS.put("c:gems/diamond", 40.0);
        MATERIALS.put("c:ingots/steel", 25.0);
        MATERIALS.put("c:gems/emerald", 20.0);
        MATERIALS.put("c:gems", 15.0);
        MATERIALS.put("c:ingots/iron", 10.0);
        MATERIALS.put("c:ingots/gold", 10.0);
        MATERIALS.put("c:ingots/bronze", 8.0);
        MATERIALS.put("c:ingots/copper", 3.0);
        MATERIALS.put("c:ingots", 6.0);
        MATERIALS.put("c:leathers", 3.0);
        MATERIALS.put("c:gunpowders", 2.0);
        MATERIALS.put("c:nuggets", 1.0);
        MATERIALS.put("c:strings", 1.0);
    }

    /** What one item is worth as smithing material. */
    public static double material(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (Map.Entry<String, Double> e : MATERIALS.entrySet()) {
            if (stack.is(itemTag(e.getKey()))) return e.getValue();
        }
        if (stack.is(Items.LAPIS_LAZULI)) return 3;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 1;
        return 0.5; // sticks, planks, logs, stone, feathers...
    }

    private static boolean extraNamespace(Item item) {
        String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        return Config.BLACKSMITH_NAMESPACES.get().contains(ns);
    }

    /** Tools, weapons and armor: damageable items, or anything from the configured armour mods. */
    public static boolean isGear(ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamageableItem() || extraNamespace(stack.getItem()));
    }

    /** XP for making one item at the Armory Table: the value of the materials it costs. */
    public static double armory(ArmoryEntry entry) {
        double xp = 0;
        for (ArmoryEntry.Cost c : entry.cost()) xp += material(c.display()) * c.count();
        return Math.max(5, xp);
    }

    /** XP for crafting gear on a crafting table: the value of the ingredients in the grid (0 if not gear). */
    public static double crafted(ItemStack result, Container grid) {
        if (!isGear(result)) return 0;
        double xp = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) xp += material(grid.getItem(i));
        // wooden and stone gear (only cheap materials) is not smithing
        return xp < 5 ? 1 : xp;
    }

    /** XP per ingot taken out of a furnace (steel furnace, bloomery, bronze furnace or any other). */
    public static double smelted(ItemStack stack) {
        if (stack.is(itemTag("c:ingots/steel"))) return 6;
        if (stack.is(itemTag("c:ingots/iron"))) return 3;
        if (stack.is(itemTag("c:ingots/bronze"))) return 2;
        if (stack.is(itemTag("c:ingots"))) return 1;
        return 0;
    }

    // ------------------------------------------------------------------ miner

    /**
     * Ores generated in this world (see ores.tiers in the config) by name of their "c:ores/<name>" tag,
     * by tier: 1 coal, copper, tin - 2 quartz, zinc, lapis - 3 iron, gold, redstone - 4 sulfur and gems.
     * The rest only exists in land generated before those ores were switched off.
     */
    private static final Map<String, Double> ORES = new LinkedHashMap<>();
    static {
        ORES.put("netherite_scrap", 80.0);
        ORES.put("emerald", 55.0);
        ORES.put("diamond", 50.0);
        ORES.put("ruby", 40.0);
        ORES.put("sapphire", 40.0);
        ORES.put("iridium", 40.0);
        ORES.put("peridot", 35.0);
        ORES.put("platinum", 30.0);
        ORES.put("uranium", 25.0);
        ORES.put("sulfur", 18.0);
        ORES.put("gold", 16.0);
        ORES.put("silver", 16.0);
        ORES.put("osmium", 16.0);
        ORES.put("iron", 14.0);
        ORES.put("lead", 14.0);
        ORES.put("nickel", 14.0);
        ORES.put("fluorite", 14.0);
        ORES.put("cinnabar", 14.0);
        ORES.put("lapis", 12.0);
        ORES.put("aluminum", 12.0);
        ORES.put("redstone", 11.0);
        ORES.put("quartz", 9.0);
        ORES.put("zinc", 9.0);
        ORES.put("coal", 6.0);
        ORES.put("tin", 6.0);
        ORES.put("salt", 6.0);
        ORES.put("copper", 5.0);
    }

    /** Natural stone of this world (vanilla and Create's striated stone layers) by block id. */
    private static final Map<String, Double> STONES = new HashMap<>();
    static {
        for (String id : new String[]{"stone", "granite", "diorite", "andesite", "dripstone_block", "blackstone", "end_stone"}) {
            STONES.put("minecraft:" + id, 1.0);
        }
        STONES.put("minecraft:deepslate", 1.4);
        STONES.put("minecraft:tuff", 1.2);
        STONES.put("minecraft:calcite", 1.2);
        STONES.put("minecraft:smooth_basalt", 1.2);
        STONES.put("minecraft:amethyst_block", 2.0);
        STONES.put("minecraft:basalt", 0.8);
        STONES.put("minecraft:magma_block", 0.8);
        STONES.put("minecraft:sandstone", 0.8);
        STONES.put("minecraft:red_sandstone", 0.8);
        STONES.put("minecraft:netherrack", 0.3);
        STONES.put("minecraft:pointed_dripstone", 0.3);
        STONES.put("minecraft:ice", 0.3);
        STONES.put("minecraft:packed_ice", 0.5);
        STONES.put("minecraft:blue_ice", 0.8);
        for (String id : new String[]{"asurine", "crimsite", "limestone", "ochrum", "veridium"}) {
            STONES.put("create:" + id, 1.5);
        }
        STONES.put("create:scoria", 1.0);
        STONES.put("create:scorchia", 1.0);
    }

    /** block -> XP (cache; cleared when tags reload) */
    private static final Map<Block, Double> MINE_CACHE = new HashMap<>();

    public static void clearCache() {
        synchronized (MINE_CACHE) {
            MINE_CACHE.clear();
        }
    }

    private static double mineValue(BlockState state) {
        Block block = state.getBlock();
        synchronized (MINE_CACHE) {
            Double cached = MINE_CACHE.get(block);
            if (cached != null) return cached;
            double xp = computeMineValue(state);
            MINE_CACHE.put(block, xp);
            return xp;
        }
    }

    /** Positive = stone XP (any pickaxe), negative = ore XP (needs the right pickaxe). */
    private static double computeMineValue(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean deep = id.contains("deepslate");
        if (state.is(Blocks.NETHER_GOLD_ORE)) return -3;
        for (Map.Entry<String, Double> e : ORES.entrySet()) {
            double xp = e.getValue();
            if (state.is(blockTag("c:ores/" + e.getKey()))) return -(deep ? xp * 1.25 : xp);
            // raw ore blocks of big ore veins hold several ores' worth
            if (state.is(blockTag("c:storage_blocks/raw_" + e.getKey()))) return -(xp * 2);
        }
        if (state.is(Tags.Blocks.ORES)) return -(deep ? 12.5 : 10); // an ore of another mod
        Double stone = STONES.get(id);
        if (stone != null) return stone;
        if (state.is(BlockTags.TERRACOTTA)) return 0.8;
        return 0; // player-made blocks, furnaces, rails...
    }

    /** XP for mining one block with a pickaxe: natural stone, or an ore with a pickaxe good enough to drop it. */
    public static double miner(BlockState state, BlockGetter level, BlockPos pos, boolean correctTool) {
        if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 0;
        double v = mineValue(state);
        if (v < 0) return correctTool ? -v : 0;
        return v;
    }

    // ------------------------------------------------------------------ farmer

    /** XP for breaking a block as a harvest (0 if the block is not a ripe crop). */
    public static double farmer(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state) ? 10 : 0; // wheat, carrots, potatoes, beetroot, flax
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3 ? 10 : 0;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2 ? 8 : 0;
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) return 8;
        if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO)) {
            // only the grown part, not the planted base
            return level.getBlockState(pos.below()).is(block) ? 2 : 0;
        }
        return 0;
    }

    public static final double SWEET_BERRY_HARVEST = 6;
    public static final double BREEDING = 20;

    /** XP for picking a bush by hand (sweet berries, medicinal herbs), 0 if it is not ready. */
    public static double picked(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DeadMedicinalHerbBlock) return state.getValue(MedicinalHerbBushBlock.AGE) > 1 ? 15 : 0;
        if (block instanceof MedicinalHerbBushBlock) return state.getValue(MedicinalHerbBushBlock.AGE) > 1 ? 12 : 0;
        if (block instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            return state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) > 1 ? SWEET_BERRY_HARVEST : 0;
        }
        return 0;
    }

    // ------------------------------------------------------------------ hunter

    public static final double WOLF = 100;
    public static final double WOLF_LEADER = 200;
    /** A lone bear: 60 health and a heavy blow. */
    public static final double BEAR = 300;

    /**
     * XP for killing a creature. Hostile mobs no longer spawn on their own, so wolf packs (6-8 wolves, every pack
     * fights together) are the hunter's main prey; wild animals give a little; pets and babies give nothing.
     */
    public static double hunter(LivingEntity victim) {
        if (victim.isBaby()) return 0;
        if (victim instanceof OwnableEntity owned && owned.getOwnerUUID() != null) return 0; // someone's pet or horse
        if (victim instanceof Wolf wolf) return WolfPacks.isLeader(wolf) ? WOLF_LEADER : WOLF;
        if (victim instanceof com.valoris.mobs.Bear) return BEAR;
        if (victim instanceof EnderDragon) return 1500;
        if (victim instanceof WitherBoss) return 1200;
        if (victim instanceof Warden) return 800;
        if (victim instanceof ElderGuardian) return 400;
        float hp = victim.getMaxHealth();
        if (victim instanceof Enemy) return clamp(hp * 0.8, 3, 120);          // spawner and structure mobs
        if (victim instanceof Animal || victim instanceof WaterAnimal || victim instanceof AmbientCreature) {
            return clamp(hp, 3, 30);                                           // cow and pig 10, chicken 4
        }
        return 0;
    }
}
