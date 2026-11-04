package com.valoris.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.coup.CoupService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Coup d'etat: /valoris coup start, /valoris coup side ruler|leader (the side window sends it). */
final class CoupCommands {
    private CoupCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("coup")
                .then(Commands.literal("start").executes(c -> CommandUtil.run(c, CoupService::start)))
                .then(Commands.literal("side")
                        .then(Commands.literal("ruler").executes(c -> CommandUtil.run(c, p -> CoupService.choose(p, 1))))
                        .then(Commands.literal("leader").executes(c -> CommandUtil.run(c, p -> CoupService.choose(p, 2)))));
    }
}
