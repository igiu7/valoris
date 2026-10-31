package com.valoris.client.gui;

import java.util.ArrayDeque;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.valoris.client.ClientActions;
import com.valoris.client.ClientState;
import com.valoris.net.CityMapPayload;
import com.valoris.net.CityMapRequestPayload;
import com.valoris.net.GuiState;
import com.valoris.net.Row;
import com.valoris.valoris2;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Top-down survey of a city: walls in blue, buildings in green, the city banner in red on top of everything,
 * terrain shaded by height with contour lines. Scroll to zoom, drag to move, hover for details.
 * The king of an enemy kingdom can start a siege from here.
 */
public final class CityMapScreen extends BaseScreen {
    private static final int HEADER = 38;
    private static final int SIDE = 158;
    private static int textureCounter;

    private final String cityName;
    private CityMapPayload map;
    private ResourceLocation texture;
    private DynamicTexture dynamic;
    private int buildings, wallColumns, highestWall, tallestBuilding;
    private long requestedAt;

    // view: pixels per block and the position of the map's corner inside the view
    private float zoom = -1;
    private float panX, panY;

    public CityMapScreen(String cityName) {
        this.cityName = cityName;
        request();
    }

    private void request() {
        requestedAt = System.currentTimeMillis();
        PacketDistributor.sendToServer(new CityMapRequestPayload(cityName));
    }

    /** The survey arrived from the server. */
    public void receive(CityMapPayload payload) {
        if (!payload.city().equalsIgnoreCase(cityName)) return;
        releaseTexture();
        map = payload;
        zoom = -1;
        if (payload.error().isEmpty() && payload.width() > 0) {
            buildTexture();
            countStats();
        }
        refreshWidgets();
    }

    // ------------------------------------------------------------------ texture

    /** Colour of walls and buildings. */
    private static final int STRUCTURE = 0x5aa2ff;

    private static int abgr(int rgb, float light) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * light), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 255) * light), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * light), 0, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** What the map shows: walls and buildings are one kind of structure, roads and floors are drawn as terrain. */
    private static int shown(int raw) {
        return switch (raw) {
            case CityMapPayload.WALL -> CityMapPayload.BUILDING;
            case CityMapPayload.PAVED -> CityMapPayload.GROUND;
            default -> raw;
        };
    }

    private int type(int x, int z) {
        if (x < 0 || z < 0 || x >= map.width() || z >= map.depth()) return CityMapPayload.OUTSIDE;
        return shown(map.cells()[z * map.width() + x]);
    }

    private int ground(int x, int z) {
        return map.ground()[z * map.width() + x] & 255;
    }

    private int height(int x, int z) {
        return map.height()[z * map.width() + x] & 255;
    }

    private void buildTexture() {
        int w = map.width(), d = map.depth();
        int minG = 255, maxG = 0;
        for (int i = 0; i < w * d; i++) {
            if (map.cells()[i] == CityMapPayload.OUTSIDE) continue;
            minG = Math.min(minG, map.ground()[i] & 255);
            maxG = Math.max(maxG, map.ground()[i] & 255);
        }
        float range = Math.max(8, maxG - minG);
        NativeImage img = new NativeImage(w, d, true);
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int t = type(x, z);
                if (t == CityMapPayload.OUTSIDE) {
                    img.setPixelRGBA(x, z, 0);
                    continue;
                }
                float relief = 0.72F + 0.5F * ((ground(x, z) - minG) / range);
                // a darker edge where a structure meets something else gives crisp outlines
                boolean edge = type(x - 1, z) != t || type(x + 1, z) != t || type(x, z - 1) != t || type(x, z + 1) != t;
                int color = switch (t) {
                    case CityMapPayload.BUILDING -> abgr(STRUCTURE, (0.8F + 0.35F * Math.min(16, height(x, z)) / 16F) * (edge ? 0.7F : 1F));
                    case CityMapPayload.WATER -> abgr(0x27465f, 0.85F + 0.15F * relief);
                    default -> {
                        // terrain with a contour line every 4 blocks of height
                        boolean contour = x > 0 && z > 0 && type(x - 1, z) != CityMapPayload.OUTSIDE
                                && (ground(x, z) / 4 != ground(x - 1, z) / 4 || ground(x, z) / 4 != ground(x, z - 1) / 4);
                        yield abgr(0x51493a, relief * (contour ? 0.8F : 1F));
                    }
                };
                img.setPixelRGBA(x, z, color);
            }
        }
        dynamic = new DynamicTexture(img);
        texture = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "city_map/" + (textureCounter++));
        Minecraft.getInstance().getTextureManager().register(texture, dynamic);
    }

    private void releaseTexture() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(texture);
            texture = null;
            dynamic = null;
        }
    }

    private void countStats() {
        int w = map.width(), d = map.depth();
        boolean[] seen = new boolean[w * d];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        buildings = wallColumns = highestWall = tallestBuilding = 0;
        for (int i = 0; i < w * d; i++) {
            int t = shown(map.cells()[i]);
            int h = map.height()[i] & 255;
            if (t != CityMapPayload.BUILDING) continue;
            tallestBuilding = Math.max(tallestBuilding, h);
            if (seen[i]) continue;
            buildings++;
            seen[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int j = queue.poll();
                int x = j % w, z = j / w;
                int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] o : n) {
                    int nx = x + o[0], nz = z + o[1];
                    if (nx < 0 || nz < 0 || nx >= w || nz >= d) continue;
                    int k = nz * w + nx;
                    if (!seen[k] && shown(map.cells()[k]) == CityMapPayload.BUILDING) {
                        seen[k] = true;
                        queue.add(k);
                    }
                }
            }
        }
    }

    @Override
    public void onClosed() {
        super.onClosed();
        releaseTexture();
    }

    // ------------------------------------------------------------------ city info from the GUI state

    private Row cityRow() {
        for (Row c : ClientState.get().cities) if (c.a().equalsIgnoreCase(cityName)) return c;
        return null;
    }

    private boolean atWarWith(String realm) {
        for (Row r : ClientState.get().realms) if (r.a().equals(realm) && r.m() == 3) return true;
        return false;
    }

    /** Null when the player may besiege the city now, otherwise the reason why not. */
    private String besiegeBlocked() {
        GuiState s = ClientState.get();
        Row c = cityRow();
        if (c == null) return "Unknown city";
        String realm = HubScreen.realmOfCity(c);
        if (realm.equals(s.realmName)) return "This is your own city";
        if (!atWarWith(realm)) return "You are not at war with them";
        if (!s.isLeader) return s.realmIsKingdom ? "Only your " + s.rulerTitle + " can besiege" : "Only your ruler can besiege";
        for (Row r : s.sieges) {
            if (!r.a().equals(s.realmName)) continue;
            return r.b().equalsIgnoreCase(cityName) ? "You are already besieging it" : "You are besieging another city";
        }
        if (underSiege()) return null; // joins the running siege
        if ((c.m() & 4) != 0) return "Protected after a siege";
        if ((c.m() & 1) == 0) return "The city has no banner";
        return null;
    }

    private boolean underSiege() {
        Row c = cityRow();
        return c != null && (c.m() & 2) != 0;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(380, Math.min(560, sw - 12)), Math.max(240, Math.min(340, sh - 12)));
        return true;
    }

    private int mapX() { return 6; }
    private int mapY() { return HEADER + 6; }
    private int mapW() { return width - SIDE - 18; }
    private int mapH() { return height - HEADER - 12; }
    private int sideX() { return width - SIDE - 6; }

    @Override
    public void addWidgets() {
        Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui(false));
        close.setPos(width - 24, 11);
        add(close);
        Buttons.IconButton back = new Buttons.IconButton(this, Icons.LEFT, "Back", () -> closeGui(true));
        back.setPos(width - 44, 11);
        add(back);
        Buttons.IconButton refresh = new Buttons.IconButton(this, Icons.REFRESH, "Survey again", () -> {
            if (System.currentTimeMillis() - requestedAt > 2000) request();
        });
        refresh.setPos(width - 64, 11);
        add(refresh);

        MapView view = new MapView(this);
        view.setPosAndSize(mapX(), mapY(), mapW(), mapH());
        add(view);

        BesiegeButton besiege = new BesiegeButton(this);
        besiege.setPosAndSize(sideX() + 8, height - 32, SIDE - 16, 20);
        add(besiege);
    }

    @Override
    public void alignWidgets() {
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        Ui.RED.draw(g, x + 1, y + HEADER - 1, w - 2, 1);
        Ui.box(g, x + 9, y + 6, 26, 26, Ui.RED.withAlpha(50), Ui.RED);
        ItemIcon.getItemIcon(Items.FILLED_MAP).draw(g, x + 14, y + 11, 16, 16);
        Ui.text(g, theme, Component.literal(cityName).withStyle(ChatFormatting.BOLD), x + 44, y + 9, Ui.TEXT, true);
        Row c = cityRow();
        String sub = c == null ? "City survey" : (c.c().isEmpty() ? "Independent city" : ClientState.get().kingdomTitle(c.c()))
                + "  -  ruler " + c.b();
        Ui.text(g, theme, Ui.fit(theme, sub, w - 120), x + 44, y + 21, Ui.MUTED, false);
        drawSide(g, theme, x + sideX(), y + mapY(), SIDE, mapH());
    }

    private void legend(GuiGraphics g, Theme theme, int x, int y, Color4I color, String label) {
        Ui.box(g, x, y + 1, 8, 8, color, color.addBrightness(-0.2F));
        Ui.text(g, theme, label, x + 13, y + 1, Ui.TEXT, false);
    }

    private void stat(GuiGraphics g, Theme theme, int x, int y, int w, String label, String value) {
        Ui.text(g, theme, label, x, y, Ui.MUTED, false);
        Ui.textRight(g, theme, value, x + w, y, Ui.TEXT, true);
    }

    private void drawSide(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        Ui.box(g, x, y, w, h, Ui.CONTENT, Ui.BORDER);
        int ty = y + 8;
        Ui.text(g, theme, "LEGEND", x + 8, ty, Ui.MUTED, false);
        ty += 12;
        legend(g, theme, x + 8, ty, Ui.RED, "City banner"); ty += 11;
        legend(g, theme, x + 8, ty, Color4I.rgb(STRUCTURE), "Walls and buildings"); ty += 11;
        legend(g, theme, x + 8, ty, Color4I.rgb(0x51493a), "Terrain (lines every 4 blocks)"); ty += 11;
        legend(g, theme, x + 8, ty, Color4I.rgb(0x27465f), "Water"); ty += 15;

        if (map != null && map.error().isEmpty()) {
            Ui.text(g, theme, "SURVEY", x + 8, ty, Ui.MUTED, false);
            ty += 12;
            int sw = w - 16;
            stat(g, theme, x + 8, ty, sw, "Land", map.chunks() + " chunks"); ty += 10;
            stat(g, theme, x + 8, ty, sw, "Walls and buildings", String.valueOf(buildings)); ty += 10;
            if (buildings > 0) { stat(g, theme, x + 8, ty, sw, "Tallest", tallestBuilding + " high"); ty += 10; }
            stat(g, theme, x + 8, ty, sw, "Banner", map.hasBanner() ? map.bannerX() + ", " + map.bannerZ() : "missing");
            ty += 14;
        }

        Row c = cityRow();
        if (c != null) {
            String status;
            Color4I color;
            if ((c.m() & 2) != 0) { status = "Under siege"; color = Ui.ORANGE; }
            else if ((c.m() & 4) != 0) { status = "Protected after a siege"; color = Ui.BLUE; }
            else if ((c.m() & 1) == 0) { status = "No city banner"; color = Ui.GRAY; }
            else { status = "Can be besieged"; color = Ui.GOLD; }
            Ui.text(g, theme, Ui.fit(theme, status, w - 16), x + 8, ty, color, true);
        }
        Ui.text(g, theme, "Scroll: zoom  -  drag: move", x + 8, y + h - 44, Ui.MUTED.withAlpha(160), false);
    }

    // ------------------------------------------------------------------ the map

    private final class MapView extends Widget {
        private boolean dragging;

        MapView(Panel panel) {
            super(panel);
        }

        private float fitZoom() {
            return Math.min((width - 8) / (float) map.width(), (height - 8) / (float) map.depth());
        }

        private void ensureView() {
            if (zoom > 0 || map == null || map.width() == 0) return;
            zoom = fitZoom();
            panX = (width - map.width() * zoom) / 2F;
            panY = (height - map.depth() * zoom) / 2F;
        }

        private void clampPan() {
            float mw = map.width() * zoom, mh = map.depth() * zoom;
            panX = mw <= width ? (width - mw) / 2F : Mth.clamp(panX, width - mw - 20, 20);
            panY = mh <= height ? (height - mh) / 2F : Mth.clamp(panY, height - mh - 20, 20);
        }

        private boolean ready() {
            return map != null && map.error().isEmpty() && texture != null;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Ui.box(g, x - 1, y - 1, w + 2, h + 2, Color4I.rgb(0x10131a), Ui.BORDER);
            if (map == null) {
                theme.drawString(g, "Your scouts are surveying " + cityName + "...", x + w / 2, y + h / 2 - 4, Ui.MUTED, Theme.CENTERED);
                return;
            }
            if (!map.error().isEmpty()) {
                int ty = y + h / 2 - 10;
                for (FormattedText l : theme.listFormattedStringToWidth(Component.literal(map.error()), w - 30)) {
                    theme.drawString(g, l, x + w / 2, ty, Ui.RED, Theme.CENTERED);
                    ty += 10;
                }
                return;
            }
            if (!ready()) return;
            ensureView();
            GuiHelper.pushScissor(getWindow(), x, y, w, h);

            int mx = Math.round(x + panX), my = Math.round(y + panY);
            int mw = Math.round(map.width() * zoom), mh = Math.round(map.depth() * zoom);
            RenderSystem.enableBlend();
            g.blit(texture, mx, my, mw, mh, 0F, 0F, map.width(), map.depth(), map.width(), map.depth());

            // chunk grid
            if (zoom >= 1.5F) {
                Color4I line = Color4I.BLACK.withAlpha(45);
                for (int cx = 0; cx <= map.width(); cx += 16) line.draw(g, mx + Math.round(cx * zoom), my, 1, mh);
                for (int cz = 0; cz <= map.depth(); cz += 16) line.draw(g, mx, my + Math.round(cz * zoom), mw, 1);
            }

            // the banner, on top of everything
            if (map.hasBanner()) {
                int[] b = bannerRect(x, y);
                float pulse = 0.75F + 0.25F * Mth.sin((System.currentTimeMillis() % 1400L) / 1400F * Mth.TWO_PI);
                Color4I.BLACK.withAlpha(150).draw(g, b[0] - 2, b[1] - 2, b[2] + 4, b[2] + 4);
                Color4I.WHITE.withAlpha((int) (255 * pulse)).draw(g, b[0] - 1, b[1] - 1, b[2] + 2, b[2] + 2);
                Ui.RED.draw(g, b[0], b[1], b[2], b[2]);
            }

            // north arrow
            theme.drawString(g, "N", x + w - 10, y + 5, Ui.TEXT, Theme.CENTERED | Theme.SHADOW);
            Ui.TEXT.draw(g, x + w - 11, y + 14, 2, 6);
            GuiHelper.popScissor(getWindow());
        }

        /** x, y, size of the banner marker on screen (never smaller than 6 px). */
        private int[] bannerRect(int x, int y) {
            int size = Math.max(6, Math.round(zoom));
            float cx = x + panX + (map.bannerX() - map.originX() + 0.5F) * zoom;
            float cz = y + panY + (map.bannerZ() - map.originZ() + 0.5F) * zoom;
            return new int[]{Math.round(cx - size / 2F), Math.round(cz - size / 2F), size};
        }

        @Override
        public boolean mouseScrolled(double scroll) {
            if (!isMouseOver() || !ready()) return false;
            float old = zoom;
            float min = Math.min(fitZoom(), 1F);
            zoom = Mth.clamp(zoom * (scroll > 0 ? 1.25F : 0.8F), min, 10F);
            // keep the block under the cursor in place
            float mxl = getMouseX() - getX(), myl = getMouseY() - getY();
            panX = mxl - (mxl - panX) * (zoom / old);
            panY = myl - (myl - panY) * (zoom / old);
            clampPan();
            return true;
        }

        @Override
        public boolean mousePressed(MouseButton button) {
            if (isMouseOver() && ready()) {
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(int button, double dragX, double dragY) {
            if (!dragging) return false;
            panX += (float) dragX;
            panY += (float) dragY;
            clampPan();
            return true;
        }

        @Override
        public void mouseReleased(MouseButton button) {
            dragging = false;
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            if (!isMouseOver() || !ready()) return;
            int mx = getMouseX(), my = getMouseY();
            if (map.hasBanner()) {
                int[] b = bannerRect(getX(), getY());
                if (mx >= b[0] - 1 && my >= b[1] - 1 && mx <= b[0] + b[2] && my <= b[1] + b[2]) {
                    list.add(Component.literal("City banner").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    list.add(Component.literal("During the siege, hit it to bring it down and conquer the city")
                            .withStyle(ChatFormatting.GRAY));
                    list.add(Component.literal("X " + map.bannerX() + "  Z " + map.bannerZ()).withStyle(ChatFormatting.DARK_GRAY));
                    return;
                }
            }
            int bx = (int) Math.floor((mx - getX() - panX) / zoom);
            int bz = (int) Math.floor((my - getY() - panY) / zoom);
            int t = type(bx, bz);
            if (t == CityMapPayload.OUTSIDE) return;
            int h = height(bx, bz);
            switch (t) {
                case CityMapPayload.BUILDING -> {
                    list.add(Component.literal("Wall or building").withColor(STRUCTURE));
                    list.add(Component.literal(h + " blocks high").withStyle(ChatFormatting.GRAY));
                }
                case CityMapPayload.WATER -> list.add(Component.literal("Water").withColor(0x6fa8d6));
                default -> list.add(Component.literal("Open ground"));
            }
            int wx = map.originX() + bx, wz = map.originZ() + bz;
            list.add(Component.literal("X " + wx + "  Z " + wz + "  -  ground y " + (map.groundBase() + ground(bx, bz)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Red button that starts the siege (after a confirmation); grey with the reason when it is not possible. */
    private final class BesiegeButton extends Button {
        BesiegeButton(Panel panel) {
            super(panel, Component.literal("Besiege"), Icon.empty());
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            String reason = besiegeBlocked();
            boolean ok = reason == null;
            Color4I c = ok ? Ui.RED : Ui.GRAY;
            Ui.box(g, x, y, w, h, c.withAlpha(ok ? (isMouseOver() ? 255 : 200) : 60), c.withAlpha(ok ? 255 : 130));
            String label = ok ? (underSiege() ? "Join the siege of " : "Besiege ") + cityName : reason;
            theme.drawString(g, Ui.fit(theme, label, w - 8), x + w / 2, y + (h - 9) / 2 + 1, ok ? Ui.TEXT : Ui.MUTED,
                    Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (besiegeBlocked() != null) return;
            playClickSound();
            GuiState s = ClientState.get();
            boolean join = underSiege();
            Dialogs.confirm(CityMapScreen.this, (join ? "Join the siege of " : "Besiege ") + cityName + "?",
                    join ? "Your army fights alongside the realm that started the siege. If the banner falls, that "
                            + "realm takes the city; you share the victory."
                    : "The siege begins in " + s.siegePrepMinutes + " minutes and lasts " + s.siegeMinutes
                            + " minutes. Destroy the city banner to conquer the city. Other enemies of the city may "
                            + "join you. The whole server is told.",
                    Ui.RED, () -> {
                        ClientActions.command("valoris war siege " + ClientActions.q(cityName));
                        closeGui(true);
                    });
        }
    }
}
