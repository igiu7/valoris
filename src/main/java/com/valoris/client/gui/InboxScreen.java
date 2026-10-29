package com.valoris.client.gui;

import java.util.List;

import com.valoris.client.ClientActions;
import com.valoris.client.ClientInbox;
import com.valoris.net.InboxPayload;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.Items;

/**
 * Every invitation, proposal and call the player can answer, one card each: what it is about, the time left to
 * answer, and the Accept / Decline buttons. Opens by itself when a new request arrives.
 */
public final class InboxScreen extends ValorisFrame {
    public InboxScreen() {
        preferredWidth = 380;
        preferredHeight = 260;
    }

    @Override
    protected String title() {
        return "Requests";
    }

    @Override
    protected String subtitle() {
        int n = ClientInbox.entries().size();
        return n == 0 ? "Nothing waiting for you" : n + (n == 1 ? " request waits" : " requests wait") + " for your answer";
    }

    @Override
    protected Color4I accent() {
        return Ui.GOLD;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.WRITABLE_BOOK);
    }

    @Override
    protected String footer() {
        return "Reopen with /valoris requests  -  requests expire after a while";
    }

    static Icon kindIcon(String kind) {
        return switch (kind) {
            case "city" -> Icons.PLAYER;
            case "kingdom" -> Icons.STAR;
            case "alliance" -> Icons.FRIENDS;
            case "peace" -> Icons.ACCEPT;
            case "war" -> ItemIcon.getItemIcon(Items.IRON_SWORD);
            case "conquest" -> Icons.BARRIER;
            default -> Icons.CHAT;
        };
    }

    static Color4I kindColor(String kind) {
        return switch (kind) {
            case "alliance", "city" -> Ui.GREEN;
            case "peace" -> Ui.BLUE;
            case "war", "conquest" -> Ui.RED;
            case "kingdom" -> Ui.GOLD;
            default -> Ui.GRAY;
        };
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        List<InboxPayload.Entry> list = ClientInbox.entries();
        list.removeIf(e -> ClientInbox.secondsLeft(e) == 0);
        if (list.isEmpty()) {
            p.add(new Widgets.InfoBox(p, cw, "There is nothing to answer right now.", Ui.GRAY, Icons.CHECK));
            return;
        }
        for (InboxPayload.Entry e : list) p.add(new Card(p, cw, e));
    }

    private void answer(InboxPayload.Entry e, boolean accept) {
        ClientActions.command(accept ? e.acceptCommand() : e.declineCommand());
        ClientInbox.remove(e.id());
        if (ClientInbox.entries().isEmpty()) closeGui(true);
        else refreshWidgets();
    }

    /** One request: icon, title, description, time left and the two answers. */
    private final class Card extends Panel {
        private final InboxPayload.Entry entry;
        private final List<FormattedText> lines;
        private final Color4I color;
        private Buttons.ChipButton accept;
        private Buttons.ChipButton decline;

        Card(Panel parent, int width, InboxPayload.Entry entry) {
            super(parent);
            this.entry = entry;
            this.color = kindColor(entry.kind());
            this.lines = parent.getGui().getTheme().listFormattedStringToWidth(Component.literal(entry.description()), width - 44);
            setSize(width, 24 + lines.size() * 10 + 26);
        }

        @Override
        public void addWidgets() {
            accept = new Buttons.ChipButton(this, Math.max(70, Ui.chipWidth(getGui().getTheme(), entry.accept()) + 8),
                    entry.accept(), Ui.GREEN, () -> answer(entry, true));
            decline = new Buttons.ChipButton(this, Math.max(60, Ui.chipWidth(getGui().getTheme(), entry.decline()) + 8),
                    entry.decline(), Ui.RED, () -> answer(entry, false));
            add(accept);
            add(decline);
        }

        @Override
        public void alignWidgets() {
            int y = height - 20;
            decline.setPos(width - 8 - decline.width, y);
            accept.setPos(decline.posX - 6 - accept.width, y);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Ui.box(g, x, y, w, h, Ui.TILE, Ui.TILE_BORDER);
            color.draw(g, x, y, 3, h);
            Ui.box(g, x + 9, y + 6, 22, 22, color.withAlpha(45), color.withAlpha(160));
            kindIcon(entry.kind()).draw(g, x + 12, y + 9, 16, 16);
            Ui.text(g, theme, Component.literal(Ui.fit(theme, entry.title(), w - 110)).withStyle(ChatFormatting.BOLD),
                    x + 38, y + 7, Ui.TEXT, true);
            int left = ClientInbox.secondsLeft(entry);
            if (left >= 0) Ui.textRight(g, theme, Ui.time(left) + " left", x + w - 8, y + 7, left < 30 ? Ui.RED : Ui.MUTED, false);
            int ty = y + 20;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + 38, ty, Ui.MUTED, false);
                ty += 10;
            }
        }
    }
}
