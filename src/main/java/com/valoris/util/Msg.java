package com.valoris.util;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.valoris.data.ValorisData.Kingdom;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Msg {
    private Msg() {}

    public static MutableComponent text(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color);
    }

    public static void ok(ServerPlayer p, String s) { p.sendSystemMessage(text(s, ChatFormatting.GREEN)); }
    public static void info(ServerPlayer p, String s) { p.sendSystemMessage(text(s, ChatFormatting.GOLD)); }

    public static void toPlayer(MinecraftServer server, UUID id, Component c) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) p.sendSystemMessage(c);
    }

    public static void toTeam(UUID teamId, Component c) {
        FTBTeamsAPI.api().getManager().getTeamByID(teamId)
                .ifPresent(t -> t.getOnlineMembers().forEach(p -> p.sendSystemMessage(c)));
    }

    public static void toKingdom(Kingdom k, Component c) {
        for (UUID cityId : k.cities) toTeam(cityId, c);
    }

    public static void broadcast(MinecraftServer server, Component c) {
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? "unknown" : cache.get(id).map(GameProfile::getName).orElse("unknown");
    }

    /** A clickable chat button that runs a command. */
    public static MutableComponent button(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    public static String q(String name) {
        return "\"" + name + "\"";
    }
}
