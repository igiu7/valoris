package com.valoris.command;

import com.mojang.brigadier.arguments.StringArgumentType;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.diplomacy.DiplomacyService;
import com.valoris.diplomacy.Requests.Proposal;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Diplomatic actions of kings. */
final class DiplomacyCommands {
    private DiplomacyCommands() {}

    private static LiteralArgumentBuilder<CommandSourceStack> propose(String literal, Proposal proposal) {
        return Commands.literal(literal)
                .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm",
                        (p, name) -> DiplomacyService.propose(p, name, proposal))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("diplomacy")
                .then(Commands.literal("status").executes(c -> CommandUtil.run(c, DiplomacyService::status)))
                .then(Commands.literal("propose")
                        .then(propose("alliance", Proposal.ALLIANCE))
                        .then(propose("peace", Proposal.PEACE)))
                .then(Commands.literal("accept")
                        .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm", DiplomacyService::accept))))
                .then(Commands.literal("decline")
                        .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm", DiplomacyService::decline))))
                .then(Commands.literal("war")
                        .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm", DiplomacyService::declareWar))))
                .then(Commands.literal("call")
                        .then(CommandUtil.realmArg("ally").then(CommandUtil.realmArg("enemy").executes(c -> CommandUtil.run(c,
                                p -> com.valoris.diplomacy.WarService.call(p, StringArgumentType.getString(c, "ally"),
                                        StringArgumentType.getString(c, "enemy")))))))
                .then(Commands.literal("join")
                        .then(CommandUtil.realmArg("caller").then(CommandUtil.realmArg("enemy").executes(c -> CommandUtil.run(c,
                                p -> com.valoris.diplomacy.WarService.answer(p, StringArgumentType.getString(c, "caller"),
                                        StringArgumentType.getString(c, "enemy"), true))))))
                .then(Commands.literal("refuse")
                        .then(CommandUtil.realmArg("caller").then(CommandUtil.realmArg("enemy").executes(c -> CommandUtil.run(c,
                                p -> com.valoris.diplomacy.WarService.answer(p, StringArgumentType.getString(c, "caller"),
                                        StringArgumentType.getString(c, "enemy"), false))))))
                .then(Commands.literal("withdraw")
                        .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm", DiplomacyService::withdraw))))
                .then(Commands.literal("break")
                        .then(CommandUtil.realmArg("realm").executes(c -> CommandUtil.withName(c, "realm", DiplomacyService::breakTreaty))));
    }
}
