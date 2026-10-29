package com.valoris.client.gui;

import com.valoris.client.ClientActions;
import com.valoris.net.ConquestPayload;
import com.valoris.net.Row;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;

/**
 * Shown to the conquering king after a city fell: appoint a ruler (a resident of the city or one of his own),
 * force the city into the kingdom with its current ruler, leave the city alone or destroy the city and its FTB team.
 */
public final class ConquestScreen extends ValorisFrame {
    private final ConquestPayload data;
    private boolean appointing;

    public ConquestScreen(ConquestPayload data) {
        this.data = data;
        this.preferredWidth = 360;
        this.preferredHeight = 250;
    }

    @Override
    protected String title() {
        return "City conquered";
    }

    @Override
    protected String subtitle() {
        return data.city() + "  -  conquered by " + data.attacker();
    }

    @Override
    protected Color4I accent() {
        return Ui.RED;
    }

    @Override
    protected Icon headerIcon() {
        return Icons.BARRIER;
    }

    @Override
    protected String footer() {
        return "Choose how to deal with the conquered city";
    }

    private void toggleAppointing(boolean value) {
        appointing = value;
        resetScroll();
        refreshWidgets();
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        p.add(new Widgets.InfoBox(p, cw, "The city of " + data.city() + " has fallen to " + data.attacker()
                + ". Its current ruler is " + data.ruler() + ". Decide what happens to the city.", Ui.GOLD, Icons.INFO));
        if (!data.note().isEmpty()) {
            p.add(new Widgets.InfoBox(p, cw, data.note(), Ui.RED, Icons.CANCEL));
        }

        if (data.awaiting()) {
            p.add(new Widgets.InfoBox(p, cw, "Waiting for " + data.ruler() + " to answer your request...", Ui.BLUE, Icons.CHAT));
            return;
        }

        if (appointing) {
            Tile back = new Tile(p, cw, "Back", "", Icons.BACK).accent(Ui.GRAY).click(() -> toggleAppointing(false));
            p.add(back);
            p.add(new Widgets.SectionHeader(p, cw, "Choose the new ruler"));
            if (data.residents().isEmpty()) {
                p.add(new Widgets.InfoBox(p, cw, "Nobody can be appointed: the city has no other residents and your"
                        + " cities have none but their rulers.", Ui.GRAY, Icons.INFO));
            }
            for (Row r : data.residents()) {
                boolean own = r.n() == 1; // one of the conqueror's own residents
                String effect = own
                        ? r.a() + " leaves their city, moves to " + data.city() + " and rules it. The city joins your kingdom."
                        : r.a() + " becomes the ruler of " + data.city() + ". The city joins your kingdom.";
                p.add(new Tile(p, cw, r.a(), r.c(), Icons.PLAYER).accent(own ? Ui.BLUE : Ui.GRAY)
                        .chip("Appoint", Ui.GOLD, () -> confirm("Appoint " + r.a() + "?", effect, Ui.GOLD, () -> {
                                    ClientActions.command("valoris war conquest appoint " + r.b());
                                    closeGui();
                                })));
            }
            return;
        }

        if (!data.kingdom()) {
            p.add(new Widgets.InfoBox(p, cw, "Your city belongs to no kingdom, so it cannot take the conquered city in."
                    + " You can leave it alone or destroy it. (Found a kingdom first to be able to add conquered cities to it.)",
                    Ui.ORANGE, Icons.INFO));
        } else if (data.full()) {
            p.add(new Widgets.InfoBox(p, cw, "Your kingdom is full, so the city cannot join it. You can leave it alone or destroy it.",
                    Ui.ORANGE, Icons.INFO));
        } else {
            p.add(new Widgets.SectionHeader(p, cw, "Keep the city"));
            p.add(new Tile(p, cw, "Appoint a new ruler", "A resident of the city or one of yours; the city joins your kingdom", Icons.STAR)
                    .accent(Ui.GOLD).click(() -> toggleAppointing(true)));
            p.add(new Tile(p, cw, "Force " + data.ruler() + " to submit", "The city joins your kingdom; " + data.ruler()
                    + " keeps ruling it", Icons.ADD).accent(Ui.GREEN)
                    .click(() -> confirm("Force " + data.city() + " to submit?",
                            "The city joins your kingdom at once and " + data.ruler() + " goes on ruling it - they are not asked."
                                    + " If it was a kingdom's capital, that kingdom is already gone and its other cities are free.",
                            Ui.GREEN, () -> {
                                ClientActions.command("valoris war conquest subjugate");
                                closeGui();
                            })));
        }

        p.add(new Widgets.SectionHeader(p, cw, "Leave it"));
        p.add(new Tile(p, cw, "Leave the city alone", "Nothing happens to it: it keeps its ruler and land and is free again",
                Icons.CANCEL).accent(Ui.GRAY)
                .click(() -> confirm("Leave " + data.city() + " alone?",
                        "The city stays as it is and becomes free (independent) again. You cannot decide later.", Ui.GRAY, () -> {
                            ClientActions.command("valoris war conquest release");
                            closeGui();
                        })));

        p.add(new Widgets.SectionHeader(p, cw, "Destroy"));
        p.add(new Tile(p, cw, "Destroy the city", "Removes the city, its claims and its team", Icons.BIN).accent(Ui.RED)
                .click(() -> confirm("Destroy " + data.city() + "?",
                        "The city and its FTB team are removed for good. This cannot be undone.", Ui.RED, () -> {
                            ClientActions.command("valoris war conquest destroy");
                            closeGui();
                        })));
    }
}
