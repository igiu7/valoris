package com.valoris.client.gui;

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

/** Small standalone buttons. */
final class Buttons {
    private Buttons() {}

    /** Icon-only button with a hover highlight (window close button). */
    static final class IconButton extends Button {
        private final Runnable action;
        private final String tip;

        IconButton(Panel panel, Icon icon, String tip, Runnable action) {
            super(panel, Component.literal(tip), icon);
            this.tip = tip;
            this.action = action;
            setSize(16, 16);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            if (isMouseOver()) Ui.box(g, x, y, w, h, Ui.TILE_HOVER, Ui.TILE_BORDER);
            icon.draw(g, x + 2, y + 2, w - 4, h - 4);
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            action.run();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(tip));
        }
    }

    /** Coloured text button used in dialogs. */
    static final class ChipButton extends Button {
        private final String label;
        private final Color4I color;
        private final Runnable action;
        private boolean enabled = true;

        ChipButton(Panel panel, int width, String label, Color4I color, Runnable action) {
            super(panel);
            this.label = label;
            this.color = color;
            this.action = action;
            setSize(width, 18);
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Color4I base = enabled ? color : Ui.GRAY;
            Ui.box(g, x, y, w, h, base.withAlpha(enabled ? (isMouseOver() ? 235 : 170) : 80), base.withAlpha(enabled ? 255 : 110));
            theme.drawString(g, label, x + w / 2, y + (h - 9) / 2 + 1, enabled ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (!enabled) return;
            playClickSound();
            action.run();
        }
    }

    /** Tab button of the sidebar, with an optional notification counter. */
    static final class SidebarButton extends Button {
        private final String label;
        private final boolean selected;
        private final int badge;
        private final Color4I accent;
        private final Runnable action;

        SidebarButton(Panel panel, int width, String label, Icon icon, boolean selected, int badge, Color4I accent, Runnable action) {
            super(panel, Component.literal(label), icon);
            this.label = label;
            this.selected = selected;
            this.badge = badge;
            this.accent = accent;
            this.action = action;
            setSize(width, 26);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            if (selected) {
                Ui.TILE.draw(g, x, y, w, h);
                accent.draw(g, x, y, 3, h);
            } else if (isMouseOver()) {
                Ui.TILE.withAlpha(150).draw(g, x, y, w, h);
            }
            icon.draw(g, x + 9, y + (h - 14) / 2, 14, 14);
            Ui.text(g, theme, label, x + 28, y + (h - 9) / 2 + 1, selected ? Ui.TEXT : Ui.MUTED, selected);

            if (badge > 0) {
                String n = badge > 9 ? "9+" : String.valueOf(badge);
                int bw = theme.getStringWidth(n) + 8;
                int bx = x + w - bw - 5;
                int by = y + (h - 11) / 2;
                Ui.box(g, bx, by, bw, 11, Ui.RED, Ui.RED);
                theme.drawString(g, n, bx + bw / 2, by + 2, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            action.run();
        }
    }
}
