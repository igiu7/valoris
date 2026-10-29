package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Axis;
import com.valoris.net.NoticeRow;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/**
 * A wooden notice board: planks in a dark frame, with the cities' notices pinned on it as parchment sheets.
 * Notices are laid out in columns, each sheet in the shortest column, slightly tilted like real paper.
 */
final class NoticeBoardWidget extends Widget {
    private static final Color4I FRAME = Color4I.rgb(0x2e1c10);
    private static final Color4I FRAME_LIGHT = Color4I.rgb(0x4a2f1b);
    private static final Color4I PLANK_A = Color4I.rgb(0x6f4a2a);
    private static final Color4I PLANK_B = Color4I.rgb(0x664427);
    private static final Color4I SEAM = Color4I.rgb(0x3f2716);
    private static final Color4I GRAIN = Color4I.rgb(0x5a3b21);
    private static final Color4I PAPER = Color4I.rgb(0xeadcb4);
    private static final Color4I PAPER_EDGE = Color4I.rgb(0xc4ab78);
    private static final Color4I PAPER_DARK = Color4I.rgb(0xd8c595);
    private static final Color4I INK = Color4I.rgb(0x2e1e10);
    private static final Color4I INK_SOFT = Color4I.rgb(0x6b5032);
    private static final Color4I PIN = Color4I.rgb(0xa8322a);
    private static final Color4I PIN_LIGHT = Color4I.rgb(0xe07a6e);
    private static final Color4I NAIL = Color4I.rgb(0x8d8d8d);

    private static final int FRAME_W = 7;
    private static final int PAD = 10;
    private static final int GAP = 10;
    private static final int TOP = 26;     // carved title strip

    private static final int BODY_LINES = 5;

    private record Sheet(NoticeRow row, int x, int y, int w, int h, List<FormattedText> title, List<FormattedText> body,
                         boolean cut, float tilt) {}

    private final List<Sheet> sheets = new ArrayList<>();

    NoticeBoardWidget(Panel panel, int width, List<NoticeRow> notices) {
        super(panel);
        Theme theme = panel.getGui().getTheme();
        int inner = width - 2 * FRAME_W - 2 * PAD;
        int cols = inner >= 330 ? 3 : inner >= 200 ? 2 : 1;
        int sheetW = (inner - GAP * (cols - 1)) / cols;
        int[] colY = new int[cols];
        int i = 0;
        for (NoticeRow n : notices) {
            List<FormattedText> title = theme.listFormattedStringToWidth(
                    Component.literal(n.title()).withStyle(ChatFormatting.BOLD), sheetW - 16);
            List<FormattedText> body = theme.listFormattedStringToWidth(Component.literal(n.body()), sheetW - 16);
            boolean cut = body.size() > BODY_LINES;
            if (cut) body = body.subList(0, BODY_LINES);
            int h = 14 + title.size() * 10 + 4 + 10 + 6 + body.size() * 10 + (cut ? 10 : 0) + 8 + 10 + 8;
            int c = 0;
            for (int k = 1; k < cols; k++) if (colY[k] < colY[c]) c = k;
            int x = FRAME_W + PAD + c * (sheetW + GAP);
            int y = FRAME_W + TOP + colY[c];
            float tilt = ((i * 37 + n.city().hashCode()) % 5 - 2) * 0.45F;
            sheets.add(new Sheet(n, x, y, sheetW, h, title, body, cut, tilt));
            colY[c] += h + GAP;
            i++;
        }
        int contentH = 0;
        for (int v : colY) contentH = Math.max(contentH, v);
        if (notices.isEmpty()) contentH = 70;
        setSize(width, FRAME_W * 2 + TOP + contentH + PAD);
    }

    private int drawnX, drawnY;

    private Sheet sheetAt(int mx, int my) {
        for (Sheet s : sheets) {
            if (mx >= s.x && mx < s.x + s.w && my >= s.y && my < s.y + s.h) return s;
        }
        return null;
    }

    private Sheet hovered() {
        return isMouseOver() ? sheetAt(getMouseX() - drawnX, getMouseY() - drawnY) : null;
    }

    @Override
    public boolean mousePressed(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
        if (!button.isLeft()) return false;
        Sheet s = hovered();
        if (s == null) return false;
        playClickSound();
        Dialogs.noticeView(getGui(), s.row);
        return true;
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        drawnX = x;
        drawnY = y;
        drawBoard(g, theme, x, y, w, h);
        if (sheets.isEmpty()) {
            drawEmpty(g, theme, x, y, w);
            return;
        }
        Sheet over = hovered();
        for (Sheet s : sheets) drawSheet(g, theme, x + s.x, y + s.y, s, s == over);
    }

    private void drawBoard(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Color4I.BLACK.withAlpha(70).draw(g, x + 3, y + 3, w, h);
        FRAME.draw(g, x, y, w, h);
        FRAME_LIGHT.draw(g, x + 1, y + 1, w - 2, 1);
        FRAME_LIGHT.draw(g, x + 1, y + 1, 1, h - 2);
        int ix = x + FRAME_W, iy = y + FRAME_W, iw = w - 2 * FRAME_W, ih = h - 2 * FRAME_W;
        int plank = 18;
        for (int py = 0, n = 0; py < ih; py += plank, n++) {
            int ph = Math.min(plank, ih - py);
            (n % 2 == 0 ? PLANK_A : PLANK_B).draw(g, ix, iy + py, iw, ph);
            if (py > 0) SEAM.draw(g, ix, iy + py, iw, 1);
            // wood grain: short streaks at fixed pseudo-random places
            for (int k = 0; k < iw / 22; k++) {
                int gx = (k * 53 + n * 29) % Math.max(1, iw - 14);
                int gy = 4 + (k * 7 + n * 3) % Math.max(1, ph - 7);
                GRAIN.draw(g, ix + gx, iy + py + gy, 6 + (k * 5 + n) % 9, 1);
            }
        }
        // inner shadow under the frame
        Color4I.BLACK.withAlpha(60).draw(g, ix, iy, iw, 2);
        Color4I.BLACK.withAlpha(40).draw(g, ix, iy, 2, ih);
        // nails in the corners of the frame
        for (int[] c : new int[][] {{x + 2, y + 2}, {x + w - 5, y + 2}, {x + 2, y + h - 5}, {x + w - 5, y + h - 5}}) {
            NAIL.draw(g, c[0], c[1], 3, 3);
            Color4I.WHITE.withAlpha(120).draw(g, c[0], c[1], 1, 1);
        }
        // carved title
        String title = "NOTICE BOARD";
        int tw = theme.getStringWidth(title);
        int tx = x + (w - tw) / 2;
        FRAME.withAlpha(200).draw(g, tx - 10, iy + 4, tw + 20, 15);
        FRAME_LIGHT.draw(g, tx - 10, iy + 18, tw + 20, 1);
        theme.drawString(g, title, tx, iy + 8, Color4I.rgb(0xd9b77a), 0);
    }

    private void drawEmpty(GuiGraphics g, Theme theme, int x, int y, int w) {
        int sw = Math.min(220, w - 40), sx = x + (w - sw) / 2, sy = y + FRAME_W + TOP + 6;
        PAPER_EDGE.draw(g, sx - 1, sy - 1, sw + 2, 44);
        PAPER.draw(g, sx, sy, sw, 42);
        pin(g, sx + sw / 2, sy + 2);
        theme.drawString(g, "The board is empty.", sx + sw / 2, sy + 14, INK, Theme.CENTERED);
        theme.drawString(g, "No city has pinned a notice.", sx + sw / 2, sy + 26, INK_SOFT, Theme.CENTERED);
    }

    private static void pin(GuiGraphics g, int cx, int cy) {
        Color4I.BLACK.withAlpha(90).draw(g, cx - 1, cy + 1, 4, 4);
        PIN.draw(g, cx - 2, cy - 2, 5, 5);
        PIN.draw(g, cx - 1, cy - 3, 3, 7);
        PIN.draw(g, cx - 3, cy - 1, 7, 3);
        PIN_LIGHT.draw(g, cx - 1, cy - 1, 2, 2);
    }

    private static String time(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + (seconds % 3600) / 60 + "m left";
        if (seconds >= 60) return (seconds / 60) + "m left";
        return "less than a minute left";
    }

    private void drawSheet(GuiGraphics g, Theme theme, int x, int y, Sheet s, boolean over) {
        g.pose().pushPose();
        g.pose().translate(x + s.w / 2F, y + s.h / 2F - (over ? 1 : 0), 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(over ? 0F : s.tilt));
        g.pose().translate(-s.w / 2F, -s.h / 2F, 0);

        // shadow, paper, edges and a darker lower corner
        Color4I.BLACK.withAlpha(80).draw(g, 2, 3, s.w, s.h);
        PAPER_EDGE.draw(g, -1, -1, s.w + 2, s.h + 2);
        PAPER.draw(g, 0, 0, s.w, s.h);
        PAPER_DARK.draw(g, 0, s.h - 3, s.w, 3);
        PAPER_DARK.draw(g, s.w - 3, 0, 3, s.h);
        PAPER_DARK.draw(g, s.w - 7, s.h - 7, 7, 7);
        // the city's heraldic colour as a ribbon on the left
        Color4I.rgb(s.row.color() & 0xFFFFFF).draw(g, 0, 0, 3, s.h);
        pin(g, s.w / 2, 5);

        int ty = 14;
        for (FormattedText line : s.title) {
            Ui.text(g, theme, line, 8, ty, INK, false);
            ty += 10;
        }
        Color4I.rgb(0xb59a66).draw(g, 8, ty + 1, s.w - 16, 1);
        ty += 5;
        String from = s.row.city() + (s.row.kingdom().isEmpty() ? "" : ", " + s.row.kingdom());
        Ui.text(g, theme, Ui.fit(theme, from, s.w - 16), 8, ty, Color4I.rgb(0x7a4b1e), false);
        ty += 16;
        for (FormattedText line : s.body) {
            Ui.text(g, theme, line, 8, ty, INK, false);
            ty += 10;
        }
        if (s.cut) {
            Ui.text(g, theme, "...", 8, ty - 2, INK_SOFT, false);
            String more = "read more";
            theme.drawString(g, more, s.w - 8 - theme.getStringWidth(more), ty, over ? Color4I.rgb(0xa8322a) : INK_SOFT, 0);
            ty += 10;
        }
        if (over) Color4I.rgb(0xb59a66).draw(g, -1, -1, s.w + 2, 1); // lifted sheet: a lighter top edge
        ty += 8;
        String footer = "- " + s.row.ruler();
        Ui.text(g, theme, Ui.fit(theme, footer, s.w / 2), 8, s.h - 16, INK_SOFT, false);
        String left = time(s.row.secondsLeft());
        theme.drawString(g, left, s.w - 8 - theme.getStringWidth(left), s.h - 16, INK_SOFT, 0);
        if (s.row.mine()) {
            // a small wax seal marks your own notice
            Color4I.rgb(0x2f6b3a).draw(g, s.w - 14, 6, 8, 8);
            Color4I.rgb(0x58a065).draw(g, s.w - 13, 7, 3, 3);
        }
        g.pose().popPose();
    }
}
