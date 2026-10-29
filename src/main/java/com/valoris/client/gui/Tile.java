package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.List;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The basic building block of the menus: an icon, a title, a second line, an optional badge on the right and
 * optional small action buttons ("chips"). The whole tile can be clickable as well.
 */
final class Tile extends Button {
    private final String name;
    private final String sub;
    private Color4I accent = Ui.BLUE;
    private String badge = "";
    private Color4I badgeColor = Ui.GRAY;
    private Runnable click;
    private boolean enabled = true;
    private final List<Chip> chips = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();

    Tile(Panel panel, int width, String name, String sub, Icon icon) {
        super(panel, Component.literal(name), icon);
        this.name = name;
        this.sub = sub == null ? "" : sub;
        setSize(width, this.sub.isEmpty() ? 24 : 30);
    }

    Tile accent(Color4I color) { this.accent = color; return this; }
    Tile badge(String text, Color4I color) { this.badge = text; this.badgeColor = color; return this; }
    Tile click(Runnable action) { this.click = action; return this; }
    Tile chip(String label, Color4I color, Runnable action) { chips.add(Chip.of(label, color, action)); return this; }
    Tile disabled() { this.enabled = false; return this; }
    Tile tip(String... lines) { for (String l : lines) tooltip.add(Component.literal(l)); return this; }

    // ------------------------------------------------------------ layout of badge and chips

    private record Layout(int[] chipX, int[] chipW, int badgeX, int badgeW, int textRight) {}

    private Layout layout(Theme theme) {
        int n = chips.size();
        int[] xs = new int[n];
        int[] ws = new int[n];
        int right = width - 6;
        for (int i = n - 1; i >= 0; i--) {
            ws[i] = Ui.chipWidth(theme, chips.get(i).label());
            xs[i] = right - ws[i];
            right = xs[i] - 4;
        }
        int badgeX = 0;
        int badgeW = 0;
        if (!badge.isEmpty()) {
            badgeW = theme.getStringWidth(badge) + 10;
            badgeX = right - badgeW;
            right = badgeX - 6;
        }
        return new Layout(xs, ws, badgeX, badgeW, right);
    }

    private int chipIndexAt(Theme theme, int mouseX, int mouseY) {
        if (chips.isEmpty()) return -1;
        Layout l = layout(theme);
        int relX = mouseX - getX();
        int relY = mouseY - getY();
        int chipTop = (height - Ui.CHIP_H) / 2;
        if (relY < chipTop || relY >= chipTop + Ui.CHIP_H) return -1;
        for (int i = 0; i < chips.size(); i++) {
            if (relX >= l.chipX()[i] && relX < l.chipX()[i] + l.chipW()[i]) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        boolean hover = enabled && click != null && isMouseOver();
        Color4I barColor = enabled ? accent : Ui.GRAY;
        Ui.box(g, x, y, w, h, !enabled ? Ui.TILE_DISABLED : hover ? Ui.TILE_HOVER : Ui.TILE,
                hover ? accent : Ui.TILE_BORDER);
        barColor.draw(g, x, y, 3, h);

        if (!icon.isEmpty()) icon.draw(g, x + 9, y + (h - 16) / 2, 16, 16);

        Layout l = layout(theme);
        int textX = x + 32;
        int maxTextW = l.textRight() - 32;
        if (sub.isEmpty()) {
            Ui.text(g, theme, Ui.fit(theme, name, maxTextW), textX, y + (h - 9) / 2 + 1, enabled ? Ui.TEXT : Ui.MUTED, true);
        } else {
            Ui.text(g, theme, Ui.fit(theme, name, maxTextW), textX, y + 6, enabled ? Ui.TEXT : Ui.MUTED, true);
            Ui.text(g, theme, Ui.fit(theme, sub, maxTextW), textX, y + 17, Ui.MUTED, false);
        }

        if (!badge.isEmpty()) {
            int by = y + (h - 12) / 2;
            Ui.box(g, x + l.badgeX(), by, l.badgeW(), 12, badgeColor.withAlpha(70), badgeColor.withAlpha(200));
            theme.drawString(g, badge, x + l.badgeX() + l.badgeW() / 2, by + 2, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);
        }

        int hoveredChip = isMouseOver() ? chipIndexAt(theme, getMouseX(), getMouseY()) : -1;
        int chipY = y + (h - Ui.CHIP_H) / 2;
        for (int i = 0; i < chips.size(); i++) {
            Chip c = chips.get(i);
            Ui.chip(g, theme, x + l.chipX()[i], chipY, l.chipW()[i], c.label(), c.color(), i == hoveredChip, c.enabled());
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!isMouseOver()) return false;
        int idx = chipIndexAt(getGui().getTheme(), getMouseX(), getMouseY());
        if (idx >= 0) {
            Chip c = chips.get(idx);
            if (c.enabled()) {
                playClickSound();
                c.action().run();
            }
            return true;
        }
        if (enabled && click != null) {
            playClickSound();
            click.run();
            return true;
        }
        return false;
    }

    @Override
    public void onClicked(MouseButton button) {
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        for (Component line : tooltip) list.add(line);
    }
}
