package com.valoris.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.valoris.data.ValorisData;
import com.valoris.util.Msg;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;

final class CommandUtil {
    private CommandUtil() {}

    @FunctionalInterface interface Action { void run(ServerPlayer p) throws CommandSyntaxException; }
    @FunctionalInterface interface NameAction { void run(ServerPlayer p, String name) throws CommandSyntaxException; }
    @FunctionalInterface interface TargetAction { void run(ServerPlayer p, ServerPlayer target) throws CommandSyntaxException; }

    static int run(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException());
        return 1;
    }

    static int withName(CommandContext<CommandSourceStack> ctx, String arg, NameAction action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, arg));
        return 1;
    }

    static int withPlayer(CommandContext<CommandSourceStack> ctx, TargetAction action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
        return 1;
    }

    static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArg() {
        return Commands.argument("player", EntityArgument.player());
    }

    /** Free-text name (single word, or "quoted phrase"). */
    static RequiredArgumentBuilder<CommandSourceStack, String> nameArg(String name) {
        return Commands.argument(name, StringArgumentType.string());
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> cityArg(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests(cities());
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> kingdomArg(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests(kingdoms());
    }

    /** A kingdom or an independent city (see Realms). */
    static RequiredArgumentBuilder<CommandSourceStack, String> realmArg(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                com.valoris.realm.Realms.all(ValorisData.get(ctx.getSource().getServer())).stream()
                        .map(r -> Msg.q(r.name())), b));
    }

    private static SuggestionProvider<CommandSourceStack> cities() {
        return (ctx, b) -> SharedSuggestionProvider.suggest(
                ValorisData.get(ctx.getSource().getServer()).all().stream()
                        .filter(c -> c.founded).map(c -> Msg.q(c.name)), b);
    }

    private static SuggestionProvider<CommandSourceStack> kingdoms() {
        return (ctx, b) -> SharedSuggestionProvider.suggest(
                ValorisData.get(ctx.getSource().getServer()).kingdoms().stream().map(k -> Msg.q(k.name)), b);
    }
}
