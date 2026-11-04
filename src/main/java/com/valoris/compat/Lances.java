package com.valoris.compat;

import com.valoris.Config;
import com.valoris.tech.WeaponBalance;
import com.valoris.valoris2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Epic Knights lances (LanceItem: the chivalry lances and the addon's lance) by era, and their armor piercing: a hit
 * from horseback takes Config lances.armorPiercing* (bronze, iron, steel age) off what the victim's armor would have
 * saved him. The mounted charge itself: compatmixin.LanceChargeMixin.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Lances {
    public static final int BRONZE = 0, IRON = 1, STEEL = 2;
    private static final String LANCE = "com.magistuarmory.item.LanceItem";

    private Lances() {}

    public static boolean isLance(Item item) {
        for (Class<?> c = item.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(LANCE)) return true;
        return false;
    }

    /**
     * The era of a lance by its material ("iron_chivalrylance" -> iron): up to the base damage of bronze the bronze
     * age, up to iron the iron age, above it the steel age.
     */
    public static int era(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        int split = path.indexOf('_');
        var mats = WeaponBalance.materials();
        Double mat = split > 0 ? mats.get(path.substring(0, split)) : null;
        if (mat == null || path.startsWith("wood_")) mat = 0.0; // wood_ is not in the table: bronze age
        if (mat <= mats.getOrDefault("bronze", 5.0)) return BRONZE;
        if (mat <= mats.getOrDefault("iron", 6.0)) return IRON;
        return STEEL;
    }

    /** Share of the armor's protection a mounted hit with this lance ignores (0..1; 0 for anything else). */
    public static double armorPiercing(ItemStack stack) {
        if (stack.isEmpty() || !isLance(stack.getItem())) return 0;
        double p = switch (era(stack.getItem())) {
            case BRONZE -> Config.LANCE_PIERCING_BRONZE.get();
            case IRON -> Config.LANCE_PIERCING_IRON.get();
            default -> Config.LANCE_PIERCING_STEEL.get();
        };
        return Math.max(0, Math.min(1, p));
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.getVehicle() == null) return;
        var direct = event.getSource().getDirectEntity(); // a melee hit (the charge hit may come without one), not a projectile
        if ((direct != null && direct != attacker) || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        double p = armorPiercing(attacker.getMainHandItem());
        if (p <= 0) return;
        float keep = (float) (1 - p);
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * keep);
    }
}
