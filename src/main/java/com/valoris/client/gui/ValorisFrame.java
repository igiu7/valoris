package com.valoris.client.gui;

import java.util.function.Consumer;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.ScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Common window of all Valoris screens: a header with icon, title and subtitle, an optional sidebar,
 * a scrollable content area with its own scrollbar, and a footer line.
 */
public abstract class ValorisFrame extends BaseScreen {
    static final int HEADER_H = 38;
    static final int FOOTER_H = 14;

    protected int sidebarW = 0;
    protected int preferredWidth = 430;
    protected int preferredHeight = 272;

    private PanelScrollBar scrollBar;
    private double savedScroll;

    // ---- what a concrete screen provides ----
    protected abstract String title();
    protected abstract String subtitle();
    protected abstract Color4I accent();
    protected abstract Icon headerIcon();
    protected abstract void buildContent(Panel panel, int cw);

    protected void addSidebar() {}

    /** False: no close button (the screen decides itself when it goes away). */
    protected boolean closable() {
        return true;
    }

    protected String footer() {
        return "Press K to open this menu  -  /valoris for commands";
    }

    // ---- sizing and widgets ----
    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(300, Math.min(preferredWidth, sw - 16)), Math.max(190, Math.min(preferredHeight, sh - 16)));
        return true;
    }

    @Override
    public void addWidgets() {
        int cx = sidebarW;
        int cy = HEADER_H;
        int cw = width - sidebarW - 1;
        int ch = height - HEADER_H - FOOTER_H;

        ContentPanel content = new ContentPanel(this);
        content.setPosAndSize(cx, cy, cw, ch);
        add(content);

        scrollBar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, content);
        scrollBar.setPosAndSize(cx + cw - ContentPanel.SCROLL - 1, cy + 3, ContentPanel.SCROLL, ch - 6);
        add(scrollBar);

        if (closable()) {
            Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui());
            close.setPos(width - 24, 11);
            add(close);
        }
        addSidebar();
    }

    @Override
    public void alignWidgets() {
        // content height is known only now; restore the scroll position the player had
        if (scrollBar != null && savedScroll > 0) scrollBar.setValue(savedScroll);
    }

    /** Called when new state arrived from the server: rebuild, but stay at the same scroll position. */
    public void onStateChanged() {
        if (scrollBar != null) savedScroll = scrollBar.getValue();
        refreshWidgets();
    }

    protected void resetScroll() {
        savedScroll = 0;
        if (scrollBar != null) scrollBar.setValue(0);
    }

    // ---- dialogs ----
    protected void confirm(String title, String message, Color4I accent, Runnable onYes) {
        Dialogs.confirm(this, title, message, accent, onYes);
    }

    protected void prompt(String title, String hint, Color4I accent, Consumer<String> onAccept) {
        Dialogs.prompt(this, title, hint, accent, onAccept);
    }

    // ---- drawing ----
    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER_H - 1);
        accent().draw(g, x + 1, y + HEADER_H - 1, w - 2, 1);
        if (sidebarW > 0) Ui.SIDEBAR.draw(g, x + 1, y + HEADER_H, sidebarW, h - HEADER_H - FOOTER_H);
        Ui.HEADER.draw(g, x + 1, y + h - FOOTER_H, w - 2, FOOTER_H - 1);
        Ui.BORDER.draw(g, x + 1, y + h - FOOTER_H, w - 2, 1);

        // header: coloured icon tile, title, subtitle
        Ui.box(g, x + 9, y + 6, 26, 26, accent().withAlpha(50), accent());
        headerIcon().draw(g, x + 14, y + 11, 16, 16);
        Ui.text(g, theme, Component.literal(title()).withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
        Ui.text(g, theme, subtitle(), x + 44, y + 21, accent(), false);

        Ui.text(g, theme, footer(), x + 8, y + h - FOOTER_H + 3, Ui.MUTED, false);
    }
}
