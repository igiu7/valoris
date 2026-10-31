package com.valoris.compat;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.valoris.net.ReloadInterruptPayload;
import com.valoris.valoris2;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TACZ guns (the muskets): a player hurt by another player while reloading loses the reload - nothing is loaded
 * and it has to be started again. TACZ only interrupts reloads of guns with a script of their own, so the
 * reload state is cleared here directly; the client is told to stop the reload animation (client.GunReloadClient).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class GunReloadInterrupt {
    private GunReloadInterrupt() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!Compat.TACZ || event.getNewDamage() <= 0) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) return;
        Tacz.interrupt(victim);
    }

    /** Loaded only when TACZ is installed. */
    private static final class Tacz {
        static void interrupt(ServerPlayer player) {
            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            ShooterDataHolder data = operator.getDataHolder();
            if (data == null || data.reloadStateType == null || !data.reloadStateType.isReloading()) return;
            operator.cancelReload(); // guns with a script handle it themselves
            data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
            data.reloadTimestamp = -1;
            PacketDistributor.sendToPlayer(player, ReloadInterruptPayload.INSTANCE);
        }
    }
}
