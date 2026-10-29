package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.valoris.armory.ArmoryEntry;
import com.valoris.client.ClientArmory;
import com.valoris.net.ArmoryCraftPayload;

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
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Armory Table: pick a category and an era, select an item, see its stats and cost, craft it. */
public final class ArmoryScreen extends BaseScreen {
    // the "armor" category of the catalog is shown as four tabs, by the slot the piece is worn in ("armor:<slot>")
    private static final String[] CATS = {"weapons", "armor:head", "armor:chest", "armor:legs", "armor:feet", "horse",
            "shields", "ranged", "siege", "decorations"};
    private static final String[] CAT_NAMES = {"Weapons", "Helmets", "Chestplates", "Leggings", "Boots", "Horse",
            "Shields", "Ranged", "Siege Engines", "Decorations"};
    private static final String[] CAT_ICONS = {"magistuarmory:iron_bastardsword", "magistuarmory:armet",
            "magistuarmory:knight_chestplate", "magistuarmory:knight_leggings", "magistuarmory:knight_boots",
            "minecraft:iron_horse_armor", "magistuarmory:iron_heatershield", "magistuarmory:longbow",
            "siegemachines:catapult", "magistuarmory:big_plume_decoration"};
    private static final int CAT_STEP = 27;
    private static double sidebarScroll;
    private static final String[] ERAS = {"bronze", "iron", "steel", "gunpowder"};
    private static final Color4I ACCENT = Ui.ORANGE;

    private static final int HEADER = 38;
    private static final int SIDEBAR = 104;
    private static final int DETAIL = 172;
    private static final int SLOT = 22;
    private static final int GAP = 2;

    private static String lastCategory = "weapons";
    private static String lastEra = "bronze";

    private final BlockPos pos;
    private String category = lastCategory;
    private String era = lastEra;
    private String search = "";
    private ArmoryEntry selected;
    private GridPanel grid;
    private DetailPanel detail;

    public ArmoryScreen(BlockPos pos) {
        this.pos = pos;
        if (inCategory(category).isEmpty()) {
            for (String cat : CATS) if (!inCategory(cat).isEmpty()) { category = cat; break; }
        }
        fixEra();
    }

    // ------------------------------------------------------------------ data helpers

    /** Where an armor piece is worn: "head", "chest", "legs" or "feet" (chest when it cannot be told). */
    private static String armorSlot(ItemStack stack) {
        EquipmentSlot slot = null;
        Equipable eq = Equipable.get(stack);
        if (eq != null) slot = eq.getEquipmentSlot();
        if (slot == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            // pieces that are not plain armor items: the slot their armor points are given for
            for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
                if (e.slot() == EquipmentSlotGroup.HEAD) slot = EquipmentSlot.HEAD;
                else if (e.slot() == EquipmentSlotGroup.LEGS) slot = EquipmentSlot.LEGS;
                else if (e.slot() == EquipmentSlotGroup.FEET) slot = EquipmentSlot.FEET;
                else if (e.slot() == EquipmentSlotGroup.CHEST) slot = EquipmentSlot.CHEST;
            }
        }
        if (slot == null) return "chest";
        return switch (slot) {
            case HEAD -> "head";
            case LEGS -> "legs";
            case FEET -> "feet";
            default -> "chest";
        };
    }

    private static boolean inCat(ArmoryEntry e, String cat) {
        if (cat.startsWith("armor:")) return e.category().equals("armor") && armorSlot(e.result()).equals(cat.substring(6));
        return e.category().equals(cat);
    }

    private static List<ArmoryEntry> inCategory(String cat) {
        List<ArmoryEntry> out = new ArrayList<>();
        for (ArmoryEntry e : ClientArmory.entries()) if (inCat(e, cat)) out.add(e);
        return out;
    }

    private List<String> erasOf(String cat) {
        Set<String> present = new LinkedHashSet<>();
        for (String e : ERAS) {
            for (ArmoryEntry entry : inCategory(cat)) {
                if (entry.era().equals(e)) { present.add(e); break; }
            }
        }
        return new ArrayList<>(present);
    }

    private void fixEra() {
        List<String> eras = erasOf(category);
        if (!eras.isEmpty() && !eras.contains(era)) era = eras.get(0);
    }

    private List<ArmoryEntry> visible() {
        List<ArmoryEntry> out = new ArrayList<>();
        for (ArmoryEntry e : ClientArmory.entries()) {
            if (!inCat(e, category) || !e.era().equals(era)) continue;
            if (!search.isEmpty() && !e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)) continue;
            out.add(e);
        }
        return out;
    }

    private static int have(ArmoryEntry.Cost cost) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (cost.matches(s)) n += s.getCount();
        }
        return n;
    }

    private static boolean creative() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }

    private static boolean hasMaterials(ArmoryEntry e) {
        if (creative()) return true;
        for (ArmoryEntry.Cost c : e.cost()) if (have(c) < c.count()) return false;
        return true;
    }

    private boolean canCraft() {
        return selected != null && ClientArmory.knows(selected.tech()) && hasMaterials(selected);
    }

    private void select(ArmoryEntry e) {
        selected = e;
        if (detail != null) detail.setScrollY(0);
    }

    // ------------------------------------------------------------------ layout

    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(380, Math.min(500, sw - 16)), Math.max(230, Math.min(290, sh - 16)));
        return true;
    }

    private int gridX() { return SIDEBAR + 6; }
    private int gridY() { return HEADER + 26; }
    private int gridW() { return width - SIDEBAR - DETAIL - 22; }
    private int gridH() { return height - gridY() - 8; }
    private int detailX() { return width - DETAIL - 6; }
    /** The detail box: from level with the grid down to the bottom of the window. */
    private int detailY() { return gridY() - 2; }
    private int detailH() { return height - gridY() - 4; }
    /** Height of the strip at the bottom of the detail box that holds the craft button. */
    private static final int CRAFT_STRIP = 30;

    @Override
    public void addWidgets() {
        Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui());
        close.setPos(width - 24, 11);
        add(close);

        TextBox box = new TextBox(this) {
            @Override
            public void onTextChanged() {
                search = getText().trim().toLowerCase(Locale.ROOT);
                if (grid != null) grid.refreshWidgets();
            }
        };
        box.ghostText = "Search...";
        box.setText(search, false);
        box.setPosAndSize(width - 158, 12, 128, 14);
        add(box);

        // categories in a scrolling list (there are more than fit in a small window)
        int listY = HEADER + 6, listH = height - listY - 4;
        CategoryPanel cats = new CategoryPanel(this);
        cats.setPosAndSize(4, listY, SIDEBAR - 9, listH);
        add(cats);
        PanelScrollBar catBar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, cats);
        catBar.setPosAndSize(SIDEBAR - 4, listY, 3, listH);
        add(catBar);

        // era tabs: full names when they fit above the grid, otherwise short ones ("Gunpowder")
        List<String> eras = erasOf(category);
        int full = 0;
        for (String e : eras) full += Minecraft.getInstance().font.width(eraName(e, false)) + 17;
        boolean compact = full > width - gridX() - 34; // the strip above the detail panel is free too
        int x = gridX();
        for (String e : eras) {
            EraTab tab = new EraTab(this, e, eraName(e, compact));
            tab.setPos(x, HEADER + 5);
            add(tab);
            x += tab.width + 3;
        }

        grid = new GridPanel(this);
        grid.setPosAndSize(gridX(), gridY(), gridW(), gridH());
        add(grid);
        PanelScrollBar bar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, grid);
        bar.setPosAndSize(gridX() + gridW() + 2, gridY(), 6, gridH());
        add(bar);

        // item details and materials scroll; the craft button stays below them, always at the bottom
        int contentH = detailH() - CRAFT_STRIP - 1;
        detail = new DetailPanel(this);
        detail.setPosAndSize(detailX() + 1, detailY() + 1, DETAIL - 7, contentH);
        add(detail);
        PanelScrollBar detailBar = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, detail);
        detailBar.setPosAndSize(detailX() + DETAIL - 5, detailY() + 2, 3, contentH - 2);
        add(detailBar);

        CraftButton craft = new CraftButton(this);
        craft.setPosAndSize(detailX() + 8, detailY() + detailH() - CRAFT_STRIP + 5, DETAIL - 16, 20);
        add(craft);
    }

    @Override
    public void alignWidgets() {
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        ACCENT.draw(g, x + 1, y + HEADER - 1, w - 2, 1);
        Ui.SIDEBAR.draw(g, x + 1, y + HEADER, SIDEBAR, h - HEADER - 1);

        Ui.box(g, x + 9, y + 6, 26, 26, ACCENT.withAlpha(50), ACCENT);
        ItemIcon.getItemIcon("minecraft:anvil").draw(g, x + 14, y + 11, 16, 16);
        Ui.text(g, theme, Component.literal("Armory Table").withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
        Ui.text(g, theme, ClientArmory.eraNames().getOrDefault(era, era) + "  -  " + catName(category),
                x + 44, y + 21, ACCENT, false);

        Ui.CONTENT.draw(g, x + gridX() - 2, y + gridY() - 2, gridW() + 12, gridH() + 4);
        // the detail box starts level with the grid, below the era tabs; its content is the DetailPanel
        Ui.box(g, x + detailX(), y + detailY(), DETAIL, detailH(), Ui.CONTENT, Ui.BORDER);
        Ui.BORDER.draw(g, x + detailX() + 1, y + detailY() + detailH() - CRAFT_STRIP - 1, DETAIL - 2, 1);
    }

    private static String eraName(String id, boolean compact) {
        String name = ClientArmory.eraNames().getOrDefault(id, id);
        return compact ? name.replace(" Age", "").replace(" Era", "") : name;
    }

    private static String catName(String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return CAT_NAMES[i];
        return cat;
    }

    /**
     * The selected item: icon, name, era, stats, the missing technology and the materials, top to bottom. Draws at
     * x, y (unless g is null) and returns the height it takes, which the scrolling DetailPanel needs.
     */
    private int drawDetail(GuiGraphics g, Theme theme, int x, int y, int w) {
        if (selected == null) {
            List<FormattedText> lines = theme.listFormattedStringToWidth(
                    Component.literal("Select an item to see its statistics and the materials it needs."), w - 14);
            int ty = y + 9;
            for (FormattedText l : lines) {
                if (g != null) Ui.text(g, theme, l, x + 7, ty, Ui.MUTED, false);
                ty += 10;
            }
            return ty - y + 4;
        }
        ItemStack stack = selected.result();
        boolean known = ClientArmory.knows(selected.tech());

        if (g != null) {
            Ui.box(g, x + 5, y + 5, 36, 36, (known ? Ui.GREEN : Ui.GRAY).withAlpha(40), known ? Ui.GREEN : Ui.GRAY);
            ItemIcon.getItemIcon(stack).draw(g, x + 7, y + 7, 32, 32);
            int ty = y + 7;
            for (FormattedText l : theme.listFormattedStringToWidth(stack.getHoverName(), w - 52)) {
                if (ty > y + 29) break;
                Ui.text(g, theme, l, x + 47, ty, Ui.TEXT, true);
                ty += 10;
            }
            String count = stack.getCount() > 1 ? "  x" + stack.getCount() : "";
            Ui.text(g, theme, ClientArmory.eraNames().getOrDefault(selected.era(), selected.era()) + count,
                    x + 47, y + 32, ACCENT, false);
        }

        int ty = y + 47;
        for (String[] stat : stats(stack)) {
            if (g != null) {
                Ui.text(g, theme, stat[0], x + 7, ty, Ui.MUTED, false);
                Ui.textRight(g, theme, stat[1], x + w - 4, ty, Ui.TEXT, true);
            }
            ty += 11;
        }

        if (!known) {
            ty += 2;
            for (FormattedText l : theme.listFormattedStringToWidth(
                    Component.literal("Requires " + ClientArmory.techName(selected.tech())), w - 12)) {
                if (g != null) Ui.text(g, theme, l, x + 7, ty, Ui.RED, false);
                ty += 10;
            }
        }

        // materials, below the stats (the panel scrolls when they do not fit)
        int my = ty + 5;
        if (g != null) {
            Ui.text(g, theme, "MATERIALS", x + 7, my, Ui.MUTED, false);
            Ui.BORDER.draw(g, x + 7 + theme.getStringWidth("MATERIALS") + 6, my + 4, w - 17 - theme.getStringWidth("MATERIALS"), 1);
        }
        my += 12;
        for (ArmoryEntry.Cost c : selected.cost()) {
            if (g != null) {
                ItemStack shown = c.display();
                ItemIcon.getItemIcon(shown).draw(g, x + 7, my, 16, 16);
                int got = have(c);
                boolean enough = creative() || got >= c.count();
                String name = Ui.fit(theme, c.count() + "x " + shown.getHoverName().getString(), w - 66);
                Ui.text(g, theme, name, x + 27, my + 4, Ui.TEXT, false);
                Ui.textRight(g, theme, got + "/" + c.count(), x + w - 4, my + 4, enough ? Ui.GREEN : Ui.RED, true);
            }
            my += 18;
        }
        return my - y + 4;
    }

    /** Damage, attack speed, reach, armor... read from the item's attributes (era balance included). */
    private static List<String[]> stats(ItemStack stack) {
        List<String[]> out = new ArrayList<>();
        double damage = 0, speed = 0, reach = 0, armor = 0, toughness = 0, knockback = 0;
        boolean weapon = false, hasSpeed = false, isArmor = false;
        ItemAttributeModifiers mods = stack.getAttributeModifiers();
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
            double v = e.modifier().amount();
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE)) { damage += v; weapon = true; }
            else if (e.attribute().equals(Attributes.ATTACK_SPEED)) { speed += v; hasSpeed = true; }
            else if (e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)) reach += v;
            else if (e.attribute().equals(Attributes.ARMOR)) { armor += v; isArmor = true; }
            else if (e.attribute().equals(Attributes.ARMOR_TOUGHNESS)) { toughness += v; isArmor = true; }
            else if (e.attribute().equals(Attributes.KNOCKBACK_RESISTANCE)) knockback += v;
        }
        if (weapon) out.add(new String[]{"Attack damage", fmt(1 + damage)});
        if (hasSpeed) out.add(new String[]{"Attack speed", fmt(4 + speed)});
        if (reach != 0) out.add(new String[]{"Reach", (reach > 0 ? "+" : "") + fmt(reach)});
        if (isArmor) {
            out.add(new String[]{"Armor", fmt(armor)});
            if (toughness > 0) out.add(new String[]{"Toughness", fmt(toughness)});
            if (knockback > 0) out.add(new String[]{"Knockback res.", fmt(knockback * 10)});
        }
        if (stack.isDamageableItem()) out.add(new String[]{"Durability", String.valueOf(stack.getMaxDamage())});
        return out;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ widgets

    /** Era tab above the grid. */
    private final class EraTab extends Button {
        private final String eraId;

        EraTab(Panel panel, String eraId, String label) {
            super(panel, Component.literal(label), Icon.empty());
            this.eraId = eraId;
            setSize(Minecraft.getInstance().font.width(getTitle()) + 14, 16);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean on = eraId.equals(era);
            Ui.box(g, x, y, w, h, on ? ACCENT.withAlpha(200) : isMouseOver() ? Ui.TILE_HOVER : Ui.TILE,
                    on ? ACCENT : Ui.TILE_BORDER);
            theme.drawString(g, getTitle(), x + w / 2, y + 4, on ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | (on ? Theme.SHADOW : 0));
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            era = eraId;
            lastEra = eraId;
            selected = null;
            refreshWidgets();
        }
    }

    /** The category buttons, scrolling with the mouse wheel; keeps its scroll position when a category is picked. */
    private final class CategoryPanel extends Panel {
        CategoryPanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(CAT_STEP);
        }

        @Override
        public void addWidgets() {
            int y = 0;
            for (int i = 0; i < CATS.length; i++) {
                String cat = CATS[i];
                if (inCategory(cat).isEmpty()) continue;
                Buttons.SidebarButton b = new Buttons.SidebarButton(this, width, CAT_NAMES[i],
                        ItemIcon.getItemIcon(CAT_ICONS[i]), cat.equals(category), 0, ACCENT, () -> {
                    category = cat;
                    lastCategory = cat;
                    fixEra();
                    lastEra = era;
                    selected = null;
                    ArmoryScreen.this.refreshWidgets();
                });
                b.setPos(0, y);
                add(b);
                y += CAT_STEP;
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

    /** The scrolling grid of item slots. */
    private final class GridPanel extends Panel {
        GridPanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(SLOT + GAP);
        }

        @Override
        public void addWidgets() {
            for (ArmoryEntry e : visible()) add(new Slot(this, e));
        }

        @Override
        public void alignWidgets() {
            int cols = Math.max(1, (width - 2) / (SLOT + GAP));
            int i = 0;
            for (Widget w : widgets) {
                w.setPos(1 + (i % cols) * (SLOT + GAP), 1 + (i / cols) * (SLOT + GAP));
                i++;
            }
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            if (widgets.isEmpty()) {
                Ui.text(g, theme, search.isEmpty() ? "Nothing here." : "No matches.", x + 4, y + 4, Ui.MUTED, false);
            }
        }
    }

    /** One item in the grid. Green border when selected, dimmed with a lock when the technology is missing. */
    private final class Slot extends Button {
        private final ArmoryEntry entry;

        Slot(Panel panel, ArmoryEntry entry) {
            super(panel, entry.result().getHoverName(), ItemIcon.getItemIcon(entry.result()));
            this.entry = entry;
            setSize(SLOT, SLOT);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean sel = selected == entry;
            boolean known = ClientArmory.knows(entry.tech());
            Ui.box(g, x, y, w, h, isMouseOver() ? Ui.TILE_HOVER : Ui.TILE, sel ? Ui.GREEN : Ui.TILE_BORDER);
            if (sel) GuiHelper.drawHollowRect(g, x + 1, y + 1, w - 2, h - 2, Ui.GREEN, false);
            icon.draw(g, x + 3, y + 3, 16, 16);
            if (!known) {
                Color4I.BLACK.withAlpha(130).draw(g, x + 1, y + 1, w - 2, h - 2);
                Icons.LOCK.draw(g, x + w - 10, y + h - 10, 8, 8);
            } else if (entry.result().getCount() > 1) {
                theme.drawString(g, String.valueOf(entry.result().getCount()), x + w - 3, y + h - 10, Ui.TEXT,
                        Theme.SHADOW | Theme.CENTERED);
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            select(entry);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(entry.result().getHoverName());
            if (!ClientArmory.knows(entry.tech())) {
                list.add(Component.literal("Requires " + ClientArmory.techName(entry.tech())).withStyle(ChatFormatting.RED));
            } else if (!hasMaterials(entry)) {
                list.add(Component.literal("Missing materials").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** The selected item's details and materials, scrolling with the mouse wheel when they are taller than the box. */
    private final class DetailPanel extends Panel {
        DetailPanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(18);
        }

        @Override
        public void addWidgets() {
        }

        @Override
        public void alignWidgets() {
        }

        @Override
        public int getContentHeight() {
            return drawDetail(null, getTheme(), 0, 0, width);
        }

        @Override
        public int getContentWidth() {
            return width; // never scrolls sideways
        }

        @Override
        public void setScrollY(double scroll) {
            // stay within the content (it changes when another item is selected)
            super.setScrollY(Math.max(0, Math.min(scroll, getContentHeight() - height)));
        }

        @Override
        public void drawOffsetBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            drawDetail(g, theme, x, y, w);
        }
    }

    /** Green when the selected item can be crafted. */
    private final class CraftButton extends Button {
        CraftButton(Panel panel) {
            super(panel, Component.literal("Craft"), Icon.empty());
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean ok = canCraft();
            Color4I c = ok ? Ui.GREEN : Ui.GRAY;
            Ui.box(g, x, y, w, h, c.withAlpha(ok ? (isMouseOver() ? 255 : 200) : 70), c.withAlpha(ok ? 255 : 120));
            String label = selected == null ? "Craft"
                    : !ClientArmory.knows(selected.tech()) ? "Locked"
                    : !hasMaterials(selected) ? "Missing materials" : "Craft";
            theme.drawString(g, label, x + w / 2, y + (h - 9) / 2 + 1, ok ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (!canCraft()) return;
            playClickSound();
            PacketDistributor.sendToServer(new ArmoryCraftPayload(pos, selected.id()));
        }
    }
}
