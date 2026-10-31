package com.valoris.chat;

import java.lang.reflect.Field;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.valoris.data.ValorisData;
import com.valoris.moderation.ModerationService;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * /msg /tell /w <player> <message>   private message (replaces the vanilla one, so /r knows whom to answer)
 * /r <message>                       answer the last private message
 * /me <action>                       emote
 * /cc [message]  /kc [message]       city / kingdom chat; without a message: switch normal chat to it and back
 * /cmsg <city> <message>             message to every member of a city
 * /kmsg <kingdom> <message>          message to every member of a kingdom
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class ChatCommands {
    private ChatCommands() {}

    @FunctionalInterface private interface Act { void run(ServerPlayer p, String text) throws CommandSyntaxException; }

    private static int run(CommandContext<CommandSourceStack> c, String arg, Act act) throws CommandSyntaxException {
        act.run(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, arg));
        return 1;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> message() {
        return Commands.argument("message", StringArgumentType.greedyString());
    }

    /** Takes a vanilla command out of the tree (Brigadier has no public way to replace one). */
    @SuppressWarnings("unchecked")
    private static void removeVanilla(RootCommandNode<CommandSourceStack> root, String name) {
        try {
            for (String field : new String[]{"children", "literals"}) {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<String, ?>) f.get(root)).remove(name);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            valoris2.LOGGER.warn("Could not replace the vanilla /{} command", name, e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void register(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        for (String vanilla : new String[]{"msg", "tell", "w", "me"}) removeVanilla(dispatcher.getRoot(), vanilla);

        LiteralCommandNode<CommandSourceStack> msg = dispatcher.register(Commands.literal("msg")
                .then(Commands.argument("player", EntityArgument.player()).then(message().executes(c -> {
                    ChatService.msg(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"),
                            StringArgumentType.getString(c, "message"));
                    return 1;
                }))));
        dispatcher.register(Commands.literal("tell").redirect(msg));
        dispatcher.register(Commands.literal("w").redirect(msg));
        dispatcher.register(Commands.literal("r").then(message().executes(c -> run(c, "message", ChatService::reply))));
        dispatcher.register(Commands.literal("me").then(Commands.argument("action", StringArgumentType.greedyString())
                .executes(c -> run(c, "action", ChatService::me))));

        dispatcher.register(Commands.literal("cc")
                .executes(c -> { ChatService.toggle(c.getSource().getPlayerOrException(), ChatService.Channel.CITY); return 1; })
                .then(message().executes(c -> run(c, "message", ChatService::cityChat))));
        dispatcher.register(Commands.literal("kc")
                .executes(c -> { ChatService.toggle(c.getSource().getPlayerOrException(), ChatService.Channel.KINGDOM); return 1; })
                .then(message().executes(c -> run(c, "message", ChatService::kingdomChat))));

        dispatcher.register(Commands.literal("cmsg").then(Commands.argument("city", StringArgumentType.string())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(ValorisData.get(c.getSource().getServer()).all().stream()
                        .filter(x -> x.founded).map(x -> Msg.q(x.name)), b))
                .then(message().executes(c -> {
                    ChatService.toCity(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "city"),
                            StringArgumentType.getString(c, "message"));
                    return 1;
                }))));
        dispatcher.register(Commands.literal("kmsg").then(Commands.argument("kingdom", StringArgumentType.string())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(ValorisData.get(c.getSource().getServer()).kingdoms()
                        .stream().map(k -> Msg.q(k.name)), b))
                .then(message().executes(c -> {
                    ChatService.toKingdom(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "kingdom"),
                            StringArgumentType.getString(c, "message"));
                    return 1;
                }))));

        dispatcher.register(Commands.literal("vx").then(Commands.literal("spy")
                .requires(s -> s.hasPermission(ModerationService.MOD_LEVEL)).executes(c -> {
                    boolean on = ChatService.toggleSpy(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.literal("Message spy " + (on ? "on." : "off.")), false);
                    return 1;
                })));
    }

    /** Normal chat of a player in the city / kingdom chat mode goes there (after the mute check). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        if (event.isCanceled()) return;
        event.getPlayer().refreshDisplayName(); // the realm (and so the colour, see onNameFormat) may have changed
        if (ChatService.routeChannel(event.getPlayer(), event.getRawText())) event.setCanceled(true);
    }

    /** The name in chat (and other messages) wears the colour of the player's kingdom or independent city. */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        int rgb = ChatService.realmColor(p);
        if (rgb < 0) return;
        event.setDisplayname(event.getDisplayname().copy()
                .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb))));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChatService.forget(event.getEntity().getUUID());
    }
}
