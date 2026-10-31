package com.valoris.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.client.RealmTabList;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

/** The tab list grouped by realm (client.RealmTabList); with a list scoreboard objective the vanilla one stays. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow @Nullable private Component header;
    @Shadow @Nullable private Component footer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void valoris$grouped(GuiGraphics g, int width, Scoreboard scoreboard, @Nullable Objective objective, CallbackInfo ci) {
        if (objective == null && RealmTabList.render(g, width, (PlayerTabOverlay) (Object) this, header, footer)) ci.cancel();
    }
}
