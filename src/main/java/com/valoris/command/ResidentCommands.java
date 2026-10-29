package com.valoris.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.city.CityService;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.util.Msg;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Actions available to every player (peasants / residents). */
final class ResidentCommands {
    private ResidentCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("resident")
                .then(Commands.literal("info").executes(c -> CommandUtil.run(c, p -> {
                    Roles.Role role = Roles.of(p);
                    String city = Roles.cityOfMember(p).map(x -> x.name).orElse("none");
                    String kingdom = Roles.kingdomOf(p).map(k -> k.name).orElse("none");
                    Msg.info(p, "Role: " + role.title + "\nCity: " + city + "\nKingdom: " + kingdom);
                })))
                .then(Commands.literal("cities").executes(c -> CommandUtil.run(c, p -> {
                    ValorisData data = ValorisData.get(p.getServer());
                    StringBuilder sb = new StringBuilder("Cities:");
                    for (City city : data.all()) {
                        if (!city.founded) continue;
                        sb.append("\n - ").append(city.name).append(" (ruler ").append(Msg.nameOf(p.getServer(), city.ruler)).append(")");
                        data.kingdomOfCity(city.teamId).ifPresent(k -> sb.append(" - ").append(k.name));
                    }
                    Msg.info(p, sb.toString());
                })))
                .then(Commands.literal("request")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", CityService::requestJoin))))
                .then(Commands.literal("accept")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", CityService::acceptInvite))))
                .then(Commands.literal("decline")
                        .then(CommandUtil.cityArg("city").executes(c -> CommandUtil.withName(c, "city", CityService::declineInvite))))
                .then(Commands.literal("leave").executes(c -> CommandUtil.run(c, CityService::leave)));
    }
}
