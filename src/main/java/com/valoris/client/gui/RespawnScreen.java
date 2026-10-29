package com.valoris.client.gui;

import com.valoris.client.RespawnClient;
import com.valoris.net.RespawnOptionsPayload;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Items;

/**
 * Shown instead of the death screen: respawn at a random place, or pick a city banner of your kingdom and spawn
 * there once its cooldown is over. Cannot be closed; it goes away when the player respawns.
 */
public final class RespawnScreen extends ValorisFrame {
    private static final Icon BANNER = ItemIcon.getItemIcon(com.valoris.registry.ModBlocks.CITY_BANNER_ITEM.get());
    private boolean bannerList;
    private String selected;
    private boolean sent;
    private int ticks;

    public RespawnScreen() {
        preferredWidth = 360;
        preferredHeight = 260;
    }

    @Override
    protected boolean closable() {
        return false;
    }

    @Override
    public boolean onClosedByKey(Key key) {
        return false; // Escape does nothing: the player must choose
    }

    @Override
    protected String title() {
        return RespawnClient.starting() ? "Welcome to Valoris" : "You died";
    }

    @Override
    protected String subtitle() {
        if (RespawnClient.starting()) return "Choose where your journey begins";
        RespawnOptionsPayload o = RespawnClient.options();
        if (o == null) return "";
        String m = o.deathMessage();
        return m.length() > 52 ? m.substring(0, 50) + "..." : m;
    }

    @Override
    protected Color4I accent() {
        return RespawnClient.starting() ? Ui.GOLD : Ui.RED;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(RespawnClient.starting() ? Items.COMPASS : Items.SKELETON_SKULL);
    }

    @Override
    protected String footer() {
        if (bannerList) return "Pick a banner, then press Spawn";
        return RespawnClient.starting() ? "Choose where you begin" : "Choose where you rise again";
    }

    @Override
    public void addWidgets() {
        super.addWidgets();
        Buttons.ChipButton leave = new Buttons.ChipButton(this, 66, "Disconnect", Ui.GRAY, RespawnScreen::disconnect);
        leave.setPos(width - 72, 10);
        add(leave);
    }

    private static void disconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.disconnect();
        mc.disconnect();
        mc.setScreen(new TitleScreen());
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 20 == 0) onStateChanged(); // countdowns
    }

    /** New options from the server (e.g. a choice was refused): let the player choose again. */
    public void optionsArrived() {
        sent = false;
        onStateChanged();
    }

    private void send(String city) {
        if (sent) return;
        sent = true;
        RespawnClient.choose(city);
        onStateChanged();
    }

    private static String time(int s) {
        return s >= 60 ? (s / 60) + "m " + String.format("%02d", s % 60) + "s" : s + "s";
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        RespawnOptionsPayload o = RespawnClient.options();
        if (o == null) {
            p.add(new Widgets.InfoBox(p, cw, "Waiting for the server...", Ui.GRAY, Icons.INFO));
            return;
        }
        if (sent) {
            p.add(new Widgets.InfoBox(p, cw, o.firstJoin() ? "Setting out..." : "Respawning...", Ui.GREEN, Icons.CHECK));
            return;
        }
        if (!bannerList) {
            String sub = !o.noBannersReason().isEmpty() ? o.noBannersReason()
                    : o.inKingdom() ? "Rise at one of the city banners of your kingdom."
                    : "Rise at the banner of your city.";
            String status = o.banners().isEmpty() ? "Unavailable"
                    : o.banners().size() == 1 ? "1 banner" : o.banners().size() + " banners";
            int h = Math.max(150, Math.min(200, getHeight() - HEADER_H - FOOTER_H - 46));
            p.add(new Widgets.SectionHeader(p, cw, o.firstJoin() ? "Choose where to begin" : "Choose where to respawn"));
            p.add(new ChoiceCards(p, cw, h, java.util.List.of(
                    new ChoiceCards.Card("Wilderness", o.randomArea() + ", at a random place.", "Now",
                            ItemIcon.getItemIcon(Items.GRASS_BLOCK), Ui.GREEN, true, () -> send("")),
                    new ChoiceCards.Card("City banner", sub, status, BANNER, Ui.GOLD, !o.banners().isEmpty(), () -> {
                        bannerList = true;
                        resetScroll();
                        refreshWidgets();
                    }))));
            return;
        }

        p.add(new Tile(p, cw, "Back", "Other respawn options", Icons.LEFT).accent(Ui.GRAY).click(() -> {
            bannerList = false;
            selected = null;
            resetScroll();
            refreshWidgets();
        }));
        p.add(new Widgets.SectionHeader(p, cw, o.inKingdom() ? "City banners of your kingdom" : "Your city's banner"));
        RespawnOptionsPayload.Banner chosen = null;
        for (RespawnOptionsPayload.Banner b : o.banners()) {
            int left = RespawnClient.secondsLeft(b);
            boolean isSel = b.city().equals(selected);
            if (isSel) chosen = b;
            String sub = b.x() + ", " + b.y() + ", " + b.z() + (b.besieged() ? "  -  UNDER SIEGE" : "")
                    + (b.own() ? "  -  your city" : "");
            Tile t = new Tile(p, cw, b.city(), sub, BANNER)
                    .accent(isSel ? Ui.GOLD : b.besieged() ? Ui.RED : Ui.BLUE)
                    .badge(isSel ? "SELECTED" : left > 0 ? time(left) : "Ready",
                            isSel ? Ui.GOLD : left > 0 ? Ui.ORANGE : Ui.GREEN)
                    .click(() -> {
                        selected = b.city();
                        onStateChanged();
                    });
            p.add(t);
        }
        int left = chosen == null ? 0 : RespawnClient.secondsLeft(chosen);
        String label = chosen == null ? "Select a banner" : left > 0 ? "Spawn in " + time(left) : "Spawn";
        String city = chosen == null ? null : chosen.city();
        Buttons.ChipButton spawn = new Buttons.ChipButton(p, cw, label, Ui.GREEN, () -> {
            if (city != null) send(city);
        });
        spawn.setEnabled(chosen != null && left <= 0);
        p.add(spawn);
    }
}
