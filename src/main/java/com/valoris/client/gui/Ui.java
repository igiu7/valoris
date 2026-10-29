package com.valoris.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Palette and small drawing helpers shared by all Valoris widgets. */
final class Ui {
    static final Color4I WINDOW = Color4I.rgb(0x141720);
    static final Color4I BORDER = Color4I.rgb(0x3a4256);
    static final Color4I HEADER = Color4I.rgb(0x1b2030);
    static final Color4I SIDEBAR = Color4I.rgb(0x171a24);
    static final Color4I CONTENT = Color4I.rgb(0x1d212c);
    static final Color4I TILE = Color4I.rgb(0x293042);
    static final Color4I TILE_HOVER = Color4I.rgb(0x364059);
    static final Color4I TILE_DISABLED = Color4I.rgb(0x222733);
    static final Color4I TILE_BORDER = Color4I.rgb(0x384056);
    static final Color4I TEXT = Color4I.rgb(0xeef1f8);
    static final Color4I MUTED = Color4I.rgb(0x98a2b8);
    static final Color4I GOLD = Color4I.rgb(0xf2c14e);
    static final Color4I BLUE = Color4I.rgb(0x5aa2ff);
    static final Color4I GREEN = Color4I.rgb(0x58c47a);
    static final Color4I RED = Color4I.rgb(0xe0564f);
    static final Color4I ORANGE = Color4I.rgb(0xf0953c);
    static final Color4I GRAY = Color4I.rgb(0x7a8499);

    static final int CHIP_H = 14;

    private Ui() {}

    static void box(GuiGraphics g, int x, int y, int w, int h, Color4I fill, Color4I border) {
        fill.draw(g, x, y, w, h);
        GuiHelper.drawHollowRect(g, x, y, w, h, border, false);
    }

    static void text(GuiGraphics g, Theme theme, Object text, int x, int y, Color4I color, boolean shadow) {
        theme.drawString(g, text, x, y, color, shadow ? Theme.SHADOW : 0);
    }

    /** Right-aligned text ending at x. */
    static void textRight(GuiGraphics g, Theme theme, String text, int x, int y, Color4I color, boolean shadow) {
        theme.drawString(g, text, x - theme.getStringWidth(text), y, color, shadow ? Theme.SHADOW : 0);
    }

    /** Trims a string with "..." if it is wider than maxW. */
    static String fit(Theme theme, String s, int maxW) {
        if (maxW <= 0) return "";
        if (theme.getStringWidth(s) <= maxW) return s;
        String cut = theme.trimStringToWidth(s, Math.max(0, maxW - theme.getStringWidth("...")));
        return cut + "...";
    }

    static int chipWidth(Theme theme, String label) {
        return Math.max(34, theme.getStringWidth(label) + 12);
    }

    static void chip(GuiGraphics g, Theme theme, int x, int y, int w, String label, Color4I color, boolean hover, boolean enabled) {
        Color4I base = enabled ? color : GRAY;
        Color4I fill = base.withAlpha(enabled ? (hover ? 235 : 165) : 90);
        Ui.box(g, x, y, w, CHIP_H, fill, base.withAlpha(enabled ? 255 : 120));
        theme.drawString(g, label, x + w / 2, y + (CHIP_H - 9) / 2 + 1, enabled ? TEXT : MUTED, Theme.CENTERED | Theme.SHADOW);
    }

    static String time(int seconds) {
        if (seconds >= 60) {
            int r = seconds % 60;
            return (seconds / 60) + " min" + (r == 0 ? "" : " " + r + " s");
        }
        return seconds + " s";
    }
}
