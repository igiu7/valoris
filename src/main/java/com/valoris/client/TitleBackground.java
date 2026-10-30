package com.valoris.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.valoris.valoris2;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Background of the menus outside a world (instead of the turning panorama, see mixin.TitleBackgroundMixin): a slide
 * show of castle pictures (textures/gui/title_bg/bg_<n>.png, 1280x720, 16:9, lightly blurred). Each one stays for
 * SHOW_MS and fades into the next; the first one is picked at random when the game starts. A picture covers the
 * whole screen, cropped around its middle when the window is not 16:9.
 */
public final class TitleBackground {
    private TitleBackground() {}

    private static final int COUNT = 6;
    private static final int TEX_W = 1280, TEX_H = 720;
    private static final long SHOW_MS = 10_000, FADE_MS = 1_500;
    private static final ResourceLocation[] PICTURES = new ResourceLocation[COUNT];
    static {
        for (int i = 0; i < COUNT; i++) {
            PICTURES[i] = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "textures/gui/title_bg/bg_" + i + ".png");
        }
    }
    private static final long START = System.currentTimeMillis();
    private static final int FIRST = (int) (Math.random() * COUNT);

    public static void render(GuiGraphics g, int width, int height) {
        long t = System.currentTimeMillis() - START;
        int slot = (int) (t / SHOW_MS);
        long into = t % SHOW_MS;
        int current = (FIRST + slot) % COUNT;
        draw(g, current, width, height, 1.0F);
        if (into > SHOW_MS - FADE_MS) {
            float alpha = (into - (SHOW_MS - FADE_MS)) / (float) FADE_MS;
            draw(g, (current + 1) % COUNT, width, height, alpha);
        }
    }

    /** One picture covering the screen (the middle of it when the window is not 16:9). */
    private static void draw(GuiGraphics g, int index, int width, int height, float alpha) {
        if (width <= 0 || height <= 0) return;
        float scale = Math.max(width / (float) TEX_W, height / (float) TEX_H);
        int uw = Math.min(TEX_W, Math.round(width / scale));
        int vh = Math.min(TEX_H, Math.round(height / scale));
        float u = (TEX_W - uw) / 2.0F, v = (TEX_H - vh) / 2.0F;
        RenderSystem.enableBlend();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(PICTURES[index], 0, 0, width, height, u, v, uw, vh, TEX_W, TEX_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
