package com.valoris.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.war.ConquestService;
import com.valoris.war.SiegeService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;

/** Siege and conquest commands. The GUI uses the same commands. */
final class WarCommands {
    private WarCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("war")
                .then(Commands.literal("siege")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", SiegeService::start))))
                .then(Commands.literal("status").executes(c -> CommandUtil.run(c, SiegeService::status)))
                .then(Commands.literal("conquest")
                        .executes(c -> CommandUtil.run(c, ConquestService::reopen))
                        .then(Commands.literal("appoint")
                                .then(Commands.argument("uuid", UuidArgument.uuid()).executes(c -> CommandUtil.run(c,
                                        p -> ConquestService.appoint(p, UuidArgument.getUuid(c, "uuid"))))))
                        .then(Commands.literal("subjugate").executes(c -> CommandUtil.run(c, ConquestService::subjugate)))
                        // the ruler is no longer asked: the old command forces the city in as well
                        .then(Commands.literal("invite").executes(c -> CommandUtil.run(c, ConquestService::subjugate)))
                        .then(Commands.literal("release").executes(c -> CommandUtil.run(c, ConquestService::release)))
                        .then(Commands.literal("loot").executes(c -> CommandUtil.run(c, ConquestService::loot)))
                        .then(Commands.literal("accept").executes(c -> CommandUtil.run(c, ConquestService::accept)))
                        .then(Commands.literal("decline").executes(c -> CommandUtil.run(c, ConquestService::decline))));
    }
}
