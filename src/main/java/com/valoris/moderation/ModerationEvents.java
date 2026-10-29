package com.valoris.moderation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.valoris.moderation.ModerationData.Mute;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Enforces mutes (chat, private messages, /me, /say...) and freezes (no moving, building, fighting, dropping or
 * commands; cannot be hurt), keeps every player's last name and IP, and warns the staff about accounts that share
 * an IP address with a banned or muted player (ban / mute evasion).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class ModerationEvents {
    /** Commands a muted player cannot use: everything that talks to other players. */
    private static final Set<String> TALK_COMMANDS = Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm", "r", "reply",
            "cc", "kc", "cmsg", "kmsg");
    /** The only commands a frozen player may use: talking to the staff. */
    private static final Set<String> FROZEN_ALLOWED = Set.of("msg", "tell", "w", "r", "reply");
    /** Where frozen players are held. */
    private static final Map<UUID, Vec3> ANCHORS = new HashMap<>();

    private ModerationEvents() {}

    static void anchor(ServerPlayer player, boolean frozen) {
        if (frozen) ANCHORS.put(player.getUUID(), player.position());
        else ANCHORS.remove(player.getUUID());
    }

    private static boolean frozen(ServerPlayer p) {
        return ModerationService.isFrozen(p.getServer(), p.getUUID());
    }

    private static void denyFrozen(ServerPlayer p) {
        p.displayClientMessage(Component.literal("You are frozen by the staff.").withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ chat and commands

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        Optional<Mute> mute = ModerationService.mute(p.getServer(), p.getUUID());
        if (mute.isPresent()) {
            event.setCanceled(true);
            p.sendSystemMessage(ModerationService.mutedMessage(mute.get()));
        }
    }

    private static String root(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        int space = input.indexOf(' ');
        String root = (space < 0 ? input : input.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':'); // "minecraft:msg"
        return colon >= 0 ? root.substring(colon + 1) : root;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer p)) return;
        String root = root(event);
        if (TALK_COMMANDS.contains(root)) {
            Optional<Mute> mute = ModerationService.mute(p.getServer(), p.getUUID());
            if (mute.isPresent()) {
                event.setCanceled(true);
                p.sendSystemMessage(ModerationService.mutedMessage(mute.get()));
                return;
            }
        }
        if (frozen(p) && !FROZEN_ALLOWED.contains(root) && !p.hasPermissions(ModerationService.MOD_LEVEL)) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.literal("You are frozen by the staff and cannot use commands.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ login / logout

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.getServer();
        ModerationData d = ModerationData.get(server);
        String ip = p.getIpAddress();
        d.names.put(p.getUUID(), p.getGameProfile().getName());
        d.lastIp.put(p.getUUID(), ip);
        d.setDirty();

        ModerationService.mute(server, p.getUUID()).ifPresent(m -> p.sendSystemMessage(ModerationService.mutedMessage(m)));
        if (frozen(p)) {
            anchor(p, true);
            p.sendSystemMessage(Component.literal("You are still frozen by the staff.").withStyle(ChatFormatting.RED));
            ModerationService.toStaff(server, Component.literal(p.getGameProfile().getName()
                    + " (frozen) joined again.").withStyle(ChatFormatting.AQUA));
        }

        // accounts on the same address that are banned or muted: probably evasion
        List<String> flagged = new java.util.ArrayList<>();
        d.lastIp.forEach((other, addr) -> {
            if (other.equals(p.getUUID()) || !addr.equals(ip)) return;
            String name = ModerationService.nameOf(server, other);
            var profile = new com.mojang.authlib.GameProfile(other, name);
            if (server.getPlayerList().getBans().isBanned(profile)) flagged.add(name + " (banned)");
            else if (ModerationService.mute(server, other).isPresent()) flagged.add(name + " (muted)");
        });
        if (!flagged.isEmpty()) {
            ModerationService.toStaff(server, Component.literal("Possible alt: " + p.getGameProfile().getName()
                    + " shares an IP address with " + String.join(", ", flagged) + ".").withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (frozen(p)) {
            ModerationService.toStaff(p.getServer(), Component.literal(p.getGameProfile().getName()
                    + " logged out while frozen.").withStyle(ChatFormatting.RED));
        }
        ANCHORS.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ freeze

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        Vec3 anchor = ANCHORS.get(p.getUUID());
        if (anchor == null) {
            if (frozen(p)) anchor(p, true); // frozen through the save data (e.g. after a restart)
            return;
        }
        if (p.isPassenger()) p.stopRiding();
        if (p.position().distanceToSqr(anchor) > 0.01) {
            p.connection.teleport(anchor.x, anchor.y, anchor.z, p.getYRot(), p.getXRot());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) { event.setCanceled(true); denyFrozen(p); }
    }

    /** A frozen player cannot get rid of items (the item goes back to the inventory). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && frozen(p)) {
            event.setCanceled(true);
            p.getInventory().placeItemBackInInventory(event.getEntity().getItem().copy());
            denyFrozen(p);
        }
    }

    /** Nobody can hurt a frozen player (they cannot defend themselves). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && frozen(p)) event.setCanceled(true);
    }
}
