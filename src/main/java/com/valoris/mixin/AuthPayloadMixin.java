package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.auth.Auth;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Mod network messages of a player who has not logged in yet are dropped, except Config auth.allowedPayloadNamespaces. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AuthPayloadMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void valoris$untilLoggedIn(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (!Auth.payloadAllowed(player, packet.payload().type().id())) ci.cancel();
    }
}
