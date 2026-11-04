package com.valoris.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Login with a password, for a server in offline mode (where anyone can join under any name). A player joins
 * "locked": frozen where they stand, blind, unhurt and ignored by mobs, unable to break, build, use, attack, drop,
 * pick up, chat or run commands - only /register <password> <password> (first time) or /login <password>. Mod
 * network messages wait too (mixin.AuthPayloadMixin), and nobody can take the place of a logged-in player by joining
 * under their name (mixin.AuthLoginMixin). Too slow or too many wrong passwords: kicked. Accounts: Accounts.
 * Players: /changepassword <old> <new>. Operators: /valoris admin auth reset <name> (the player registers again).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Auth {
    private Auth() {}

    private static final class Pending {
        final ServerLevel level;
        final Vec3 pos;
        final float yRot, xRot;
        final long since;
        int attempts;
        boolean passwordOk; // the password was right; only the Discord confirmation is missing

        Pending(ServerPlayer p, long since) {
            this.level = p.serverLevel();
            this.pos = p.position();
            this.yRot = p.getYRot();
            this.xRot = p.getXRot();
            this.since = since;
        }
    }

    private static final Set<UUID> LOGGED_IN = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> ALLOWED_COMMANDS = Set.of("login", "l", "register", "reg", "discord");
    /** new players whose Discord account confirmed them (discord.DiscordLink), until they register: id, name */
    private static final Map<UUID, String[]> CONFIRMED = new ConcurrentHashMap<>();

    /**
     * The player still has to confirm their Discord account: a new player before choosing a password, or one whose
     * account was made while the Discord check was off (they confirm it after /login).
     */
    private static boolean needsDiscord(ServerPlayer p) {
        if (!com.valoris.discord.DiscordLink.required()) return false;
        Accounts.Account a = Accounts.get(p.getGameProfile().getName());
        return a == null ? !CONFIRMED.containsKey(p.getUUID()) : a.discordId == null;
    }

    /** The password is done (or not needed yet) and the Discord step is what is left. */
    private static boolean atDiscordStep(ServerPlayer p) {
        if (!needsDiscord(p)) return false;
        Pending pend = PENDING.get(p.getUUID());
        return Accounts.get(p.getGameProfile().getName()) == null || pend == null || pend.passwordOk;
    }

    /** The Discord member said "Yes" (server thread). */
    public static void discordConfirmed(ServerPlayer p, String discordId, String discordName) {
        String name = p.getGameProfile().getName();
        Accounts.Account account = Accounts.get(name);
        if (account != null) { // an account made without Discord: link it now
            if (account.discordId != null) return;
            if (Accounts.countByDiscord(discordId) >= Config.DISCORD_ACCOUNTS_PER_MEMBER.get()) {
                p.sendSystemMessage(Component.literal("That Discord account has already confirmed another Minecraft name.")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            Accounts.setDiscord(name, discordId, discordName);
            p.sendSystemMessage(Component.literal("Discord confirmed (" + discordName + ") and linked to " + name + ".")
                    .withStyle(ChatFormatting.GREEN));
            Pending pend = PENDING.get(p.getUUID());
            if (pend != null && pend.passwordOk) {
                unlock(p);
                p.sendSystemMessage(Component.literal("Logged in. Welcome, " + name + "!").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        if (!PENDING.containsKey(p.getUUID())) return;
        CONFIRMED.put(p.getUUID(), new String[]{discordId, discordName});
        p.sendSystemMessage(Component.literal("Discord confirmed (" + discordName + ").").withStyle(ChatFormatting.GREEN));
        prompt(p, false);
    }

    public static boolean isLoggedIn(ServerPlayer player) {
        return LOGGED_IN.contains(player.getUUID());
    }

    private static boolean locked(Player player) {
        return player instanceof ServerPlayer sp && PENDING.containsKey(sp.getUUID());
    }

    /** Whether a mod network message may be handled now (mixin.AuthPayloadMixin; may run off the main thread). */
    public static boolean payloadAllowed(ServerPlayer player, ResourceLocation id) {
        if (player == null || !PENDING.containsKey(player.getUUID())) return true;
        return Config.AUTH_ALLOWED_PAYLOADS.get().contains(id.getNamespace());
    }

    private static String ip(ServerPlayer p) {
        return p.getIpAddress();
    }

    private static boolean active(MinecraftServer server) {
        return Config.AUTH_ENABLED.get() && server.isDedicatedServer();
    }

    // ------------------------------------------------------------------ joining and leaving

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent event) {
        LOGGED_IN.clear();
        PENDING.clear();
        Accounts.load(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.getServer();
        if (!active(server)) {
            LOGGED_IN.add(p.getUUID());
            return;
        }
        Accounts.Account account = Accounts.get(p.getGameProfile().getName());
        if (account != null && !account.name.equals(p.getGameProfile().getName())) {
            // "Steve" registered; "steve" is another player to the game, but must not pass for him
            p.connection.disconnect(Component.literal("This name is registered as " + account.name + ". Join with exactly that name."));
            return;
        }
        long session = Config.AUTH_SESSION_MINUTES.get() * 60_000L;
        if (account != null && session > 0 && ip(p).equals(account.lastIp)
                && System.currentTimeMillis() - account.lastLogout < session) {
            LOGGED_IN.add(p.getUUID());
            p.sendSystemMessage(Component.literal("Welcome back - logged in automatically (same address, same session).")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (p.isPassenger()) p.stopRiding();
        PENDING.put(p.getUUID(), new Pending(p, server.getTickCount()));
        prompt(p, account != null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PENDING.remove(p.getUUID());
        CONFIRMED.remove(p.getUUID());
        com.valoris.discord.DiscordLink.forget(p.getUUID());
        if (LOGGED_IN.remove(p.getUUID()) && active(p.getServer())) {
            Accounts.touch(p.getGameProfile().getName(), null, System.currentTimeMillis());
        }
    }

    private static void prompt(ServerPlayer p, boolean registered) {
        if (registered && atDiscordStep(p)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 70, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Confirm your Discord").withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("/discord <your Discord name>").withStyle(ChatFormatting.YELLOW)));
            p.sendSystemMessage(Component.literal("Your account is not linked to Discord yet. Type /discord <your Discord name>:"
                    + " our bot will ask you in a Discord DM whether " + p.getGameProfile().getName() + " is you.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!registered && needsDiscord(p)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 70, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Confirm your Discord").withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("/discord <your Discord name>").withStyle(ChatFormatting.YELLOW)));
            p.sendSystemMessage(Component.literal("New here? First confirm that you are on our Discord: type /discord <your Discord name>."
                    + " Our bot will ask you in a Discord DM whether " + p.getGameProfile().getName() + " is you.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Component title = Component.literal(registered ? "Log in" : "Register").withStyle(ChatFormatting.GOLD);
        Component sub = Component.literal(registered ? "/login <password>" : "/register <password> <password>")
                .withStyle(ChatFormatting.YELLOW);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 70, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.sendSystemMessage(Component.literal(registered
                ? "This name is protected by a password. Log in with /login <password>"
                : "Choose a password for your name: /register <password> <password> (at least "
                        + Config.AUTH_MIN_LENGTH.get() + " characters, no spaces). You will need it on every join.")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void unlock(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        LOGGED_IN.add(p.getUUID());
        p.removeEffect(MobEffects.BLINDNESS);
        p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        Accounts.touch(p.getGameProfile().getName(), ip(p), 0);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Pending pend = e.getValue();
            boolean registered = Accounts.get(p.getGameProfile().getName()) != null;
            long timeout = (needsDiscord(p) ? Config.DISCORD_LINK_TIMEOUT.get() : Config.AUTH_TIMEOUT_SECONDS.get()) * 20L;
            if (now - pend.since > timeout) {
                p.connection.disconnect(Component.literal("You did not log in in time."));
                continue;
            }
            // stay where you joined
            if (p.level() != pend.level || p.position().distanceToSqr(pend.pos) > 0.01) {
                p.teleportTo(pend.level, pend.pos.x, pend.pos.y, pend.pos.z, pend.yRot, pend.xRot);
            }
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            if ((now - pend.since) % 20 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
                long left = (timeout - (now - pend.since)) / 20;
                String hint = atDiscordStep(p) ? "/discord <your Discord name>"
                        : registered ? "/login <password>" : "/register <password> <password>";
                p.displayClientMessage(Component.literal(hint
                        + "  -  " + left + " s").withStyle(ChatFormatting.YELLOW), true);
            }
            if ((now - pend.since) % 200 == 100) prompt(p, registered);
        }
    }

    // ------------------------------------------------------------------ commands

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        var login = d.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString()).executes(Auth::login)));
        d.register(Commands.literal("l").redirect(login));
        var register = d.register(Commands.literal("register")
                .then(Commands.argument("password and again", StringArgumentType.greedyString()).executes(Auth::register)));
        d.register(Commands.literal("reg").redirect(register));
        d.register(Commands.literal("discord")
                .then(Commands.argument("your Discord name", StringArgumentType.greedyString()).executes(Auth::discord)));
        d.register(Commands.literal("changepassword")
                .then(Commands.argument("old and new", StringArgumentType.greedyString()).executes(Auth::change)));
        d.register(Commands.literal("valoris").then(Commands.literal("admin").then(Commands.literal("auth")
                .requires(s -> s.hasPermission(3))
                .then(Commands.literal("reset").then(Commands.argument("name", StringArgumentType.word())
                        .executes(Auth::reset))))));
    }

    private static void reply(CommandSourceStack s, String text, ChatFormatting color) {
        s.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static int login(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        Pending pend = PENDING.get(p.getUUID());
        if (pend == null) {
            reply(c.getSource(), "You are already logged in.", ChatFormatting.GRAY);
            return 0;
        }
        if (pend.passwordOk) {
            reply(c.getSource(), "Password already accepted. Now confirm your Discord: /discord <your Discord name>", ChatFormatting.YELLOW);
            return 0;
        }
        String name = p.getGameProfile().getName();
        if (Accounts.get(name) == null) {
            reply(c.getSource(), "This name has no password yet: /register <password> <password>", ChatFormatting.RED);
            return 0;
        }
        if (!Accounts.check(name, StringArgumentType.getString(c, "password"))) {
            pend.attempts++;
            int left = Config.AUTH_MAX_ATTEMPTS.get() - pend.attempts;
            if (left <= 0) {
                p.connection.disconnect(Component.literal("Wrong password."));
                return 0;
            }
            reply(c.getSource(), "Wrong password (" + left + (left == 1 ? " try" : " tries") + " left).", ChatFormatting.RED);
            return 0;
        }
        if (needsDiscord(p)) { // made while the Discord check was off: one more step
            pend.passwordOk = true;
            reply(c.getSource(), "Password accepted.", ChatFormatting.GREEN);
            prompt(p, true);
            return 1;
        }
        unlock(p);
        reply(c.getSource(), "Logged in. Welcome, " + name + "!", ChatFormatting.GREEN);
        return 1;
    }

    private static int discord(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        if (!com.valoris.discord.DiscordLink.required()) {
            reply(c.getSource(), "The Discord check is off on this server.", ChatFormatting.GRAY);
            return 0;
        }
        Accounts.Account account = Accounts.get(p.getGameProfile().getName());
        if (account != null) {
            if (account.discordId != null) {
                reply(c.getSource(), "Your Discord is already confirmed (" + account.discordName + ").", ChatFormatting.GRAY);
                return 0;
            }
            Pending pend = PENDING.get(p.getUUID());
            if (pend != null && !pend.passwordOk) {
                reply(c.getSource(), "First log in: /login <password>. Then confirm your Discord.", ChatFormatting.RED);
                return 0;
            }
            com.valoris.discord.DiscordLink.ask(p, StringArgumentType.getString(c, "your Discord name"));
            return 1;
        }
        if (!PENDING.containsKey(p.getUUID())) return 0;
        if (CONFIRMED.containsKey(p.getUUID())) {
            reply(c.getSource(), "Discord already confirmed. Now: /register <password> <password>", ChatFormatting.GRAY);
            return 0;
        }
        com.valoris.discord.DiscordLink.ask(p, StringArgumentType.getString(c, "your Discord name"));
        return 1;
    }

    private static int register(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        String name = p.getGameProfile().getName();
        if (Accounts.get(name) != null) {
            reply(c.getSource(), isLoggedIn(p) ? "You are already registered. To change it: /changepassword <old> <new>"
                    : "This name is already registered: /login <password>", ChatFormatting.RED);
            return 0;
        }
        if (needsDiscord(p)) {
            reply(c.getSource(), "First confirm your Discord: /discord <your Discord name>", ChatFormatting.RED);
            return 0;
        }
        String[] parts = StringArgumentType.getString(c, "password and again").trim().split("\\s+");
        if (parts.length != 2) {
            reply(c.getSource(), "Type the password twice: /register <password> <password> (no spaces in it).", ChatFormatting.RED);
            return 0;
        }
        if (!parts[0].equals(parts[1])) {
            reply(c.getSource(), "The two passwords are not the same.", ChatFormatting.RED);
            return 0;
        }
        if (parts[0].length() < Config.AUTH_MIN_LENGTH.get()) {
            reply(c.getSource(), "Too short: at least " + Config.AUTH_MIN_LENGTH.get() + " characters.", ChatFormatting.RED);
            return 0;
        }
        if (parts[0].equalsIgnoreCase(name)) {
            reply(c.getSource(), "Do not use your name as the password.", ChatFormatting.RED);
            return 0;
        }
        String[] discord = CONFIRMED.remove(p.getUUID());
        if (discord != null && Accounts.countByDiscord(discord[0]) >= Config.DISCORD_ACCOUNTS_PER_MEMBER.get()) {
            reply(c.getSource(), "That Discord account has confirmed another name in the meantime.", ChatFormatting.RED);
            return 0;
        }
        Accounts.register(name, parts[0], ip(p), discord == null ? null : discord[0], discord == null ? null : discord[1]);
        unlock(p);
        reply(c.getSource(), "Registered and logged in. Remember your password: you need it on every join.", ChatFormatting.GREEN);
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p) || !isLoggedIn(p)) return 0;
        String name = p.getGameProfile().getName();
        String[] parts = StringArgumentType.getString(c, "old and new").trim().split("\\s+");
        if (parts.length != 2) {
            reply(c.getSource(), "Usage: /changepassword <old> <new>", ChatFormatting.RED);
            return 0;
        }
        if (Accounts.get(name) == null || !Accounts.check(name, parts[0])) {
            reply(c.getSource(), "The old password is wrong.", ChatFormatting.RED);
            return 0;
        }
        if (parts[1].length() < Config.AUTH_MIN_LENGTH.get()) {
            reply(c.getSource(), "Too short: at least " + Config.AUTH_MIN_LENGTH.get() + " characters.", ChatFormatting.RED);
            return 0;
        }
        Accounts.setPassword(name, parts[1]);
        reply(c.getSource(), "Password changed.", ChatFormatting.GREEN);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        if (!Accounts.remove(name)) {
            reply(c.getSource(), "No account named " + name + ".", ChatFormatting.RED);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("The account " + name
                + " was removed: on the next join the player registers a new password.").withStyle(ChatFormatting.GREEN), true);
        ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) online.connection.disconnect(Component.literal("Your password was reset. Join again and register."));
        return 1;
    }

    // ------------------------------------------------------------------ what a locked player cannot do

    private static void deny(Player p) {
        p.displayClientMessage(Component.literal("Log in first: /login <password>").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || !locked(p)) return;
        String cmd = event.getParseResults().getReader().getString().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        int space = cmd.indexOf(' ');
        String root = (space < 0 ? cmd : cmd.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
        if (ALLOWED_COMMANDS.contains(root)) return;
        event.setCanceled(true);
        p.sendSystemMessage(Component.literal("Log in first: /login <password>").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (!locked(event.getPlayer())) return;
        event.setCanceled(true);
        deny(event.getPlayer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && locked(p)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) { event.setCanceled(true); deny(event.getEntity()); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        Player p = event.getPlayer();
        if (!locked(p)) return;
        // the stack is already out of the inventory: give it back
        event.setCanceled(true);
        p.getInventory().add(event.getEntity().getItem().copy());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (locked(event.getPlayer())) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player p && locked(p)) event.setCanceled(true);
        else if (event.getSource().getEntity() instanceof Player p && locked(p)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player p && locked(p)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (locked(event.getEntity()) && event.getEntity() instanceof ServerPlayer p) p.closeContainer();
    }
}
