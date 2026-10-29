package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.valoris.moderation.Vanish;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;

/** A vanished staff member leaving the game is announced to the staff only (everybody else saw them "leave" already). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerLeaveMessageMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "removePlayerFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void valoris$quietLeave(PlayerList list, Component message, boolean overlay) {
        if (Vanish.isVanished(player)) Vanish.toStaff(list, message);
        else list.broadcastSystemMessage(message, overlay);
    }
}
