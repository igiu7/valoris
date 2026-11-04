package com.valoris.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import com.valoris.auth.Auth;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/**
 * Offline mode: a new connection under the name of a player who is online and logged in (auth.Auth) is refused,
 * instead of the game kicking the player already there.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class AuthLoginMixin {
    @Shadow @Final MinecraftServer server;

    @Shadow
    public abstract void disconnect(Component reason);

    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void valoris$noTakeover(GameProfile profile, CallbackInfo ci) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(profile.getName()); // ignores case
        if (online == null) online = server.getPlayerList().getPlayer(profile.getId());
        if (online != null && Auth.isLoggedIn(online)) {
            disconnect(Component.literal("A player with this name is already on the server."));
            ci.cancel();
        }
    }
}
