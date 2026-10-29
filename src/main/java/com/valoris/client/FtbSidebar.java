package com.valoris.client;

import java.util.List;

import dev.ftb.mods.ftblibrary.api.sidebar.SidebarButtonCreatedEvent;
import dev.ftb.mods.ftblibrary.sidebar.RegisteredSidebarButton;
import dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager;
import dev.ftb.mods.ftblibrary.sidebar.SidebarGuiButton;
import net.minecraft.resources.ResourceLocation;

/**
 * The FTB sidebar in the inventory shows only the FTB Chunks map and claim manager. Every other FTB button
 * (team, team lives, game mode, day/night/rain, config...) is removed - also from the sidebar's edit mode,
 * so it cannot be added back.
 */
public final class FtbSidebar {
    /** Allowed buttons, in sidebar order. */
    private static final List<ResourceLocation> ALLOWED = List.of(
            ResourceLocation.fromNamespaceAndPath("ftbchunks", "chunks"),
            ResourceLocation.fromNamespaceAndPath("ftbchunks", "claim_chunks"));

    private FtbSidebar() {}

    /** Called from the mod constructor, before the first resource reload loads the sidebar buttons. */
    public static void init() {
        // fired once per button at the end of every sidebar reload, after the button list is built
        SidebarButtonCreatedEvent.EVENT.register(event -> apply(event.getButton()));
    }

    private static void apply(RegisteredSidebarButton button) {
        List<SidebarGuiButton> list = SidebarButtonManager.INSTANCE.getButtonList();
        int slot = ALLOWED.indexOf(button.getId());
        if (slot < 0) {
            button.setForceHidden(true);
            list.removeIf(b -> b.getSidebarButton() == button);
            return;
        }
        for (SidebarGuiButton b : list) {
            if (b.getSidebarButton() == button) {
                b.setEnabled(true);
                b.setGridLocation(slot, 0);
            }
        }
    }
}
