package com.valoris.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The notice board: every city (also cities in a kingdom) may pin one notice, a title and a text. Only the city
 * ruler can post or take it down; a new notice replaces the old one; a notice disappears after 24 hours.
 */
public final class NoticeBoard {
    private NoticeBoard() {}

    public static final long LIFETIME_MS = 24L * 60 * 60 * 1000;
    public static final int TITLE_MAX = 40;
    public static final int BODY_MAX = 300;

    public static boolean active(City c) {
        return !c.noticeTitle.isEmpty() && System.currentTimeMillis() < c.noticeAt + LIFETIME_MS;
    }

    public static long secondsLeft(City c) {
        return Math.max(0, (c.noticeAt + LIFETIME_MS - System.currentTimeMillis()) / 1000);
    }

    /** Active notices, newest first; expired ones are cleared on the way. */
    public static List<City> notices(ValorisData data) {
        List<City> list = new ArrayList<>();
        for (City c : data.all()) {
            if (c.noticeTitle.isEmpty()) continue;
            if (!active(c)) {
                clear(c);
                data.setDirty();
                continue;
            }
            list.add(c);
        }
        list.sort(Comparator.comparingLong((City c) -> c.noticeAt).reversed());
        return list;
    }

    private static void clear(City c) {
        c.noticeTitle = "";
        c.noticeBody = "";
        c.noticeAt = 0;
    }

    private static String clean(String text, int max) {
        String t = text.replace('§', ' ').replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * Tells everybody on the server about a new notice: the city, who posted it and the title; hovering shows the text,
     * clicking [Read] opens the Valoris menu. A soft page-turn sound goes with it.
     */
    private static void announce(ServerPlayer author, City city, String title, String body) {
        var hover = new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n" + body).withStyle(ChatFormatting.WHITE)));
        Component news = Component.literal("[Notice Board] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("New notice from the City of " + city.name + " (" + author.getGameProfile().getName()
                        + "): ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(title).withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(ChatFormatting.WHITE).withHoverEvent(hover)))
                .append(Component.literal(" "))
                .append(Component.literal("[Read]").withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(ChatFormatting.GREEN).withHoverEvent(hover)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/valoris gui"))));
        for (ServerPlayer p : author.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(news);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    /** Post (or replace) the city's notice; empty title and text take it down. */
    public static void post(ServerPlayer player, String rawTitle, String rawBody) {
        var mute = com.valoris.moderation.ModerationService.mute(player.getServer(), player.getUUID());
        if (mute.isPresent()) { // a muted ruler cannot talk through the notice board either
            player.sendSystemMessage(com.valoris.moderation.ModerationService.mutedMessage(mute.get()));
            return;
        }
        ValorisData data = ValorisData.get(player.getServer());
        Optional<City> opt = CityService.cityOf(player);
        if (opt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Only a city ruler can use the notice board.").withStyle(ChatFormatting.RED));
            return;
        }
        City city = opt.get();
        String title = clean(rawTitle, TITLE_MAX);
        String body = clean(rawBody, BODY_MAX);
        if (title.isEmpty() && body.isEmpty()) {
            if (city.noticeTitle.isEmpty()) return;
            clear(city);
            data.setDirty();
            player.sendSystemMessage(Component.literal("Your notice was taken down.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (title.length() < 3 || body.isEmpty()) {
            player.sendSystemMessage(Component.literal("A notice needs a title (3+ characters) and a text.").withStyle(ChatFormatting.RED));
            return;
        }
        boolean replaced = active(city);
        city.noticeTitle = title;
        city.noticeBody = body;
        city.noticeAt = System.currentTimeMillis();
        data.setDirty();
        player.sendSystemMessage(Component.literal(replaced ? "Your old notice was replaced." : "Your notice is on the board for 24 hours.")
                .withStyle(ChatFormatting.GREEN));
        announce(player, city, title, body);
    }
}
