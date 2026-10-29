package com.valoris.client.gui;

import java.util.List;
import java.util.Locale;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Non-interactive building blocks of the menus. */
final class Widgets {
    private Widgets() {}

    /** SMALL CAPS LABEL followed by a thin line. */
    static final class SectionHeader extends Widget {
        private final String text;

        SectionHeader(Panel panel, int width, String text) {
            super(panel);
            this.text = text.toUpperCase(Locale.ROOT);
            setSize(width, 18);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.text(g, theme, text, x + 1, y + 8, Ui.MUTED, false);
            int lineX = x + theme.getStringWidth(text) + 7;
            Ui.BORDER.draw(g, lineX, y + 12, Math.max(0, x + w - lineX), 1);
        }
    }

    /** "Label ........ value". */
    static final class StatRow extends Widget {
        private final String label;
        private final String value;
        private final Color4I color;

        StatRow(Panel panel, int width, String label, String value, Color4I color) {
            super(panel);
            this.label = label;
            this.value = value;
            this.color = color;
            setSize(width, 14);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.text(g, theme, label, x + 4, y + 3, Ui.MUTED, false);
            Ui.textRight(g, theme, Ui.fit(theme, value, w - theme.getStringWidth(label) - 20), x + w - 4, y + 3, color, true);
            Ui.BORDER.withAlpha(70).draw(g, x, y + h - 1, w, 1);
        }
    }

    /** Labelled progress bar, used for claimed chunks. */
    static final class ProgressRow extends Widget {
        private final String label;
        private final int current;
        private final int max;

        ProgressRow(Panel panel, int width, String label, int current, int max) {
            super(panel);
            this.label = label;
            this.current = current;
            this.max = Math.max(1, max);
            setSize(width, 26);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.text(g, theme, label, x + 4, y + 2, Ui.MUTED, false);
            Ui.textRight(g, theme, current + " / " + max, x + w - 4, y + 2, Ui.TEXT, true);

            float ratio = Math.min(1F, (float) current / max);
            Color4I fill = ratio >= 1F ? Ui.ORANGE : ratio >= 0.7F ? Ui.GOLD : Ui.GREEN;
            int barY = y + 14;
            Ui.box(g, x + 2, barY, w - 4, 8, Ui.TILE_DISABLED, Ui.TILE_BORDER);
            int fillW = (int) ((w - 6) * ratio);
            if (fillW > 0) fill.draw(g, x + 3, barY + 1, fillW, 6);
        }
    }

    /** Coloured note with an icon and wrapped text. */
    static final class InfoBox extends Widget {
        private final List<FormattedText> lines;
        private final Color4I color;
        private final Icon icon;

        InfoBox(Panel panel, int width, String text, Color4I color, Icon icon) {
            super(panel);
            this.color = color;
            this.icon = icon;
            this.lines = panel.getGui().getTheme().listFormattedStringToWidth(Component.literal(text), width - 38);
            setSize(width, Math.max(28, lines.size() * 10 + 12));
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.box(g, x, y, w, h, color.withAlpha(36), color.withAlpha(150));
            color.draw(g, x, y, 3, h);
            if (!icon.isEmpty()) icon.draw(g, x + 9, y + (h - 16) / 2, 16, 16);
            int ty = y + (h - lines.size() * 10 + 1) / 2;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + 32, ty, Ui.TEXT, false);
                ty += 10;
            }
        }
    }

    /** A row of coloured counters, e.g. Allies 2 | At war 1 | Neutral 3. */
    static final class SummaryStrip extends Widget {
        private final String[] labels;
        private final int[] values;
        private final Color4I[] colors;

        SummaryStrip(Panel panel, int width, String[] labels, int[] values, Color4I[] colors) {
            super(panel);
            this.labels = labels;
            this.values = values;
            this.colors = colors;
            setSize(width, 36);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            int n = labels.length;
            int gap = 4;
            int cell = (w - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                int cx = x + i * (cell + gap);
                Color4I c = values[i] > 0 ? colors[i] : Ui.GRAY;
                Ui.box(g, cx, y + 2, cell, h - 4, c.withAlpha(values[i] > 0 ? 34 : 18), c.withAlpha(values[i] > 0 ? 140 : 70));
                c.draw(g, cx, y + 2, cell, 2);
                g.pose().pushPose();
                g.pose().translate(cx + cell / 2F, y + 7, 0);
                g.pose().scale(1.5F, 1.5F, 1F);
                theme.drawString(g, String.valueOf(values[i]), 0, 0, values[i] > 0 ? Ui.TEXT : Ui.MUTED,
                        Theme.CENTERED | Theme.SHADOW);
                g.pose().popPose();
                theme.drawString(g, Ui.fit(theme, labels[i], cell - 6), cx + cell / 2, y + h - 13, Ui.MUTED, Theme.CENTERED);
            }
        }
    }

    /** One line of a chronicle: a muted time stamp and wrapped text. */
    static final class LogRow extends Widget {
        private static final int TIME_W = 62;
        private final String time;
        private final List<FormattedText> lines;

        LogRow(Panel panel, int width, String time, String text) {
            super(panel);
            this.time = time;
            this.lines = panel.getGui().getTheme().listFormattedStringToWidth(Component.literal(text), width - TIME_W - 8);
            setSize(width, Math.max(12, lines.size() * 10 + 2));
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.text(g, theme, time, x + 4, y + 1, Ui.MUTED, false);
            int ty = y + 1;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + TIME_W, ty, Ui.TEXT, false);
                ty += 10;
            }
            Ui.BORDER.withAlpha(60).draw(g, x, y + h - 1, w, 1);
        }
    }
}
