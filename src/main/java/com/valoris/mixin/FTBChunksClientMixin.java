package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.architectury.event.EventResult;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.PointerIconMode;
import dev.ftb.mods.ftbchunks.client.gui.PointerIcon;
import dev.ftb.mods.ftbchunks.client.mapicon.EntityMapIcon;
import dev.ftb.mods.ftblibrary.icon.FaceIcon;
import net.minecraft.client.Minecraft;

/**
 * The "add waypoint" and "waypoint manager" keys do nothing (waypoints are disabled), and the map and minimap show
 * no other players and no mobs - only your own marker on the large map. (FTB's client settings for entities and
 * player heads cannot bring them back.)
 */
@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class FTBChunksClientMixin {
    @Inject(method = "mapIcons", at = @At("HEAD"), cancellable = true)
    private void valoris$noPlayersOrMobs(MapIconEvent event, CallbackInfo ci) {
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.getMapType().isMinimap()) return;
        PointerIconMode mode = FTBChunksClientConfig.POINTER_ICON_MODE.get();
        if (mode.showFace()) event.add(new EntityMapIcon(mc.player, FaceIcon.getFace(mc.player.getGameProfile())));
        if (mode.showPointer()) event.add(new PointerIcon());
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/Minecraft;IIII)Ldev/architectury/event/EventResult;",
            at = @At("HEAD"), cancellable = true)
    private void valoris$noWaypointKeys(Minecraft client, int keyCode, int scanCode, int action, int modifiers,
                                        CallbackInfoReturnable<EventResult> cir) {
        FTBChunksClient self = (FTBChunksClient) (Object) this;
        if (FTBChunksClient.doesKeybindMatch(self.addWaypointKey, keyCode, scanCode, modifiers)
                || FTBChunksClient.doesKeybindMatch(self.waypointManagerKey, keyCode, scanCode, modifiers)) {
            cir.setReturnValue(EventResult.pass());
        }
    }
}
