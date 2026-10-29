package com.valoris.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;

/** The claim manager's top bar keeps only its close button (no unclaim-all, admin, info or claim-mode buttons). */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen$CustomTopPanel", remap = false)
public abstract class ChunkScreenTopPanelMixin {
    @Shadow @Final private Button closeButton;

    @Inject(method = "addWidgets", at = @At("HEAD"), cancellable = true)
    private void valoris$onlyClose(CallbackInfo ci) {
        ((Panel) (Object) this).add(closeButton);
        ci.cancel();
    }
}
