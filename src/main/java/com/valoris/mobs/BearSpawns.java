package com.valoris.mobs;

import com.valoris.Config;
import com.valoris.farming.SoilQuality;
import com.valoris.registry.ModEntities;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bears live alone in tier 2 and 3 land (forests, meadows, taiga, savanna, swamps, hills): now and then one
 * spawns 40-72 blocks from a player, never on water, beaches, mushroom islands or a city's land, and never more
 * than one within 128 blocks. Like other creatures they leave when nobody is near. Health, damage and the
 * detection range follow the config whenever a bear is loaded.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class BearSpawns {
    private BearSpawns() {}

    private static final int SPAWN_INTERVAL = 600; // every 30 s per player, between the wolf pack checks
    private static final int SPAWN_OFFSET = 300;

    public static boolean bearLand(Holder<Biome> biome) {
        int tier = SoilQuality.tier(biome);
        return (tier == 2 || tier == 3) && WolfPacks.anyWolfBiome(biome);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        double chance = Config.BEAR_CHANCE.get();
        if (chance <= 0) return;
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % SPAWN_INTERVAL != SPAWN_OFFSET) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || level.random.nextDouble() >= chance) continue;
            if (!level.getEntitiesOfClass(Bear.class, p.getBoundingBox().inflate(128.0)).isEmpty()) continue;
            trySpawn(level, p);
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer near) {
        var rnd = level.random;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = 40.0 + rnd.nextDouble() * 32.0;
            BlockPos pos = WolfPacks.ground(level, Mth.floor(near.getX() + Math.cos(angle) * dist),
                    Mth.floor(near.getZ() + Math.sin(angle) * dist));
            if (pos == null || !bearLand(level.getBiome(pos))) continue;
            if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0, false) != null) continue;
            if (WolfPacks.claimed(level, pos)) continue;
            Bear bear = ModEntities.BEAR.get().create(level);
            if (bear == null) return;
            bear.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(bear)) continue;
            bear.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            applyConfig(bear);
            bear.setHealth(bear.getMaxHealth());
            level.addFreshEntity(bear);
            return;
        }
    }

    private static void base(Bear bear, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = bear.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    static void applyConfig(Bear bear) {
        base(bear, Attributes.MAX_HEALTH, Config.BEAR_HEALTH.get());
        base(bear, Attributes.ATTACK_DAMAGE, Config.BEAR_DAMAGE.get());
        base(bear, Attributes.FOLLOW_RANGE, Config.BEAR_DETECTION.get());
        if (bear.getHealth() > bear.getMaxHealth()) bear.setHealth(bear.getMaxHealth());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Bear bear) applyConfig(bear);
    }
}
