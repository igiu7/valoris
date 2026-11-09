package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * An axe does not knock a shield out of the hands at the first blow: it takes 3 blocked axe hits (4 against a
 * pavise). The count starts again once the shield is knocked out, or after 10 seconds without an axe hit.
 */
@Mixin(Player.class)
public abstract class ShieldDisableMixin {
    @Unique private int valoris$axeHits;
    @Unique private long valoris$lastAxeHit;

    @Redirect(method = "blockUsingShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;canDisableShield()Z"))
    private boolean valoris$severalHits(LivingEntity attacker) {
        if (!attacker.canDisableShield()) return false;
        Player self = (Player) (Object) this;
        long now = self.level().getGameTime();
        if (now - valoris$lastAxeHit > 200) valoris$axeHits = 0;
        valoris$lastAxeHit = now;
        boolean pavise = BuiltInRegistries.ITEM.getKey(self.getUseItem().getItem()).getPath().contains("pavise");
        if (++valoris$axeHits < (pavise ? 4 : 3)) return false;
        valoris$axeHits = 0;
        return true;
    }
}
