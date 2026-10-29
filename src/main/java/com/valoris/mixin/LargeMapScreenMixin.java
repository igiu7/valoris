package com.valoris.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftblibrary.ui.ContextMenu;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;

/** Right-clicking the large map no longer opens the "Add Waypoint" menu (waypoints are disabled). */
@Mixin(value = LargeMapScreen.class, remap = false)
public abstract class LargeMapScreenMixin {
    @Redirect(method = "mousePressed", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/gui/LargeMapScreen;openContextMenu(Ljava/util/List;)Ldev/ftb/mods/ftblibrary/ui/ContextMenu;"))
    private ContextMenu valoris$noWaypointMenu(LargeMapScreen screen, List<ContextMenuItem> items) {
        return null;
    }
}
