package com.valoris.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.net.ValorisNet;
import com.valoris.tech.TechConfig;
import com.valoris.tech.TechNode;
import com.valoris.tech.TechService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /valoris tech                          - your city's technologies
 * /valoris tech research <id>           - ruler: start researching a technology
 * /valoris tech cancel                   - ruler: cancel the current research
 * /valoris tech finish <city>            - admin: complete a city's current research now
 * /valoris tech reload                   - admin: reload config/valoris2/technology.json
 * /valoris tech grant|revoke <city> <id> - admin
 * /valoris tech era [open|close <era>]   - admin: eras open for research (all but the first start closed)
 */
final class TechCommands {
    private TechCommands() {}

    private static SuggestionProvider<CommandSourceStack> techIds() {
        return (c, b) -> SharedSuggestionProvider.suggest(TechConfig.nodes().keySet(), b);
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tech")
                .executes(c -> CommandUtil.run(c, TechCommands::list))
                .then(Commands.literal("research").then(Commands.argument("id", StringArgumentType.word())
                        .suggests(techIds()).executes(c -> CommandUtil.run(c,
                                p -> TechService.startResearch(p, StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("cancel").executes(c -> CommandUtil.run(c, TechService::cancelResearch)))
                .then(Commands.literal("finish").requires(s -> s.hasPermission(2))
                        .then(CommandUtil.cityArg("city").executes(TechCommands::finish)))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(TechCommands::reload))
                .then(Commands.literal("grant").requires(s -> s.hasPermission(2))
                        .then(CommandUtil.cityArg("city").then(Commands.argument("id", StringArgumentType.word())
                                .suggests(techIds()).executes(c -> grant(c, true)))))
                .then(Commands.literal("revoke").requires(s -> s.hasPermission(2))
                        .then(CommandUtil.cityArg("city").then(Commands.argument("id", StringArgumentType.word())
                                .suggests(techIds()).executes(c -> grant(c, false)))))
                .then(Commands.literal("era").requires(s -> s.hasPermission(2))
                        .executes(TechCommands::eras)
                        .then(Commands.literal("open").then(Commands.argument("era", StringArgumentType.word())
                                .suggests(eraIds()).executes(c -> setEra(c, true))))
                        .then(Commands.literal("close").then(Commands.argument("era", StringArgumentType.word())
                                .suggests(eraIds()).executes(c -> setEra(c, false)))));
    }

    private static SuggestionProvider<CommandSourceStack> eraIds() {
        return (c, b) -> SharedSuggestionProvider.suggest(TechConfig.eraNames().keySet().stream()
                .filter(e -> !e.equals(TechService.FIRST_ERA)), b);
    }

    /** Admin: which eras are open for research. */
    private static int eras(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("Eras (researching an era's technologies needs it to be open):");
        TechConfig.eraNames().forEach((id, name) -> sb.append("\n - ").append(name).append(" [").append(id).append("]: ")
                .append(TechService.eraOpen(id) ? "open" : "closed")
                .append(id.equals(TechService.FIRST_ERA) ? " (always)" : ""));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /** A big title in the middle of every player's screen, with a fanfare. */
    private static void announceEra(net.minecraft.server.MinecraftServer server, String eraName) {
        var times = new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(20, 100, 30);
        var title = new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal(eraName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        var subtitle = new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal("A new era has begun - its technologies can now be researched").withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(times);
            p.connection.send(subtitle);
            p.connection.send(title);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    /** Admin: opens or closes an era (/valoris tech era open iron). Technologies already known stay known. */
    private static int setEra(CommandContext<CommandSourceStack> ctx, boolean open) throws CommandSyntaxException {
        String era = StringArgumentType.getString(ctx, "era");
        if (!TechConfig.eraNames().containsKey(era)) throw Errors.of("Unknown era: " + era);
        if (era.equals(TechService.FIRST_ERA)) throw Errors.of("The first era is always open.");
        ValorisData data = ValorisData.get(ctx.getSource().getServer());
        boolean changed = open ? data.openEras.add(era) : data.openEras.remove(era);
        if (!changed) throw Errors.of("That era is already " + (open ? "open." : "closed."));
        data.touch();
        String name = TechConfig.eraNames().get(era);
        if (open) {
            Msg.broadcast(ctx.getSource().getServer(), Msg.text("A new era begins: the " + name
                    + "! Its technologies can now be researched.", ChatFormatting.GOLD));
            announceEra(ctx.getSource().getServer(), name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("The " + name + " is now " + (open ? "open." : "closed.")), true);
        return 1;
    }

    private static void list(ServerPlayer player) throws CommandSyntaxException {
        City member = Roles.cityOfMember(player).orElseThrow(() -> Errors.of("You do not belong to a city."));
        ValorisData data = ValorisData.get(player.getServer());
        City city = TechService.holder(data, member);
        int[] levels = TechService.cityLevels(player.getServer(), city);
        Msg.info(player, "Technologies of the " + data.kingdomOfCity(member.teamId).map(k -> k.title())
                .orElse("City of " + member.name) + ":");
        for (TechNode node : TechConfig.nodes().values()) {
            TechService.Status st = TechService.status(city, node, levels);
            String detail = st == TechService.Status.LOCKED ? "requires " + TechService.missingPrereqs(city, node)
                    : "needs " + TechService.missingSkills(node, levels);
            if (st == TechService.Status.RESEARCHING) {
                detail = TechService.duration(TechService.remainingSeconds(city)) + " left";
            }
            player.sendSystemMessage(TechService.listLine(node, st, detail));
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        String error = TechConfig.load();
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal("Reload failed: " + error + " (previous technologies kept)"));
            return 0;
        }
        String armoryError = com.valoris.armory.ArmoryCatalog.load(ctx.getSource().getServer());
        if (armoryError != null) {
            ctx.getSource().sendFailure(Component.literal("armory.json: " + armoryError));
        }
        com.valoris.furnace.CookingRestrictions.apply(ctx.getSource().getServer());
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
            ValorisNet.sendWeaponBalance(p);
            ValorisNet.sendArmoryCatalog(p);
        });
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded " + TechConfig.nodes().size() + " technologies and "
                + com.valoris.armory.ArmoryCatalog.entries().size() + " armory entries.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int finish(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String cityName = StringArgumentType.getString(ctx, "city");
        City city = ValorisData.get(ctx.getSource().getServer()).cityByName(cityName)
                .orElseThrow(() -> Errors.of("No such city."));
        if (!TechService.finishNow(ctx.getSource().getServer(), city)) throw Errors.of(city.name + " is not researching anything.");
        ctx.getSource().sendSuccess(() -> Component.literal("Finished the research of " + city.name + "."), true);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        String cityName = StringArgumentType.getString(ctx, "city");
        String id = StringArgumentType.getString(ctx, "id");
        ValorisData data = ValorisData.get(ctx.getSource().getServer());
        City city = data.cityByName(cityName).orElseThrow(() -> Errors.of("No such city."));
        TechService.grant(data, city, id, add);
        data.touch();
        TechService.update(ctx.getSource().getServer(), city);
        ctx.getSource().sendSuccess(() -> Component.literal((add ? "Granted " : "Revoked ") + id + " for " + city.name + "."), true);
        return 1;
    }
}
