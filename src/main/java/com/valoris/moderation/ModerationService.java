package com.valoris.moderation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.moderation.ModerationData.Entry;
import com.valoris.moderation.ModerationData.Mute;
import com.valoris.util.Errors;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.UserBanListEntry;

/**
 * Moderation actions behind /vx. Bans go into the vanilla ban lists (banned-players.json / banned-ips.json), which the
 * server checks before a player can join and which expire on their own; mutes, freezes and the record of every
 * punishment are kept in ModerationData.
 */
public final class ModerationService {
    /** Kick, warn, mute, freeze, check, staff chat. */
    public static final int MOD_LEVEL = 2;
    /** Bans and IP bans. */
    public static final int ADMIN_LEVEL = 3;
    static final String NO_REASON = "No reason given";

    private ModerationService() {}

    // ------------------------------------------------------------------ helpers

    private static String actor(CommandSourceStack src) {
        return src.getTextName();
    }

    private static String reason(String r) {
        return r == null || r.isBlank() ? NO_REASON : r.trim();
    }

    /** True if the source may act against the target (see checkRank). */
    static boolean outranks(CommandSourceStack src, GameProfile target) {
        try {
            checkRank(src, target);
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    /** Staff may not punish themselves or anyone of the same or a higher permission level (the console may). */
    private static void checkRank(CommandSourceStack src, GameProfile target) throws CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayer actor)) return;
        if (actor.getUUID().equals(target.getId())) throw Errors.of("You cannot do that to yourself.");
        MinecraftServer server = src.getServer();
        if (server.getProfilePermissions(target) >= server.getProfilePermissions(actor.getGameProfile())) {
            throw Errors.of(target.getName() + " has the same or a higher staff rank than you.");
        }
    }

    public static boolean isStaff(ServerPlayer p) {
        return p.hasPermissions(MOD_LEVEL);
    }

    /** To every online staff member and the server log. */
    public static void toStaff(MinecraftServer server, Component msg) {
        Component line = Component.literal("[Staff] ").withStyle(ChatFormatting.DARK_AQUA).append(msg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (isStaff(p)) p.sendSystemMessage(line);
        valoris2.LOGGER.info("[Staff] {}", msg.getString());
    }

    private static Component text(String s, ChatFormatting c) {
        return Component.literal(s).withStyle(c);
    }

    private static String until(long until) {
        return until <= 0 ? "permanently" : "until " + Durations.date(until) + " (" + Durations.format(until - System.currentTimeMillis()) + ")";
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ModerationData d = ModerationData.get(server);
        String n = d.names.get(id);
        if (n != null) return n;
        return server.getProfileCache() == null ? id.toString()
                : server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
    }

    private static Component banScreen(String reason, long until) {
        MutableComponent c = Component.literal("You are banned from this server.\n\n").withStyle(ChatFormatting.RED);
        c.append(Component.literal("Reason: " + reason + "\n").withStyle(ChatFormatting.WHITE));
        c.append(Component.literal(until <= 0 ? "This ban is permanent." : "Your ban ends on " + Durations.date(until) + ".")
                .withStyle(ChatFormatting.GRAY));
        return c;
    }

    // ------------------------------------------------------------------ bans

    public static void ban(CommandSourceStack src, GameProfile target, long duration, String rawReason) throws CommandSyntaxException {
        checkRank(src, target);
        MinecraftServer server = src.getServer();
        String reason = reason(rawReason);
        long now = System.currentTimeMillis();
        long until = duration > 0 ? now + duration : 0;
        server.getPlayerList().getBans().add(new UserBanListEntry(target, new Date(now), actor(src),
                until > 0 ? new Date(until) : null, reason));
        ModerationData.get(server).log(target.getId(), new Entry(duration > 0 ? "TEMPBAN" : "BAN", reason, actor(src), now, duration));
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) {
            CombatTag.exempt(online.getUUID()); // banned by the staff: not an anti-logout case
            online.connection.disconnect(banScreen(reason, until));
        }
        toStaff(server, text(actor(src) + " banned " + target.getName() + " " + until(until) + ": " + reason, ChatFormatting.RED));
    }

    public static void unban(CommandSourceStack src, GameProfile target) throws CommandSyntaxException {
        var bans = src.getServer().getPlayerList().getBans();
        ModerationData md = ModerationData.get(src.getServer());
        boolean pending = md.combatLogged.remove(target.getId()); // anti-logout ban still waiting for the next join
        if (pending) md.setDirty();
        if (!bans.isBanned(target) && !pending) throw Errors.of(target.getName() + " is not banned.");
        bans.remove(target);
        ModerationData.get(src.getServer()).log(target.getId(), new Entry("UNBAN", "", actor(src), System.currentTimeMillis(), -1));
        toStaff(src.getServer(), text(actor(src) + " unbanned " + target.getName() + ".", ChatFormatting.GREEN));
    }

    /** An IP address, or the online / last known IP of a player name. */
    private static String resolveIp(MinecraftServer server, String target) throws CommandSyntaxException {
        if (target.matches("[0-9a-fA-F.:]+") && (target.contains(".") || target.contains(":"))) return target;
        ServerPlayer online = server.getPlayerList().getPlayerByName(target);
        if (online != null) return online.getIpAddress();
        ModerationData d = ModerationData.get(server);
        for (Map.Entry<UUID, String> e : d.names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(target) && d.lastIp.containsKey(e.getKey())) return d.lastIp.get(e.getKey());
        }
        throw Errors.of("No IP known for " + target + " (the player has not joined since the moderation system was added).");
    }

    public static void ipBan(CommandSourceStack src, String target, long duration, String rawReason) throws CommandSyntaxException {
        MinecraftServer server = src.getServer();
        String ip = resolveIp(server, target);
        String reason = reason(rawReason);
        long now = System.currentTimeMillis();
        long until = duration > 0 ? now + duration : 0;
        // never lock out a staff member (or yourself) sharing that address
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (ip.equals(p.getIpAddress()) && p.hasPermissions(MOD_LEVEL)) {
                throw Errors.of("Staff member " + p.getGameProfile().getName() + " uses that IP address.");
            }
        }
        server.getPlayerList().getIpBans().add(new IpBanListEntry(ip, new Date(now), actor(src),
                until > 0 ? new Date(until) : null, reason));
        ModerationData d = ModerationData.get(server);
        List<String> hit = new ArrayList<>();
        d.lastIp.forEach((id, addr) -> {
            if (addr.equals(ip)) {
                d.log(id, new Entry(duration > 0 ? "TEMPIPBAN" : "IPBAN", reason, actor(src), now, duration));
                hit.add(nameOf(server, id));
            }
        });
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            if (ip.equals(p.getIpAddress())) {
                CombatTag.exempt(p.getUUID());
                p.connection.disconnect(banScreen(reason, until));
            }
        }
        toStaff(server, text(actor(src) + " banned the IP of " + (hit.isEmpty() ? "?" : String.join(", ", hit)) + " "
                + until(until) + ": " + reason, ChatFormatting.RED));
    }

    public static void ipUnban(CommandSourceStack src, String target) throws CommandSyntaxException {
        MinecraftServer server = src.getServer();
        String ip = resolveIp(server, target);
        var bans = server.getPlayerList().getIpBans();
        if (!bans.isBanned(ip)) throw Errors.of("That IP address is not banned.");
        bans.remove(ip);
        toStaff(server, text(actor(src) + " lifted the IP ban of " + target + ".", ChatFormatting.GREEN));
    }

    // ------------------------------------------------------------------ mutes

    /** The active mute of a player (expired mutes are dropped here). */
    public static Optional<Mute> mute(MinecraftServer server, UUID id) {
        ModerationData d = ModerationData.get(server);
        Mute m = d.mutes.get(id);
        if (m == null) return Optional.empty();
        if (m.expired(System.currentTimeMillis())) {
            d.mutes.remove(id);
            d.setDirty();
            return Optional.empty();
        }
        return Optional.of(m);
    }

    public static Component mutedMessage(Mute m) {
        return text("You are muted " + until(m.until()) + ". Reason: " + m.reason(), ChatFormatting.RED);
    }

    public static void mute(CommandSourceStack src, GameProfile target, long duration, String rawReason) throws CommandSyntaxException {
        checkRank(src, target);
        MinecraftServer server = src.getServer();
        String reason = reason(rawReason);
        long now = System.currentTimeMillis();
        Mute m = new Mute(duration > 0 ? now + duration : 0, reason, actor(src), now);
        ModerationData d = ModerationData.get(server);
        d.mutes.put(target.getId(), m);
        d.log(target.getId(), new Entry(duration > 0 ? "TEMPMUTE" : "MUTE", reason, actor(src), now, duration));
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) online.sendSystemMessage(mutedMessage(m));
        toStaff(server, text(actor(src) + " muted " + target.getName() + " " + until(m.until()) + ": " + reason, ChatFormatting.GOLD));
    }

    public static void unmute(CommandSourceStack src, GameProfile target) throws CommandSyntaxException {
        MinecraftServer server = src.getServer();
        if (mute(server, target.getId()).isEmpty()) throw Errors.of(target.getName() + " is not muted.");
        ModerationData d = ModerationData.get(server);
        d.mutes.remove(target.getId());
        d.log(target.getId(), new Entry("UNMUTE", "", actor(src), System.currentTimeMillis(), -1));
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) online.sendSystemMessage(text("You are no longer muted.", ChatFormatting.GREEN));
        toStaff(server, text(actor(src) + " unmuted " + target.getName() + ".", ChatFormatting.GREEN));
    }

    // ------------------------------------------------------------------ kick, warn, freeze

    public static void kick(CommandSourceStack src, ServerPlayer target, String rawReason) throws CommandSyntaxException {
        checkRank(src, target.getGameProfile());
        String reason = reason(rawReason);
        ModerationData.get(src.getServer()).log(target.getUUID(), new Entry("KICK", reason, actor(src), System.currentTimeMillis(), -1));
        CombatTag.exempt(target.getUUID());
        target.connection.disconnect(text("You were kicked from the server.\n\nReason: " + reason, ChatFormatting.RED));
        toStaff(src.getServer(), text(actor(src) + " kicked " + target.getGameProfile().getName() + ": " + reason, ChatFormatting.GOLD));
    }

    public static void warn(CommandSourceStack src, GameProfile target, String rawReason) throws CommandSyntaxException {
        checkRank(src, target);
        MinecraftServer server = src.getServer();
        String reason = reason(rawReason);
        ModerationData d = ModerationData.get(server);
        d.log(target.getId(), new Entry("WARN", reason, actor(src), System.currentTimeMillis(), -1));
        long warns = d.history.getOrDefault(target.getId(), List.of()).stream().filter(e -> e.type().equals("WARN")).count();
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) {
            online.sendSystemMessage(text("You have been warned by the staff: " + reason, ChatFormatting.RED));
        }
        toStaff(server, text(actor(src) + " warned " + target.getName() + " (warning #" + warns + ")"
                + (online == null ? " - offline, will not see it" : "") + ": " + reason, ChatFormatting.YELLOW));
    }

    public static boolean isFrozen(MinecraftServer server, UUID id) {
        return ModerationData.get(server).frozen.contains(id);
    }

    public static void freeze(CommandSourceStack src, ServerPlayer target, boolean freeze) throws CommandSyntaxException {
        if (freeze) checkRank(src, target.getGameProfile());
        ModerationData d = ModerationData.get(src.getServer());
        String name = target.getGameProfile().getName();
        if (freeze ? !d.frozen.add(target.getUUID()) : !d.frozen.remove(target.getUUID())) {
            throw Errors.of(name + (freeze ? " is already frozen." : " is not frozen."));
        }
        d.log(target.getUUID(), new Entry(freeze ? "FREEZE" : "UNFREEZE", "", actor(src), System.currentTimeMillis(), -1));
        ModerationEvents.anchor(target, freeze);
        target.sendSystemMessage(freeze
                ? text("You have been frozen by the staff. Do not log out - talk to the staff in chat.", ChatFormatting.RED)
                : text("You are no longer frozen.", ChatFormatting.GREEN));
        toStaff(src.getServer(), text(actor(src) + (freeze ? " froze " : " unfroze ") + name + ".", ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------------ information

    public static void check(CommandSourceStack src, GameProfile target) {
        MinecraftServer server = src.getServer();
        ModerationData d = ModerationData.get(server);
        UUID id = target.getId();
        List<String> lines = new ArrayList<>();
        lines.add("== " + target.getName() + " ==");
        UserBanListEntry ban = server.getPlayerList().getBans().get(target);
        if (ban != null) {
            long until = ban.getExpires() == null ? 0 : ban.getExpires().getTime();
            lines.add("Banned " + until(until) + " by " + ban.getSource() + ": " + ban.getReason());
        } else lines.add("Not banned");
        String ip = d.lastIp.get(id);
        if (ip != null && server.getPlayerList().getIpBans().isBanned(ip)) lines.add("Their IP address is banned");
        lines.add(mute(server, id).map(m -> "Muted " + until(m.until()) + " by " + m.by() + ": " + m.reason()).orElse("Not muted"));
        if (d.frozen.contains(id)) lines.add("Frozen");
        if (d.combatLogged.contains(id)) lines.add("Left during combat: banned when they join again");
        List<Entry> hist = d.history.getOrDefault(id, List.of());
        lines.add("Record: " + hist.stream().filter(e -> e.type().equals("WARN")).count() + " warnings, "
                + hist.stream().filter(e -> e.type().contains("BAN") && !e.type().startsWith("UN")).count() + " bans, "
                + hist.stream().filter(e -> e.type().contains("MUTE") && !e.type().startsWith("UN")).count() + " mutes, "
                + hist.stream().filter(e -> e.type().equals("KICK")).count() + " kicks");
        List<String> alts = alts(server, id);
        if (!alts.isEmpty()) lines.add("Same IP as: " + String.join(", ", alts));
        if (ip != null && src.hasPermission(ADMIN_LEVEL)) lines.add("Last IP: " + ip);
        src.sendSuccess(() -> text(String.join("\n", lines), ChatFormatting.GOLD), false);
    }

    public static void history(CommandSourceStack src, GameProfile target) {
        List<Entry> hist = ModerationData.get(src.getServer()).history.getOrDefault(target.getId(), List.of());
        if (hist.isEmpty()) {
            src.sendSuccess(() -> text(target.getName() + " has a clean record.", ChatFormatting.GREEN), false);
            return;
        }
        StringBuilder sb = new StringBuilder("Record of " + target.getName() + " (last 15 of " + hist.size() + "):");
        for (Entry e : hist.subList(Math.max(0, hist.size() - 15), hist.size())) {
            sb.append("\n ").append(Durations.date(e.at())).append("  ").append(e.type());
            if (e.duration() > 0) sb.append(" ").append(Durations.format(e.duration()));
            sb.append(" by ").append(e.by());
            if (!e.reason().isEmpty()) sb.append(": ").append(e.reason());
        }
        src.sendSuccess(() -> text(sb.toString(), ChatFormatting.GOLD), false);
    }

    /** Other accounts last seen on the same IP address. */
    public static List<String> alts(MinecraftServer server, UUID id) {
        ModerationData d = ModerationData.get(server);
        String ip = d.lastIp.get(id);
        List<String> out = new ArrayList<>();
        if (ip == null) return out;
        d.lastIp.forEach((other, addr) -> {
            if (!other.equals(id) && addr.equals(ip)) out.add(nameOf(server, other));
        });
        return out;
    }

    public static void listActive(CommandSourceStack src, String what) {
        MinecraftServer server = src.getServer();
        List<String> out = new ArrayList<>();
        switch (what) {
            case "bans" -> {
                for (UserBanListEntry e : server.getPlayerList().getBans().getEntries()) {
                    long until = e.getExpires() == null ? 0 : e.getExpires().getTime();
                    if (until > 0 && until <= System.currentTimeMillis()) continue;
                    out.add(e.getDisplayName().getString() + " - " + until(until) + ": " + e.getReason());
                }
                for (IpBanListEntry e : server.getPlayerList().getIpBans().getEntries()) {
                    out.add("IP " + (src.hasPermission(ADMIN_LEVEL) ? e.getDisplayName().getString() : "(hidden)") + ": " + e.getReason());
                }
            }
            case "mutes" -> {
                ModerationData d = ModerationData.get(server);
                for (UUID id : List.copyOf(d.mutes.keySet())) {
                    mute(server, id).ifPresent(m -> out.add(nameOf(server, id) + " - " + until(m.until()) + ": " + m.reason()));
                }
            }
            default -> {
                for (UUID id : ModerationData.get(server).frozen) out.add(nameOf(server, id));
            }
        }
        String title = "Active " + what + " (" + out.size() + "):";
        src.sendSuccess(() -> text(title + (out.isEmpty() ? "\n none" : "\n " + String.join("\n ", out)), ChatFormatting.GOLD), false);
    }

    public static void staffChat(CommandSourceStack src, String message) {
        toStaff(src.getServer(), Component.literal(actor(src) + ": ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }

    public static void clearChat(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        Component blank = Component.literal(" ");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isStaff(p)) continue;
            for (int i = 0; i < 100; i++) p.sendSystemMessage(blank);
        }
        server.getPlayerList().broadcastSystemMessage(text("The chat was cleared by the staff.", ChatFormatting.GRAY), false);
    }
}
