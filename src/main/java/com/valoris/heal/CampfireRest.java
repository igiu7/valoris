package com.valoris.heal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Resting by a fire: a player with a full hunger bar near a lit campfire slowly regains health (natural regeneration
 * is off), also while moving around it. Leaving the fire's radius, or the hunger bar dropping, interrupts the rest.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class CampfireRest {
    private CampfireRest() {}

    private static final class Rest {
        int nearSeconds;
    }

    private static final Map<UUID, Rest> RESTING = new HashMap<>();
    private static final int SETTLE_SECONDS = 3;

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RESTING.remove(event.getEntity().getUUID());
    }

    private static boolean nearLitCampfire(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(s)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.CAMPFIRE_REST_ENABLED.get()) return;
        if (player.tickCount % 20 != 0) return;
        Rest rest = RESTING.computeIfAbsent(player.getUUID(), k -> new Rest());

        boolean ok = player.isAlive() && !player.isSpectator() && player.getFoodData().getFoodLevel() >= 20
                && player.getHealth() < player.getMaxHealth()
                && nearLitCampfire(player.serverLevel(), player.blockPosition(), Config.CAMPFIRE_REST_RADIUS.get());
        if (!ok) {
            rest.nearSeconds = 0;
            return;
        }
        rest.nearSeconds++;
        int every = Config.CAMPFIRE_REST_SECONDS.get();
        if (rest.nearSeconds < SETTLE_SECONDS || (rest.nearSeconds - SETTLE_SECONDS) % every != 0) return;
        player.heal(1.0F);
        player.causeFoodExhaustion(Config.CAMPFIRE_REST_EXHAUSTION.get().floatValue());
        player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.9, player.getZ(),
                1, 0.25, 0.1, 0.25, 0.0);
    }
}
