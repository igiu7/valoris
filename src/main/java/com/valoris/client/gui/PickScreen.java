package com.valoris.client.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.valoris.client.ClientActions;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.network.chat.Component;

/** A generic list screen. Entries are re-read from client state on every refresh. */
public class PickScreen extends ValorisScreen {
    public record Entry(Component label, Icon icon, Consumer<PickScreen> action, List<Component> tooltip) {
        public static Entry readOnly(String text, Icon icon) {
            return new Entry(Component.literal(text), icon, s -> {}, List.of());
        }

        /** Runs a command; optionally closes the list afterwards. */
        public static Entry command(String text, Icon icon, String command, boolean close) {
            return new Entry(Component.literal(text), icon, s -> {
                ClientActions.command(command);
                if (close) s.closeGui();
            }, List.of());
        }

        /** Asks for confirmation, runs the command, then closes the list. */
        public static Entry confirmed(String text, Icon icon, String question, String command) {
            return new Entry(Component.literal(text), icon,
                    s -> s.confirm(Component.literal(question), Component.empty(), () -> {
                        ClientActions.command(command);
                        s.closeGui();
                    }), List.of());
        }

        /** Opens a context menu with several actions. */
        public static Entry menu(String text, Icon icon, List<ContextMenuItem> items) {
            return new Entry(Component.literal(text), icon, s -> s.openContextMenu(items), List.of());
        }
    }

    public static ContextMenuItem menuItem(String text, Icon icon, String command) {
        return new ContextMenuItem(Component.literal(text), icon, b -> ClientActions.command(command));
    }

    private final String emptyText;
    private final Supplier<List<Entry>> entries;

    public PickScreen(String title, String emptyText, Supplier<List<Entry>> entries) {
        super(Component.literal(title));
        this.emptyText = emptyText;
        this.entries = entries;
    }

    public PickScreen withSearch() {
        setHasSearchBox(true);
        return this;
    }

    @Override
    public void addButtons(Panel panel) {
        List<Entry> list = entries.get();
        if (list.isEmpty()) {
            panel.add(com.valoris.client.gui.ValorisScreen.infoRow(panel, emptyText));
            return;
        }
        for (Entry entry : list) {
            panel.add(com.valoris.client.gui.ValorisScreen.button(panel, entry.label(), entry.icon(), () -> entry.action().accept(this),
                    entry.tooltip().toArray(new Component[0])));
        }
    }

    /** Small helper for screens that only show a list of icons + text. */
    public static Icon iconFor(boolean positive) {
        return positive ? Icons.ACCEPT : Icons.CANCEL;
    }
}
