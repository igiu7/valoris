package com.valoris.client.gui;

import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetLayout;
import net.minecraft.client.gui.GuiGraphics;

/** The scrollable area of a Valoris screen. Its content is built by the owning frame. */
final class ContentPanel extends Panel {
    static final int PAD = 8;
    static final int SCROLL = 8;

    private final ValorisFrame frame;

    ContentPanel(ValorisFrame frame) {
        super(frame);
        this.frame = frame;
        setOnlyRenderWidgetsInside(true);
        setOnlyInteractWithWidgetsInside(true);
        setScrollStep(22);
    }

    /** Width available to the widgets that are added to this panel. */
    int innerWidth() {
        return width - PAD * 2 - SCROLL;
    }

    @Override
    public void addWidgets() {
        frame.buildContent(this, innerWidth());
    }

    @Override
    public void alignWidgets() {
        for (Widget widget : widgets) {
            widget.setX(PAD);
        }
        align(new WidgetLayout.Vertical(PAD, 4, PAD));
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.CONTENT.draw(g, x, y, w, h);
    }
}
