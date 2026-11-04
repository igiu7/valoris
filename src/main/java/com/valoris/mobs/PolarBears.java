package com.valoris.mobs;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Polar bears are the bears of the snow: the same health, blow, armor and poor senses as mobs.Bear (the bear config),
 * hostile to players they notice, the same drops (loot table entities/polar_bear) and hunter XP. Their natural spawns
 * are removed (biome modifier remove_polar_bears); instead they spawn like bears, as often, in snowy and icy land that
 * is not mountains (peaks, slopes, groves), never on water, beaches or a city's land.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class PolarBears {
    private PolarBears() {}

    private static final int SPAWN_INTERVAL = 600; // like bears, every 30 s per player
    private static final int SPAWN_OFFSET = 450;

    public static boolean polarLand(Holder<Biome> biome) {
        if (!biome.is(Tags.Biomes.IS_SNOWY) && !biome.is(Tags.Biomes.IS_ICY)) return false;
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_MOUNTAIN) || biome.is(Biomes.GROVE)) return false;
        return WolfPacks.anyWolfBiome(biome);
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
            if (!level.getEntitiesOfClass(PolarBear.class, p.getBoundingBox().inflate(128.0)).isEmpty()) continue;
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
            if (pos == null || !polarLand(level.getBiome(pos))) continue;
            if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0, false) != null) continue;
            if (WolfPacks.claimed(level, pos)) continue;
            PolarBear bear = EntityType.POLAR_BEAR.create(level);
            if (bear == null) return;
            bear.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(bear)) continue;
            bear.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            bear.setAge(0); // a lone adult, never a cub
            applyConfig(bear);
            bear.setHealth(bear.getMaxHealth());
            level.addFreshEntity(bear);
            return;
        }
    }

    private static void base(PolarBear bear, Holder<Attribute> attr, double value) {
        AttributeInstance inst = bear.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    /** The attributes of mobs.Bear, with the bear config. */
    private static void applyConfig(PolarBear bear) {
        base(bear, Attributes.MAX_HEALTH, Config.BEAR_HEALTH.get());
        base(bear, Attributes.ATTACK_DAMAGE, Config.BEAR_DAMAGE.get());
        base(bear, Attributes.FOLLOW_RANGE, Config.BEAR_DETECTION.get());
        base(bear, Attributes.ATTACK_KNOCKBACK, 1.0);
        base(bear, Attributes.MOVEMENT_SPEED, 0.27);
        base(bear, Attributes.KNOCKBACK_RESISTANCE, 0.6);
        base(bear, Attributes.ARMOR, 4.0);
        base(bear, Attributes.STEP_HEIGHT, 1.0);
        if (bear.getHealth() > bear.getMaxHealth()) bear.setHealth(bear.getMaxHealth());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PolarBear bear)) return;
        applyConfig(bear);
        // like a bear it goes for any player it notices (vanilla ones only defend their cubs); cubs do not
        bear.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(bear, Player.class, 10, true, false, null) {
            @Override
            public boolean canUse() {
                return !bear.isBaby() && super.canUse();
            }
        });
    }
}
