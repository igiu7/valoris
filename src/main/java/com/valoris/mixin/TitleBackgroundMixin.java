package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.client.TitleBackground;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The menus shown outside a world (main menu, options, server list...) show the castle slide show instead of the
 * turning panorama (see client.TitleBackground). The title screen overrides renderPanorama, so it is hooked too.
 */
@Mixin({Screen.class, TitleScreen.class})
public abstract class TitleBackgroundMixin {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void valoris$castles(GuiGraphics g, float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        TitleBackground.render(g, self.width, self.height);
        ci.cancel();
    }
}
