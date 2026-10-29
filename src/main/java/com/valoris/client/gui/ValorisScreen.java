package com.valoris.client.gui;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.config.ui.EditStringConfigOverlay;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.network.chat.Component;

/** Base for all Valoris screens: a scrollable button list built from FTB Library. */
public abstract class ValorisScreen extends AbstractButtonListScreen {
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '_-]{3,24}");

    protected ValorisScreen(Component title) {
        super();
        showBottomPanel(false);
        showCloseButton(true);
        setTitle(title);
    }

    @Override
    protected void doCancel() {
        closeGui();
    }

    @Override
    protected void doAccept() {
        closeGui();
    }

    /** Called when fresh state arrived from the server. */
    public void onStateChanged() {
        refreshWidgets();
    }

    protected static NordButton button(Panel panel, Component label, Icon icon, Runnable action, Component... tooltip) {
        return new NordButton(panel, label, icon) {
            @Override
            public void onClicked(MouseButton mouseButton) {
                playClickSound();
                action.run();
            }

            @Override
            public void addMouseOverText(TooltipList list) {
                for (Component line : tooltip) list.add(line);
            }
        };
    }

    protected static NordButton button(Panel panel, String label, Icon icon, Runnable action) {
        return button(panel, Component.literal(label), icon, action);
    }

    /** A row that only shows information. */
    protected static NordButton infoRow(Panel panel, String text) {
        return button(panel, Component.literal(text), Icons.INFO_GRAY, () -> {});
    }

    public void confirm(Component question, Component description, Runnable onYes) {
        openYesNo(question, description, onYes);
    }

    /** Opens a small text box asking for a city/kingdom name. */
    protected void promptName(Component title, Consumer<String> onAccept) {
        StringConfig config = new StringConfig(NAME);
        EditStringConfigOverlay<String> overlay = new EditStringConfigOverlay<>(this, config, accepted -> {
            String value = config.getValue();
            if (accepted && value != null && !value.isBlank()) onAccept.accept(value.trim());
        }, title).atMousePosition();
        pushModalPanel(overlay);
    }
}
