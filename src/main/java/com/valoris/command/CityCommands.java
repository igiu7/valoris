package com.valoris.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.city.CityService;
import com.valoris.kingdom.KingdomService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Actions for city rulers. */
final class CityCommands {
    private CityCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("city")
                .then(Commands.literal("create")
                        .then(CommandUtil.nameArg("name").executes(c -> CommandUtil.withName(c, "name", CityService::createCity))))
                .then(Commands.literal("info").executes(c -> CommandUtil.run(c, CityService::info)))
                .then(Commands.literal("rename")
                        .then(CommandUtil.nameArg("name").executes(c -> CommandUtil.withName(c, "name",
                                com.valoris.realm.Heraldry::renameCity))))
                .then(Commands.literal("color")
                        .then(Commands.argument("rgb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> CommandUtil.withName(c, "rgb", com.valoris.realm.Heraldry::cityColor))))
                .then(Commands.literal("claim").executes(c -> CommandUtil.run(c, CityService::claimHere)))
                .then(Commands.literal("unclaim").executes(c -> CommandUtil.run(c, CityService::unclaimHere)))
                .then(Commands.literal("banner")
                        .executes(c -> CommandUtil.run(c, CityService::giveReplacementBanner))
                        .then(Commands.literal("move").executes(c -> CommandUtil.run(c, CityService::moveBanner))))
                .then(Commands.literal("disband").executes(c -> CommandUtil.run(c, CityService::disband)))
                .then(Commands.literal("invite")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, CityService::invite))))
                .then(Commands.literal("kick")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, CityService::kick))))
                .then(Commands.literal("accept")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, CityService::acceptRequest))))
                .then(Commands.literal("decline")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, CityService::declineRequest))))
                .then(Commands.literal("transfer")
                        .then(CommandUtil.playerArg().executes(c -> CommandUtil.withPlayer(c, CityService::transfer))))
                .then(Commands.literal("kingdom")
                        .then(Commands.literal("join")
                                .then(CommandUtil.kingdomArg("kingdom").executes(c -> CommandUtil.withName(c, "kingdom", KingdomService::requestJoin))))
                        .then(Commands.literal("accept")
                                .then(CommandUtil.kingdomArg("kingdom").executes(c -> CommandUtil.withName(c, "kingdom", KingdomService::acceptInvite))))
                        .then(Commands.literal("decline")
                                .then(CommandUtil.kingdomArg("kingdom").executes(c -> CommandUtil.withName(c, "kingdom", KingdomService::declineInvite))))
                        .then(Commands.literal("rebel")
                                .executes(c -> CommandUtil.run(c, KingdomService::rebel))
                                // older clients send a name (rebels used to found a kingdom): ignored
                                .then(CommandUtil.nameArg("name").executes(c -> CommandUtil.run(c, KingdomService::rebel)))));
    }
}
