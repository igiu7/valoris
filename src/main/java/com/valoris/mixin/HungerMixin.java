package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.valoris.Config;

import net.minecraft.world.entity.player.Player;

/** Players get hungry faster: every food exhaustion is multiplied by Config.HUNGER_MULTIPLIER (server side). */
@Mixin(Player.class)
public abstract class HungerMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float valoris$hungrier(float exhaustion) {
        return exhaustion * Config.HUNGER_MULTIPLIER.get().floatValue();
    }
}
