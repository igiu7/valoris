package com.valoris.skill;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class SkillService {
    private SkillService() {}

    public static SkillData data(ServerPlayer player) {
        return player.getData(SkillAttachments.SKILLS);
    }

    private static double multiplier(Skill skill) {
        return switch (skill) {
            case BLACKSMITH -> Config.XP_MULT_BLACKSMITH.get();
            case FARMER -> Config.XP_MULT_FARMER.get();
            case MINER -> Config.XP_MULT_MINER.get();
            case HUNTER -> Config.XP_MULT_HUNTER.get();
        };
    }

    /** Adds XP (scaled by the config multiplier) and announces level-ups. */
    public static void addXp(ServerPlayer player, Skill skill, double baseAmount) {
        if (baseAmount <= 0 || player.isCreative() || player.isSpectator()) return;
        double amount = baseAmount * multiplier(skill);
        if (amount <= 0) return;

        SkillData data = data(player);
        int before = data.level(skill);
        if (before >= SkillCurve.MAX_LEVEL) return;
        data.setXp(skill, data.xp(skill) + amount);
        player.setData(SkillAttachments.SKILLS, data);
        com.valoris.data.DataVault.noteSkills(player, data); // the server's own copy (valoris_data/skills.json)
        int after = data.level(skill);

        if (after > before) {
            com.valoris.tech.TechService.cacheSkills(player);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
            player.sendSystemMessage(Msg.text(skill.title + " level up! You are now level " + after + ".", ChatFormatting.GOLD));
            if (after == SkillCurve.MAX_LEVEL) {
                Msg.broadcast(player.getServer(), Msg.text(player.getGameProfile().getName()
                        + " has mastered the " + skill.title + " skill (level " + SkillCurve.MAX_LEVEL + ")!", ChatFormatting.GOLD));
            }
        } else if (Config.SHOW_XP_GAIN.get()) {
            double xp = data.xp(skill);
            String gain = amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount);
            player.displayClientMessage(Component.literal("+" + gain + " " + skill.title + " XP  ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Lv " + after + "  " + SkillCurve.progressInLevel(xp) + " / "
                            + SkillCurve.needFor(after)).withStyle(ChatFormatting.GRAY)), true);
        }
    }

    public static void info(ServerPlayer player) {
        SkillData data = data(player);
        StringBuilder sb = new StringBuilder("Skills:");
        for (Skill s : Skill.values()) {
            int level = data.level(s);
            sb.append("\n - ").append(s.title).append(": level ").append(level);
            if (level < SkillCurve.MAX_LEVEL) {
                sb.append(" (").append(SkillCurve.progressInLevel(data.xp(s))).append(" / ")
                        .append(SkillCurve.needFor(level)).append(" XP)");
            } else {
                sb.append(" (max)");
            }
        }
        Msg.info(player, sb.toString());
    }

    /** Admin: set a skill to the start of the given level. */
    public static void setLevel(ServerPlayer target, Skill skill, int level) throws CommandSyntaxException {
        SkillData data = data(target);
        data.setXp(skill, SkillCurve.totalFor(level));
        target.setData(SkillAttachments.SKILLS, data);
        com.valoris.data.DataVault.noteSkills(target, data); // also lowering: the copy must not bring it back
        com.valoris.tech.TechService.cacheSkills(target);
    }
}
