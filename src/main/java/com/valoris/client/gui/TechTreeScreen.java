package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.valoris.armory.ArmoryEntry;
import com.valoris.client.ClientActions;
import com.valoris.client.ClientArmory;
import com.valoris.client.ClientState;
import com.valoris.net.GuiState;
import com.valoris.net.OpenRequestPayload;
import com.valoris.net.TechRow;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The technology tree of the player's city: nodes laid out by era with prerequisite lines, drag or scroll to pan.
 * Known technologies have a green frame, the one being researched a blue frame. Clicking a node shows its
 * details on the right with the Research button.
 */
public final class TechTreeScreen extends BaseScreen {
    private static final int HEADER = 38;
    private static final int FOOTER = 16;
    private static final int DETAIL = 178;

    // tree geometry (content coordinates)
    private static final int PAD = 10;
    private static final int ERA_H = 18;
    private static final int COL_W = 92;
    private static final int ROW_H = 54;
    private static final int NODE_W = 80;
    private static final int NODE_H = 52;
    private static final int BOX = 28;

    private static final String[] SKILLS = {"Blacksmith", "Farmer", "Miner", "Hunter"}; // order of com.valoris.skill.Skill
    private static final Color4I[] ERA_TINT = {Color4I.rgb(0x2a2418), Color4I.rgb(0x1f2530), Color4I.rgb(0x23262e),
            Color4I.rgb(0x2a1e1e)};

    private static String lastSelected = "";
    private static double lastScrollX = -1, lastScrollY = -1;

    private final Map<String, TechRow> rows = new LinkedHashMap<>();
    private TechRow selected;
    private TreePanel tree;
    private long cancelArmedUntil;
    private int refreshCooldown;
    /** Unlock icons drawn in the detail panel during the last frame, for tooltips: x, y, stack. */
    private final List<Object[]> drawnIcons = new ArrayList<>();

    public TechTreeScreen() {
        loadRows();
    }

    private static GuiState state() {
        return ClientState.get();
    }

    private void loadRows() {
        rows.clear();
        for (TechRow r : state().techs) rows.put(r.id(), r);
        selected = rows.get(lastSelected);
        if (selected == null) selected = rows.get(state().researching);
        if (selected == null) {
            for (TechRow r : rows.values()) if (r.status() == TechRow.AVAILABLE) { selected = r; break; }
        }
        if (selected == null && !rows.isEmpty()) selected = rows.values().iterator().next();
        if (selected != null) lastSelected = selected.id();
    }

    /** Called when the server sent a fresh state. */
    public void onStateChanged() {
        if (state().techs.isEmpty()) { closeGui(true); return; }
        loadRows();
        refreshWidgets();
    }

    // ------------------------------------------------------------------ time

    /** Seconds left of a researching row, counted down locally since the snapshot arrived. */
    private static int remaining(TechRow r) {
        if (r.status() != TechRow.RESEARCHING) return 0;
        long passed = (System.currentTimeMillis() - ClientState.receivedAt()) / 1000L;
        return (int) Math.max(0, r.remainingSeconds() - passed);
    }

    private static float progress(TechRow r) {
        if (r.researchSeconds() <= 0) return 1F;
        return Mth.clamp(1F - remaining(r) / (float) r.researchSeconds(), 0F, 1F);
    }

    static String duration(int seconds) {
        if (seconds <= 0) return "instant";
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m < 10 ? m + "m " + s + "s" : m + "m";
        return s + "s";
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshCooldown > 0) refreshCooldown--;
        TechRow current = rows.get(state().researching);
        if (current != null && remaining(current) <= 0 && refreshCooldown == 0) {
            refreshCooldown = 60; // research should be done: ask the server every 3 s until it says so
            PacketDistributor.sendToServer(new OpenRequestPayload(false));
        }
    }

    // ------------------------------------------------------------------ colors

    private static Color4I statusColor(int status) {
        return switch (status) {
            case TechRow.KNOWN -> Ui.GREEN;
            case TechRow.RESEARCHING -> Ui.BLUE;
            case TechRow.AVAILABLE -> Ui.GOLD;
            case TechRow.NEEDS_SKILLS -> Ui.ORANGE;
            default -> Ui.GRAY;
        };
    }

    private static String statusLabel(int status) {
        return switch (status) {
            case TechRow.KNOWN -> "Researched";
            case TechRow.RESEARCHING -> "Researching";
            case TechRow.AVAILABLE -> "Ready to research";
            case TechRow.NEEDS_SKILLS -> "Skills too low";
            case TechRow.ERA_CLOSED -> "Era not begun yet";
            default -> "Locked";
        };
    }

    /** Locked: by prerequisites, or because the admins have not opened its era yet. */
    private static boolean locked(int status) {
        return status == TechRow.LOCKED || status == TechRow.ERA_CLOSED;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(400, Math.min(600, sw - 12)), Math.max(240, Math.min(330, sh - 12)));
        return true;
    }

    private int treeX() { return 6; }
    private int treeY() { return HEADER + 6; }
    private int treeW() { return width - DETAIL - 18; }
    private int treeH() { return height - HEADER - FOOTER - 10; }
    private int detailX() { return width - DETAIL - 6; }
    private int detailY() { return HEADER + 6; }
    private int detailH() { return height - HEADER - FOOTER - 10; }

    @Override
    public void addWidgets() {
        Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui(false));
        close.setPos(width - 24, 11);
        add(close);
        Buttons.IconButton back = new Buttons.IconButton(this, Icons.LEFT, "Back to the main menu", () -> closeGui(true));
        back.setPos(width - 44, 11);
        add(back);

        tree = new TreePanel(this);
        tree.setPosAndSize(treeX(), treeY(), treeW(), treeH());
        add(tree);

        ResearchButton research = new ResearchButton(this);
        research.setPosAndSize(detailX() + 8, detailY() + detailH() - 28, DETAIL - 16, 20);
        add(research);
    }

    @Override
    public void alignWidgets() {
    }

    @Override
    public void onClosed() {
        super.onClosed();
        if (tree != null) {
            lastScrollX = tree.getScrollX();
            lastScrollY = tree.getScrollY();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        Ui.BLUE.draw(g, x + 1, y + HEADER - 1, w - 2, 1);

        Ui.box(g, x + 9, y + 6, 26, 26, Ui.BLUE.withAlpha(50), Ui.BLUE);
        Icons.BEACON.draw(g, x + 14, y + 11, 16, 16);
        Ui.text(g, theme, Component.literal("Technology Tree").withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
        long known = rows.values().stream().filter(r -> r.status() == TechRow.KNOWN).count();
        Ui.text(g, theme, state().cityName + "  -  " + known + " / " + rows.size() + " known", x + 44, y + 21, Ui.MUTED, false);

        drawResearchSummary(g, theme, x + w - 56 - 176, y + 6, 176, 26);

        drawDetail(g, theme, x + detailX(), y + detailY(), DETAIL, detailH());
        drawLegend(g, theme, x + 8, y + h - FOOTER + 2);
    }

    private void drawResearchSummary(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        TechRow current = rows.get(state().researching);
        if (current == null) {
            boolean any = rows.values().stream().anyMatch(r -> r.status() == TechRow.AVAILABLE);
            Ui.textRight(g, theme, any ? (state().canResearch ? "Pick a technology to research" : "No research in progress")
                    : "Nothing to research right now", x + w, y + 9, Ui.MUTED, false);
            return;
        }
        Ui.box(g, x, y, w, h, Ui.BLUE.withAlpha(35), Ui.BLUE.withAlpha(160));
        ItemIcon.getItemIcon(current.icon()).draw(g, x + 4, y + 5, 16, 16);
        Ui.text(g, theme, Ui.fit(theme, current.name(), w - 80), x + 24, y + 4, Ui.TEXT, true);
        Ui.textRight(g, theme, duration(remaining(current)), x + w - 5, y + 4, Ui.BLUE, false);
        int bw = w - 30;
        Ui.TILE_DISABLED.draw(g, x + 24, y + 17, bw, 4);
        Ui.BLUE.draw(g, x + 24, y + 17, (int) (bw * progress(current)), 4);
    }

    private void drawLegend(GuiGraphics g, Theme theme, int x, int y) {
        int[] statuses = {TechRow.KNOWN, TechRow.RESEARCHING, TechRow.AVAILABLE, TechRow.NEEDS_SKILLS, TechRow.LOCKED};
        String[] labels = {"Researched", "Researching", "Available", "Low skills", "Locked / era not begun"};
        for (int i = 0; i < statuses.length; i++) {
            Color4I c = statusColor(statuses[i]);
            Ui.box(g, x, y + 2, 8, 8, c.withAlpha(60), c);
            Ui.text(g, theme, labels[i], x + 12, y + 2, Ui.MUTED, false);
            x += theme.getStringWidth(labels[i]) + 22;
        }
        // the hint sits under the detail panel, and only when there is room for it
        String hint = "Drag or scroll to move";
        int hx = getX() + width - 8 - theme.getStringWidth(hint);
        if (hx > x + 6) Ui.text(g, theme, hint, hx, y + 2, Ui.MUTED.withAlpha(150), false);
    }

    private static void section(GuiGraphics g, Theme theme, String label, int x, int y, int w) {
        Ui.text(g, theme, label, x, y, Ui.MUTED, false);
        int lw = theme.getStringWidth(label);
        Ui.BORDER.draw(g, x + lw + 5, y + 4, w - lw - 5, 1);
    }

    private int wrap(GuiGraphics g, Theme theme, String text, int x, int y, int w, Color4I color, int bottom) {
        for (FormattedText line : theme.listFormattedStringToWidth(Component.literal(text), w)) {
            if (y + 9 > bottom) break;
            Ui.text(g, theme, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void drawDetail(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        drawnIcons.clear();
        Ui.box(g, x, y, w, h, Ui.CONTENT, Ui.BORDER);
        if (selected == null) {
            wrap(g, theme, "Click a technology to see what it unlocks and what it needs.", x + 8, y + 10, w - 16, Ui.MUTED, y + h);
            return;
        }
        TechRow r = selected;
        Color4I c = statusColor(r.status());
        int bottom = y + h - 34; // above the research button

        Ui.box(g, x + 6, y + 6, 36, 36, c.withAlpha(40), c);
        ItemIcon.getItemIcon(r.icon()).draw(g, x + 8, y + 8, 32, 32);
        int ty = y + 8;
        for (FormattedText l : theme.listFormattedStringToWidth(Component.literal(r.name()), w - 54)) {
            if (ty > y + 18) break;
            Ui.text(g, theme, l, x + 48, ty, Ui.TEXT, true);
            ty += 10;
        }
        Ui.text(g, theme, r.eraName(), x + 48, y + 31, Ui.MUTED, false);

        // status
        ty = y + 48;
        String status = statusLabel(r.status());
        if (r.status() == TechRow.RESEARCHING) status += "  -  " + duration(remaining(r)) + " left";
        else if (r.status() != TechRow.KNOWN) status += "  -  takes " + duration(r.researchSeconds());
        Ui.text(g, theme, Ui.fit(theme, status, w - 16), x + 8, ty, c, true);
        ty += 11;
        if (r.status() == TechRow.RESEARCHING) {
            Ui.TILE_DISABLED.draw(g, x + 8, ty, w - 16, 4);
            Ui.BLUE.draw(g, x + 8, ty, (int) ((w - 16) * progress(r)), 4);
            ty += 8;
        }

        GuiHelper.pushScissor(getWindow(), x + 1, y + 1, w - 2, bottom - y);
        if (!r.description().isEmpty()) ty = wrap(g, theme, r.description(), x + 8, ty + 2, w - 16, Ui.MUTED, bottom) + 3;

        // requirements
        section(g, theme, "REQUIRES", x + 8, ty, w - 16);
        ty += 12;
        boolean none = true;
        for (String req : r.requires()) {
            TechRow p = rows.get(req);
            boolean ok = p != null && p.status() == TechRow.KNOWN;
            Ui.text(g, theme, Ui.fit(theme, (ok ? "✔ " : "✘ ") + (p == null ? req : p.name()), w - 16),
                    x + 8, ty, ok ? Ui.GREEN : Ui.RED, false);
            ty += 10;
            none = false;
        }
        int[] best = state().citySkills;
        for (int i = 0; i < r.skills().length && i < SKILLS.length; i++) {
            int need = r.skills()[i];
            if (need <= 0) continue;
            int have = i < best.length ? best[i] : 1;
            boolean ok = have >= need;
            Ui.text(g, theme, (ok ? "✔ " : "✘ ") + SKILLS[i] + " " + need, x + 8, ty, ok ? Ui.GREEN : Ui.RED, false);
            Ui.textRight(g, theme, "best " + have, x + w - 8, ty, Ui.MUTED, false);
            ty += 10;
            none = false;
        }
        if (none) { Ui.text(g, theme, "Nothing", x + 8, ty, Ui.MUTED, false); ty += 10; }

        // what comes next
        List<String> next = new ArrayList<>();
        for (TechRow o : rows.values()) if (o.requires().contains(r.id())) next.add(o.name());
        if (!next.isEmpty()) ty = wrap(g, theme, "Leads to: " + String.join(", ", next), x + 8, ty + 2, w - 16,
                Ui.MUTED.withAlpha(200), bottom);

        // unlocks
        ty += 3;
        section(g, theme, "UNLOCKS", x + 8, ty, w - 16);
        ty += 12;
        for (String f : r.features()) ty = wrap(g, theme, "• " + f, x + 8, ty, w - 16, Ui.TEXT, bottom);
        List<ItemStack> items = new ArrayList<>();
        for (ArmoryEntry e : ClientArmory.entries()) if (e.tech().equals(r.id())) items.add(e.result());
        if (!items.isEmpty()) {
            ty += 2;
            int cols = Math.max(1, (w - 16) / 18);
            int rowsFit = Math.max(0, (bottom - ty) / 18);
            int max = cols * rowsFit;
            int shown = items.size() > max ? Math.max(0, max - 1) : items.size();
            for (int i = 0; i < shown; i++) {
                int ix = x + 8 + (i % cols) * 18, iy = ty + (i / cols) * 18;
                ItemIcon.getItemIcon(items.get(i)).draw(g, ix, iy, 16, 16);
                drawnIcons.add(new Object[]{ix, iy, items.get(i)});
            }
            if (shown < items.size() && max > 0) {
                int i = shown;
                int ix = x + 8 + (i % cols) * 18, iy = ty + (i / cols) * 18;
                theme.drawString(g, "+" + (items.size() - shown), ix + 8, iy + 4, Ui.MUTED, Theme.CENTERED);
            }
        } else if (r.features().isEmpty()) {
            Ui.text(g, theme, "Nothing new to craft", x + 8, ty, Ui.MUTED, false);
        }
        GuiHelper.popScissor(getWindow());
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        super.addMouseOverText(list);
        for (Object[] icon : drawnIcons) {
            if (isMouseOver((int) icon[0], (int) icon[1], 16, 16)) {
                list.add(((ItemStack) icon[2]).getHoverName());
                break;
            }
        }
    }

    // ------------------------------------------------------------------ the tree

    private static int nodeX(TechRow r) { return PAD + Math.max(0, r.x()) * COL_W; }
    private static int nodeY(TechRow r) { return PAD + ERA_H + Math.max(0, r.y()) * ROW_H; }

    private final class TreePanel extends Panel {
        private boolean dragging;

        TreePanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(30);
        }

        @Override
        public void addWidgets() {
            int maxX = 0, maxY = 0;
            for (TechRow r : rows.values()) {
                Node node = new Node(this, r);
                node.setPos(nodeX(r), nodeY(r));
                add(node);
                maxX = Math.max(maxX, nodeX(r) + NODE_W);
                maxY = Math.max(maxY, nodeY(r) + NODE_H);
            }
            // spacers so the content has a margin on every side
            add(new Spacer(this, 0, 0));
            add(new Spacer(this, maxX + PAD, maxY + PAD));
        }

        @Override
        public void alignWidgets() {
            if (lastScrollX < 0) focusOn(rows.get(state().researching) != null ? rows.get(state().researching) : selected);
            else {
                setScrollX(Mth.clamp(lastScrollX, 0, Math.max(0, getContentWidth() - width)));
                setScrollY(Mth.clamp(lastScrollY, 0, Math.max(0, getContentHeight() - height)));
            }
        }

        private void focusOn(TechRow r) {
            if (r == null) return;
            setScrollX(Mth.clamp(nodeX(r) + NODE_W / 2.0 - width / 2.0, 0, Math.max(0, getContentWidth() - width)));
            setScrollY(Mth.clamp(nodeY(r) + NODE_H / 2.0 - height / 2.0, 0, Math.max(0, getContentHeight() - height)));
            lastScrollX = getScrollX();
            lastScrollY = getScrollY();
        }

        @Override
        public void setScrollX(double scroll) {
            super.setScrollX(scroll);
            lastScrollX = scroll;
        }

        @Override
        public void setScrollY(double scroll) {
            super.setScrollY(scroll);
            lastScrollY = scroll;
        }

        @Override
        public boolean scrollPanel(double xDelta, double yDelta) {
            if (!isMouseOver()) return false;
            // a wide, short tree: the wheel scrolls sideways when there is nothing to scroll vertically
            if (getContentHeight() <= height && xDelta == 0) return movePanelScroll(-getScrollStep() * yDelta, 0);
            return super.scrollPanel(xDelta, yDelta);
        }

        @Override
        public boolean mousePressed(MouseButton button) {
            if (super.mousePressed(button)) return true;
            if (isMouseOver()) {
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(int button, double dragX, double dragY) {
            if (dragging) {
                movePanelScroll(-dragX, -dragY);
                return true;
            }
            return super.mouseDragged(button, dragX, dragY);
        }

        @Override
        public void mouseReleased(MouseButton button) {
            dragging = false;
            super.mouseReleased(button);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Ui.box(g, x - 1, y - 1, w + 2, h + 2, Color4I.rgb(0x10131a), Ui.BORDER);
        }

        /** Era bands and prerequisite lines, drawn under the nodes. x / y = content origin. */
        @Override
        public void drawOffsetBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            int contentH = getContentHeight();
            // era bands
            Map<String, int[]> eras = new LinkedHashMap<>(); // era -> min col, max col
            Map<String, String> eraNames = new LinkedHashMap<>();
            for (TechRow r : rows.values()) {
                int[] span = eras.computeIfAbsent(r.era(), k -> new int[]{Integer.MAX_VALUE, -1});
                span[0] = Math.min(span[0], r.x());
                span[1] = Math.max(span[1], r.x());
                eraNames.put(r.era(), r.eraName());
            }
            int i = 0;
            for (Map.Entry<String, int[]> e : eras.entrySet()) {
                int bx = x + PAD + e.getValue()[0] * COL_W - 4;
                int bw = (e.getValue()[1] - e.getValue()[0] + 1) * COL_W - 4;
                ERA_TINT[i % ERA_TINT.length].withAlpha(170).draw(g, bx, y + 4, bw, contentH - 6);
                String name = eraNames.get(e.getKey()).toUpperCase();
                theme.drawString(g, name, bx + bw / 2, y + 8, Ui.MUTED, Theme.CENTERED);
                i++;
            }
            // prerequisite lines
            for (TechRow child : rows.values()) {
                for (String req : child.requires()) {
                    TechRow parent = rows.get(req);
                    if (parent == null) continue;
                    Color4I color;
                    if (child.status() == TechRow.KNOWN) color = Ui.GREEN.withAlpha(190);
                    else if (parent.status() == TechRow.KNOWN) color = child.status() == TechRow.RESEARCHING
                            ? Ui.BLUE : Ui.MUTED.withAlpha(170);
                    else color = Ui.BORDER;
                    if (selected != null && (selected == child || selected == parent)) color = color.withAlpha(255);
                    drawLink(g, x, y, parent, child, color, selected == child);
                }
            }
        }

        private void drawLink(GuiGraphics g, int ox, int oy, TechRow from, TechRow to, Color4I color, boolean bold) {
            int t = bold ? 3 : 2;
            int x1 = ox + nodeX(from) + NODE_W / 2 + BOX / 2 + 1;
            int y1 = oy + nodeY(from) + BOX / 2 - t / 2;
            int x2 = ox + nodeX(to) + NODE_W / 2 - BOX / 2 - 1;
            int y2 = oy + nodeY(to) + BOX / 2 - t / 2;
            if (x2 <= x1) return;
            int mid = x2 - 12;                         // bend just before the child, so lines into one node merge
            if (mid < x1 + 4) mid = (x1 + x2) / 2;
            color.draw(g, x1, y1, mid - x1 + t, t);
            color.draw(g, mid, Math.min(y1, y2), t, Math.abs(y2 - y1) + t);
            color.draw(g, mid, y2, x2 - mid, t);
            // arrow head
            color.draw(g, x2 - 3, y2 - 2, 2, t + 4);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.draw(g, theme, x, y, w, h);
            // thin scroll indicators
            int cw = getContentWidth(), ch = getContentHeight();
            if (cw > w) {
                int bw = Math.max(16, w * w / cw);
                int bx = (int) ((w - bw) * (getScrollX() / (cw - w)));
                Ui.MUTED.withAlpha(110).draw(g, x + bx, y + h - 3, bw, 2);
            }
            if (ch > h) {
                int bh = Math.max(16, h * h / ch);
                int by = (int) ((h - bh) * (getScrollY() / (ch - h)));
                Ui.MUTED.withAlpha(110).draw(g, x + w - 3, y + by, 2, bh);
            }
        }
    }

    /** Invisible widget that only stretches the scrollable area. */
    private static final class Spacer extends dev.ftb.mods.ftblibrary.ui.Widget {
        Spacer(Panel panel, int x, int y) {
            super(panel);
            setPosAndSize(x, y, 1, 1);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        }
    }

    /** One technology in the tree: icon box with a status frame and the name under it. */
    private final class Node extends Button {
        final TechRow row;

        Node(Panel panel, TechRow row) {
            super(panel, Component.literal(row.name()), ItemIcon.getItemIcon(row.icon()));
            this.row = row;
            setSize(NODE_W, NODE_H);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            int bx = x + (w - BOX) / 2;
            int by = y;
            Color4I c = statusColor(row.status());
            boolean sel = selected != null && selected.id().equals(row.id());
            boolean dim = locked(row.status()) || row.status() == TechRow.NEEDS_SKILLS;

            if (sel) GuiHelper.drawHollowRect(g, bx - 3, by - 3, BOX + 6, BOX + 6, Ui.TEXT, false);
            Color4I fill = switch (row.status()) {
                case TechRow.KNOWN -> Ui.GREEN.withAlpha(45);
                case TechRow.RESEARCHING -> Ui.BLUE.withAlpha(45);
                default -> isMouseOver() ? Ui.TILE_HOVER : (dim ? Ui.TILE_DISABLED : Ui.TILE);
            };
            Color4I border = c;
            if (row.status() == TechRow.RESEARCHING) {
                float pulse = 0.65F + 0.35F * Mth.sin((System.currentTimeMillis() % 1600L) / 1600F * Mth.TWO_PI);
                border = Ui.BLUE.withAlpha((int) (255 * pulse));
            } else if (locked(row.status())) {
                border = Ui.TILE_BORDER;
            }
            Ui.box(g, bx, by, BOX, BOX, fill, border);
            if (row.status() == TechRow.KNOWN || row.status() == TechRow.RESEARCHING) {
                GuiHelper.drawHollowRect(g, bx + 1, by + 1, BOX - 2, BOX - 2, border, false); // double frame
            }
            icon.draw(g, bx + 6, by + 6, 16, 16);
            if (dim) {
                Color4I.BLACK.withAlpha(locked(row.status()) ? 140 : 90).draw(g, bx + 1, by + 1, BOX - 2, BOX - 2);
            }
            if (locked(row.status())) Icons.LOCK.draw(g, bx + BOX - 10, by + BOX - 10, 8, 8);
            if (row.status() == TechRow.KNOWN) Icons.CHECK.draw(g, bx + BOX - 10, by + BOX - 10, 8, 8);
            if (row.status() == TechRow.RESEARCHING) {
                Ui.TILE_DISABLED.draw(g, bx, by + BOX + 1, BOX, 3);
                Ui.BLUE.draw(g, bx, by + BOX + 1, (int) (BOX * progress(row)), 3);
            }

            int ty = by + BOX + 5;
            Color4I tc = dim ? Ui.MUTED : Ui.TEXT;
            List<FormattedText> lines = theme.listFormattedStringToWidth(Component.literal(row.name()), w);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                theme.drawString(g, lines.get(i), x + w / 2, ty, tc, Theme.CENTERED | (dim ? 0 : Theme.SHADOW));
                ty += 9;
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            selected = row;
            lastSelected = row.id();
            cancelArmedUntil = 0;
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(row.name()).withStyle(ChatFormatting.BOLD));
            String line = statusLabel(row.status());
            if (row.status() == TechRow.RESEARCHING) line += " - " + duration(remaining(row)) + " left";
            else if (row.status() != TechRow.KNOWN) line += " - takes " + duration(row.researchSeconds());
            list.add(Component.literal(line).withColor(statusColor(row.status()).rgb()));
        }
    }

    /** Research / cancel button under the detail panel. */
    private final class ResearchButton extends Button {
        ResearchButton(Panel panel) {
            super(panel, Component.literal("Research"), Icon.empty());
        }

        private boolean armed() {
            return System.currentTimeMillis() < cancelArmedUntil;
        }

        /** Label, color, enabled. */
        private Object[] look() {
            GuiState s = state();
            if (selected == null) return new Object[]{"Select a technology", Ui.GRAY, false};
            return switch (selected.status()) {
                case TechRow.KNOWN -> new Object[]{"✔ Researched", Ui.GREEN, false};
                case TechRow.RESEARCHING -> s.canResearch
                        ? new Object[]{armed() ? "Click again to cancel" : "Cancel research", Ui.RED, true}
                        : new Object[]{"Researching...", Ui.BLUE, false};
                case TechRow.AVAILABLE -> !s.canResearch ? new Object[]{(s.kingdomName.isEmpty() ? "Only the city ruler" : "Only the " + s.rulerTitle) + " can research", Ui.GRAY, false}
                        : !s.researching.isEmpty() ? new Object[]{"Another research is running", Ui.GRAY, false}
                        : new Object[]{"Research  (" + duration(selected.researchSeconds()) + ")", Ui.GREEN, true};
                case TechRow.NEEDS_SKILLS -> new Object[]{"Skills too low", Ui.GRAY, false};
                case TechRow.ERA_CLOSED -> new Object[]{"This era has not begun yet", Ui.GRAY, false};
                default -> new Object[]{"Research the prerequisites first", Ui.GRAY, false};
            };
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Object[] look = look();
            Color4I c = (Color4I) look[1];
            boolean on = (boolean) look[2];
            Ui.box(g, x, y, w, h, c.withAlpha(on ? (isMouseOver() ? 255 : 200) : 60), c.withAlpha(on ? 255 : 130));
            theme.drawString(g, Ui.fit(theme, (String) look[0], w - 8), x + w / 2, y + (h - 9) / 2 + 1,
                    on ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            Object[] look = look();
            if (!(boolean) look[2] || selected == null) return;
            playClickSound();
            if (selected.status() == TechRow.RESEARCHING) {
                if (!armed()) {
                    cancelArmedUntil = System.currentTimeMillis() + 3000;
                    return;
                }
                cancelArmedUntil = 0;
                ClientActions.command("valoris tech cancel");
            } else {
                ClientActions.command("valoris tech research " + selected.id());
            }
        }
    }
}
