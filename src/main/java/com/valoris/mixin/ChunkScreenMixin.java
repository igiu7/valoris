package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.input.Key;

/** The claim manager keeps only the large map button on its left side (no client / server settings). */
@Mixin(value = ChunkScreen.class, remap = false)
public abstract class ChunkScreenMixin {
    @Shadow private Button settingsButton;
    @Shadow private Button serverSettingsButton;

    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void valoris$removeSettings(CallbackInfo ci) {
        ChunkScreen self = (ChunkScreen) (Object) this;
        self.getWidgets().remove(settingsButton);
        self.getWidgets().remove(serverSettingsButton);
    }

    /** S / Ctrl+S would open the settings. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void valoris$noSettingsKey(Key key, CallbackInfoReturnable<Boolean> cir) {
        if (key.is(83)) cir.setReturnValue(true);
    }
}
