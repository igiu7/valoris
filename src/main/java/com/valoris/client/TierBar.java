package com.valoris.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.valoris.valoris2;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Vanilla experience is not used, so the experience bar shows the soil tier of the biome the player stands in
 * (farming.SoilQuality, sent with net.ClimatePayload): always full, coloured like the tiers on the map preview
 * (green 1, yellow-green 2, orange 3, red 4), with "T1".."T4" in place of the level.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class TierBar {
    private TierBar() {}

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final int[] COLORS = {0x8a8a8a, 0x3cc84a, 0xb8d84a, 0xe0a030, 0xd04030};

    private static int tier = 0;

    public static void receive(int t) {
        tier = t >= 1 && t <= 4 ? t : 0;
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_BAR, TierBar::renderBar);
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, TierBar::renderLabel);
    }

    /** Like Gui.isExperienceBarVisible: not while riding something that jumps (its jump meter is there). */
    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.gameMode != null && mc.player.jumpableVehicle() == null && mc.gameMode.hasExperience();
    }

    private static void renderBar(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        int x = g.guiWidth() / 2 - 91, y = g.guiHeight() - 29;
        int c = COLORS[tier];
        RenderSystem.enableBlend();
        g.blitSprite(BACKGROUND, x, y, 182, 5);
        RenderSystem.disableBlend();
        // the inside of the frame: the tier colour, a lighter top line and a darker bottom line like the vanilla fill
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF000000 | c);
        g.fill(x + 1, y + 1, x + 181, y + 2, 0xFF000000 | shade(c, 1.25));
        g.fill(x + 1, y + 3, x + 181, y + 4, 0xFF000000 | shade(c, 0.75));
    }

    private static void renderLabel(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc) || tier == 0) return;
        Font font = mc.font;
        String s = "T" + tier;
        int x = (g.guiWidth() - font.width(s)) / 2, y = g.guiHeight() - 35;
        // outlined like the vanilla level number
        g.drawString(font, s, x + 1, y, 0, false);
        g.drawString(font, s, x - 1, y, 0, false);
        g.drawString(font, s, x, y + 1, 0, false);
        g.drawString(font, s, x, y - 1, 0, false);
        g.drawString(font, s, x, y, 0xFF000000 | COLORS[tier], false);
    }

    private static int shade(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int gr = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (gr << 8) | b;
    }
}
