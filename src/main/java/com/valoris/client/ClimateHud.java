package com.valoris.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.valoris.climate.Climate;
import com.valoris.net.ClimatePayload;
import com.valoris.valoris2;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The cold and heat meters (climate.Climate), drawn like the air bubbles - above the food bar, right after the air
 * - with snowflakes and flames. A meter is shown in its biomes and while it is not full.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class ClimateHud {
    private ClimateHud() {}

    private static final ResourceLocation COLD = sprite("cold");
    private static final ResourceLocation COLD_FADING = sprite("cold_fading");
    private static final ResourceLocation HEAT = sprite("heat");
    private static final ResourceLocation HEAT_FADING = sprite("heat_fading");

    private static int cold = Climate.MAX, heat = Climate.MAX, zone = Climate.NONE;

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "hud/" + name);
    }

    public static void receive(ClimatePayload payload) {
        cold = payload.cold();
        heat = payload.heat();
        zone = payload.zone();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "climate"),
                ClimateHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        if (zone == Climate.COLD || cold < Climate.MAX) meter(mc.gui, g, cold, COLD, COLD_FADING);
        if (zone == Climate.HOT || heat < Climate.MAX) meter(mc.gui, g, heat, HEAT, HEAT_FADING);
    }

    /** Like Gui.renderAirLevel: whole icons, then the one running out. */
    private static void meter(Gui gui, GuiGraphics g, int value, ResourceLocation full, ResourceLocation fading) {
        int right = g.guiWidth() / 2 + 91;
        int y = g.guiHeight() - gui.rightHeight;
        int whole = Math.max(0, Mth.ceil((value - 20) * 10.0 / Climate.MAX));
        int partial = Math.max(0, Mth.ceil(value * 10.0 / Climate.MAX) - whole);
        RenderSystem.enableBlend();
        for (int i = 0; i < whole + partial; i++) {
            g.blitSprite(i < whole ? full : fading, right - i * 8 - 9, y, 9, 9);
        }
        RenderSystem.disableBlend();
        gui.rightHeight += 10;
    }
}
