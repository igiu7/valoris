package com.valoris.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.valoris.skill.Skill;
import com.valoris.skill.SkillCurve;
import com.valoris.skill.SkillService;
import com.valoris.util.Errors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /valoris skills                              - your skill levels
 * /valoris skills set <player> <skill> <level> - admins (permission level 2), for testing
 */
final class SkillCommands {
    private SkillCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("skills")
                .executes(c -> CommandUtil.run(c, SkillService::info))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Skill.values()).map(Skill::id), b))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, SkillCurve.MAX_LEVEL))
                                                .executes(c -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                                    String id = StringArgumentType.getString(c, "skill");
                                                    Skill skill = Skill.byId(id).orElseThrow(() -> Errors.of("Unknown skill: " + id));
                                                    int level = IntegerArgumentType.getInteger(c, "level");
                                                    SkillService.setLevel(target, skill, level);
                                                    c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName()
                                                            + ": " + skill.title + " set to level " + level + "."), true);
                                                    return 1;
                                                })))));
    }
}
