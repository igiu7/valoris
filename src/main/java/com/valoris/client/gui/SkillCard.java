package com.valoris.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** One profession: icon, name, level badge, how to earn XP and a progress bar to the next level. */
final class SkillCard extends Widget {
    private final String name;
    private final String description;
    private final Icon icon;
    private final Color4I color;
    private final int level;
    private final int maxLevel;
    private final int xp;
    private final int need;

    SkillCard(Panel panel, int width, String name, String description, Icon icon, Color4I color,
              int level, int maxLevel, int xp, int need) {
        super(panel);
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.color = color;
        this.level = level;
        this.maxLevel = maxLevel;
        this.xp = xp;
        this.need = need;
        setSize(width, 50);
    }

    private boolean maxed() {
        return level >= maxLevel || need <= 0;
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        Ui.box(g, x, y, w, h, isMouseOver() ? Ui.TILE_HOVER : Ui.TILE, Ui.TILE_BORDER);
        color.draw(g, x, y, 3, h);

        // icon tile
        Ui.box(g, x + 8, y + 8, 22, 22, color.withAlpha(45), color.withAlpha(180));
        icon.draw(g, x + 11, y + 11, 16, 16);

        // level badge (right)
        String badge = maxed() ? "MAX" : "Lv " + level;
        int bw = Math.max(36, theme.getStringWidth(badge) + 12);
        int bx = x + w - bw - 8;
        Ui.box(g, bx, y + 7, bw, 14, color.withAlpha(maxed() ? 230 : 90), color);
        theme.drawString(g, badge, bx + bw / 2, y + 10, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);

        // name + "level x / 20" + description
        Ui.text(g, theme, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 38, y + 8, Ui.TEXT, true);
        int nameW = theme.getStringWidth(Component.literal(name).withStyle(ChatFormatting.BOLD));
        Ui.text(g, theme, "Level " + level + " / " + maxLevel, x + 38 + nameW + 6, y + 8, Ui.MUTED, false);
        Ui.text(g, theme, Ui.fit(theme, description, bx - (x + 38) - 6), x + 38, y + 20, Ui.MUTED, false);

        // progress bar
        int barX = x + 38;
        int barY = y + 34;
        int barW = w - 38 - 8;
        int barH = 9;
        Ui.box(g, barX, barY, barW, barH, Ui.TILE_DISABLED, Ui.TILE_BORDER);
        float ratio = maxed() ? 1F : Math.min(1F, need == 0 ? 0F : (float) xp / need);
        int fillW = (int) ((barW - 2) * ratio);
        if (fillW > 0) {
            color.draw(g, barX + 1, barY + 1, fillW, barH - 2);
            Color4I.WHITE.withAlpha(45).draw(g, barX + 1, barY + 1, fillW, 2); // highlight
        }
        String label = maxed() ? "Mastered" : String.format("%,d / %,d XP", xp, need);
        theme.drawString(g, label, barX + barW / 2, barY + 1, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        list.add(Component.literal(name + " - level " + level + " / " + maxLevel));
        list.add(Component.literal(description).withStyle(ChatFormatting.GRAY));
        if (!maxed()) {
            list.add(Component.literal(String.format("%,d XP to level %d", Math.max(0, need - xp), level + 1))
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
