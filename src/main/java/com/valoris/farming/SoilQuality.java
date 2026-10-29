package com.valoris.farming;

import java.util.List;

import com.valoris.Config;
import com.valoris.plant.DeadMedicinalHerbBlock;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Soil quality: every biome belongs to one of four tiers, and plants (crops, berry bushes, flax, medicinal herbs,
 * melons, pumpkins, sugar cane, cocoa...) grow only at a fraction of the vanilla speed there:
 * tier 1 (plains) 40%, tier 2 (forests) 30%, tier 3 (jungle, taiga, savanna, swamp, ...) 20%,
 * tier 4 (desert, badlands, snow and ice, ...) 10%. The dead medicinal herb grows only in deserts, badlands
 * and savannas (much slower in savannas). Bone meal is not affected.
 * Right-clicking with a hoe shows the soil tier of the biome.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class SoilQuality {
    private SoilQuality() {}

    private static TagKey<Biome> tag(String id) {
        return TagKey.create(Registries.BIOME, ResourceLocation.parse(id));
    }

    private static boolean is(Holder<Biome> b, String... ids) {
        for (String id : ids) {
            if (id.startsWith("#")) {
                if (b.is(tag(id.substring(1)))) return true;
            } else if (b.unwrapKey().map(k -> k.location().toString().equals(id)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDesertOrBadlands(Holder<Biome> b) {
        return is(b, "#minecraft:is_badlands", "#c:is_desert", "#c:is_badlands", "minecraft:desert", "minecraft:badlands",
                "minecraft:eroded_badlands", "minecraft:wooded_badlands");
    }

    private static boolean isSavanna(Holder<Biome> b) {
        return is(b, "#minecraft:is_savanna", "#c:is_savanna", "minecraft:savanna", "minecraft:savanna_plateau",
                "minecraft:windswept_savanna");
    }

    /** 1 (most fertile) .. 4 (barren). */
    public static int tier(Holder<Biome> b) {
        String id = b.unwrapKey().map(k -> k.location().toString()).orElse("");
        for (String entry : Config.SOIL_BIOME_OVERRIDES.get()) {
            int eq = entry.lastIndexOf('=');
            if (eq > 0 && entry.substring(0, eq).trim().equals(id)) {
                try {
                    return Math.max(1, Math.min(4, Integer.parseInt(entry.substring(eq + 1).trim())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // the Valoris map's own beaches, rivers and peaks carry their tier in the name: valoris2:beach_t2 = tier 2
        if (id.startsWith(valoris2.MODID + ":") && id.length() > 3 && id.charAt(id.length() - 3) == '_'
                && id.charAt(id.length() - 2) == 't') {
            char d = id.charAt(id.length() - 1);
            if (d >= '1' && d <= '4') return d - '0';
        }
        // tier 4: dry, frozen and otherworldly land
        if (isDesertOrBadlands(b)) return 4;
        if (is(b, "#c:is_snowy", "#c:is_icy", "#minecraft:is_nether", "#minecraft:is_end", "#c:is_nether", "#c:is_end",
                "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:snowy_slopes",
                "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:grove",
                "minecraft:frozen_river", "minecraft:snowy_beach", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:deep_dark")) return 4;
        // tier 1: plains
        if (is(b, "#c:is_plains", "minecraft:plains", "minecraft:sunflower_plains")) return 1;
        // tier 3: jungle, taiga, savanna, swamp, hills, coasts and seas
        if (isSavanna(b) || is(b, "#minecraft:is_jungle", "#c:is_jungle", "minecraft:jungle", "minecraft:sparse_jungle",
                "minecraft:bamboo_jungle", "#minecraft:is_taiga", "#c:is_taiga", "#c:is_swamp", "#minecraft:is_ocean",
                "#minecraft:is_beach", "#minecraft:is_hill", "minecraft:swamp",
                "minecraft:mangrove_swamp", "minecraft:taiga", "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                "minecraft:windswept_forest", "minecraft:stony_shore", "minecraft:dripstone_caves")) return 3;
        // tier 2: forests, meadows, rivers, mushroom fields, lush caves
        if (is(b, "#minecraft:is_forest", "#c:is_forest", "#minecraft:is_river", "#c:is_mushroom", "minecraft:forest",
                "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:flower_forest",
                "minecraft:dark_forest", "minecraft:cherry_grove", "minecraft:meadow", "minecraft:river",
                "minecraft:mushroom_fields", "minecraft:lush_caves")) return 2;
        return 3;
    }

    /**
     * Soil tier at a place: the biome's tier; on the Valoris map world it is kept within the tiers of the land mass
     * the place belongs to (so its beaches, rivers and sea floor cannot bring another tier, and its ores, into a
     * region). Other worlds: just the biome's tier.
     */
    public static int tier(LevelAccessor level, int x, int z, Holder<Biome> biome) {
        int t = tier(biome);
        if (level instanceof net.minecraft.server.level.ServerLevel sl
                && sl.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && sl.getChunkSource().getGenerator() instanceof com.valoris.worldgen.MapChunkGenerator) {
            com.valoris.worldgen.MapDesign.Profile region = com.valoris.worldgen.MapDesign.regionAt(x, z);
            t = Math.max(region.minTier, Math.min(region.maxTier, t));
        }
        return t;
    }

    public static double speed(int tier) {
        return switch (tier) {
            case 1 -> Config.SOIL_TIER1_SPEED.get();
            case 2 -> Config.SOIL_TIER2_SPEED.get();
            case 3 -> Config.SOIL_TIER3_SPEED.get();
            default -> Config.SOIL_TIER4_SPEED.get();
        };
    }

    /** Fraction of the vanilla growth speed this plant has here. */
    public static double speed(LevelAccessor level, BlockPos pos, BlockState state) {
        Holder<Biome> biome = level.getBiome(pos);
        if (state.getBlock() instanceof DeadMedicinalHerbBlock) {
            if (isDesertOrBadlands(biome)) return Config.DEAD_HERB_DRY_SPEED.get();
            if (isSavanna(biome)) return Config.DEAD_HERB_SAVANNA_SPEED.get();
            return 0.0;
        }
        return speed(tier(level, pos.getX(), pos.getZ(), biome));
    }

    /** Field crops: anything planted on farmland (wheat, carrots, potatoes, beetroot, flax, melon and pumpkin stems...). */
    public static boolean onFarmland(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.FarmBlock;
    }

    /** Inside land claimed with FTB Chunks (a city's land). */
    public static boolean claimed(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof net.minecraft.world.level.Level l)) return true;
        try {
            return dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager()
                    .getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(l, pos)) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean exempt(BlockState state) {
        List<? extends String> list = Config.SOIL_EXEMPT_BLOCKS.get();
        return !list.isEmpty() && list.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!Config.SOIL_QUALITY_ENABLED.get() || event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        if (Config.CROPS_NEED_CLAIM.get() && onFarmland(event.getLevel(), event.getPos())
                && !claimed(event.getLevel(), event.getPos())) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
            return;
        }
        if (exempt(state)) return;
        double speed = speed(event.getLevel(), event.getPos(), state);
        if (speed >= 1.0) return;
        if (speed <= 0.0 || event.getLevel().getRandom().nextDouble() >= speed) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    private static final String[] NAMES = {"", "Fertile", "Good", "Poor", "Barren"};
    private static final ChatFormatting[] COLORS = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.GOLD, ChatFormatting.RED};

    /** Right-click with a hoe: show the soil tier. */
    @SubscribeEvent
    public static void onHoe(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.SOIL_QUALITY_ENABLED.get() || event.getLevel().isClientSide()) return;
        if (!(event.getItemStack().getItem() instanceof HoeItem)) return;
        int t = tier(event.getLevel(), event.getPos().getX(), event.getPos().getZ(), event.getLevel().getBiome(event.getPos()));
        int pct = (int) Math.round(speed(t) * 100.0);
        boolean wild = Config.CROPS_NEED_CLAIM.get() && !claimed(event.getLevel(), event.getPos());
        event.getEntity().displayClientMessage(Component.literal("Soil: Tier " + t + " - " + NAMES[t]
                + (wild ? " - unclaimed land: field crops will not grow here"
                        : " (crops grow at " + pct + "% speed, ores up to tier " + t + ")"))
                .withStyle(wild ? ChatFormatting.RED : COLORS[t]), true);
    }
}
