package com.valoris.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.Key;

/** The FTB Chunks large map keeps only the claim manager button (no waypoints, settings, info, dimensions). */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.MapScreenButtonPanel", remap = false)
public abstract class MapScreenButtonPanelMixin {
    @Shadow @Final private Button claimChunksButton;

    @Inject(method = "addWidgets", at = @At("HEAD"), cancellable = true)
    private void valoris$onlyClaimButton(CallbackInfo ci) {
        ((Panel) (Object) this).add(claimChunksButton);
        ci.cancel();
    }

    /** Only the C hotkey (claim manager) stays; S / Ctrl+S (settings) and the waypoint key do nothing. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void valoris$onlyClaimKey(Key key, CallbackInfoReturnable<Boolean> cir) {
        if (!key.is(67)) cir.setReturnValue(false);
    }
}
