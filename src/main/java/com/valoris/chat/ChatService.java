package com.valoris.chat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.moderation.ModerationService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Private messages (/msg, /r), emotes (/me), the city and kingdom chats (/cc, /kc - also as a chat mode) and
 * messages to a whole other city or kingdom (/cmsg, /kmsg). Muted players can use none of them; staff with
 * /vx spy see private and cross-realm messages.
 */
public final class ChatService {
    public enum Channel { PUBLIC, CITY, KINGDOM }

    /** player -> the last player they talked to privately (for /r) */
    private static final Map<UUID, UUID> LAST = new HashMap<>();
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Set<UUID> SPIES = new HashSet<>();

    private ChatService() {}

    static void forget(UUID player) {
        LAST.remove(player);
        CHANNELS.remove(player);
    }

    // ------------------------------------------------------------------ helpers

    private static void checkMute(ServerPlayer p) throws CommandSyntaxException {
        var mute = ModerationService.mute(p.getServer(), p.getUUID());
        if (mute.isPresent()) throw Errors.of(ModerationService.mutedMessage(mute.get()).getString());
    }

    /** A player's name; clicking it starts a private message to them. */
    private static MutableComponent name(ServerPlayer p, ChatFormatting color) {
        String n = p.getGameProfile().getName();
        return Component.literal(n).withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + n + " "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Message " + n))));
    }

    private static void spy(MinecraftServer server, ServerPlayer from, String to, String message) {
        Component line = Component.literal("[Spy] " + from.getGameProfile().getName() + " -> " + to + ": " + message)
                .withStyle(ChatFormatting.DARK_GRAY);
        for (UUID id : SPIES) {
            ServerPlayer s = server.getPlayerList().getPlayer(id);
            if (s != null && s != from && ModerationService.isStaff(s)) s.sendSystemMessage(line);
        }
        valoris2.LOGGER.info("[Message] {} -> {}: {}", from.getGameProfile().getName(), to, message);
    }

    private static City requireCity(ServerPlayer p) throws CommandSyntaxException {
        return Roles.cityOfMember(p).orElseThrow(() -> Errors.of("You do not belong to a city."));
    }

    private static Kingdom requireKingdom(ServerPlayer p) throws CommandSyntaxException {
        return Roles.kingdomOf(p).orElseThrow(() -> Errors.of("Your city is not in a kingdom."));
    }

    // ------------------------------------------------------------------ private messages

    public static void msg(ServerPlayer from, ServerPlayer to, String message) throws CommandSyntaxException {
        checkMute(from);
        if (from == to) throw Errors.of("You cannot message yourself.");
        // a vanished staff member is not online as far as players can tell
        if (com.valoris.moderation.Vanish.hiddenFrom(to, from)) throw Errors.of("No player was found");
        to.sendSystemMessage(Component.literal("[").withStyle(ChatFormatting.GRAY)
                .append(name(from, ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" -> you] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
        from.sendSystemMessage(Component.literal("[you -> ").withStyle(ChatFormatting.GRAY)
                .append(name(to, ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
        LAST.put(from.getUUID(), to.getUUID());
        LAST.put(to.getUUID(), from.getUUID());
        spy(from.getServer(), from, to.getGameProfile().getName(), message);
    }

    public static void reply(ServerPlayer from, String message) throws CommandSyntaxException {
        UUID last = LAST.get(from.getUUID());
        if (last == null) throw Errors.of("Nobody to reply to.");
        ServerPlayer to = from.getServer().getPlayerList().getPlayer(last);
        if (to == null) throw Errors.of("That player is no longer online.");
        msg(from, to, message);
    }

    public static void me(ServerPlayer from, String action) throws CommandSyntaxException {
        checkMute(from);
        Msg.broadcast(from.getServer(), Component.literal("* ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(name(from, ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" " + action).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)));
    }

    // ------------------------------------------------------------------ own city / kingdom

    public static void cityChat(ServerPlayer from, String message) throws CommandSyntaxException {
        checkMute(from);
        City city = requireCity(from);
        Msg.toTeam(city.teamId, Component.literal("[" + city.name + "] ").withStyle(ChatFormatting.GREEN)
                .append(name(from, ChatFormatting.WHITE))
                .append(Component.literal(": " + message).withStyle(ChatFormatting.GREEN)));
        valoris2.LOGGER.info("[City {}] {}: {}", city.name, from.getGameProfile().getName(), message);
    }

    public static void kingdomChat(ServerPlayer from, String message) throws CommandSyntaxException {
        checkMute(from);
        Kingdom k = requireKingdom(from);
        String city = Roles.cityOfMember(from).map(c -> c.name).orElse("?");
        Msg.toKingdom(k, Component.literal("[" + k.name + "] ").withStyle(ChatFormatting.GOLD)
                .append(name(from, ChatFormatting.WHITE))
                .append(Component.literal(" (" + city + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": " + message).withStyle(ChatFormatting.GOLD)));
        valoris2.LOGGER.info("[Kingdom {}] {}: {}", k.name, from.getGameProfile().getName(), message);
    }

    /** /cc or /kc without a message: normal chat goes to that channel until switched back. */
    public static void toggle(ServerPlayer p, Channel channel) throws CommandSyntaxException {
        if (channel == Channel.CITY) requireCity(p);
        if (channel == Channel.KINGDOM) requireKingdom(p);
        Channel now = CHANNELS.getOrDefault(p.getUUID(), Channel.PUBLIC) == channel ? Channel.PUBLIC : channel;
        if (now == Channel.PUBLIC) CHANNELS.remove(p.getUUID());
        else CHANNELS.put(p.getUUID(), now);
        Msg.info(p, switch (now) {
            case CITY -> "Your chat now goes to your city. Type /cc again for the public chat.";
            case KINGDOM -> "Your chat now goes to your kingdom. Type /kc again for the public chat.";
            default -> "Your chat is public again.";
        });
    }

    /** Called for normal chat: true if it was sent to the player's city or kingdom chat instead. */
    static boolean routeChannel(ServerPlayer p, String message) {
        Channel ch = CHANNELS.get(p.getUUID());
        if (ch == null) return false;
        try {
            if (ch == Channel.CITY) cityChat(p, message);
            else kingdomChat(p, message);
        } catch (CommandSyntaxException e) { // no longer in a city / kingdom
            CHANNELS.remove(p.getUUID());
            p.sendSystemMessage(Component.literal(e.getRawMessage().getString() + " Your chat is public again.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ another city / kingdom

    public static void toCity(ServerPlayer from, String cityName, String message) throws CommandSyntaxException {
        checkMute(from);
        ValorisData data = ValorisData.get(from.getServer());
        City city = data.cityByName(cityName).filter(c -> c.founded).orElseThrow(() -> Errors.of("No such city."));
        Component line = Component.literal("[").withStyle(ChatFormatting.DARK_GREEN)
                .append(name(from, ChatFormatting.WHITE))
                .append(Component.literal(" -> City of " + city.name + "] ").withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        Msg.toTeam(city.teamId, line);
        if (!Roles.cityOfMember(from).map(c -> c.teamId.equals(city.teamId)).orElse(false)) from.sendSystemMessage(line);
        spy(from.getServer(), from, "City of " + city.name, message);
    }

    public static void toKingdom(ServerPlayer from, String kingdomName, String message) throws CommandSyntaxException {
        checkMute(from);
        ValorisData data = ValorisData.get(from.getServer());
        Kingdom k = data.kingdomByName(kingdomName).orElseThrow(() -> Errors.of("No such kingdom."));
        Component line = Component.literal("[").withStyle(ChatFormatting.DARK_AQUA)
                .append(name(from, ChatFormatting.WHITE))
                .append(Component.literal(" -> Kingdom of " + k.name + "] ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        Msg.toKingdom(k, line);
        if (!Roles.kingdomOf(from).map(x -> x.id.equals(k.id)).orElse(false)) from.sendSystemMessage(line);
        spy(from.getServer(), from, "Kingdom of " + k.name, message);
    }

    // ------------------------------------------------------------------ staff

    /** /vx spy: see private messages and messages to other cities and kingdoms. */
    public static boolean toggleSpy(ServerPlayer staff) {
        if (SPIES.remove(staff.getUUID())) return false;
        SPIES.add(staff.getUUID());
        return true;
    }

    static Optional<Channel> channel(UUID id) {
        return Optional.ofNullable(CHANNELS.get(id));
    }
}
