package com.valoris.client.gui;

import java.util.List;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Big clickable cards side by side: large icon, title, description and a status line. */
final class ChoiceCards extends Widget {
    record Card(String title, String description, String status, Icon icon, Color4I accent, boolean enabled, Runnable action) {}

    private static final int GAP = 8;
    private final List<Card> cards;
    private final List<List<FormattedText>> lines;
    private int drawnX, drawnY;

    ChoiceCards(Panel panel, int width, int height, List<Card> cards) {
        super(panel);
        this.cards = cards;
        Theme theme = panel.getGui().getTheme();
        int cardW = cardWidth(width);
        this.lines = cards.stream().map(c -> theme.listFormattedStringToWidth(Component.literal(c.description()), cardW - 16)).toList();
        setSize(width, height);
    }

    private int cardWidth(int w) {
        return (w - GAP * (cards.size() - 1)) / cards.size();
    }

    private int cardAt(int mx, int my) {
        if (my < 0 || my >= height) return -1;
        int cw = cardWidth(width);
        for (int i = 0; i < cards.size(); i++) {
            int cx = i * (cw + GAP);
            if (mx >= cx && mx < cx + cw) return i;
        }
        return -1;
    }

    private int hovered() {
        return isMouseOver() ? cardAt(getMouseX() - drawnX, getMouseY() - drawnY) : -1;
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!button.isLeft()) return false;
        int i = hovered();
        if (i < 0 || !cards.get(i).enabled()) return false;
        playClickSound();
        cards.get(i).action().run();
        return true;
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        drawnX = x;
        drawnY = y;
        int over = hovered();
        int cw = cardWidth(w);
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int cx = x + i * (cw + GAP);
            boolean hot = i == over && c.enabled();
            Color4I accent = c.enabled() ? c.accent() : Ui.GRAY;
            Ui.box(g, cx, y, cw, h, hot ? Ui.TILE_HOVER : c.enabled() ? Ui.TILE : Ui.TILE_DISABLED,
                    hot ? accent : Ui.TILE_BORDER);
            accent.draw(g, cx + 1, y + 1, cw - 2, 3);

            // big icon on a soft disc of the accent colour
            int iconSize = 32;
            int ix = cx + (cw - iconSize) / 2, iy = y + 16;
            Ui.box(g, ix - 6, iy - 6, iconSize + 12, iconSize + 12, accent.withAlpha(hot ? 60 : 36), accent.withAlpha(110));
            c.icon().draw(g, ix, iy, iconSize, iconSize);

            int ty = iy + iconSize + 14;
            theme.drawString(g, Component.literal(c.title()).withStyle(ChatFormatting.BOLD), cx + cw / 2, ty,
                    c.enabled() ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
            ty += 14;
            for (FormattedText line : lines.get(i)) {
                int lw = theme.getStringWidth(line);
                Ui.text(g, theme, line, cx + (cw - lw) / 2, ty, Ui.MUTED, false);
                ty += 10;
            }
            if (!c.status().isEmpty()) {
                int sw = Ui.chipWidth(theme, c.status());
                int sx = cx + (cw - sw) / 2, sy = y + h - 22;
                Ui.box(g, sx, sy, sw, 14, accent.withAlpha(70), accent);
                theme.drawString(g, c.status(), sx + sw / 2, sy + 3, Ui.TEXT, Theme.CENTERED);
            }
        }
    }
}
