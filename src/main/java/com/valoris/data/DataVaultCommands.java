package com.valoris.data;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.ValorisData.City;
import com.valoris.tech.TechService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin (op level 3):
 * /valoris admin backup                          take a backup now
 * /valoris admin backups                         list the backups (newest first)
 * /valoris admin restore <backup>                put the Valoris data of a backup back (the current state is backed up first)
 * /valoris admin archive                         list removed cities and kingdoms
 * /valoris admin archive techs <entry> "<city>"  give the technologies of an archived city / kingdom to a city's realm
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DataVaultCommands {
    private DataVaultCommands() {}

    private static int list(CommandContext<CommandSourceStack> c, String title, List<String> items) {
        String text = title + (items.isEmpty() ? "\n none" : "\n " + String.join("\n ", items.subList(0, Math.min(30, items.size()))))
                + (items.size() > 30 ? "\n ... (" + items.size() + " in total)" : "");
        c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("valoris").then(Commands.literal("admin")
                .requires(s -> s.hasPermission(3))
                .then(Commands.literal("backup").executes(c -> {
                    var path = DataVault.backup(c.getSource().getServer(), "manual", false);
                    c.getSource().sendSuccess(() -> Component.literal("Backup started: valoris_data/backups/"
                            + path.getFileName()), true);
                    return 1;
                }))
                .then(Commands.literal("backups").executes(c -> list(c, "Backups (newest first):", DataVault.listBackups())))
                .then(Commands.literal("restore").then(Commands.argument("backup", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(DataVault.listBackups(), b))
                        .executes(DataVaultCommands::restore)))
                .then(Commands.literal("archive")
                        .executes(c -> list(c, "Archive of removed cities and kingdoms (newest first):", DataVault.listArchive()))
                        .then(Commands.literal("techs").then(Commands.argument("entry", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DataVault.listArchive(), b))
                                .then(Commands.argument("city", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ValorisData.get(c.getSource()
                                                .getServer()).all().stream().map(x -> Msg.q(x.name)), b))
                                        .executes(DataVaultCommands::giveTechs)))))));
    }

    private static int restore(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "backup");
        try {
            DataVault.restore(c.getSource().getServer(), name);
        } catch (IOException e) {
            throw Errors.of("Could not restore: " + e.getMessage());
        }
        c.getSource().sendSuccess(() -> Component.literal("Restored the Valoris data of " + name
                + " (the state before it was backed up). FTB teams and claims are not changed by this; to restore them, "
                + "stop the server and copy ftbteams/ and ftbchunks/ from that backup into the world folder.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int giveTechs(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String entry = StringArgumentType.getString(c, "entry");
        String cityName = StringArgumentType.getString(c, "city");
        ValorisData data = ValorisData.get(c.getSource().getServer());
        City city = data.cityByName(cityName).orElseThrow(() -> Errors.of("No such city."));
        Set<String> techs;
        try {
            techs = DataVault.archivedTechs(entry);
        } catch (IOException e) {
            throw Errors.of("Could not read that archive entry: " + e.getMessage());
        }
        int given = 0;
        for (String t : techs) {
            if (com.valoris.tech.TechConfig.node(t) == null || TechService.has(city, t)) continue;
            TechService.grant(data, city, t, true);
            given++;
        }
        data.touch();
        int n = given;
        c.getSource().sendSuccess(() -> Component.literal("Gave " + n + " technologies from " + entry + " to the realm of "
                + city.name + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
