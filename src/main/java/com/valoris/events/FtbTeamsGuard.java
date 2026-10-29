package com.valoris.events;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.brigadier.tree.CommandNode;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Players cannot use any FTB Chunks / FTB Teams / FTB Library command (/ftbchunks, /chunks, /ftbteams, /ftblibrary...):
 * cities, residents and
 * claims are managed only through Valoris. The commands are hidden from players (no tab completion) and refused if
 * typed anyway; the server console keeps them. The FTB map, claim manager and team logic do not use these commands,
 * so they keep working.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public class FtbTeamsGuard {
    private static final Set<String> BLOCKED = Set.of(
            "ftbchunks", "chunks", "ftbchunks_dev", "ftbteams", "ftbteams_add_fake_player", "ftblibrary");

    /** Runs after FTB registered its commands: players no longer pass the requirement of the root nodes. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        Field requirement;
        try {
            requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            valoris2.LOGGER.warn("Cannot hide FTB commands from players (they are still refused when typed)", e);
            return;
        }
        for (String name : BLOCKED) {
            CommandNode<CommandSourceStack> node = event.getDispatcher().getRoot().getChild(name);
            if (node == null) continue;
            Predicate<CommandSourceStack> old = node.getRequirement();
            try {
                requirement.set(node, (Predicate<CommandSourceStack>) src -> !src.isPlayer() && old.test(src));
            } catch (ReflectiveOperationException | RuntimeException e) {
                valoris2.LOGGER.warn("Cannot hide /{} from players", name, e);
            }
        }
    }

    /** Fallback: refuses the command even if the node could not be hidden. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;

        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        int space = input.indexOf(' ');
        String root = (space < 0 ? input : input.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
        if (!BLOCKED.contains(root)) return;

        event.setCanceled(true);
        player.sendSystemMessage(Msg.text("This command is disabled. Use the Valoris menu or /valoris instead.",
                ChatFormatting.RED));
    }
}
