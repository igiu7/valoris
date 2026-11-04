package com.valoris.archery;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Siege machines (Siege Machines) against fire: a burning arrow sets a machine on fire for longer than anything else,
 * and fire hurts a machine Config.FIRE_ARROW_MACHINE_BURN times more (on top of the mod's own fire multiplier), so a
 * few fire arrows do far more than plain arrows. A fire arrow hits players for Config.FIRE_ARROW_PLAYER_DAMAGE
 * (half) of its damage; the burning afterwards is as usual.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class FireArrows {
    private FireArrows() {}

    /** Marks an arrow shot as a fire arrow (FireArrowItem). */
    public static final String TAG = "valoris2_fire_arrow";

    private static final String MACHINE = "ru.magistu.siegemachines.entity.machine.Machine";

    private static boolean machine(Entity entity) {
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(MACHINE)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player
                && event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getPersistentData().getBoolean(TAG)) {
            event.setAmount(event.getAmount() * Config.FIRE_ARROW_PLAYER_DAMAGE.get().floatValue());
            return;
        }
        if (!machine(event.getEntity())) return;
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.isOnFire()) {
            event.getEntity().igniteForSeconds(Config.FIRE_ARROW_MACHINE_SECONDS.get());
        } else if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setAmount(event.getAmount() * Config.FIRE_ARROW_MACHINE_BURN.get().floatValue());
        }
    }
}
