package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Every movement a client sends is checked for standing on a refused block (movement.GhostBlockGuard). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GhostBlockMoveMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void valoris$ghostBlocks(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player.getServer() != null && player.getServer().isSameThread()) {
            com.valoris.movement.GhostBlockGuard.afterMove(player);
        }
    }
}
