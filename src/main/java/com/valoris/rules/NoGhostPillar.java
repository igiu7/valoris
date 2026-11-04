package com.valoris.rules;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A refused placement (FTB Chunks claim protection, siege rules, ...) still shows on the client until the server's
 * correction arrives, long enough to land on the ghost block and jump again: jumping and placing under yourself
 * climbed protected land. When a refused block was under the player, the player is put back down to its height.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class NoGhostPillar {
    private NoGhostPillar() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        BlockPos pos = event.getPos();
        AABB box = player.getBoundingBox();
        double top = pos.getY() + 1;
        // the block's top between a bit below the feet (while jumping) and the feet, overlapping the player's footprint
        boolean under = top <= box.minY + 0.1 && top >= box.minY - 1.5
                && box.maxX > pos.getX() && box.minX < pos.getX() + 1
                && box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1;
        if (!under || player.getY() <= pos.getY()) return;
        player.connection.teleport(player.getX(), pos.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
    }
}
