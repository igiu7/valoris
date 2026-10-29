package com.valoris.moderation;

import java.util.Collection;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.util.Errors;
import com.valoris.valoris2;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Staff commands. Permission level 2 (moderators): kick, warn, mute, freeze, check, history, alts, list, staff chat,
 * clearchat. Level 3 (admins): bans and IP bans. Players may be offline for everything except kick and freeze.
 *
 * /vx ban <player> [reason]                /vx tempban <player> <time> [reason]      /vx unban <player>
 * /vx ipban <player|ip> [reason]           /vx tempipban <player|ip> <time> [reason] /vx unbanip <player|ip>
 * /vx mute <player> [reason]               /vx tempmute <player> <time> [reason]     /vx unmute <player>
 * /vx kick <player> [reason]               /vx warn <player> <reason>
 * /vx freeze <player>                      /vx unfreeze <player>
 * /vx check <player>   /vx history <player>   /vx alts <player>   /vx list bans|mutes|frozen
 * /vx sc <message>     /vx clearchat     /vx spy
 * /vx vanish           /vx invsee <player>   /vx ecsee <player>  (admins may edit, moderators only look)
 * Times: 30m, 2h, 7d, 1w, 1d12h...
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class ModerationCommands {
    private ModerationCommands() {}

    private static GameProfile one(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        if (profiles.size() != 1) throw Errors.of("Name exactly one player.");
        return profiles.iterator().next();
    }

    private static String reason(CommandContext<CommandSourceStack> c) {
        try {
            return StringArgumentType.getString(c, "reason");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> profile() {
        return Commands.argument("player", GameProfileArgument.gameProfile());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> reasonArg() {
        return Commands.argument("reason", StringArgumentType.greedyString());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> time() {
        return Commands.argument("time", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"30m", "1h", "6h", "1d", "3d", "7d", "30d"}, b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> ipTarget() {
        return Commands.argument("target", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b));
    }

    /** Admins may change the inventory of anyone below their rank; everybody else only looks. */
    private static boolean canEdit(CommandSourceStack src, ServerPlayer target) {
        return src.hasPermission(ModerationService.ADMIN_LEVEL) && ModerationService.outranks(src, target.getGameProfile());
    }

    private static long time(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return Durations.parse(StringArgumentType.getString(c, "time"));
    }

    @FunctionalInterface private interface Run { void run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException; }

    private static int ok(CommandContext<CommandSourceStack> c, Run r) throws CommandSyntaxException {
        r.run(c);
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        int mod = ModerationService.MOD_LEVEL, admin = ModerationService.ADMIN_LEVEL;
        LiteralArgumentBuilder<CommandSourceStack> vx = Commands.literal("vx").requires(s -> s.hasPermission(mod));

        // bans
        vx.then(Commands.literal("ban").requires(s -> s.hasPermission(admin)).then(profile()
                .executes(c -> ok(c, x -> ModerationService.ban(x.getSource(), one(x), 0, "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.ban(x.getSource(), one(x), 0, reason(x)))))));
        vx.then(Commands.literal("tempban").requires(s -> s.hasPermission(admin)).then(profile().then(time()
                .executes(c -> ok(c, x -> ModerationService.ban(x.getSource(), one(x), time(x), "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.ban(x.getSource(), one(x), time(x), reason(x))))))));
        vx.then(Commands.literal("unban").requires(s -> s.hasPermission(admin)).then(profile()
                .executes(c -> ok(c, x -> ModerationService.unban(x.getSource(), one(x))))));
        vx.then(Commands.literal("ipban").requires(s -> s.hasPermission(admin)).then(ipTarget()
                .executes(c -> ok(c, x -> ModerationService.ipBan(x.getSource(), StringArgumentType.getString(x, "target"), 0, "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.ipBan(x.getSource(),
                        StringArgumentType.getString(x, "target"), 0, reason(x)))))));
        vx.then(Commands.literal("tempipban").requires(s -> s.hasPermission(admin)).then(ipTarget().then(time()
                .executes(c -> ok(c, x -> ModerationService.ipBan(x.getSource(), StringArgumentType.getString(x, "target"), time(x), "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.ipBan(x.getSource(),
                        StringArgumentType.getString(x, "target"), time(x), reason(x))))))));
        vx.then(Commands.literal("unbanip").requires(s -> s.hasPermission(admin)).then(ipTarget()
                .executes(c -> ok(c, x -> ModerationService.ipUnban(x.getSource(), StringArgumentType.getString(x, "target"))))));

        // mutes
        vx.then(Commands.literal("mute").then(profile()
                .executes(c -> ok(c, x -> ModerationService.mute(x.getSource(), one(x), 0, "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.mute(x.getSource(), one(x), 0, reason(x)))))));
        vx.then(Commands.literal("tempmute").then(profile().then(time()
                .executes(c -> ok(c, x -> ModerationService.mute(x.getSource(), one(x), time(x), "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.mute(x.getSource(), one(x), time(x), reason(x))))))));
        vx.then(Commands.literal("unmute").then(profile()
                .executes(c -> ok(c, x -> ModerationService.unmute(x.getSource(), one(x))))));

        // kick, warn, freeze
        vx.then(Commands.literal("kick").then(Commands.argument("target", EntityArgument.player())
                .executes(c -> ok(c, x -> ModerationService.kick(x.getSource(), EntityArgument.getPlayer(x, "target"), "")))
                .then(reasonArg().executes(c -> ok(c, x -> ModerationService.kick(x.getSource(),
                        EntityArgument.getPlayer(x, "target"), reason(x)))))));
        vx.then(Commands.literal("warn").then(profile().then(reasonArg()
                .executes(c -> ok(c, x -> ModerationService.warn(x.getSource(), one(x), reason(x)))))));
        vx.then(Commands.literal("freeze").then(Commands.argument("target", EntityArgument.player())
                .executes(c -> ok(c, x -> ModerationService.freeze(x.getSource(), EntityArgument.getPlayer(x, "target"), true)))));
        vx.then(Commands.literal("unfreeze").then(Commands.argument("target", EntityArgument.player())
                .executes(c -> ok(c, x -> ModerationService.freeze(x.getSource(), EntityArgument.getPlayer(x, "target"), false)))));

        // information
        vx.then(Commands.literal("check").then(profile().executes(c -> ok(c, x -> ModerationService.check(x.getSource(), one(x))))));
        vx.then(Commands.literal("history").then(profile().executes(c -> ok(c, x -> ModerationService.history(x.getSource(), one(x))))));
        vx.then(Commands.literal("alts").then(profile().executes(c -> ok(c, x -> {
            GameProfile p = one(x);
            var alts = ModerationService.alts(x.getSource().getServer(), p.getId());
            x.getSource().sendSuccess(() -> Component.literal(alts.isEmpty() ? "No other accounts share the IP of " + p.getName() + "."
                    : "Accounts sharing the IP of " + p.getName() + ": " + String.join(", ", alts)), false);
        }))));
        vx.then(Commands.literal("list")
                .then(Commands.literal("bans").executes(c -> ok(c, x -> ModerationService.listActive(x.getSource(), "bans"))))
                .then(Commands.literal("mutes").executes(c -> ok(c, x -> ModerationService.listActive(x.getSource(), "mutes"))))
                .then(Commands.literal("frozen").executes(c -> ok(c, x -> ModerationService.listActive(x.getSource(), "frozen")))));

        // vanish and inventories
        vx.then(Commands.literal("vanish").executes(c -> ok(c, x -> {
            boolean on = Vanish.toggle(x.getSource().getPlayerOrException());
            x.getSource().sendSuccess(() -> Component.literal(on
                    ? "You are vanished. Only the staff sees you (your chat messages are still visible)."
                    : "You are visible again."), false);
        })));
        vx.then(Commands.literal("invsee").then(Commands.argument("target", EntityArgument.player())
                .executes(c -> ok(c, x -> {
                    ServerPlayer t = EntityArgument.getPlayer(x, "target");
                    Invsee.inventory(x.getSource().getPlayerOrException(), t, canEdit(x.getSource(), t));
                }))));
        vx.then(Commands.literal("ecsee").then(Commands.argument("target", EntityArgument.player())
                .executes(c -> ok(c, x -> {
                    ServerPlayer t = EntityArgument.getPlayer(x, "target");
                    Invsee.enderChest(x.getSource().getPlayerOrException(), t, canEdit(x.getSource(), t));
                }))));

        // staff tools
        vx.then(Commands.literal("sc").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(c -> ok(c, x -> ModerationService.staffChat(x.getSource(), StringArgumentType.getString(x, "message"))))));
        vx.then(Commands.literal("clearchat").executes(c -> ok(c, x -> ModerationService.clearChat(x.getSource()))));

        event.getDispatcher().register(vx);
    }
}
