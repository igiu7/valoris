package com.valoris.client.gui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.valoris.client.ClientState;
import com.valoris.net.GuiState;
import com.valoris.net.Row;
import com.valoris.net.WarInfo;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.ScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetLayout;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * One war side by side: our side (us and our allies) on the left, theirs on the right. Each side lists its
 * totals, then every realm with its cities. A second view shows the war's chronicle, a third its sieges: those
 * under way and the enemy cities to pick a target from (each opens the city's map, where the siege is started).
 */
public final class WarScreen extends BaseScreen {
    private static final int HEADER = 38;
    private static final int TABS = 20;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    public enum View { SIDES, CHRONICLE, SIEGE }

    private final String warId;
    private final Consumer<String> openRealm;
    private View view;

    public WarScreen(String warId, Consumer<String> openRealm) {
        this(warId, openRealm, View.SIDES);
    }

    public WarScreen(String warId, Consumer<String> openRealm, View view) {
        this.warId = warId;
        this.openRealm = openRealm;
        this.view = view;
    }

    private static GuiState state() {
        return ClientState.get();
    }

    private Row war() {
        for (Row r : state().wars) if (r.a().equals(warId)) return r;
        return null;
    }

    private WarInfo info() {
        for (WarInfo i : state().warInfos) if (i.warId().equals(warId)) return i;
        return null;
    }

    static String when(long epoch) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()).format(WHEN);
    }

    static String age(long since) {
        long minutes = Math.max(0, (System.currentTimeMillis() - since) / 60000L);
        long days = minutes / 1440, hours = (minutes % 1440) / 60, mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    private static Row realm(String name) {
        for (Row r : state().realms) if (r.a().equals(name)) return r;
        return null;
    }

    private static String title(String name) {
        Row r = realm(name);
        if (r == null) return name;
        return (r.c().equals("KINGDOM") ? "Kingdom of " : "City of ") + name;
    }

    private static List<Row> members(String warId, String side) {
        List<Row> out = new ArrayList<>();
        for (Row m : state().warMembers) if (m.a().equals(warId) && m.c().equals(side)) out.add(m);
        return out;
    }

    private static List<Row> citiesOf(String realm) {
        List<Row> out = new ArrayList<>();
        for (Row c : state().cities) if (HubScreen.realmOfCity(c).equals(realm)) out.add(c);
        return out;
    }

    private static Row stat(String warId, String realm) {
        for (Row r : state().warStats) if (r.a().equals(warId) && r.b().equals(realm)) return r;
        return null;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(400, Math.min(560, sw - 12)), Math.max(240, Math.min(330, sh - 12)));
        return true;
    }

    /** Called when new state arrived. */
    public void onStateChanged() {
        refreshWidgets();
    }

    @Override
    public void addWidgets() {
        Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui(false));
        close.setPos(width - 24, 11);
        add(close);
        Buttons.IconButton back = new Buttons.IconButton(this, Icons.LEFT, "Back", () -> closeGui(true));
        back.setPos(width - 44, 11);
        add(back);

        Row w = war();
        if (w == null || info() == null) return;

        int tx = 8;
        for (View v : View.values()) {
            String label = switch (v) {
                case SIDES -> "Sides";
                case CHRONICLE -> "Chronicle";
                case SIEGE -> "Siege" + (siegesOfWar().isEmpty() ? "" : " (" + siegesOfWar().size() + ")");
            };
            ViewTab tab = new ViewTab(this, label, v);
            tab.setPos(tx, HEADER + 4);
            add(tab);
            tx += tab.width + 4;
        }
        String enemyLeader = w.c();
        ViewTab diplomacy = new ViewTab(this, "Diplomacy with the " + title(enemyLeader), View.SIDES) {
            @Override
            public void onClicked(MouseButton button) {
                playClickSound();
                openRealm.accept(enemyLeader);
                closeGui(true);
            }

            @Override
            boolean active() {
                return false;
            }
        };
        diplomacy.setPos(width - 8 - diplomacy.width, HEADER + 4);
        add(diplomacy);

        int top = HEADER + TABS + 8;
        int h = height - top - 8;
        if (view == View.SIEGE) {
            Column sieges = new Column(this, false);
            sieges.siegeView = true;
            sieges.setPosAndSize(8, top, width - 26, h);
            add(sieges);
            PanelScrollBar bar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, sieges);
            bar.setPosAndSize(width - 16, top, 6, h);
            add(bar);
        } else if (view == View.CHRONICLE) {
            Column log = new Column(this, false);
            log.setPosAndSize(8, top, width - 26, h);
            add(log);
            PanelScrollBar bar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, log);
            bar.setPosAndSize(width - 16, top, 6, h);
            add(bar);
        } else {
            int colW = (width - 16 - 8 - 16) / 2;
            Column ours = new Column(this, true);
            ours.side = "OURS";
            ours.setPosAndSize(8, top, colW, h);
            add(ours);
            PanelScrollBar b1 = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, ours);
            b1.setPosAndSize(8 + colW + 1, top, 6, h);
            add(b1);
            Column theirs = new Column(this, true);
            theirs.side = "THEIRS";
            theirs.setPosAndSize(8 + colW + 16, top, colW, h);
            add(theirs);
            PanelScrollBar b2 = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, theirs);
            b2.setPosAndSize(8 + colW * 2 + 17, top, 6, h);
            add(b2);
        }
    }

    @Override
    public void alignWidgets() {
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        Ui.RED.draw(g, x + 1, y + HEADER - 1, w - 2, 1);
        Ui.box(g, x + 9, y + 6, 26, 26, Ui.RED.withAlpha(50), Ui.RED);
        ItemIcon.getItemIcon(Items.IRON_SWORD).draw(g, x + 14, y + 11, 16, 16);
        Row war = war();
        WarInfo info = info();
        if (war == null || info == null) {
            Ui.text(g, theme, Component.literal("War").withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
            Ui.text(g, theme, "This war is over.", x + 44, y + 21, Ui.GREEN, false);
            return;
        }
        boolean offensive = (war.n() & 1) != 0;
        boolean rebellion = (war.n() & 4) != 0;
        String heading = rebellion
                ? (offensive ? "Rebellion against the " : "Rebellion of the ") + title(war.c())
                : "War against the " + title(war.c());
        String kind = rebellion
                ? (offensive ? "Rebellion - we rose against our king" : "Rebellion - a city of ours rose against us")
                : (offensive ? "Offensive - we declared it" : "Defensive - we were attacked");
        Ui.text(g, theme, Component.literal(Ui.fit(theme, heading, w - 110))
                .withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
        Ui.text(g, theme, kind + "  -  since " + when(info.started()) + " (" + age(info.started()) + ")", x + 44, y + 21,
                rebellion ? Ui.GOLD : offensive ? Ui.ORANGE : Ui.BLUE, false);
    }

    // ------------------------------------------------------------------ widgets

    // ------------------------------------------------------------------ sieges of this war

    /** Realms on either side of this war. */
    private java.util.Set<String> warRealms() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Row m : state().warMembers) if (m.a().equals(warId)) out.add(m.b());
        return out;
    }

    /** Sieges between the two sides of this war. Row(attacker, city, defender, secondsLeft, phase). */
    private List<Row> siegesOfWar() {
        java.util.Set<String> realms = warRealms();
        List<Row> out = new ArrayList<>();
        for (Row s : state().sieges) if (realms.contains(s.a()) && realms.contains(s.c())) out.add(s);
        return out;
    }

    /** Sides / Chronicle / Siege switch, and the link to diplomacy. */
    private class ViewTab extends Button {
        private final View target;

        ViewTab(Panel panel, String label, View target) {
            super(panel, Component.literal(label), Icon.empty());
            this.target = target;
            setSize(net.minecraft.client.Minecraft.getInstance().font.width(label) + 14, 16);
        }

        boolean active() {
            return target == view;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean on = active();
            Ui.box(g, x, y, w, h, on ? Ui.RED.withAlpha(190) : isMouseOver() ? Ui.TILE_HOVER : Ui.TILE, on ? Ui.RED : Ui.TILE_BORDER);
            theme.drawString(g, getTitle(), x + w / 2, y + 4, on ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | (on ? Theme.SHADOW : 0));
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            view = target;
            refreshWidgets();
        }
    }

    /** A scrolling list: one side of the war, the chronicle or the sieges. */
    private final class Column extends Panel {
        private final boolean sides;
        String side = "OURS";
        boolean siegeView;

        Column(Panel parent, boolean sides) {
            super(parent);
            this.sides = sides;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(14);
        }

        @Override
        public void addWidgets() {
            int w = width - 8;
            if (siegeView) {
                addSieges(w);
                return;
            }
            if (!sides) {
                WarInfo info = info();
                if (info == null) return;
                if (info.logTexts().isEmpty()) add(new Line(this, w, "Nothing has happened yet.", "", Ui.MUTED, Ui.MUTED));
                for (int i = info.logTexts().size() - 1; i >= 0; i--) {
                    add(new Widgets.LogRow(this, w, when(info.logTimes().get(i)), info.logTexts().get(i)));
                }
                return;
            }
            boolean ours = side.equals("OURS");
            WarInfo info = info();
            List<Row> list = members(warId, side);
            int cities = 0, people = 0, land = 0;
            for (Row m : list) {
                for (Row c : citiesOf(m.b())) {
                    cities++;
                    people += c.n();
                    land += c.m() >> 8;
                }
            }
            add(new SideHeader(this, w, ours ? "OUR SIDE" : "THEIR SIDE", ours ? Ui.GREEN : Ui.RED));
            add(new Line(this, w, "Realms", String.valueOf(list.size()), Ui.MUTED, Ui.TEXT));
            add(new Line(this, w, "Cities", String.valueOf(cities), Ui.MUTED, Ui.TEXT));
            add(new Line(this, w, "Population", people + (people == 1 ? " resident" : " residents"), Ui.MUTED, Ui.TEXT));
            add(new Line(this, w, "Land", land + " chunks", Ui.MUTED, Ui.TEXT));
            if (info != null) {
                add(new Line(this, w, "Enemies slain", String.valueOf(ours ? info.ourKills() : info.theirKills()), Ui.MUTED, Ui.TEXT));
                add(new Line(this, w, "Fallen", String.valueOf(ours ? info.theirKills() : info.ourKills()), Ui.MUTED, Ui.TEXT));
                add(new Line(this, w, "Cities taken", String.valueOf(ours ? info.ourTaken() : info.theirTaken()), Ui.MUTED, Ui.TEXT));
                add(new Line(this, w, "Sieges held off", String.valueOf(ours ? info.ourRepelled() : info.theirRepelled()), Ui.MUTED, Ui.TEXT));
            }

            for (Row m : list) {
                add(new Spacer(this, w, 6));
                Row st = stat(warId, m.b());
                add(new RealmLine(this, w, m, ours));
                if (st != null && !st.c().isEmpty()) {
                    add(new Line(this, w, "  " + Character.toUpperCase(st.c().charAt(0)) + st.c().substring(1), "", Ui.MUTED, Ui.MUTED));
                }
                Row realm = realm(m.b());
                if (realm != null) add(new Line(this, w, "  " + (realm.c().equals("KINGDOM") ? "King" : "Ruler"), realm.b(), Ui.MUTED, Ui.TEXT));
                add(new Line(this, w, "  Slain / fallen", (st == null ? 0 : st.n()) + " / " + (st == null ? 0 : st.m()), Ui.MUTED, Ui.TEXT));
                for (Row c : citiesOf(m.b())) {
                    int flags = c.m();
                    String status = (flags & 2) != 0 ? "  - under siege" : (flags & 4) != 0 ? "  - protected" : "";
                    Color4I color = (flags & 2) != 0 ? Ui.ORANGE : Ui.TEXT;
                    add(new Line(this, w, "  • " + c.a() + status, c.n() + " res  -  " + (flags >> 8) + " ch", color, Ui.MUTED));
                }
            }
        }

        /** Sieges under way in this war, then every enemy city as a possible target. */
        private void addSieges(int w) {
            GuiState s = state();
            List<Row> running = siegesOfWar();
            add(new SideHeader(this, w, "SIEGES OF THIS WAR", Ui.ORANGE));
            if (running.isEmpty()) add(new Line(this, w, "No siege is under way.", "", Ui.MUTED, Ui.MUTED));
            for (Row r : running) add(new SiegeLine(this, w, r));

            add(new Spacer(this, w, 8));
            add(new SideHeader(this, w, "ENEMY CITIES - PICK A TARGET", Ui.RED));
            boolean any = false;
            for (Row m : members(warId, "THEIRS")) {
                for (Row c : citiesOf(m.b())) {
                    any = true;
                    add(new CityLine(this, w, c));
                }
            }
            if (!any) add(new Line(this, w, "The other side has no cities.", "", Ui.MUTED, Ui.MUTED));

            add(new Spacer(this, w, 8));
            add(new Line(this, w, "Click a city to see its walls, buildings and banner and start the siege there.", "",
                    Ui.MUTED, Ui.MUTED));
            add(new Line(this, w, "It begins " + s.siegePrepMinutes + " min later and lasts " + s.siegeMinutes
                    + " min; hit the banner to take the city.", "", Ui.MUTED, Ui.MUTED));
            add(new Line(this, w, "Everyone on our side of the war fights in our sieges.", "", Ui.MUTED, Ui.MUTED));
        }

        @Override
        public void alignWidgets() {
            align(new WidgetLayout.Vertical(2, 0, 4));
            for (Widget wd : widgets) wd.setX(4);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Ui.box(g, x - 1, y - 1, w + 2, h + 2, Ui.CONTENT, Ui.BORDER);
        }
    }

    private static final class Spacer extends Widget {
        Spacer(Panel panel, int w, int h) {
            super(panel);
            setSize(w, h);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        }
    }

    /** Coloured band naming the side. */
    private static final class SideHeader extends Widget {
        private final String text;
        private final Color4I color;

        SideHeader(Panel panel, int w, String text, Color4I color) {
            super(panel);
            this.text = text;
            this.color = color;
            setSize(w, 16);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            color.withAlpha(45).draw(g, x, y, w, h - 2);
            color.draw(g, x, y + h - 3, w, 1);
            theme.drawString(g, text, x + 5, y + 4, color, Theme.SHADOW);
        }
    }

    /** "Label ........ value" in one line, with a faint divider. */
    private static final class Line extends Widget {
        private final String label;
        private final String value;
        private final Color4I labelColor;
        private final Color4I valueColor;

        Line(Panel panel, int w, String label, String value, Color4I labelColor, Color4I valueColor) {
            super(panel);
            this.label = label;
            this.value = value;
            this.labelColor = labelColor;
            this.valueColor = valueColor;
            setSize(w, 12);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            int vw = value.isEmpty() ? 0 : Math.min(theme.getStringWidth(value), w / 2);
            Ui.text(g, theme, Ui.fit(theme, label, w - vw - 10), x + 2, y + 2, labelColor, false);
            if (!value.isEmpty()) Ui.textRight(g, theme, Ui.fit(theme, value, w / 2), x + w - 2, y + 2, valueColor, false);
            Ui.BORDER.withAlpha(45).draw(g, x, y + h - 1, w, 1);
        }
    }

    /** A siege of this war; click for the city's map. */
    private final class SiegeLine extends Button {
        private final Row siege;

        SiegeLine(Panel panel, int w, Row siege) {
            super(panel, Component.literal(siege.b()), Icon.empty());
            this.siege = siege;
            setSize(w, 24);
        }

        private boolean attacking() {
            return members(warId, "OURS").stream().anyMatch(m -> m.b().equals(siege.a()));
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean attack = attacking();
            boolean prep = siege.m() == 0;
            Color4I c = attack ? Ui.ORANGE : Ui.RED;
            Ui.box(g, x, y, w, h - 2, isMouseOver() ? Ui.TILE_HOVER : Ui.TILE, c.withAlpha(160));
            c.draw(g, x, y, 2, h - 2);
            Icons.BARRIER.draw(g, x + 6, y + 5, 12, 12);
            Ui.text(g, theme, Component.literal(Ui.fit(theme, (attack ? "Attacking " : "Defending ") + siege.b(), w - 110))
                    .withStyle(ChatFormatting.BOLD), x + 22, y + 3, Ui.TEXT, true);
            Ui.text(g, theme, Ui.fit(theme, (attack ? "against the " + title(siege.c()) : "attacked by the " + title(siege.a())),
                    w - 110), x + 22, y + 12, Ui.MUTED, false);
            String badge = prep ? "PREPARING" : "FIGHTING";
            int bw = theme.getStringWidth(badge) + 8;
            Ui.box(g, x + w - bw - 4, y + 3, bw, 10, (prep ? Ui.GOLD : Ui.RED).withAlpha(60), prep ? Ui.GOLD : Ui.RED);
            theme.drawString(g, badge, x + w - bw / 2 - 4, y + 4, prep ? Ui.GOLD : Ui.RED, Theme.CENTERED);
            Ui.textRight(g, theme, (prep ? "begins in " : "ends in ") + Ui.time(siege.n()), x + w - 4, y + 13, Ui.MUTED, false);
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            new CityMapScreen(siege.b()).openGui();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal("Map of " + siege.b()));
        }
    }

    /** An enemy city with its siege status; click for its map (and the Besiege button). */
    private final class CityLine extends Button {
        private final Row city;

        CityLine(Panel panel, int w, Row city) {
            super(panel, Component.literal(city.a()), Icon.empty());
            this.city = city;
            setSize(w, 16);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            int flags = city.m();
            String badge;
            Color4I c;
            if ((flags & 2) != 0) { badge = "UNDER SIEGE - JOIN"; c = Ui.ORANGE; }
            else if ((flags & 4) != 0) { badge = "PROTECTED"; c = Ui.BLUE; }
            else if ((flags & 1) == 0) { badge = "NO BANNER"; c = Ui.GRAY; }
            else { badge = "OPEN TARGET"; c = Ui.GOLD; }
            if (isMouseOver()) Ui.TILE_HOVER.draw(g, x, y, w, h);
            Ui.RED.draw(g, x, y + 2, 2, h - 4);
            ItemIcon.getItemIcon(Items.FILLED_MAP).draw(g, x + 5, y + 2, 12, 12);
            int bw = theme.getStringWidth(badge) + 8;
            Ui.text(g, theme, Ui.fit(theme, city.a() + "  -  " + city.n() + " res, " + (flags >> 8) + " ch", w - bw - 30),
                    x + 20, y + 4, Ui.TEXT, false);
            Ui.box(g, x + w - bw - 2, y + 2, bw, 12, c.withAlpha(60), c);
            theme.drawString(g, badge, x + w - bw / 2 - 2, y + 4, c, Theme.CENTERED);
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            new CityMapScreen(city.a()).openGui();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal("Map of " + city.a() + " - start or join the siege there"));
        }
    }

    /** A realm on a side; click to open the diplomacy with it. */
    private final class RealmLine extends Button {
        private final Row member;
        private final boolean ours;

        RealmLine(Panel panel, int w, Row member, boolean ours) {
            super(panel, Component.literal(member.b()), Icon.empty());
            this.member = member;
            this.ours = ours;
            setSize(w, 16);
        }

        private boolean me() {
            return member.b().equals(state().realmName);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Color4I c = ours ? Ui.GREEN : Ui.RED;
            if (isMouseOver() && !me()) Ui.TILE_HOVER.draw(g, x, y, w, h);
            c.draw(g, x, y + 2, 2, h - 4);
            Row realm = realm(member.b());
            Icon icon = realm != null && realm.c().equals("KINGDOM") ? Icons.GLOBE : ItemIcon.getItemIcon("valoris2:city_banner");
            icon.draw(g, x + 5, y + 2, 12, 12);
            String badge = member.n() == 1 ? "LEADS" : me() ? "YOU" : "";
            int bw = badge.isEmpty() ? 0 : theme.getStringWidth(badge) + 8;
            Ui.text(g, theme, Component.literal(Ui.fit(theme, title(member.b()), w - 26 - bw)).withStyle(ChatFormatting.BOLD),
                    x + 20, y + 4, Ui.TEXT, true);
            if (bw > 0) {
                Ui.box(g, x + w - bw - 2, y + 2, bw, 12, Ui.GOLD.withAlpha(60), Ui.GOLD);
                theme.drawString(g, badge, x + w - bw / 2 - 2, y + 4, Ui.GOLD, Theme.CENTERED);
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            if (me()) return;
            playClickSound();
            openRealm.accept(member.b());
            closeGui(true);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            if (!me()) list.add(Component.literal("Diplomacy with the " + title(member.b())));
        }
    }
}
