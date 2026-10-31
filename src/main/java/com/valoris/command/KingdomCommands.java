package com.valoris.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.kingdom.KingdomService;
import com.valoris.util.Errors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Actions for kings (plus read-only info for everybody). */
final class KingdomCommands {
    private KingdomCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("kingdom")
                .then(Commands.literal("create")
                        .then(CommandUtil.nameArg("name").executes(c -> CommandUtil.withName(c, "name", KingdomService::create))))
                .then(Commands.literal("info")
                        .executes(c -> CommandUtil.run(c, p -> KingdomService.info(p,
                                Roles.kingdomOf(p).orElseThrow(() -> Errors.of("Your city is not in a kingdom.")))))
                        .then(CommandUtil.kingdomArg("kingdom").executes(c -> CommandUtil.withName(c, "kingdom", (p, name) ->
                                KingdomService.info(p, ValorisData.get(p.getServer()).kingdomByName(name)
                                        .orElseThrow(() -> Errors.of("No such kingdom.")))))))
                .then(Commands.literal("list").executes(c -> CommandUtil.run(c, KingdomService::list)))
                .then(Commands.literal("rename")
                        .then(CommandUtil.nameArg("name").executes(c -> CommandUtil.withName(c, "name",
                                com.valoris.realm.Heraldry::renameKingdom))))
                .then(Commands.literal("prefix")
                        .then(CommandUtil.nameArg("prefix").executes(c -> CommandUtil.withName(c, "prefix",
                                com.valoris.realm.Heraldry::kingdomPrefix))))
                .then(Commands.literal("title")
                        .then(CommandUtil.nameArg("title").executes(c -> CommandUtil.withName(c, "title",
                                com.valoris.realm.Heraldry::rulerTitle))))
                .then(Commands.literal("color")
                        .then(Commands.argument("rgb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> CommandUtil.withName(c, "rgb", com.valoris.realm.Heraldry::kingdomColor))))
                .then(Commands.literal("invite")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, KingdomService::invite))))
                .then(Commands.literal("accept")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", KingdomService::acceptRequest))))
                .then(Commands.literal("decline")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", KingdomService::declineRequest))))
                .then(Commands.literal("expel")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", KingdomService::expel))))
                .then(Commands.literal("crown")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, KingdomService::crown))))
                .then(Commands.literal("disband").executes(c -> CommandUtil.run(c, KingdomService::disband)));
    }
}
