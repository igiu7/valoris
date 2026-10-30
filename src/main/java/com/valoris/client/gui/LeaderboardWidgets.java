package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import com.valoris.net.LeaderRow;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The leaderboard of the main menu: a podium with the top three (gold, silver, bronze), a bar to choose what to
 * rank by, and one row per kingdom or independent city with its medal, its ranked value and all its statistics.
 */
final class LeaderboardWidgets {
    private LeaderboardWidgets() {}

    static final Color4I GOLD = Color4I.rgb(0xf2c14e);
    static final Color4I SILVER = Color4I.rgb(0xc9d3df);
    static final Color4I BRONZE = Color4I.rgb(0xd0864a);

    /** What the board can be ranked by. */
    enum Sort {
        KILLS("Kills"), KD("K/D"), DEATHS("Deaths"), LAND("Land"), RESIDENTS("Residents"), CITIES("Cities"),
        TECHS("Technologies"), SIEGES_WON("Sieges won"), DEFENDED("Defended");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        double value(LeaderRow r) {
            return switch (this) {
                case KILLS -> r.stat(LeaderRow.KILLS);
                case KD -> r.kd();
                case DEATHS -> r.stat(LeaderRow.DEATHS);
                case LAND -> r.stat(LeaderRow.LAND);
                case RESIDENTS -> r.stat(LeaderRow.RESIDENTS);
                case CITIES -> r.stat(LeaderRow.CITIES);
                case TECHS -> r.stat(LeaderRow.TECHS);
                case SIEGES_WON -> r.stat(LeaderRow.SIEGES_WON);
                case DEFENDED -> r.stat(LeaderRow.SIEGES_REPELLED);
            };
        }

        String format(LeaderRow r) {
            return this == KD ? String.format(Locale.ROOT, "%.2f", r.kd()) : String.valueOf((int) value(r));
        }

        /** Unit shown under the value in a row. */
        String unit() {
            return switch (this) {
                case LAND -> "chunks";
                case KD -> "kills per death";
                default -> label.toLowerCase(Locale.ROOT);
            };
        }
    }

    static List<LeaderRow> sorted(List<LeaderRow> rows, Sort sort) {
        List<LeaderRow> out = new ArrayList<>(rows);
        out.sort(Comparator.comparingDouble((LeaderRow r) -> -sort.value(r))
                .thenComparing(r -> -r.stat(LeaderRow.KILLS))
                .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    static Color4I medal(int rank) {
        return switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> Ui.GRAY;
        };
    }

    static String medalName(int rank) {
        return switch (rank) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> rank + "th";
        };
    }

    /** The coloured square of a realm's map colour. */
    static void realmDot(GuiGraphics g, int x, int y, int rgb) {
        Ui.box(g, x, y, 7, 7, Color4I.rgb(rgb), Color4I.BLACK.withAlpha(160));
    }

    /** Every statistic of a realm, for tooltips. */
    static void tooltip(TooltipList list, LeaderRow r, int rank) {
        list.add(Component.literal(medalName(rank) + "  " + r.name()));
        list.add(Component.literal((r.kingdom() ? "Kingdom" : "Independent city") + ", led by " + r.leader()));
        list.add(Component.literal("Kills " + r.stat(LeaderRow.KILLS) + "   Deaths " + r.stat(LeaderRow.DEATHS)
                + "   K/D " + String.format(Locale.ROOT, "%.2f", r.kd())));
        list.add(Component.literal("Land " + r.stat(LeaderRow.LAND) + " chunks   Residents " + r.stat(LeaderRow.RESIDENTS)
                + "   Cities " + r.stat(LeaderRow.CITIES)));
        list.add(Component.literal("Technologies " + r.stat(LeaderRow.TECHS) + "   Sieges won "
                + r.stat(LeaderRow.SIEGES_WON) + "   Defended " + r.stat(LeaderRow.SIEGES_REPELLED)));
    }

    // ------------------------------------------------------------------ podium

    /** The top three on a podium: 2nd on the left, 1st in the middle and highest, 3rd on the right. */
    static final class Podium extends Widget {
        private final List<LeaderRow> top;
        private final Sort sort;

        Podium(Panel panel, int width, List<LeaderRow> sorted, Sort sort) {
            super(panel);
            this.top = sorted.subList(0, Math.min(3, sorted.size()));
            this.sort = sort;
            setSize(width, 96);
        }

        private int columnX(int rank, int x, int col, int gap, int w) {
            int left = x + (w - (3 * col + 2 * gap)) / 2;
            return switch (rank) {
                case 2 -> left;
                case 1 -> left + col + gap;
                default -> left + 2 * (col + gap);
            };
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            int gap = 6;
            int col = Math.min(104, (w - 2 * gap - 8) / 3);
            int base = y + h - 2;
            int[] heights = {0, 40, 28, 20};
            for (int rank = 1; rank <= top.size(); rank++) {
                LeaderRow r = top.get(rank - 1);
                Color4I c = medal(rank);
                int cx = columnX(rank, x, col, gap, w);
                int bh = heights[rank];
                int by = base - bh;
                // the step: a coloured block with a light top edge and the place number
                Ui.box(g, cx, by, col, bh, c.withAlpha(70), c.withAlpha(220));
                c.draw(g, cx + 1, by + 1, col - 2, 2);
                g.pose().pushPose();
                g.pose().translate(cx + col / 2F, by + (bh - 12) / 2F + 1, 0);
                g.pose().scale(1.5F, 1.5F, 1F);
                theme.drawString(g, String.valueOf(rank), 0, 0, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);
                g.pose().popPose();
                // above the step: the medal, the realm and its value
                int ty = by - 34;
                Ui.box(g, cx + col / 2 - 8, ty, 16, 10, c, c.withAlpha(255));
                theme.drawString(g, medalName(rank), cx + col / 2, ty + 1, Color4I.rgb(0x1a1a1a), Theme.CENTERED);
                String name = Ui.fit(theme, r.name(), col - 12);
                int nw = theme.getStringWidth(name) + 10;
                realmDot(g, cx + (col - nw) / 2, ty + 14, r.color());
                theme.drawString(g, name, cx + (col - nw) / 2 + 10, ty + 13, r.own() ? Ui.BLUE : c, Theme.SHADOW);
                theme.drawString(g, Ui.fit(theme, sort.format(r) + " " + sort.unit(), col - 4), cx + col / 2, ty + 23,
                        Ui.MUTED, Theme.CENTERED);
            }
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            int gap = 6, col = Math.min(104, (width - 2 * gap - 8) / 3);
            int mx = getMouseX();
            for (int rank = 1; rank <= top.size(); rank++) {
                int cx = columnX(rank, getX(), col, gap, width);
                if (mx >= cx && mx < cx + col) tooltip(list, top.get(rank - 1), rank);
            }
        }
    }

    // ------------------------------------------------------------------ sort bar

    /** "RANK BY" and a chip per statistic; the chosen one is highlighted. Wraps onto more lines when narrow. */
    static final class SortBar extends Widget {
        private static final int GAP = 4;
        private final Sort current;
        private final IntConsumer onPick;
        private final List<int[]> boxes = new ArrayList<>(); // x, y, w per Sort (relative)

        SortBar(Panel panel, int width, Sort current, IntConsumer onPick) {
            super(panel);
            this.current = current;
            this.onPick = onPick;
            var font = Minecraft.getInstance().font;
            int x = 0, y = 12;
            for (Sort s : Sort.values()) {
                int w = Math.max(34, font.width(s.label) + 12);
                if (x > 0 && x + w > width) { x = 0; y += Ui.CHIP_H + GAP; }
                boxes.add(new int[]{x, y, w});
                x += w + GAP;
            }
            setSize(width, y + Ui.CHIP_H + 4);
        }

        private int under(int mx, int my) {
            for (int i = 0; i < boxes.size(); i++) {
                int[] b = boxes.get(i);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + Ui.CHIP_H) return i;
            }
            return -1;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.text(g, theme, "RANK BY", x + 1, y + 1, Ui.MUTED, false);
            int hover = isMouseOver() ? under(getMouseX() - x, getMouseY() - y) : -1;
            Sort[] all = Sort.values();
            for (int i = 0; i < all.length; i++) {
                int[] b = boxes.get(i);
                boolean on = all[i] == current;
                Ui.chip(g, theme, x + b[0], y + b[1], b[2], all[i].label, on ? Ui.GOLD : Ui.GRAY, hover == i || on, true);
            }
        }

        @Override
        public boolean mousePressed(MouseButton button) {
            if (!isMouseOver()) return false;
            int i = under(getMouseX() - getX(), getMouseY() - getY());
            if (i < 0) return false;
            playClickSound();
            onPick.accept(i);
            return true;
        }
    }

    // ------------------------------------------------------------------ rows

    /** One realm: medal or rank, name and kind, the ranked value, and a line with all its statistics. */
    static final class RankRow extends Widget {
        private final LeaderRow r;
        private final int rank;
        private final Sort sort;

        RankRow(Panel panel, int width, LeaderRow r, int rank, Sort sort) {
            super(panel);
            this.r = r;
            this.rank = rank;
            this.sort = sort;
            setSize(width, 42);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean podium = rank <= 3;
            Color4I c = medal(rank);
            int top = y + 1, rh = h - 3;
            Color4I fill = podium ? c.withAlpha(isMouseOver() ? 60 : 38) : (isMouseOver() ? Ui.TILE_HOVER : Ui.TILE);
            Color4I border = r.own() ? Ui.BLUE : podium ? c.withAlpha(200) : Ui.TILE_BORDER;
            Ui.box(g, x, top, w, rh, fill, border);
            if (podium) c.draw(g, x + 1, top + 1, 3, rh - 2);

            // rank badge: a medal for the top three, a plain number below
            int bx = x + 9, by = top + (rh - 22) / 2;
            if (podium) {
                Ui.box(g, bx, by, 22, 22, c, c.withAlpha(255));
                Color4I.WHITE.withAlpha(110).draw(g, bx + 2, by + 2, 18, 2);
                theme.drawString(g, String.valueOf(rank), bx + 11, by + 7, Color4I.rgb(0x1a1a1a), Theme.CENTERED);
            } else {
                Ui.box(g, bx, by, 22, 22, Ui.WINDOW, Ui.TILE_BORDER);
                theme.drawString(g, String.valueOf(rank), bx + 11, by + 7, Ui.MUTED, Theme.CENTERED);
            }

            // the ranked value on the right
            String value = sort.format(r);
            int vw = Math.max(theme.getStringWidth(sort.unit()), (int) (theme.getStringWidth(value) * 1.5F)) + 8;
            int vx = x + w - 8;
            g.pose().pushPose();
            g.pose().translate(vx - theme.getStringWidth(value) * 1.5F, top + 7, 0);
            g.pose().scale(1.5F, 1.5F, 1F);
            theme.drawString(g, value, 0, 0, podium ? c : Ui.TEXT, Theme.SHADOW);
            g.pose().popPose();
            Ui.textRight(g, theme, sort.unit(), vx, top + 22, Ui.MUTED, false);

            // name, kind and the full line of statistics
            int tx = bx + 30, tw = vx - vw - tx;
            realmDot(g, tx, top + 6, r.color());
            String name = Ui.fit(theme, r.name(), tw - 40);
            Ui.text(g, theme, name, tx + 11, top + 5, podium ? c : Ui.TEXT, true);
            if (r.own()) {
                int cx = tx + 11 + theme.getStringWidth(name) + 5;
                Ui.box(g, cx, top + 4, 24, 10, Ui.BLUE.withAlpha(160), Ui.BLUE);
                theme.drawString(g, "YOU", cx + 12, top + 5, Ui.TEXT, Theme.CENTERED);
            }
            String kind = (r.kingdom() ? "Kingdom" : "Free city") + "  -  " + r.leader();
            Ui.text(g, theme, Ui.fit(theme, kind, tw), tx, top + 16, Ui.MUTED, false);
            String stats = "Kills " + r.stat(LeaderRow.KILLS) + "  Deaths " + r.stat(LeaderRow.DEATHS)
                    + "  K/D " + String.format(Locale.ROOT, "%.2f", r.kd()) + "  Land " + r.stat(LeaderRow.LAND)
                    + "  Residents " + r.stat(LeaderRow.RESIDENTS) + "  Tech " + r.stat(LeaderRow.TECHS);
            Ui.text(g, theme, Ui.fit(theme, stats, tw), tx, top + 27, Ui.GRAY, false);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            tooltip(list, r, rank);
        }
    }
}
