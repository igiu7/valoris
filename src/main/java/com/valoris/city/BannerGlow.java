package com.valoris.city;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.registry.ModBlocks;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The banner of a city under siege (the active phase) glows like an entity hit by a spectral arrow: seen through
 * walls. Done on the server only - a glowing block display of the same banner, a little smaller, sits inside it. The displays are never kept:
 * one loaded from disk is dropped and a fresh one made, so a banner that is gone leaves no glow behind.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class BannerGlow {
    private BannerGlow() {}

    private static final String TAG = "valoris2_banner_glow";
    private static final float SCALE = 0.98F;
    /** city team -> its glow */
    private static final Map<UUID, Display.BlockDisplay> GLOWS = new HashMap<>();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 0) return;
        Set<UUID> seen = new HashSet<>();
        ValorisData data = ValorisData.get(server);
        for (City city : data.all()) {
            if (!city.founded || !city.hasBanner()) continue;
            if (data.siegeOfCity(city.teamId).filter(s -> s.phase == ValorisData.SiegePhase.ACTIVE).isEmpty()) continue;
            ServerLevel level = CityService.levelOf(server, city);
            if (level == null) continue;
            BlockPos pos = BlockPos.of(city.bannerPos);
            Display.BlockDisplay glow = GLOWS.get(city.teamId);
            if (glow != null && (glow.isRemoved() || glow.level() != level || !glow.blockPosition().equals(pos))) {
                glow.discard();
                glow = null;
            }
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.CITY_BANNER.get())) {
                if (glow != null) glow.discard();
                continue;
            }
            seen.add(city.teamId);
            if (glow == null) {
                glow = spawn(level, pos, level.getBlockState(pos));
                if (glow != null) GLOWS.put(city.teamId, glow);
            }
        }
        GLOWS.entrySet().removeIf(e -> {
            if (seen.contains(e.getKey())) return false;
            e.getValue().discard();
            return true;
        });
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private static Display.BlockDisplay spawn(ServerLevel level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        CompoundTag transformation = new CompoundTag();
        float inset = (1 - SCALE) / 2;
        transformation.put("translation", floats(inset, 0.005F, inset));
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("scale", floats(SCALE, SCALE, SCALE));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", transformation);
        tag.putBoolean("Glowing", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0F, 0F);
            return e;
        });
        if (!(entity instanceof Display.BlockDisplay display)) return null;
        return level.addFreshEntity(display) ? display : null;
    }

    /** A glow saved with its chunk is not used again: the tick makes a fresh one if the banner still stands. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Display.BlockDisplay d && d.getTags().contains(TAG)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        GLOWS.clear();
    }
}
