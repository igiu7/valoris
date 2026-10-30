package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.valoris.client.ClientActions;
import com.valoris.client.ClientState;
import com.valoris.net.GuiState;
import com.valoris.net.Row;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import net.minecraft.world.item.Items;
import dev.ftb.mods.ftblibrary.ui.Panel;

/**
 * The main menu. A sidebar switches between five tabs; what a tab shows depends on the player's role
 * (wanderer, resident, city ruler, king). Every button runs one of the /valoris commands.
 */
public final class HubScreen extends ValorisFrame {
    private enum Tab {
        OVERVIEW("Overview", Icons.INFO),
        CITY("City", Icons.PLAYER),
        KINGDOM("Kingdom", Icons.STAR),
        DIPLOMACY("Diplomacy", Icons.GLOBE),
        TECH("Technology", Icons.BEACON),
        SKILLS("Skills", Icons.BOOK),
        NOTICES("Notice Board", ItemIcon.getItemIcon(Items.OAK_SIGN)),
        LEADERBOARD("Leaderboard", ItemIcon.getItemIcon(Items.GOLDEN_HELMET));

        final String label;
        final Icon icon;

        Tab(String label, Icon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private Tab tab = Tab.OVERVIEW;
    /** Non-null while the page of one realm (kingdom or independent city) is shown in the Diplomacy tab. */
    private String realmView;

    public HubScreen() {
        sidebarW = 96;
    }

    private static GuiState state() {
        return ClientState.get();
    }

    private static boolean isRuler(GuiState s) {
        return s.role.equals("CITY_RULER") || s.role.equals("KING");
    }

    // ------------------------------------------------------------------ header

    @Override
    protected String title() {
        GuiState s = state();
        return s.cityName.isEmpty() ? "Wanderer" : s.cityName;
    }

    @Override
    protected String subtitle() {
        GuiState s = state();
        return s.roleTitle + (s.kingdomName.isEmpty() ? "" : "  -  " + s.kingdomName);
    }

    @Override
    protected Color4I accent() {
        return switch (state().role) {
            case "KING" -> Ui.GOLD;
            case "CITY_RULER" -> Ui.BLUE;
            case "RESIDENT" -> Ui.GREEN;
            default -> Ui.GRAY;
        };
    }

    @Override
    protected Icon headerIcon() {
        return switch (state().role) {
            case "KING" -> Icons.STAR;
            case "CITY_RULER" -> Icons.SHIELD;
            case "RESIDENT" -> Icons.PLAYER;
            default -> Icons.COMPASS;
        };
    }

    // ------------------------------------------------------------------ sidebar

    private int badgeFor(Tab t, GuiState s) {
        return switch (t) {
            case CITY -> s.cityInvites.size() + s.cityJoinRequests.size();
            case KINGDOM -> s.kingdomInvites.size() + s.kingdomJoinRequests.size() + (s.conquestCity.isEmpty() ? 0 : 1);
            case OVERVIEW -> s.sieges.size();
            case DIPLOMACY -> s.proposals.size() + s.warCalls.size();
            case TECH -> s.canResearch && s.researching.isEmpty()
                    && s.techs.stream().anyMatch(r -> r.status() == com.valoris.net.TechRow.AVAILABLE) ? 1 : 0;
            default -> 0;
        };
    }

    private static final int TAB_STEP = 27;
    private static double sidebarScroll;

    /** The tabs scroll with the mouse wheel when they do not all fit (small windows). */
    @Override
    protected void addSidebar() {
        int top = HEADER_H + 6, h = height - HEADER_H - FOOTER_H - 8;
        TabPanel tabs = new TabPanel(this);
        tabs.setPosAndSize(4, top, sidebarW - 9, h);
        add(tabs);
        dev.ftb.mods.ftblibrary.ui.PanelScrollBar bar = new dev.ftb.mods.ftblibrary.ui.PanelScrollBar(this,
                dev.ftb.mods.ftblibrary.ui.ScrollBar.Plane.VERTICAL, tabs);
        bar.setPosAndSize(sidebarW - 4, top, 3, h);
        add(bar);
    }

    private final class TabPanel extends Panel {
        TabPanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(TAB_STEP);
        }

        @Override
        public void addWidgets() {
            GuiState s = state();
            int y = 0;
            for (Tab t : Tab.values()) {
                Buttons.SidebarButton button = new Buttons.SidebarButton(this, width, t.label, t.icon,
                        t == tab, badgeFor(t, s), accent(), () -> select(t));
                button.setPos(0, y);
                add(button);
                y += TAB_STEP;
            }
            setScrollY(sidebarScroll);
        }

        @Override
        public void alignWidgets() {
        }

        @Override
        public void setScrollY(double scroll) {
            super.setScrollY(scroll);
            sidebarScroll = scroll;
        }
    }

    /** The FTB Chunks claim map (claim limits of the mod still apply there). */
    private static void openClaimMap() {
        dev.ftb.mods.ftbchunks.client.gui.ChunkScreen.openChunkScreen();
    }

    private void select(Tab t) {
        if (t == Tab.TECH && !state().techs.isEmpty()) {
            new TechTreeScreen().openGui(); // the tree has its own screen; Back returns here
            return;
        }
        tab = t;
        realmView = null;
        resetScroll();
        refreshWidgets();
    }

    // ------------------------------------------------------------------ helpers

    private static void cmd(String command) {
        ClientActions.command(command);
    }

    private static String q(String name) {
        return ClientActions.q(name);
    }

    private static void section(Panel p, int cw, String text) {
        p.add(new Widgets.SectionHeader(p, cw, text));
    }

    private static void note(Panel p, int cw, String text, Color4I color, Icon icon) {
        p.add(new Widgets.InfoBox(p, cw, text, color, icon));
    }

    private static void stat(Panel p, int cw, String label, String value, Color4I color) {
        p.add(new Widgets.StatRow(p, cw, label, value, color));
    }

    private static Tile tile(Panel p, int cw, String name, String sub, Icon icon) {
        Tile t = new Tile(p, cw, name, sub, icon);
        p.add(t);
        return t;
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String relationLabel(int rel) {
        return switch (rel) {
            case -1 -> "Yours";
            case 1 -> "Alliance";
            case 3 -> "WAR";
            default -> "Neutral";
        };
    }

    private static Color4I relationColor(int rel) {
        return switch (rel) {
            case -1 -> Ui.GOLD;
            case 1 -> Ui.GREEN;
            case 3 -> Ui.RED;
            default -> Ui.GRAY;
        };
    }

    // ------------------------------------------------------------------ content

    @Override
    protected void buildContent(Panel p, int cw) {
        GuiState s = state();
        switch (tab) {
            case OVERVIEW -> overview(p, cw, s);
            case CITY -> city(p, cw, s);
            case KINGDOM -> kingdom(p, cw, s);
            case DIPLOMACY -> diplomacy(p, cw, s);
            case TECH -> technology(p, cw, s);
            case SKILLS -> skills(p, cw, s);
            case NOTICES -> notices(p, cw, s);
            case LEADERBOARD -> leaderboard(p, cw, s);
        }
    }

    // ---- leaderboard ----

    private static LeaderboardWidgets.Sort leaderSort = LeaderboardWidgets.Sort.KILLS;

    private void leaderboard(Panel p, int cw, GuiState s) {
        section(p, cw, "Leaderboard");
        if (s.leaderboard.isEmpty()) {
            note(p, cw, "There are no kingdoms or cities yet. Found a city to appear here.", Ui.GRAY, Icons.INFO);
            return;
        }
        List<com.valoris.net.LeaderRow> sorted = LeaderboardWidgets.sorted(s.leaderboard, leaderSort);
        p.add(new LeaderboardWidgets.Podium(p, cw, sorted, leaderSort));
        p.add(new LeaderboardWidgets.SortBar(p, cw, leaderSort, i -> {
            leaderSort = LeaderboardWidgets.Sort.values()[i];
            refreshWidgets();
        }));
        section(p, cw, "All realms (" + sorted.size() + ")");
        for (int i = 0; i < sorted.size(); i++) {
            p.add(new LeaderboardWidgets.RankRow(p, cw, sorted.get(i), i + 1, leaderSort));
        }
        note(p, cw, "Kingdoms and free cities. Kills and deaths add up every resident's (kills: players killed; deaths: any"
                + " cause). Sieges are counted for the realm that fought them. Hover a realm for all its statistics.",
                Ui.GRAY, Icons.INFO);
    }

    // ---- overview ----

    private void alert(Panel p, int cw, int count, String title, String sub, Icon icon, Runnable go) {
        if (count <= 0) return;
        tile(p, cw, title, sub, icon).accent(Ui.RED).badge(String.valueOf(count), Ui.RED).click(go);
    }

    private void overview(Panel p, int cw, GuiState s) {
        boolean hasCity = !s.cityName.isEmpty();
        section(p, cw, "Overview");
        if (hasCity) {
            stat(p, cw, "City", s.cityName, Ui.TEXT);
            stat(p, cw, "Ruler", s.cityRuler, Ui.TEXT);
            stat(p, cw, "Residents", String.valueOf(s.residents), Ui.TEXT);
            if (s.founded) {
                p.add(new Widgets.ProgressRow(p, cw, "Claimed chunks", s.claimed, s.claimLimit));
            } else {
                note(p, cw, "Your city is not founded yet. Place your City Banner (the red banner you were given) "
                        + "to found it and claim your first chunk.", Ui.ORANGE, Icons.INFO);
            }
        } else {
            note(p, cw, "You do not belong to any city yet. Open the City tab to create one or to ask to join an "
                    + "existing city.", Ui.BLUE, Icons.INFO);
        }
        if (!s.kingdomName.isEmpty()) {
            stat(p, cw, "Kingdom", s.kingdomName, Ui.GOLD);
            stat(p, cw, "King", s.king, Ui.TEXT);
        }

        section(p, cw, "Needs your attention");
        boolean any = !s.conquestCity.isEmpty() || !s.cityInvites.isEmpty() || !s.cityJoinRequests.isEmpty()
                || !s.kingdomInvites.isEmpty() || !s.kingdomJoinRequests.isEmpty() || !s.proposals.isEmpty();
        if (!s.conquestCity.isEmpty()) {
            tile(p, cw, "Decide the fate of " + s.conquestCity, "You conquered this city", Icons.BARRIER)
                    .accent(Ui.GOLD).badge("!", Ui.GOLD).click(() -> cmd("valoris war conquest"));
        }
        alert(p, cw, s.cityInvites.size(), "City invitations", "Cities that want you", Icons.CHAT, () -> select(Tab.CITY));
        alert(p, cw, s.cityJoinRequests.size(), "Requests to join your city", "Players waiting for an answer", Icons.CHAT, () -> select(Tab.CITY));
        alert(p, cw, s.kingdomInvites.size(), "Kingdom invitations", "Kingdoms that want your city", Icons.CHAT, () -> select(Tab.KINGDOM));
        alert(p, cw, s.kingdomJoinRequests.size(), "Cities asking to join", "Waiting for the king", Icons.CHAT, () -> select(Tab.KINGDOM));
        alert(p, cw, com.valoris.client.ClientInbox.entries().size(), "Requests waiting for you",
                "Invitations, proposals and calls - open the requests window", ItemIcon.getItemIcon(Items.WRITABLE_BOOK),
                () -> new InboxScreen().openGui());
        alert(p, cw, s.proposals.size(), "Diplomatic proposals", "Other realms await your answer", Icons.GLOBE, () -> select(Tab.DIPLOMACY));
        alert(p, cw, s.isLeader ? s.warCalls.size() : 0, "Calls to arms", "Your allies ask you to join their wars",
                ItemIcon.getItemIcon(Items.BELL), () -> select(Tab.DIPLOMACY));
        // sieges and wars: each opens its war's screen (sieges on its Siege tab)
        for (Row r : s.sieges) {
            String warId = warOfSiege(s, r);
            Tile t = siegeTile(p, cw, s, r);
            if (warId != null) t.click(() -> openWar(warId, WarScreen.View.SIEGE));
        }
        for (Row w : s.wars) {
            String id = w.a();
            warTile(p, cw, s, w).click(() -> openWar(id, WarScreen.View.SIDES));
        }
        if (!any && s.sieges.isEmpty() && s.wars.isEmpty()) {
            note(p, cw, "Nothing needs your attention right now.", Ui.GRAY, Icons.CHECK);
        }
    }

    // ---- city ----

    private void city(Panel p, int cw, GuiState s) {
        if (s.cityName.isEmpty()) {
            wanderer(p, cw, s);
        } else if (!isRuler(s)) {
            resident(p, cw, s);
        } else {
            ruler(p, cw, s);
        }
    }

    /** The city at a glance: ruler, residents, land. */
    private void cityOverview(Panel p, int cw, GuiState s) {
        section(p, cw, s.cityName);
        stat(p, cw, "Ruler", s.cityRuler, Ui.TEXT);
        stat(p, cw, "Residents", String.valueOf(s.residents), Ui.TEXT);
        if (!s.kingdomName.isEmpty()) stat(p, cw, "Kingdom", s.kingdomName, Ui.GOLD);
        if (s.founded) p.add(new Widgets.ProgressRow(p, cw, "Claimed chunks", s.claimed, s.claimLimit));
    }

    /** The two maps of a founded city, right below its overview. */
    private void cityMaps(Panel p, int cw, GuiState s, boolean ruler) {
        section(p, cw, "Maps");
        tile(p, cw, "City map", "Walls, buildings and the banner from above", ItemIcon.getItemIcon(Items.FILLED_MAP))
                .accent(Ui.BLUE).click(() -> new CityMapScreen(s.cityName).openGui());
        tile(p, cw, "Land map", ruler ? "Claim and release chunks" : "The city's claimed chunks",
                Icons.MAP).accent(ruler ? Ui.GREEN : Ui.BLUE).click(HubScreen::openClaimMap);
    }

    private void wanderer(Panel p, int cw, GuiState s) {
        section(p, cw, "Start your own");
        tile(p, cw, "Create a city", "Pick a name, then place the City Banner to found it", Icons.ADD)
                .accent(Ui.GREEN)
                .click(() -> prompt("Name your city", "City name", Ui.GREEN, name -> cmd("valoris city create " + q(name))));

        if (!s.cityInvites.isEmpty()) {
            section(p, cw, "Invitations");
            for (String city : s.cityInvites) {
                tile(p, cw, city, "invited you to join", Icons.CHAT).accent(Ui.GOLD)
                        .chip("Accept", Ui.GREEN, () -> cmd("valoris resident accept " + q(city)));
            }
        }

        section(p, cw, "Join an existing city");
        if (s.cities.isEmpty()) note(p, cw, "No cities exist yet. Be the first!", Ui.GRAY, Icons.INFO);
        for (Row c : s.cities) {
            tile(p, cw, c.a(), "Ruler " + c.b() + "  -  " + plural(c.n(), "resident", "residents")
                    + (c.c().isEmpty() ? "" : "  -  " + c.c()), Icons.PLAYER)
                    .chip("Request", Ui.BLUE, () -> cmd("valoris resident request " + q(c.a())));
        }
    }

    private void resident(Panel p, int cw, GuiState s) {
        cityOverview(p, cw, s);
        if (s.founded) cityMaps(p, cw, s, false);

        section(p, cw, "Actions");
        tile(p, cw, "Leave city", "You become a wanderer again", Icons.CANCEL).accent(Ui.RED)
                .click(() -> confirm("Leave " + s.cityName + "?", "You will lose access to the city's land.", Ui.RED,
                        () -> cmd("valoris resident leave")));
    }

    private void ruler(Panel p, int cw, GuiState s) {
        if (!s.founded) {
            section(p, cw, s.cityName);
            note(p, cw, "Not founded yet: place your City Banner to found the city.", Ui.ORANGE, Icons.INFO);
            return;
        }

        // what needs an answer first
        if (!s.cityJoinRequests.isEmpty()) {
            section(p, cw, "Join requests");
            for (String name : s.cityJoinRequests) {
                tile(p, cw, name, "wants to join", Icons.CHAT).accent(Ui.GOLD)
                        .chip("Accept", Ui.GREEN, () -> cmd("valoris city accept " + name))
                        .chip("Decline", Ui.RED, () -> cmd("valoris city decline " + name));
            }
        }

        cityOverview(p, cw, s);
        cityMaps(p, cw, s, true);

        section(p, cw, "Residents online");
        if (s.onlineResidents.isEmpty()) note(p, cw, "Nobody else is online.", Ui.GRAY, Icons.INFO);
        for (String name : s.onlineResidents) {
            Tile t = tile(p, cw, name, "Resident", Icons.PLAYER);
            if (!s.capital) {
                t.chip("Make ruler", Ui.GOLD, () -> confirm("Hand over " + s.cityName + "?",
                        name + " becomes the ruler of the city. You become a resident.", Ui.GOLD,
                        () -> cmd("valoris city transfer " + name)));
            }
            t.chip("Remove", Ui.RED, () -> confirm("Remove " + name + "?", name + " is removed from the city.", Ui.RED,
                    () -> cmd("valoris city kick " + name)));
        }

        if (!s.candidates.isEmpty()) {
            section(p, cw, "Invite players");
            for (String name : s.candidates) {
                tile(p, cw, name, "Online, no city", Icons.PLAYER)
                        .chip("Invite", Ui.GREEN, () -> cmd("valoris city invite " + name));
            }
        }

        section(p, cw, "Settings");
        tile(p, cw, "Move the city banner", "To where you stand, inside your land", ItemIcon.getItemIcon("valoris2:city_banner"))
                .accent(Ui.BLUE)
                .click(() -> confirm("Move the city banner?",
                        "The banner moves to where you stand. You must be on your city's land, on a free spot.", Ui.BLUE,
                        () -> cmd("valoris city banner move")));
        tile(p, cw, "Rename", "Currently " + s.cityName, Icons.NOTES).accent(Ui.BLUE)
                .click(() -> Dialogs.prompt(this, "Rename " + s.cityName, "New city name", Ui.BLUE, "Rename",
                        s.cityName, name -> cmd("valoris city rename " + q(name))));
        if (s.kingdomName.isEmpty()) {
            tile(p, cw, "Map colour", "Your land's colour on the map", Color4I.rgb(s.realmColor)).accent(Ui.BLUE)
                    .click(() -> Dialogs.color(this, "Colour of " + s.cityName, s.realmColor,
                            rgb -> cmd("valoris city color " + String.format("%06x", rgb))));
        } else {
            tile(p, cw, "Map colour", "Set by the Kingdom of " + s.kingdomName, Color4I.rgb(s.realmColor)).disabled();
        }

        if (s.kingdomName.isEmpty()) {
            section(p, cw, "Danger zone");
            tile(p, cw, "Disband city", "Removes the city and its team for good", Icons.BIN).accent(Ui.RED)
                    .click(() -> confirm("Disband " + s.cityName + "?", "The city, its claims and its team are removed. "
                            + "This cannot be undone.", Ui.RED, () -> cmd("valoris city disband")));
        }
    }

    // ---- kingdom ----

    private void kingdom(Panel p, int cw, GuiState s) {
        boolean king = s.role.equals("KING");

        if (s.cityName.isEmpty()) {
            note(p, cw, "Join or found a city first.", Ui.BLUE, Icons.INFO);
            return;
        }

        if (s.kingdomName.isEmpty()) {
            if (!isRuler(s)) {
                note(p, cw, "Your city is in no kingdom. Only its ruler can found or join one.", Ui.BLUE, Icons.INFO);
                return;
            }
            if (!s.founded) {
                note(p, cw, "Found your city first: place the City Banner.", Ui.ORANGE, Icons.INFO);
                return;
            }
            // what needs an answer first
            if (!s.kingdomInvites.isEmpty()) {
                section(p, cw, "Invitations");
                for (String k : s.kingdomInvites) {
                    tile(p, cw, k, "invites your city", Icons.CHAT).accent(Ui.GOLD)
                            .chip("Accept", Ui.GREEN, () -> cmd("valoris city kingdom accept " + q(k)))
                            .chip("Decline", Ui.RED, () -> cmd("valoris city kingdom decline " + q(k)));
                }
            }

            section(p, cw, "Found a kingdom");
            tile(p, cw, "Proclaim a kingdom", "Your city becomes the capital, you the king (3+ residents)", Icons.STAR)
                    .accent(Ui.GOLD).click(() -> prompt("Name your kingdom", "Kingdom name", Ui.GOLD,
                            name -> cmd("valoris kingdom create " + q(name))));

            if (!s.kingdoms.isEmpty()) {
                section(p, cw, "Join a kingdom");
                for (Row k : s.kingdoms) {
                    tile(p, cw, k.a(), "King " + k.b() + "  -  " + plural(k.n(), "city", "cities"), Icons.GLOBE)
                            .chip("Request", Ui.BLUE, () -> cmd("valoris city kingdom join " + q(k.a())));
                }
            }
            return;
        }

        // what needs an answer first
        if (king && !s.conquestCity.isEmpty()) {
            section(p, cw, "Conquest");
            tile(p, cw, "Decide the fate of " + s.conquestCity, "Appoint, subjugate, loot or release", Icons.BARRIER)
                    .accent(Ui.GOLD).badge("!", Ui.GOLD).click(() -> cmd("valoris war conquest"));
        }
        if (king && !s.kingdomJoinRequests.isEmpty()) {
            section(p, cw, "Join requests");
            for (String city : s.kingdomJoinRequests) {
                tile(p, cw, city, "asks to join", Icons.CHAT).accent(Ui.GOLD)
                        .chip("Accept", Ui.GREEN, () -> cmd("valoris kingdom accept " + q(city)))
                        .chip("Decline", Ui.RED, () -> cmd("valoris kingdom decline " + q(city)));
            }
        }

        section(p, cw, "Kingdom of " + s.kingdomName);
        stat(p, cw, "King", s.king, Ui.GOLD);
        stat(p, cw, "Cities", String.valueOf(s.myCities.size()), Ui.TEXT);

        section(p, cw, "Cities");
        for (Row c : s.myCities) {
            boolean capital = c.n() == 1;
            boolean online = c.m() == 1;
            Tile t = tile(p, cw, c.a(), "Ruler " + c.b() + (capital ? "  -  capital" : ""),
                    capital ? Icons.STAR : ItemIcon.getItemIcon("valoris2:city_banner")).accent(capital ? Ui.GOLD : Ui.BLUE);
            if (capital) t.badge("CAPITAL", Ui.GOLD);
            if (king && !capital) {
                if (online) {
                    t.chip("Crown", Ui.GOLD, () -> confirm("Pass the crown?", c.b() + " becomes king and " + c.a()
                            + " the new capital.", Ui.GOLD, () -> cmd("valoris kingdom crown " + c.b())));
                }
                t.chip("Expel", Ui.RED, () -> confirm("Expel " + c.a() + "?", "The city leaves the kingdom.", Ui.RED,
                        () -> cmd("valoris kingdom expel " + q(c.a()))));
            }
        }

        if (king) {
            if (!s.kingdomCandidates.isEmpty()) {
                section(p, cw, "Invite a city");
                for (Row r : s.kingdomCandidates) {
                    tile(p, cw, r.b(), "Ruler " + r.a(), Icons.ADD)
                            .chip("Invite", Ui.GREEN, () -> cmd("valoris kingdom invite " + r.a()));
                }
            }

            section(p, cw, "Settings");
            tile(p, cw, "Rename", "Currently the Kingdom of " + s.kingdomName, Icons.NOTES).accent(Ui.GOLD)
                    .click(() -> Dialogs.prompt(this, "Rename the kingdom", "New kingdom name", Ui.GOLD, "Rename",
                            s.kingdomName, name -> cmd("valoris kingdom rename " + q(name))));
            tile(p, cw, "Map colour", "Worn by every city of the kingdom", Color4I.rgb(s.realmColor))
                    .accent(Ui.GOLD).click(() -> Dialogs.color(this, "Colour of " + s.kingdomName, s.realmColor,
                            rgb -> cmd("valoris kingdom color " + String.format("%06x", rgb))));

            section(p, cw, "Danger zone");
            tile(p, cw, "Disband the kingdom", "All cities become independent", Icons.BIN).accent(Ui.RED)
                    .click(() -> confirm("Disband " + s.kingdomName + "?", "The kingdom ceases to exist. "
                            + "This cannot be undone.", Ui.RED, () -> cmd("valoris kingdom disband")));
        } else if (isRuler(s)) {
            section(p, cw, "Danger zone");
            tile(p, cw, "Rebel", "Leave the kingdom and rise against the king", Icons.BARRIER)
                    .accent(Ui.RED)
                    .click(() -> confirm("Rebel against " + s.kingdomName + "?",
                            "Your city becomes independent and a rebellion begins at once. If the king takes your city"
                                    + " back, you cannot rebel again for a while.", Ui.RED,
                            () -> cmd("valoris city kingdom rebel")));
        }
    }

    // ---- diplomacy ----

    private static final int NEUTRAL = 0, ALLY = 1, WAR = 3;

    /** The war screen: both sides next to each other. Its links to diplomacy come back to this screen. */
    private void openWar(String warId) {
        openWar(warId, WarScreen.View.SIDES);
    }

    private void openWar(String warId, WarScreen.View view) {
        new WarScreen(warId, this::openRealm, view).openGui();
    }

    /** The war a siege belongs to: the one with its attacker and its defender on its two sides. */
    private static String warOfSiege(GuiState s, Row siege) {
        for (Row w : s.wars) {
            boolean attacker = false, defender = false;
            for (Row m : s.warMembers) {
                if (!m.a().equals(w.a())) continue;
                attacker |= m.b().equals(siege.a());
                defender |= m.b().equals(siege.c());
            }
            if (attacker && defender) return w.a();
        }
        return s.wars.isEmpty() ? null : s.wars.get(0).a();
    }

    private void openRealm(String name) {
        tab = Tab.DIPLOMACY;
        realmView = name;
        resetScroll();
        refreshWidgets();
    }

    private void closeRealm() {
        realmView = null;
        resetScroll();
        refreshWidgets();
    }

    private static Row realmRow(GuiState s, String name) {
        for (Row r : s.realms) if (r.a().equals(name)) return r;
        return null;
    }

    private static boolean isKingdom(Row realm) {
        return realm.c().equals("KINGDOM");
    }

    private static String realmTitle(Row realm) {
        return (isKingdom(realm) ? "Kingdom of " : "City of ") + realm.a();
    }

    private static Icon realmIcon(Row realm) {
        return isKingdom(realm) ? Icons.GLOBE : ItemIcon.getItemIcon("valoris2:city_banner");
    }

    /** The realm a city row fights for: its kingdom, or the city itself when independent. */
    static String realmOfCity(Row city) {
        return city.c().isEmpty() ? city.a() : city.c();
    }

    private static String realmSub(Row realm) {
        return isKingdom(realm) ? "King " + realm.b() + "  -  " + plural(realm.n(), "city", "cities")
                : "Ruler " + realm.b() + "  -  independent city";
    }

    private static String pending(List<Row> list, String realm) {
        for (Row r : list) if (r.a().equals(realm)) return r.b();
        return null;
    }

    private static String proposalLabel(String proposal) {
        return proposal.equals("ALLIANCE") ? "an alliance" : "peace";
    }

    // -- wars (coalitions) --

    private static List<Row> warMembers(GuiState s, String warId, String side) {
        List<Row> out = new ArrayList<>();
        for (Row m : s.warMembers) if (m.a().equals(warId) && m.c().equals(side)) out.add(m);
        return out;
    }

    private static String names(List<Row> members) {
        List<String> out = new ArrayList<>();
        for (Row m : members) out.add(m.b());
        return String.join(", ", out);
    }

    private static boolean inWar(GuiState s, String warId, String realm, String side) {
        for (Row m : warMembers(s, warId, side)) if (m.b().equals(realm)) return true;
        return false;
    }

    private static boolean leads(GuiState s, String warId, String realm) {
        for (Row m : s.warMembers) if (m.a().equals(warId) && m.b().equals(realm) && m.n() == 1) return true;
        return false;
    }

    private static List<String> myAllies(GuiState s) {
        List<String> out = new ArrayList<>();
        for (Row r : s.realms) if (r.m() == ALLY) out.add(r.a());
        return out;
    }

    private String enemyTitle(GuiState s, String name) {
        Row r = realmRow(s, name);
        return r == null ? name : realmTitle(r);
    }

    /**
     * "War against the Kingdom of X"; a rebellion (flag 4) is "Rebellion against the Kingdom of X" for the rebels
     * and "Rebellion of the City of X" for the kingdom.
     */
    private String warTitle(GuiState s, Row w) {
        boolean offensive = (w.n() & 1) != 0;
        if ((w.n() & 4) == 0) return "War against the " + enemyTitle(s, w.c());
        return (offensive ? "Rebellion against the " : "Rebellion of the ") + enemyTitle(s, w.c());
    }

    /** One war: "Your side vs their side" with the realms of both coalitions; click for its screen and sieges. */
    private Tile warTile(Panel p, int cw, GuiState s, Row w) {
        boolean offensive = (w.n() & 1) != 0;
        boolean rebellion = (w.n() & 4) != 0;
        String sub = names(warMembers(s, w.a(), "OURS")) + "  vs  " + names(warMembers(s, w.a(), "THEIRS"));
        return tile(p, cw, warTitle(s, w), sub, ItemIcon.getItemIcon(Items.IRON_SWORD))
                .accent(Ui.ORANGE).badge(rebellion ? "REBELLION" : offensive ? "ATTACKING" : "DEFENDING",
                        rebellion ? Ui.GOLD : offensive ? Ui.ORANGE : Ui.BLUE)
                .tip("Your side: " + names(warMembers(s, w.a(), "OURS")) + " (led by " + w.b() + ")",
                        "Their side: " + names(warMembers(s, w.a(), "THEIRS")) + " (led by " + w.c() + ")",
                        "Click for the war, its chronicle and sieges");
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void callToArmsTile(Panel p, int cw, GuiState s, Row call) {
        Tile t = tile(p, cw, "The " + enemyTitle(s, call.a()) + " calls you to arms",
                "against the " + enemyTitle(s, call.b()), ItemIcon.getItemIcon(Items.BELL)).accent(Ui.GOLD).badge("CALL TO ARMS", Ui.GOLD);
        if (!s.isLeader) return;
        t.chip("Join", Ui.RED, () -> confirm("Join the war against the " + enemyTitle(s, call.b()) + "?",
                "You go to war with everyone on their side and take part in every siege of your ally's side.",
                Ui.RED, () -> cmd("valoris diplomacy join " + q(call.a()) + " " + q(call.b()))));
        t.chip("Refuse", Ui.GRAY, () -> cmd("valoris diplomacy refuse " + q(call.a()) + " " + q(call.b())));
    }

    /** Declare war, with a checklist of allies to call to arms. */
    private void declareWarDialog(GuiState s, Row target) {
        List<String> allies = myAllies(s);
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < allies.size(); i++) notes.add(null);
        String title = realmTitle(target);
        Dialogs.checklist(this, "Declare war on the " + title + "?",
                "Their allies will come to their defence. Pick the allies you call to arms; each of them decides "
                        + "whether to answer. You stay at war until peace is made.",
                Ui.RED, allies, notes, "Declare war", picked -> {
                    cmd("valoris diplomacy war " + q(target.a()));
                    for (String ally : picked) cmd("valoris diplomacy call " + q(ally) + " " + q(target.a()));
                });
    }

    /** Call an ally to any of our wars it is not part of yet. */
    private void callAllyDialog(GuiState s, Row ally) {
        List<String> options = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> enemies = new ArrayList<>();
        for (Row w : s.wars) {
            options.add(warTitle(s, w));
            enemies.add(w.c());
            String note = null;
            if (inWar(s, w.a(), ally.a(), "OURS")) note = "already with you";
            else if (inWar(s, w.a(), ally.a(), "THEIRS")) note = "fights against you";
            else for (Row c : s.sentCalls) if (c.a().equals(ally.a()) && c.c().equals(w.a())) note = "already called";
            notes.add(note);
        }
        Dialogs.checklist(this, "Call the " + realmTitle(ally) + " to arms",
                "Pick the wars your ally should join. They decide whether to answer the call.",
                Ui.GOLD, options, notes, "Send the call", picked -> {
                    for (int i = 0; i < options.size(); i++) {
                        if (picked.contains(options.get(i))) cmd("valoris diplomacy call " + q(ally.a()) + " " + q(enemies.get(i)));
                    }
                });
    }

    private void diplomacy(Panel p, int cw, GuiState s) {
        if (s.realmName.isEmpty()) {
            note(p, cw, "Diplomacy belongs to kingdoms and to independent cities. Found or join a city to take part.",
                    Ui.BLUE, Icons.INFO);
            return;
        }
        if (realmView != null) {
            realmPage(p, cw, s);
            return;
        }
        List<Row> allies = new ArrayList<>(), enemies = new ArrayList<>(), neutral = new ArrayList<>();
        Row mine = null;
        for (Row r : s.realms) {
            switch (r.m()) {
                case -1 -> mine = r;
                case ALLY -> allies.add(r);
                case WAR -> enemies.add(r);
                default -> neutral.add(r);
            }
        }

        if (mine != null) {
            tile(p, cw, realmTitle(mine), s.isLeader ? "You lead its diplomacy"
                    : (s.realmIsKingdom ? "Led by your king" : "Led by your ruler"),
                    realmIcon(mine)).accent(Ui.GOLD).badge("YOUR REALM", Ui.GOLD);
        }

        if (!s.proposals.isEmpty()) {
            section(p, cw, "Proposals");
            for (Row r : s.proposals) {
                boolean peace = r.b().equals("PEACE");
                Row from = realmRow(s, r.a());
                Tile t = tile(p, cw, from == null ? r.a() : realmTitle(from), "proposes " + proposalLabel(r.b()),
                        Icons.CHAT).accent(Ui.GOLD).badge(peace ? "PEACE" : "ALLIANCE", peace ? Ui.BLUE : Ui.GREEN)
                        .click(() -> openRealm(r.a()));
                t.chip("Accept", Ui.GREEN, () -> cmd("valoris diplomacy accept " + q(r.a())));
                t.chip("Decline", Ui.RED, () -> cmd("valoris diplomacy decline " + q(r.a())));
            }
        }

        if (!s.warCalls.isEmpty()) {
            section(p, cw, "Calls to arms");
            for (Row c : s.warCalls) callToArmsTile(p, cw, s, c);
        }

        // the conflicts (each with both coalitions; sieges live in its screen) - not to be confused with the
        // list of enemy realms below
        if (!s.wars.isEmpty()) {
            section(p, cw, "Wars (" + s.wars.size() + ")");
            for (Row w : s.wars) {
                String id = w.a();
                warTile(p, cw, s, w).click(() -> openWar(id));
            }
        }

        realmSection(p, cw, s, "Enemies", enemies, "ENEMY", Ui.RED);
        realmSection(p, cw, s, "Allies", allies, "ALLY", Ui.GREEN);
        realmSection(p, cw, s, "Neutral", neutral, "", Ui.GRAY);
        if (allies.isEmpty() && enemies.isEmpty() && neutral.isEmpty()) {
            note(p, cw, "No other kingdoms or cities yet.", Ui.GRAY, Icons.INFO);
        }
    }

    private void realmSection(Panel p, int cw, GuiState s, String title, List<Row> list, String badge, Color4I color) {
        if (list.isEmpty()) return;
        section(p, cw, title);
        for (Row r : list) {
            String offer = pending(s.sentProposals, r.a());
            String incoming = pending(s.proposals, r.a());
            Tile t = tile(p, cw, realmTitle(r), realmSub(r), realmIcon(r)).accent(color).click(() -> openRealm(r.a()));
            if (incoming != null) t.badge(incoming.equals("PEACE") ? "OFFERS PEACE" : "OFFERS ALLIANCE", Ui.GOLD);
            else if (offer != null) t.badge(offer.equals("PEACE") ? "PEACE OFFERED" : "ALLIANCE OFFERED", Ui.BLUE);
            else if (!badge.isEmpty()) t.badge(badge, color);
        }
    }

    private static String truceLeft(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : Math.max(1, m) + "m";
    }

    /** Everything about one other realm: relation, actions, its cities (maps, sieges). */
    private void realmPage(Panel p, int cw, GuiState s) {
        tile(p, cw, "Back to diplomacy", "", Icons.BACK).accent(Ui.GRAY).click(this::closeRealm);
        Row r = realmRow(s, realmView);
        if (r == null || r.m() == -1) {
            note(p, cw, "That realm no longer exists.", Ui.GRAY, Icons.INFO);
            return;
        }
        String name = r.a();
        String quoted = q(name);
        int rel = r.m();
        Color4I color = relationColor(rel);

        tile(p, cw, realmTitle(r), realmSub(r), realmIcon(r)).accent(color).badge(relationLabel(rel), color);
        stat(p, cw, isKingdom(r) ? "King" : "Ruler", r.b(), Ui.TEXT);
        Row truce = null;
        for (Row t : s.truces) if (t.a().equals(name)) truce = t;
        if (truce != null) stat(p, cw, "Truce", truceLeft(truce.n()) + " left", Ui.BLUE);

        String incoming = pending(s.proposals, name);
        String offer = pending(s.sentProposals, name);
        boolean leader = s.isLeader;

        if (incoming != null) {
            section(p, cw, "Their proposal");
            Tile t = tile(p, cw, "They propose " + proposalLabel(incoming),
                    incoming.equals("PEACE") ? "Peace between the leaders ends the war; otherwise the member leaves it"
                            : "Allies defend each other when attacked",
                    Icons.CHAT).accent(Ui.GOLD);
            if (leader) {
                t.chip("Accept", Ui.GREEN, () -> cmd("valoris diplomacy accept " + quoted));
                t.chip("Decline", Ui.RED, () -> cmd("valoris diplomacy decline " + quoted));
            }
        }

        // calls to arms from this realm
        for (Row c : s.warCalls) if (c.a().equals(name)) {
            section(p, cw, "Their call to arms");
            callToArmsTile(p, cw, s, c);
        }

        // wars that involve them
        List<Row> against = new ArrayList<>(), together = new ArrayList<>();
        for (Row w : s.wars) {
            if (inWar(s, w.a(), name, "THEIRS")) against.add(w);
            else if (inWar(s, w.a(), name, "OURS")) together.add(w);
        }
        if (!against.isEmpty()) {
            section(p, cw, "Wars against them");
            for (Row w : against) {
                String id = w.a();
                warTile(p, cw, s, w).click(() -> openWar(id));
            }
        }
        if (!together.isEmpty()) {
            section(p, cw, "Wars on our side");
            for (Row w : together) {
                String id = w.a();
                warTile(p, cw, s, w).click(() -> openWar(id));
            }
        }

        section(p, cw, "Diplomacy");
        if (!leader) {
            note(p, cw, s.realmIsKingdom ? "Only your king can make decisions here."
                    : "Only the ruler of your city can make decisions here.", Ui.GRAY, Icons.INFO);
        } else if (offer != null) {
            tile(p, cw, (offer.equals("PEACE") ? "Peace" : "Alliance") + " offered", "Waiting for their answer",
                    Icons.TIME).accent(Ui.BLUE)
                    .chip("Withdraw", Ui.GRAY, () -> cmd("valoris diplomacy withdraw " + quoted));
        }
        if (leader) {
            switch (rel) {
                case WAR -> {
                    boolean iLead = false, theyLead = false;
                    String theirLeader = null;
                    for (Row w : against) {
                        iLead |= leads(s, w.a(), s.realmName);
                        theyLead |= leads(s, w.a(), name);
                        theirLeader = w.c();
                    }
                    if (offer == null && incoming == null && (iLead || theyLead)) {
                        String title, sub;
                        if (iLead && theyLead) {
                            title = "Offer peace";
                            sub = "Ends the whole war for everyone on both sides";
                        } else if (iLead) {
                            title = "Offer them a separate peace";
                            sub = "They leave the war; it goes on with the others";
                        } else {
                            title = "Ask for a separate peace";
                            sub = "You leave the war; your allies fight on";
                        }
                        String msg = sub + ".";
                        tile(p, cw, title, sub, Icons.ACCEPT).accent(Ui.BLUE)
                                .click(() -> confirm(title + "?", msg, Ui.BLUE,
                                        () -> cmd("valoris diplomacy propose peace " + quoted)));
                    } else if (!iLead && !theyLead && theirLeader != null) {
                        String lead = theirLeader;
                        tile(p, cw, "Peace is made with the leaders", "Neither of you leads a side of this war. "
                                + "Talk to the leader of their side, the " + enemyTitle(s, lead) + ".", Icons.INFO)
                                .accent(Ui.GRAY).click(() -> openRealm(lead));
                    }
                }
                case ALLY -> {
                    tile(p, cw, "Call to arms", "Ask them to join one or more of your wars", ItemIcon.getItemIcon(Items.BELL)).accent(Ui.GOLD)
                            .click(() -> callAllyDialog(s, r));
                    for (Row c : s.sentCalls) if (c.a().equals(name)) {
                        tile(p, cw, "Called to the war against the " + enemyTitle(s, c.b()), "Waiting for their answer",
                                Icons.TIME).accent(Ui.BLUE);
                    }
                    tile(p, cw, "Break the alliance", "You both become neutral; wars you fight together go on", Icons.CANCEL)
                            .accent(Ui.ORANGE).click(() -> confirm("Break the alliance?",
                                    "Your alliance with the " + realmTitle(r) + " ends. They no longer defend you when "
                                            + "you are attacked.", Ui.ORANGE,
                                    () -> cmd("valoris diplomacy break " + quoted)));
                }
                default -> {
                    if (offer == null && incoming == null) {
                        tile(p, cw, "Propose an alliance", "Allies defend each other"
, Icons.FRIENDS)
                                .accent(Ui.GREEN).click(() -> cmd("valoris diplomacy propose alliance " + quoted));
                    }
                    if (truce != null) {
                        tile(p, cw, "Truce", "You made peace: no war with them for another " + truceLeft(truce.n()),
                                Icons.TIME).accent(Ui.BLUE);
                    } else {
                        tile(p, cw, "Declare war", "Call your allies to arms; their allies defend them",
                                ItemIcon.getItemIcon(Items.IRON_SWORD)).accent(Ui.RED).click(() -> declareWarDialog(s, r));
                    }
                }
            }
        }

        // at war: sieges live in the war's screen
        if (rel == WAR && !against.isEmpty()) {
            String warId = against.get(0).a();
            section(p, cw, "Sieges");
            tile(p, cw, "Plan a siege", "On the war's Siege tab",
                    Icons.BARRIER).accent(Ui.RED).click(() -> openWar(warId, WarScreen.View.SIEGE));
        }

        // their cities (the map shows what scouts can see; nothing about sieges here)
        section(p, cw, "Their cities");
        boolean canScout = rel == WAR || rel == ALLY;
        boolean any = false;
        for (Row c : s.cities) {
            if (!realmOfCity(c).equals(name)) continue;
            any = true;
            String city = c.a();
            Tile t = tile(p, cw, city, "Ruler " + c.b() + "  -  " + plural(c.n(), "resident", "residents")
                    + (canScout ? "  -  click for the map" : ""), ItemIcon.getItemIcon(Items.FILLED_MAP)).accent(color);
            if (canScout) t.click(() -> new CityMapScreen(city).openGui());
            else t.disabled();
        }
        if (!any) note(p, cw, "They have no cities.", Ui.GRAY, Icons.INFO);
        if (!canScout && any) note(p, cw, "Only allies and enemies can see their maps.", Ui.GRAY, Icons.INFO);
    }

    private Tile siegeTile(Panel p, int cw, GuiState s, Row r) {
        boolean attacking = r.a().equals(s.realmName);
        boolean prep = r.m() == 0;
        String title = (attacking ? "Attacking " : "Defending ") + r.b();
        String sub = (attacking ? "against " + r.c() : "attacked by " + r.a()) + "  -  "
                + (prep ? "begins in " : "ends in ") + Ui.time(r.n());
        Color4I c = attacking ? Ui.ORANGE : Ui.RED;
        return tile(p, cw, title, sub, Icons.BARRIER).accent(c).badge(prep ? "PREPARING" : "FIGHTING", prep ? Ui.GOLD : Ui.RED);
    }

    static boolean canBesiege(Row city) {
        int flags = city.m();
        return (flags & 1) != 0 && (flags & 2) == 0 && (flags & 4) == 0;
    }

    // ---- technology ----


    private void technology(Panel p, int cw, GuiState s) {
        if (s.techs.isEmpty()) {
            note(p, cw, "Technologies belong to a founded city. Found or join one to advance through the ages.",
                    Ui.BLUE, Icons.INFO);
            return;
        }
        tile(p, cw, "Open the technology tree", "Research new technologies for your city", Icons.BEACON)
                .accent(Ui.BLUE).click(() -> new TechTreeScreen().openGui());
    }

    // ---- skills ----

    // same order as com.valoris.skill.Skill
    private static final String[] SKILL_NAMES = {"Blacksmith", "Farmer", "Miner", "Hunter"};
    private static final String[] SKILL_HELP = {
            "Make gear at the Armory Table or crafting table, smelt metal - better materials give more",
            "Harvest ripe crops, flax, melons, pumpkins, cane, berries and herbs, breed animals",
            "Mine stone and ores with a pickaxe - rarer ores and harder stone give more",
            "Hunt wolf packs, bears and animals, and fish with a rod - bears give the most"};
    private static final int MAX_SKILL_LEVEL = com.valoris.skill.SkillCurve.MAX_LEVEL;

    private static Icon skillIcon(int i) {
        return switch (i) {
            case 0 -> ItemIcon.getItemIcon(Items.ANVIL);
            case 1 -> ItemIcon.getItemIcon(Items.WHEAT);
            case 2 -> ItemIcon.getItemIcon(Items.IRON_PICKAXE);
            default -> ItemIcon.getItemIcon(Items.BOW);
        };
    }

    private static Color4I skillColor(int i) {
        return switch (i) {
            case 0 -> Ui.ORANGE;
            case 1 -> Ui.GREEN;
            case 2 -> Ui.BLUE;
            default -> Color4I.rgb(0xb07de0);
        };
    }

    private void skills(Panel p, int cw, GuiState s) {
        int total = 0;
        for (int level : s.skillLevel) total += level;
        int max = MAX_SKILL_LEVEL * SKILL_NAMES.length;

        section(p, cw, "Professions");
        p.add(new Widgets.ProgressRow(p, cw, "Total level", total, max));
        if (s.skillLevel.length == 0) {
            note(p, cw, "Skill data has not arrived yet.", Ui.GRAY, Icons.INFO);
            return;
        }
        for (int i = 0; i < SKILL_NAMES.length && i < s.skillLevel.length; i++) {
            p.add(new SkillCard(p, cw, SKILL_NAMES[i], SKILL_HELP[i], skillIcon(i), skillColor(i),
                    s.skillLevel[i], MAX_SKILL_LEVEL, s.skillXp[i], s.skillNeed[i]));
        }
        note(p, cw, "Skills grow as you play. Early levels come quickly; level " + MAX_SKILL_LEVEL + " takes about two "
                + "weeks of regular play. Blocks you placed yourself, pets and mobs from spawners give little or no XP.",
                Ui.GRAY, Icons.INFO);
    }

    // ---- notice board ----

    private void notices(Panel p, int cw, GuiState s) {
        com.valoris.net.NoticeRow mine = s.notices.stream().filter(com.valoris.net.NoticeRow::mine).findFirst().orElse(null);
        if (isRuler(s)) {
            section(p, cw, "Your city's notice");
            tile(p, cw, mine == null ? "Pin a notice" : "Pin a new notice",
                    mine == null ? "One notice per city, it stays up for 24 hours"
                            : "It replaces \"" + mine.title() + "\"", ItemIcon.getItemIcon(Items.WRITABLE_BOOK))
                    .accent(Ui.GOLD)
                    .click(() -> Dialogs.notice(this, "", "", (title, body) -> postNotice(title, body)));
            if (mine != null) {
                tile(p, cw, "Take your notice down", "Removes \"" + mine.title() + "\" from the board", Icons.CANCEL)
                        .accent(Ui.RED)
                        .click(() -> Dialogs.confirm(this, "Take the notice down?",
                                "\"" + mine.title() + "\" will be removed from the board.", Ui.RED, () -> postNotice("", "")));
            }
        }
        section(p, cw, s.notices.isEmpty() ? "Notices" : plural(s.notices.size(), "notice", "notices"));
        p.add(new NoticeBoardWidget(p, cw, s.notices));
        if (!isRuler(s)) {
            note(p, cw, "Only city rulers can pin notices. Every city may have one notice on the board at a time; "
                    + "it disappears after 24 hours.", Ui.GRAY, Icons.INFO);
        }
    }

    private static void postNotice(String title, String body) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.valoris.net.NoticePostPayload(title, body));
        com.valoris.client.ClientState.scheduleRefresh();
    }
}
