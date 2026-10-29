package com.valoris.mixin;

import java.util.Collection;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import com.valoris.events.TeamGuard;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** City teams change only through Valoris or the console (see TeamGuard). */
@Mixin(value = PartyTeam.class, remap = false)
public abstract class PartyTeamMixin {
    private UUID valoris$id() {
        return ((PartyTeam) (Object) this).getId();
    }

    private static MinecraftServer valoris$server() {
        return FTBTeamsAPI.api().getManager().getServer();
    }

    private static void valoris$tell(MinecraftServer server, UUID player) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
        if (p != null) {
            p.sendSystemMessage(Component.literal("City membership is managed in the Valoris menu (/valoris).")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Inject(method = "leave", at = @At("HEAD"), cancellable = true)
    private void valoris$guardLeave(UUID id, CallbackInfoReturnable<Integer> cir) {
        MinecraftServer server = valoris$server();
        if (!TeamGuard.permits(server, valoris$id(), null)) {
            valoris$tell(server, id);
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "kick", at = @At("HEAD"), cancellable = true)
    private void valoris$guardKick(CommandSourceStack from, Collection<GameProfile> players, CallbackInfoReturnable<Integer> cir) {
        if (!TeamGuard.permits(valoris$server(), valoris$id(), from)) {
            from.sendFailure(Component.literal("City membership is managed in the Valoris menu (/valoris)."));
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "forceDisband", at = @At("HEAD"), cancellable = true)
    private void valoris$guardDisband(CommandSourceStack from, CallbackInfoReturnable<Integer> cir) {
        if (!TeamGuard.permits(valoris$server(), valoris$id(), from)) {
            from.sendFailure(Component.literal("A city is disbanded in the Valoris menu (/valoris)."));
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "kickPlayerForcibly", at = @At("HEAD"), cancellable = true)
    private void valoris$guardForcedKick(ServerPlayer player, CallbackInfo ci) {
        MinecraftServer server = valoris$server();
        if (!TeamGuard.permits(server, valoris$id(), null)) {
            valoris$tell(server, player.getUUID());
            ci.cancel();
        }
    }
}
