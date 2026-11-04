package com.valoris.discord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.valoris.Config;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.data.ValorisData.Siege;
import com.valoris.data.ValorisData.SiegePhase;
import com.valoris.net.PresencePayload;
import com.valoris.valoris2;
import com.valoris.war.SiegeService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells every (logged-in) player what their Discord Rich Presence shows (client.RichPresence): their realm and city,
 * their title, a siege they fight in, and the players online. Sent when it changes, checked every 10 seconds.
 * The Discord application is Config discord.applicationId, or the bot's own (the same id for bots made since 2016).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class PresenceSender {
    private PresenceSender() {}

    private static final Map<UUID, PresencePayload> SENT = new ConcurrentHashMap<>();

    private static String appId() {
        String id = Config.DISCORD_APP_ID.get().trim();
        if (!id.isEmpty()) return id;
        return DiscordLink.botUserId();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 100 || !Config.DISCORD_PRESENCE.get()) return;
        String app = appId();
        if (app == null || app.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!com.valoris.auth.Auth.isLoggedIn(p)) continue;
            PresencePayload now = build(server, p, app);
            if (now.equals(SENT.get(p.getUUID()))) continue;
            SENT.put(p.getUUID(), now);
            PacketDistributor.sendToPlayer(p, now);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private static PresencePayload build(MinecraftServer server, ServerPlayer p, String app) {
        ValorisData data = ValorisData.get(server);
        Optional<City> city = Roles.cityOfMember(p);
        Optional<Kingdom> kingdom = Roles.kingdomOf(p);
        String details;
        if (kingdom.isPresent() && city.isPresent()) details = kingdom.get().title() + " · " + city.get().name;
        else if (city.isPresent()) details = "Free city of " + city.get().name;
        else details = "A wanderer";

        String state = Roles.titleOf(p);
        for (Siege s : data.sieges()) {
            if (s.phase != SiegePhase.ACTIVE || !SiegeService.isParticipant(p, s)) continue;
            state = (SiegeService.isAttacker(p, s) ? "Besieging " : "Defending ") + s.cityName;
            break;
        }
        if (state.equals(Roles.titleOf(p)) && kingdom.isPresent() && !data.warsOf(kingdom.get().id).isEmpty()) {
            state += " · at war";
        }
        return new PresencePayload(app, Config.DISCORD_PRESENCE_IMAGE.get().trim(), cut(details), cut(state),
                server.getPlayerCount(), server.getMaxPlayers());
    }

    /** Discord takes at most 128 characters per line. */
    private static String cut(String s) {
        return s.length() > 128 ? s.substring(0, 127) + "…" : s;
    }
}
