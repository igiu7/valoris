package com.valoris.command;

import java.util.Collection;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.ValorisData;
import com.valoris.util.Errors;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operators (permission level 3) let a player found a kingdom without the minResidentsForKingdom residents
 * (kingdom.KingdomService.checkEnoughResidents). Saved with the world (ValorisData.kingdomFounders) until removed:
 *   /valoris admin kingdomfounder add <player>
 *   /valoris admin kingdomfounder remove <player>
 *   /valoris admin kingdomfounder list
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class KingdomFounderCommands {
    private KingdomFounderCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("valoris").then(Commands.literal("admin")
                .then(Commands.literal("kingdomfounder").requires(s -> s.hasPermission(3))
                        .then(Commands.literal("add").then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(KingdomFounderCommands::add)))
                        .then(Commands.literal("remove").then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        ValorisData.get(c.getSource().getServer()).kingdomFounders.values(), b))
                                .executes(KingdomFounderCommands::remove)))
                        .then(Commands.literal("list").executes(KingdomFounderCommands::list)))));
    }

    private static int add(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ValorisData data = ValorisData.get(c.getSource().getServer());
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        for (GameProfile p : profiles) {
            data.kingdomFounders.put(p.getId(), p.getName());
            c.getSource().sendSuccess(() -> Component.literal(p.getName()
                    + " may now found a kingdom without enough residents.").withStyle(ChatFormatting.GREEN), true);
        }
        data.setDirty();
        return profiles.size();
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ValorisData data = ValorisData.get(c.getSource().getServer());
        int removed = 0;
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            if (data.kingdomFounders.remove(p.getId()) == null) continue;
            removed++;
            c.getSource().sendSuccess(() -> Component.literal(p.getName()
                    + " needs enough residents again to found a kingdom.").withStyle(ChatFormatting.GREEN), true);
        }
        if (removed == 0) throw Errors.of("That player is not on the list.");
        data.setDirty();
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Collection<String> names = ValorisData.get(c.getSource().getServer()).kingdomFounders.values();
        c.getSource().sendSuccess(() -> Component.literal(names.isEmpty()
                ? "Nobody may found a kingdom without enough residents."
                : "May found a kingdom without enough residents: " + String.join(", ", names)), false);
        return names.size();
    }
}
