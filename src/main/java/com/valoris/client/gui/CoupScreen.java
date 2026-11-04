package com.valoris.client.gui;

import com.valoris.client.ClientActions;
import com.valoris.net.CoupPayload;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import net.minecraft.world.item.Items;

/**
 * A coup d'etat broke out in the player's realm (coup.CoupService): stand with the ruler or join the leader of the
 * coup. The window cannot be closed until the time to choose is over; then it closes by itself and the fight begins.
 */
public final class CoupScreen extends ValorisFrame {
    private final CoupPayload coup;
    private final long endMillis;
    private int side;
    private int ticks;

    public CoupScreen(CoupPayload coup) {
        this.coup = coup;
        this.side = coup.side();
        this.endMillis = System.currentTimeMillis() + coup.seconds() * 1000L;
        preferredWidth = 360;
        preferredHeight = 240;
    }

    private int secondsLeft() {
        return (int) Math.max(0, (endMillis - System.currentTimeMillis() + 999) / 1000);
    }

    @Override
    protected boolean closable() {
        return false;
    }

    @Override
    public boolean onClosedByKey(Key key) {
        return false; // Escape does nothing until the choice is over
    }

    @Override
    protected String title() {
        return "Coup d'etat!";
    }

    @Override
    protected String subtitle() {
        return coup.leader() + " rises against the " + coup.rulerTitle() + " of the " + coup.realm();
    }

    @Override
    protected Color4I accent() {
        return Ui.RED;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.IRON_SWORD);
    }

    @Override
    protected String footer() {
        if (coup.locked()) return "The others choose their side: " + secondsLeft() + "s left. Yours cannot change.";
        return "Choose your side: " + secondsLeft() + "s left. Undecided, you stand with " + coup.ruler() + ".";
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 10 == 0) onStateChanged(); // the countdown
        if (secondsLeft() <= 0) closeGui(false);
    }

    private void choose(int newSide) {
        if (coup.locked()) return; // the ruler and the leader of the coup lead their sides
        side = newSide;
        ClientActions.command("valoris coup side " + (newSide == 2 ? "leader" : "ruler"));
        onStateChanged();
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        p.add(new Widgets.SectionHeader(p, cw, coup.locked() ? (side == 2 ? "You lead the coup" : "You defend your throne")
                : "Whose side are you on?"));
        String yours = coup.locked() ? "YOU LEAD IT" : "YOUR SIDE";
        int h = Math.max(140, Math.min(180, getHeight() - HEADER_H - FOOTER_H - 46));
        p.add(new ChoiceCards(p, cw, h, java.util.List.of(
                new ChoiceCards.Card("Stand with " + coup.ruler(), "Defend the " + coup.rulerTitle() + ". He wins by killing "
                        + coup.leader() + " or by holding out.", side == 1 ? yours : coup.locked() ? "" : "Choose",
                        ItemIcon.getItemIcon(Items.GOLDEN_HELMET), Ui.GOLD, !coup.locked() || side == 1, () -> choose(1)),
                new ChoiceCards.Card("Join " + coup.leader(), "Overthrow the " + coup.rulerTitle() + ". " + coup.leader()
                        + " takes the throne if " + coup.ruler() + " falls.", side == 2 ? yours : coup.locked() ? "" : "Choose",
                        Icons.BARRIER, Ui.RED, !coup.locked() || side == 2, () -> choose(2)))));
    }
}
