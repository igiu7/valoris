package com.valoris.moderation;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.moderation.ModerationData.Entry;
import com.valoris.net.CombatTagPayload;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Anti-logout. Taking damage (config antiLogout.trigger) holds a player in combat for antiLogout.seconds; the client
 * greys out its Disconnect button meanwhile. A player who leaves anyway - closing the game, losing the connection or
 * any other way - is banned for antiLogout.banMinutes, counted from the moment they join again. Server shutdowns,
 * staff kicks / bans and death do not count.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class CombatTag {
    /** player -> epoch millis when they may leave */
    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    /** players being disconnected by the server itself (kick, ban): leaving is not their doing */
    private static final Set<UUID> EXEMPT = new HashSet<>();
    private static boolean stopping;

    private CombatTag() {}

    /** Called before the server disconnects a player on purpose. */
    public static void exempt(UUID player) {
        EXEMPT.add(player);
    }

    public static long remaining(UUID player) {
        Long until = UNTIL.get(player);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    private static void send(ServerPlayer p, long millis) {
        PacketDistributor.sendToPlayer(p, new CombatTagPayload(millis));
    }

    private static void tag(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        long millis = Config.ANTI_LOGOUT_SECONDS.get() * 1000L;
        boolean fresh = remaining(p.getUUID()) == 0;
        UNTIL.put(p.getUUID(), System.currentTimeMillis() + millis);
        send(p, millis);
        if (fresh) {
            p.displayClientMessage(Component.literal("In combat! Do not log out for " + Config.ANTI_LOGOUT_SECONDS.get()
                    + " seconds.").withStyle(ChatFormatting.RED), true);
        }
    }

    private static boolean triggers(DamageSource source) {
        return switch (Config.ANTI_LOGOUT_TRIGGER.get()) {
            case "player" -> source.getEntity() instanceof Player;
            case "entity" -> source.getEntity() != null;
            default -> true;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!Config.ANTI_LOGOUT_ENABLED.get() || event.getNewDamage() <= 0) return;
        if (!(event.getEntity() instanceof ServerPlayer victim) || !triggers(event.getSource())) return;
        tag(victim);
        if (Config.ANTI_LOGOUT_TAG_ATTACKER.get() && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != victim) {
            tag(attacker);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && UNTIL.remove(p.getUUID()) != null) send(p, 0);
    }

    /** Once a second: tell players when they are free to leave. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0 || UNTIL.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                send(p, 0);
                p.displayClientMessage(Component.literal("You are out of combat and may log out safely.")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    // ------------------------------------------------------------------ leaving during combat

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        boolean exempt = EXEMPT.remove(id);
        long left = remaining(id);
        UNTIL.remove(id);
        if (left <= 0 || exempt || stopping || !Config.ANTI_LOGOUT_ENABLED.get() || p.isDeadOrDying()) return;
        MinecraftServer server = p.getServer();
        ModerationData d = ModerationData.get(server);
        d.combatLogged.add(id);
        d.setDirty();
        ModerationService.toStaff(server, Component.literal(p.getGameProfile().getName() + " logged out during combat ("
                + (left / 1000) + " s left) and will be banned for " + Config.ANTI_LOGOUT_BAN_MINUTES.get()
                + " minutes when they come back.").withStyle(ChatFormatting.RED));
    }

    /** The ban starts now, when the player who left during combat comes back. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        send(p, 0);
        MinecraftServer server = p.getServer();
        ModerationData d = ModerationData.get(server);
        if (!d.combatLogged.remove(p.getUUID())) return;
        d.setDirty();
        long now = System.currentTimeMillis();
        long millis = Config.ANTI_LOGOUT_BAN_MINUTES.get() * 60_000L;
        String reason = "Logged out during combat (anti-logout)";
        server.getPlayerList().getBans().add(new UserBanListEntry(p.getGameProfile(), new Date(now), "Anti-logout",
                new Date(now + millis), reason));
        d.log(p.getUUID(), new Entry("COMBATLOG", reason, "Anti-logout", now, millis));
        exempt(p.getUUID());
        p.connection.disconnect(Component.literal("You logged out during combat.\n\n").withStyle(ChatFormatting.RED)
                .append(Component.literal("You are banned for " + Config.ANTI_LOGOUT_BAN_MINUTES.get()
                        + " minutes (until " + Durations.date(now + millis) + ").").withStyle(ChatFormatting.WHITE)));
        ModerationService.toStaff(server, Component.literal(p.getGameProfile().getName() + " came back and was banned for "
                + Config.ANTI_LOGOUT_BAN_MINUTES.get() + " minutes (anti-logout).").withStyle(ChatFormatting.GOLD));
    }

    /** Vanilla /kick and /ban by the staff are not an anti-logout case either. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommand(net.neoforged.neoforge.event.CommandEvent event) {
        if (event.isCanceled()) return;
        String[] words = event.getParseResults().getReader().getString().trim().replaceFirst("^/", "").split("\\s+");
        if (words.length < 2) return;
        String root = words[0].toLowerCase(java.util.Locale.ROOT).replaceFirst("^minecraft:", "");
        if (!root.equals("kick") && !root.equals("ban") && !root.equals("ban-ip")) return;
        var source = event.getParseResults().getContext().getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(words[1]);
        if (target != null) exempt(target.getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        stopping = true;
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        stopping = false;
        UNTIL.clear();
        EXEMPT.clear();
    }
}
