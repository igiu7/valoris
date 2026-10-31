package com.valoris.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The tab list grouped by realm: a heading with the title of every kingdom or independent city (in its colour, with
 * its number of players online), its players below it, the players of no realm last ("Wanderers"); the own realm
 * comes first, then the biggest. The groups go one under the other; where the screen ends the list goes on in a
 * new column to the right (as many as the screen is wide, the rest counted as "+N more"). Realms and colours come
 * from chat.NameColors (NameColorsClient). Drawn in place of
 * the vanilla list by mixin.PlayerTabOverlayMixin; a server without Valoris or a list scoreboard keeps the vanilla one.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class RealmTabList {
    private RealmTabList() {}

    private static final String WANDERERS = "Wanderers";
    private static final int MAX_PLAYERS = 80, GAP = 5;
    private static final ResourceLocation[] PING = {
            ResourceLocation.withDefaultNamespace("icon/ping_unknown"), ResourceLocation.withDefaultNamespace("icon/ping_5"),
            ResourceLocation.withDefaultNamespace("icon/ping_4"), ResourceLocation.withDefaultNamespace("icon/ping_3"),
            ResourceLocation.withDefaultNamespace("icon/ping_2"), ResourceLocation.withDefaultNamespace("icon/ping_1")};

    private record Group(String title, int color, List<PlayerInfo> players) {}

    /** Returns false when the vanilla list should be drawn instead. */
    public static boolean render(GuiGraphics g, int width, PlayerTabOverlay overlay, @Nullable Component header,
                                 @Nullable Component footer) {
        Minecraft mc = Minecraft.getInstance();
        if (!NameColorsClient.received() || mc.player == null) return false;
        Font font = mc.font;

        List<PlayerInfo> infos = mc.player.connection.getListedOnlinePlayers().stream()
                .sorted(Comparator.<PlayerInfo>comparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
                        .thenComparing(p -> p.getProfile().getName(), String::compareToIgnoreCase))
                .limit(MAX_PLAYERS).toList();
        Map<String, Group> byTitle = new LinkedHashMap<>();
        for (PlayerInfo info : infos) {
            UUID id = info.getProfile().getId();
            String realm = NameColorsClient.realm(id);
            Integer rgb = NameColorsClient.color(id);
            String title = realm == null ? WANDERERS : realm;
            int color = realm == null ? 0xAAAAAA : rgb == null ? 0xFFFFFF : rgb;
            byTitle.computeIfAbsent(title, t -> new Group(t, color, new ArrayList<>())).players().add(info);
        }
        String own = NameColorsClient.realm(mc.player.getUUID());
        List<Group> groups = new ArrayList<>(byTitle.values());
        groups.sort(Comparator.<Group>comparingInt(gr -> gr.title().equals(own) ? 0 : gr.title().equals(WANDERERS) ? 2 : 1)
                .thenComparing(gr -> -gr.players().size())
                .thenComparing(Group::title, String::compareToIgnoreCase));

        boolean heads = mc.isLocalServer() || mc.getConnection().getConnection().isEncrypted();
        int nameWidth = 0;
        for (PlayerInfo info : infos) nameWidth = Math.max(nameWidth, font.width(overlay.getNameForDisplay(info)));
        int column = (heads ? 9 : 0) + nameWidth + 13;
        for (Group gr : groups) column = Math.max(column, font.width(heading(gr)) + 4);

        List<FormattedCharSequence> top = header == null ? null : font.split(header, width - 50);
        List<FormattedCharSequence> bottom = footer == null ? null : font.split(footer, width - 50);
        int y = 10;
        if (top != null) y += top.size() * 9 + 1;
        int bottomHeight = bottom == null ? 0 : bottom.size() * 9 + 2;
        int room = Math.max(HEADING + 9, g.guiHeight() - y - bottomHeight - 6);

        // groups one under the other; when the screen ends, the next column to the right (a long group goes on there
        // under its heading again)
        List<List<Line>> columns = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        int used = 0;
        for (Group gr : groups) {
            if (!current.isEmpty() && used + HEADING + 9 > room) {
                columns.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            current.add(new Line(gr, null));
            used += HEADING;
            for (PlayerInfo info : gr.players()) {
                if (used + 9 > room) {
                    columns.add(current);
                    current = new ArrayList<>();
                    current.add(new Line(gr, null));
                    used = HEADING;
                }
                current.add(new Line(gr, info));
                used += 9;
            }
            used += GROUP_GAP;
        }
        if (!current.isEmpty()) columns.add(current);

        // as many columns as the screen is wide; the players of the others are counted at the bottom
        int fit = Math.max(1, (width - 20 + GAP) / (column + GAP));
        int hidden = 0;
        while (columns.size() > fit) {
            for (Line line : columns.remove(columns.size() - 1)) if (line.player() != null) hidden++;
        }

        int grid = columns.size() * column + (columns.size() - 1) * GAP;
        int panel = grid;
        if (top != null) for (FormattedCharSequence line : top) panel = Math.max(panel, font.width(line));
        if (bottom != null) for (FormattedCharSequence line : bottom) panel = Math.max(panel, font.width(line));
        int height = 0;
        for (List<Line> col : columns) height = Math.max(height, height(col));
        if (hidden > 0) height += 10;

        if (top != null) text(g, font, top, width, 10);
        int left = width / 2 - panel / 2, gridLeft = width / 2 - grid / 2;
        g.fill(left - 1, y - 1, left + panel + 1, y + height - 2, Integer.MIN_VALUE);
        int cellColor = mc.options.getBackgroundColor(553648127);
        for (int c = 0; c < columns.size(); c++) {
            int x = gridLeft + c * (column + GAP), rowY = y;
            List<Line> col = columns.get(c);
            for (int i = 0; i < col.size(); i++) {
                Line line = col.get(i);
                if (line.player() == null) {
                    if (i > 0) rowY += GROUP_GAP;
                    Component h = heading(line.group());
                    g.drawString(font, h, x + column / 2 - font.width(h) / 2, rowY + 1, 0xFFFFFF);
                    g.fill(x + 1, rowY + 10, x + column - 1, rowY + 11, 0x40000000 | (line.group().color() & 0xFFFFFF));
                    rowY += HEADING;
                } else {
                    player(g, mc, overlay, line.player(), x, rowY, column, heads, cellColor);
                    rowY += 9;
                }
            }
        }
        int end = y + height;
        if (hidden > 0) {
            Component more = Component.literal("+" + hidden + " more").withColor(0xAAAAAA);
            g.drawString(font, more, width / 2 - font.width(more) / 2, end - 10, 0xFFFFFF);
        }
        if (bottom != null) text(g, font, bottom, width, end);
        return true;
    }

    private static final int HEADING = 12, GROUP_GAP = 3;

    /** A heading (player == null) or a player of a group, in a column. */
    private record Line(Group group, @Nullable PlayerInfo player) {}

    private static int height(List<Line> col) {
        int h = 0;
        for (int i = 0; i < col.size(); i++) {
            if (col.get(i).player() != null) h += 9;
            else h += HEADING + (i > 0 ? GROUP_GAP : 0);
        }
        return h + 1;
    }

    /** One player: head, name and ping on a cell like the vanilla list. */
    private static void player(GuiGraphics g, Minecraft mc, PlayerTabOverlay overlay, PlayerInfo info, int x, int y,
                               int cell, boolean heads, int cellColor) {
        g.fill(x, y, x + cell, y + 8, cellColor);
        RenderSystem.enableBlend();
        int nameX = x;
        if (heads) {
            GameProfile profile = info.getProfile();
            Player player = mc.level.getPlayerByUUID(profile.getId());
            boolean upsideDown = player != null && LivingEntityRenderer.isEntityUpsideDown(player);
            boolean hat = player != null && player.isModelPartShown(PlayerModelPart.HAT);
            PlayerFaceRenderer.draw(g, info.getSkin().texture(), x, y, 8, hat, upsideDown);
            nameX += 9;
        }
        g.drawString(mc.font, overlay.getNameForDisplay(info), nameX, y,
                info.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1);
        ping(g, info, x + cell - 11, y);
    }

    private static Component heading(Group gr) {
        return Component.literal(gr.title()).withColor(gr.color())
                .append(Component.literal("  (" + gr.players().size() + ")").withColor(0xAAAAAA));
    }

    /** Server header or footer lines, centred on a dark band like the vanilla ones. Returns the y below them. */
    private static int text(GuiGraphics g, Font font, List<FormattedCharSequence> lines, int width, int y) {
        int w = 0;
        for (FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
        g.fill(width / 2 - w / 2 - 1, y - 1, width / 2 + w / 2 + 1, y + lines.size() * 9, Integer.MIN_VALUE);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, width / 2 - font.width(line) / 2, y, -1);
            y += 9;
        }
        return y;
    }

    private static void ping(GuiGraphics g, PlayerInfo info, int x, int y) {
        int ms = info.getLatency();
        int i = ms < 0 ? 0 : ms < 150 ? 1 : ms < 300 ? 2 : ms < 600 ? 3 : ms < 1000 ? 4 : 5;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 100.0F);
        g.blitSprite(PING[i], x, y, 10, 8);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NameColorsClient.reset();
    }
}
