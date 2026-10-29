package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.Config;
import com.valoris.tech.WeaponBalance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/**
 * Epic Knights lances: the bonus of a mounted charge grows with the era of the lance. Epic Knights adds
 * 3 x mass x speed, capped at 70% of the lance's own (Epic Knights) damage, so it never followed the era damage of
 * tech.WeaponBalance. Here the charge (3 x mass x speed, full at lances.fullChargeImpulse) gives up to
 * lances.chargeBonus times the lance's era damage: the same charge hits proportionally harder in a later era.
 */
@Mixin(targets = "com.magistuarmory.item.LanceItem")
public abstract class LanceChargeMixin {
    @Shadow(remap = false)
    public abstract float getTotalMass(Entity entity);

    @Inject(method = "calcBonusDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private void valoris$eraCharge(Entity entity, float speed, CallbackInfoReturnable<Float> cir) {
        double era = WeaponBalance.damageFor((Item) (Object) this);
        if (Double.isNaN(era) || era <= 0) return; // not balanced by era: Epic Knights' own bonus
        double impulse = 3.0 * getTotalMass(entity) * speed;
        double charge = Math.max(0.0, Math.min(1.0, impulse / Config.LANCE_FULL_CHARGE.get()));
        double bonus = charge * era * Config.LANCE_CHARGE_BONUS.get();
        cir.setReturnValue(Double.isFinite(bonus) ? (float) bonus : 0.0F);
    }
}
