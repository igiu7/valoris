package com.valoris.command;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.city.CityService;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.diplomacy.WarService;
import com.valoris.kingdom.KingdomService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operators (permission level 3, like the other admin commands) remove a city or a kingdom, whatever its state - also during a war, a siege, a
 * conquest or a looting:
 *   /valoris admin disband city "<name>"      the city, its claims and its team (archived first)
 *   /valoris admin disband kingdom "<name>"   the kingdom; its cities stay, free
 * The realm leaves its wars first; sieges and conquests of it are called off on the next siege tick.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class AdminDisbandCommands {
    private AdminDisbandCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("valoris").then(Commands.literal("admin")
                .then(Commands.literal("disband").requires(s -> s.hasPermission(3))
                        .then(Commands.literal("city").then(Commands.argument("name", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ValorisData.get(c.getSource().getServer())
                                        .all().stream().map(city -> quote(city.name)), b))
                                .executes(AdminDisbandCommands::city)))
                        .then(Commands.literal("kingdom").then(Commands.argument("name", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ValorisData.get(c.getSource().getServer())
                                        .kingdoms().stream().map(k -> quote(k.name)), b))
                                .executes(AdminDisbandCommands::kingdom))))));
    }

    private static String quote(String name) {
        return name.matches("[A-Za-z0-9_.+-]+") ? name : "\"" + name + "\"";
    }

    /** The realm (kingdom or independent city) leaves every war it fights. */
    private static void leaveWars(MinecraftServer server, ValorisData data, java.util.UUID realm, String why) {
        for (ValorisData.War w : List.copyOf(data.warsOf(realm))) WarService.leave(server, data, w, realm, why);
    }

    private static int city(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        ValorisData data = ValorisData.get(server);
        String name = StringArgumentType.getString(c, "name");
        City city = data.cityByName(name).orElseThrow(() -> Errors.of("No such city: " + name));
        if (data.kingdomOfCity(city.teamId).isEmpty()) {
            leaveWars(server, data, city.teamId, "The city of " + city.name + " was removed by an administrator.");
        }
        data.removeConquest(city.teamId);
        String cityName = city.name;
        CityService.destroyCity(server, city, server.createCommandSourceStack());
        Msg.broadcast(server, Msg.text("The city of " + cityName + " was disbanded by an administrator.", ChatFormatting.GOLD));
        c.getSource().sendSuccess(() -> Component.literal("City " + cityName + " disbanded.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int kingdom(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        ValorisData data = ValorisData.get(server);
        String name = StringArgumentType.getString(c, "name");
        Kingdom k = data.kingdomByName(name).orElseThrow(() -> Errors.of("No such kingdom: " + name));
        leaveWars(server, data, k.id, "The " + k.title() + " was dissolved by an administrator.");
        String kingdomName = k.name, kingdomTitle = k.title();
        Msg.broadcast(server, Msg.text("The " + kingdomTitle + " was dissolved by an administrator.", ChatFormatting.GOLD));
        KingdomService.dissolve(data, k);
        c.getSource().sendSuccess(() -> Component.literal("Kingdom " + kingdomName + " dissolved; its cities are free.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
