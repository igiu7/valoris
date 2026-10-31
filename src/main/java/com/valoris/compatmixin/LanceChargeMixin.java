package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.Config;
import com.valoris.tech.WeaponBalance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/**
 * Epic Knights lances: the bonus of a mounted charge grows with the era of the lance. Epic Knights adds
 * 3 x mass x speed, capped at 70% of the lance's own (Epic Knights) damage, so it never followed the era damage of
 * tech.WeaponBalance. Here the charge (3 x mass x speed, full at lances.fullChargeImpulse) gives up to
 * lances.chargeBonus (per era) times the lance's era damage: the same charge hits proportionally harder in a later era.
 *
 * A charge replaces the hit with one hit of (hit + bonus). The hit itself is at least the lance's era damage, so a
 * charge does not lose damage to an attack bar that was not full (the collision fires on its own while riding).
 * Each era has its own multiplier, so that a full charge deals 8 damage (4 hearts) through a full set of armor of
 * the lance's own era: steel 8 x 1.95 = 15.6 against 20 armor / 6 toughness, iron 7 x 1.31 = 9.17 against 16 armor,
 * bronze 6 x 1.5 = 9.0 against 14 armor.
 */
@Mixin(targets = "com.magistuarmory.item.LanceItem")
public abstract class LanceChargeMixin {
    /** The charge bonus of the hit being dealt (set in calcBonusDamage, read when the hit is dealt; server thread). */
    @Unique
    private static float valoris$lastBonus;

    @Shadow(remap = false)
    public abstract float getTotalMass(Entity entity);

    @Inject(method = "calcBonusDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private void valoris$eraCharge(Entity entity, float speed, CallbackInfoReturnable<Float> cir) {
        valoris$lastBonus = -1;
        double era = WeaponBalance.damageFor((Item) (Object) this);
        if (Double.isNaN(era) || era <= 0) return; // not balanced by era: Epic Knights' own bonus
        double impulse = 3.0 * getTotalMass(entity) * speed;
        double charge = Math.max(0.0, Math.min(1.0, impulse / Config.LANCE_FULL_CHARGE.get()));
        double bonus = charge * era * valoris$eraBonus((Item) (Object) this);
        float result = Double.isFinite(bonus) ? (float) bonus : 0.0F;
        valoris$lastBonus = result;
        cir.setReturnValue(result);
    }

    /**
     * The charge multiplier of the lance's era, by its material ("iron_chivalrylance" -> iron): up to the base damage
     * of bronze the bronze age, up to iron the iron age, above it the steel age.
     */
    @Unique
    private static double valoris$eraBonus(Item item) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        int split = path.indexOf('_');
        var mats = WeaponBalance.materials();
        Double mat = split > 0 ? mats.get(path.substring(0, split)) : null;
        if (mat == null || path.startsWith("wood_")) mat = 0.0; // wood_ is not in the table: bronze age
        if (mat <= mats.getOrDefault("bronze", 5.0)) return Config.LANCE_CHARGE_BONUS_BRONZE.get();
        if (mat <= mats.getOrDefault("iron", 6.0)) return Config.LANCE_CHARGE_BONUS_IRON.get();
        return Config.LANCE_CHARGE_BONUS.get();
    }

    /** The charged hit: at least the era damage, plus the charge bonus. */
    @ModifyArg(method = "onHurtEntity", remap = false, index = 1, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private float valoris$chargedHit(float amount) {
        float bonus = valoris$lastBonus;
        valoris$lastBonus = -1;
        double era = WeaponBalance.damageFor((Item) (Object) this);
        if (bonus < 0 || Double.isNaN(era) || era <= 0) return amount;
        float hit = amount - bonus; // what the attack itself dealt
        return Math.max(hit, (float) era) + bonus;
    }
}
