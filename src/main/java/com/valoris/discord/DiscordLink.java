package com.valoris.discord;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.valoris.Config;
import com.valoris.auth.Accounts;
import com.valoris.auth.Auth;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Discord bot of the server (DiscordBot): its status shows the players online, and it confirms new players.
 * On the first join (auth.Auth, before the password) the player types /discord <their Discord name>; the bot finds
 * that member on the Discord server (Config discord.guildId) and asks them in a DM whether the Minecraft player is
 * them. Only "Yes" lets the player register; the Discord account is saved with the Minecraft account (Accounts).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DiscordLink {
    private DiscordLink() {}

    private record Request(UUID player, String mcName, String discordId, String discordName, long expires) {}

    private static volatile DiscordBot bot;
    private static DiscordRoles roles;
    private static MinecraftServer server;
    private static String lastStatus = "";
    /** token (in the buttons) -> request */
    private static final Map<String, Request> REQUESTS = new ConcurrentHashMap<>();
    /** player -> when they may ask again (millis) */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final long ASK_EVERY_MS = 20_000, REQUEST_MS = 5 * 60_000;

    /** The bot's user id, or null (bot off or not online yet). */
    public static String botUserId() {
        DiscordBot b = bot;
        return b == null ? null : b.selfId();
    }

    /** True when new players must confirm their Discord account before they register. */
    public static boolean required() {
        return bot != null && Config.DISCORD_REQUIRE_LINK.get();
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        if (!Config.DISCORD_ENABLED.get() || !server.isDedicatedServer()) return;
        String token = Config.DISCORD_TOKEN.get().trim();
        if (token.isEmpty() || Config.DISCORD_GUILD.get().isBlank()) {
            LogUtils.getLogger().warn("Discord: discord.enabled is on, but discord.token or discord.guildId is empty. The bot stays off.");
            return;
        }
        bot = new DiscordBot(token, DiscordLink::onInteraction);
        roles = new DiscordRoles(bot, Config.DISCORD_GUILD.get().trim(), server);
        lastStatus = status();
        bot.setStatus(lastStatus);
        bot.start();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        DiscordBot b = bot;
        bot = null;
        roles = null;
        if (b != null) b.stop();
        REQUESTS.clear();
    }

    private static String status() {
        return Config.DISCORD_STATUS.get().replace("{online}", String.valueOf(server.getPlayerCount()))
                .replace("{max}", String.valueOf(server.getMaxPlayers()));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        DiscordBot b = bot;
        if (b == null || event.getServer().getTickCount() % 200 != 0) return;
        String s = status();
        if (!s.equals(lastStatus) && event.getServer().getTickCount() % 400 == 0) { // every 20 s at most: few presence updates
            lastStatus = s;
            b.setStatus(s);
        }
        long now = System.currentTimeMillis();
        REQUESTS.values().removeIf(r -> r.expires < now);
        DiscordRoles r = roles;
        if (r != null && Config.DISCORD_ROLES.get() && event.getServer().getTickCount() % 600 == 0) { // every 30 s
            r.sync(DiscordRoles.snapshot(event.getServer()));
        }
    }

    /** The player left: their open request is void. */
    public static void forget(UUID player) {
        REQUESTS.values().removeIf(r -> r.player.equals(player));
        COOLDOWN.remove(player);
    }

    private static void tell(UUID player, Component text) {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> {
            ServerPlayer p = s.getPlayerList().getPlayer(player);
            if (p != null) p.sendSystemMessage(text);
        });
    }

    private static Component red(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static Component notOnDiscord(String name) {
        String invite = Config.DISCORD_INVITE.get().trim();
        if (invite.isEmpty()) return red("Nobody named " + name + " is on our Discord server. Join it first, then try again.");
        return Component.literal("Nobody named " + name + " is on our Discord server. Join it first: ").withStyle(ChatFormatting.RED)
                .append(Component.literal(invite).withStyle(st -> st.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))))
                .append(red(" - then try again."));
    }

    // ------------------------------------------------------------------ news

    /** Posts a line to the news channel (Config discord.newsChannel). Nobody is pinged, whatever the names say. */
    public static void announce(String emoji, String text) {
        DiscordBot b = bot;
        String channel = Config.DISCORD_NEWS_CHANNEL.get().trim();
        if (b == null || channel.isEmpty()) return;
        JsonObject msg = new JsonObject();
        msg.addProperty("content", emoji + " " + escape(text));
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        msg.add("allowed_mentions", mentions);
        b.rest("POST", "/channels/" + channel + "/messages", msg).whenComplete((v, e) -> {
            if (e != null) LogUtils.getLogger().warn("Discord: cannot post to the news channel ({})", String.valueOf(e.getCause() != null ? e.getCause() : e));
        });
    }

    /** A message already in Discord formatting (player-typed parts escaped with plain), to the news channel. */
    public static void announceRaw(String content) {
        DiscordBot b = bot;
        String channel = Config.DISCORD_NEWS_CHANNEL.get().trim();
        if (b == null || channel.isEmpty()) return;
        JsonObject msg = new JsonObject();
        msg.addProperty("content", content);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        msg.add("allowed_mentions", mentions);
        b.rest("POST", "/channels/" + channel + "/messages", msg).whenComplete((v, e) -> {
            if (e != null) LogUtils.getLogger().warn("Discord: cannot post to the news channel ({})", String.valueOf(e.getCause() != null ? e.getCause() : e));
        });
    }

    /** Text with Discord formatting characters escaped. */
    public static String plain(String text) {
        return escape(text);
    }

    /** Names are typed by players: no Discord formatting from them. */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (char ch : text.toCharArray()) {
            if ("\\*_~`|>#[]()".indexOf(ch) >= 0) out.append('\\');
            out.append(ch);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ asking

    /** /discord <name>: find the member and ask them in a DM. */
    public static void ask(ServerPlayer player, String name) {
        DiscordBot b = bot;
        if (b == null) return;
        long now = System.currentTimeMillis();
        Long next = COOLDOWN.get(player.getUUID());
        if (next != null && now < next) {
            player.sendSystemMessage(red("Wait " + ((next - now) / 1000 + 1) + " s before asking again."));
            return;
        }
        COOLDOWN.put(player.getUUID(), now + ASK_EVERY_MS);
        String query = name.trim();
        if (query.startsWith("@")) query = query.substring(1);
        if (query.isEmpty() || query.length() > 32) {
            player.sendSystemMessage(red("Usage: /discord <your Discord name>"));
            return;
        }
        UUID uuid = player.getUUID();
        String mcName = player.getGameProfile().getName();
        String wanted = query;
        player.sendSystemMessage(Component.literal("Looking for " + wanted + " on our Discord...").withStyle(ChatFormatting.GRAY));
        String guild = Config.DISCORD_GUILD.get().trim();
        b.rest("GET", "/guilds/" + guild + "/members/search?limit=100&query=" + URLEncoder.encode(wanted, StandardCharsets.UTF_8), null)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LogUtils.getLogger().warn("Discord: member search failed", error);
                        tell(uuid, red("The Discord bot could not search the server right now. Try again in a moment."));
                        return;
                    }
                    List<JsonObject> found = match(result, wanted);
                    if (found.isEmpty()) { tell(uuid, notOnDiscord(wanted)); return; }
                    if (found.size() > 1) {
                        tell(uuid, red("Several members are called " + wanted + ". Use your unique Discord username (the one in your profile)."));
                        return;
                    }
                    JsonObject user = found.get(0).getAsJsonObject("user");
                    if (user.has("bot") && user.get("bot").getAsBoolean()) { tell(uuid, red("That is a bot.")); return; }
                    String id = user.get("id").getAsString();
                    String username = user.get("username").getAsString();
                    int limit = Config.DISCORD_ACCOUNTS_PER_MEMBER.get();
                    if (Accounts.countByDiscord(id) >= limit) {
                        tell(uuid, red("This Discord account has already confirmed " + (limit == 1 ? "another Minecraft name." : limit + " Minecraft names.")));
                        return;
                    }
                    sendQuestion(b, uuid, mcName, id, username);
                });
    }

    /** Members whose username is the name; failing that, whose display name or server nickname is. */
    private static List<JsonObject> match(JsonElement result, String name) {
        List<JsonObject> byUser = new ArrayList<>(), byDisplay = new ArrayList<>();
        if (result == null || !result.isJsonArray()) return byUser;
        for (JsonElement e : (JsonArray) result) {
            JsonObject m = e.getAsJsonObject();
            JsonObject u = m.getAsJsonObject("user");
            if (u.get("username").getAsString().equalsIgnoreCase(name)) byUser.add(m);
            else if (str(u, "global_name").equalsIgnoreCase(name) || str(m, "nick").equalsIgnoreCase(name)) byDisplay.add(m);
        }
        return byUser.isEmpty() ? byDisplay : byUser;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static void sendQuestion(DiscordBot b, UUID uuid, String mcName, String discordId, String discordName) {
        JsonObject dm = new JsonObject();
        dm.addProperty("recipient_id", discordId);
        b.rest("POST", "/users/@me/channels", dm).thenCompose(channel -> {
            String token = UUID.randomUUID().toString().replace("-", "");
            REQUESTS.put(token, new Request(uuid, mcName, discordId, discordName, System.currentTimeMillis() + REQUEST_MS));
            JsonObject msg = new JsonObject();
            msg.addProperty("content", "Someone is joining the Minecraft server as **" + mcName + "** and says they are you.\n"
                    + "Is **" + mcName + "** you?");
            JsonArray row = new JsonArray();
            row.add(button(3, "Yes, that's me", "valoris_link:yes:" + token));
            row.add(button(4, "No", "valoris_link:no:" + token));
            JsonObject actionRow = new JsonObject();
            actionRow.addProperty("type", 1);
            actionRow.add("components", row);
            JsonArray components = new JsonArray();
            components.add(actionRow);
            msg.add("components", components);
            return b.rest("POST", "/channels/" + channel.getAsJsonObject().get("id").getAsString() + "/messages", msg);
        }).whenComplete((v, error) -> {
            if (error == null) {
                tell(uuid, Component.literal("The bot sent a message to " + discordName
                        + " on Discord. Open your Discord DMs and press \"Yes, that's me\".").withStyle(ChatFormatting.GREEN));
                return;
            }
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof DiscordBot.ApiException api && api.status == 403) {
                tell(uuid, red("The bot cannot message " + discordName
                        + ". In Discord allow direct messages from members of our server (Privacy Settings), then try again."));
            } else {
                LogUtils.getLogger().warn("Discord: DM failed", error);
                tell(uuid, red("The Discord bot could not send the message. Try again in a moment."));
            }
            REQUESTS.values().removeIf(r -> r.player.equals(uuid) && r.discordId.equals(discordId));
        });
    }

    private static JsonObject button(int style, String label, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("type", 2);
        o.addProperty("style", style);
        o.addProperty("label", label);
        o.addProperty("custom_id", id);
        return o;
    }

    // ------------------------------------------------------------------ the answer

    /** A button click (gateway thread): answer Discord at once, then act on the server thread. */
    private static void onInteraction(JsonObject d) {
        DiscordBot b = bot;
        if (b == null || d.get("type").getAsInt() != 3) return; // message component
        String custom = d.getAsJsonObject("data").get("custom_id").getAsString();
        if (!custom.startsWith("valoris_link:")) return;
        String[] parts = custom.split(":");
        if (parts.length != 3) return;
        boolean yes = parts[1].equals("yes");
        JsonObject user = d.has("user") ? d.getAsJsonObject("user") : d.getAsJsonObject("member").getAsJsonObject("user");
        String clicker = user.get("id").getAsString();

        Request r = REQUESTS.get(parts[2]);
        String reply;
        if (r == null || r.expires < System.currentTimeMillis() || !r.discordId.equals(clicker)) {
            reply = "This question has expired. Ask again in Minecraft with /discord.";
        } else {
            REQUESTS.remove(parts[2]);
            reply = yes ? "Confirmed: **" + r.mcName + "** is you. Go back to Minecraft and choose your password."
                    : "Thanks - **" + r.mcName + "** will not be let in with your account.";
            MinecraftServer s = server;
            if (s != null) s.execute(() -> {
                ServerPlayer p = s.getPlayerList().getPlayer(r.player);
                if (p == null) return;
                if (yes) Auth.discordConfirmed(p, r.discordId, r.discordName);
                else p.sendSystemMessage(red(r.discordName + " said on Discord that this is not them."));
            });
        }
        JsonObject data = new JsonObject();
        data.addProperty("content", reply);
        data.add("components", new JsonArray());
        JsonObject response = new JsonObject();
        response.addProperty("type", 7); // update the message: the buttons go away
        response.add("data", data);
        b.restNow("POST", "/interactions/" + d.get("id").getAsString() + "/" + d.get("token").getAsString() + "/callback", response)
                .whenComplete((v, e) -> {
                    if (e != null) LogUtils.getLogger().warn("Discord: interaction reply failed", e);
                });
    }
}
