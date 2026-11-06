package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Siege Machines' battering ram. Its health is Config.RAM_DURABILITY (also for rams saved with more). Its hit - an
 * explosion at the spot it strikes - breaks a hole of Config.RAM_HOLE x RAM_HOLE blocks, one block deep: the first
 * layer of the wall in front of the ram (across the direction it strikes), whatever the blocks are made of, but no
 * unbreakable ones (blast resistance 1200 and more). Elsewhere the blast does nothing to blocks, and people hit by it
 * take what the original hit did.
 */
@Mixin(targets = "ru.magistu.siegemachines.entity.machine.BatteringRam")
public abstract class BatteringRamMixin {
    /** the layer of the current hit: its middle block, and the direction the ram strikes in */
    @Unique private BlockPos valoris$hole;
    @Unique private Direction valoris$towards;

    @Inject(method = "tick", at = @At("HEAD"))
    private void valoris$durability(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide) return;
        AttributeInstance max = self.getAttribute(Attributes.MAX_HEALTH);
        double wanted = Config.RAM_DURABILITY.get();
        if (max == null || max.getBaseValue() == wanted) return;
        max.setBaseValue(wanted);
        if (self.getHealth() > self.getMaxHealth()) self.setHealth(self.getMaxHealth());
    }

    @Unique
    private static int valoris$half() {
        return Config.RAM_HOLE.get() / 2;
    }

    /** The hit goes to the first layer in front of the ram (up to 2 blocks past the spot) that has something to break. */
    @ModifyVariable(method = "ramHit", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockPos valoris$findWall(BlockPos pos) {
        Mob self = (Mob) (Object) this;
        Direction d = Direction.getNearest(pos.getX() + 0.5 - self.getX(), 0, pos.getZ() + 0.5 - self.getZ());
        valoris$towards = d;
        valoris$hole = pos;
        int h = valoris$half();
        for (int k = 0; k <= 2; k++) {
            BlockPos layer = pos.relative(d, k);
            for (int a = -h; a <= h; a++) {
                for (int y = -h; y <= h; y++) {
                    BlockState s = self.level().getBlockState(layer.relative(d.getClockWise(), a).above(y));
                    if (!s.isAir() && !s.canBeReplaced()) {
                        valoris$hole = layer;
                        return layer;
                    }
                }
            }
        }
        return pos;
    }

    /** The strength of the blast (the only 2.0F there): enough to reach the whole hole. */
    @ModifyConstant(method = "ramHit", constant = @Constant(floatValue = 2.0F), remap = false)
    private float valoris$power(float power) {
        return Math.max(2, Config.RAM_HOLE.get());
    }

    @Inject(method = "getExplosionDamageMultiplier", at = @At("RETURN"), cancellable = true, remap = false)
    private void valoris$entityDamage(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValueD() * 2.0 / Math.max(2, Config.RAM_HOLE.get()));
    }

    /** Nothing but unbreakable blocks holds the blast back: the hole does not depend on what the wall is made of. */
    @Inject(method = "getBlockResistance", at = @At("RETURN"), cancellable = true, remap = false)
    private void valoris$resistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid,
                                    float resistance, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() < 1200) cir.setReturnValue(0F);
    }

    @Inject(method = "shouldBlockDestroy", at = @At("RETURN"), cancellable = true, remap = false)
    private void valoris$onlyTheHole(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        BlockPos hole = valoris$hole;
        Direction d = valoris$towards;
        boolean inside = false;
        if (hole != null && d != null && state.getDestroySpeed(level, pos) >= 0) {
            int h = valoris$half();
            int depth = d.getAxis().choose(pos.getX() - hole.getX(), 0, pos.getZ() - hole.getZ());
            int across = d.getClockWise().getAxis().choose(pos.getX() - hole.getX(), 0, pos.getZ() - hole.getZ());
            inside = depth == 0 && Math.abs(across) <= h && Math.abs(pos.getY() - hole.getY()) <= h;
        }
        if (!inside) cir.setReturnValue(false);
    }
}
