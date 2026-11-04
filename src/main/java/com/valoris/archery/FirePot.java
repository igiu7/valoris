package com.valoris.archery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.registry.ModBlocks;
import com.valoris.registry.ModEntities;
import com.valoris.war.SiegeEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown fire pot (FirePotItem): flies a short arc and bursts on the first block or creature it touches. The burst
 * breaks no blocks; it sets the ground around on fire (RADIUS blocks) and burns and singes whoever stands in it.
 * Fire is not set where explosions may not break blocks (city land outside an active siege, banners:
 * SiegeEvents.spared).
 */
public class FirePot extends ThrowableItemProjectile {
    private static final double RADIUS = 2.75;
    private static final float FIRE_CHANCE = 0.65F;
    private static final float DAMAGE = 3.0F;
    private static final float BURN_SECONDS = 6.0F;

    public FirePot(EntityType<? extends FirePot> type, Level level) {
        super(type, level);
    }

    public FirePot(Level level, LivingEntity thrower) {
        super(ModEntities.FIRE_POT.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModBlocks.FIRE_POT.get();
    }

    /** Heavy: falls much faster than a snowball. */
    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && tickCount % 2 == 0) { // the burning rag
            level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.25, getZ(), 0, 0.01, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level && !isRemoved()) {
            burst(level, result.getLocation());
            discard();
        }
    }

    private void burst(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 60, RADIUS / 2, 0.4, RADIUS / 2, 0.05);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 8, 0.6, 0.3, 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 15, 1.0, 0.4, 1.0, 0.02);

        var source = damageSources().explosion(this, getOwner());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS))) {
            if (e.position().distanceToSqr(at) > RADIUS * RADIUS) continue;
            e.hurt(source, DAMAGE);
            e.igniteForSeconds(BURN_SECONDS);
        }

        ServerPlayer thrower = getOwner() instanceof ServerPlayer p ? p : null;
        ValorisData data = ValorisData.get(level.getServer());
        Map<Long, Optional<City>> cities = new HashMap<>();
        BlockPos center = BlockPos.containing(at);
        int r = (int) Math.ceil(RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
            double dx = p.getX() + 0.5 - at.x, dz = p.getZ() + 0.5 - at.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS || random.nextFloat() > FIRE_CHANCE) continue;
            if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) continue;
            if (!BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) continue;
            BlockPos fire = p.immutable();
            if (SiegeEvents.spared(data, level, fire, thrower, cities)) continue;
            level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
        }
    }
}
