package com.valoris.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

/**
 * Per-player skill XP, stored as a NeoForge data attachment (kept on death).
 * Saved as a map skill id -> XP. Older versions saved a plain list in the order
 * blacksmith, warrior, farmer, miner, hunter; such data is read and converted (warrior XP is dropped).
 */
public final class SkillData {
    private static final String[] LEGACY_ORDER = {"blacksmith", "warrior", "farmer", "miner", "hunter"};

    public static final Codec<SkillData> CODEC = Codec.either(
                    Codec.unboundedMap(Codec.STRING, Codec.DOUBLE), Codec.DOUBLE.listOf())
            .xmap(e -> e.map(SkillData::fromMap, SkillData::fromLegacyList), d -> Either.left(d.toMap()));

    private final double[] xp = new double[Skill.values().length];

    public double xp(Skill skill) {
        return xp[skill.ordinal()];
    }

    public void setXp(Skill skill, double value) {
        xp[skill.ordinal()] = Math.max(0, Math.min(value, SkillCurve.totalFor(SkillCurve.MAX_LEVEL)));
    }

    public int level(Skill skill) {
        return SkillCurve.levelFor(xp(skill));
    }

    private static SkillData fromMap(Map<String, Double> map) {
        SkillData data = new SkillData();
        map.forEach((id, v) -> Skill.byId(id).ifPresent(s -> data.setXp(s, v)));
        return data;
    }

    private static SkillData fromLegacyList(List<Double> list) {
        SkillData data = new SkillData();
        for (int i = 0; i < Math.min(list.size(), LEGACY_ORDER.length); i++) {
            int index = i;
            Skill.byId(LEGACY_ORDER[i]).ifPresent(s -> data.setXp(s, list.get(index)));
        }
        return data;
    }

    private Map<String, Double> toMap() {
        Map<String, Double> map = new HashMap<>();
        for (Skill s : Skill.values()) map.put(s.id(), xp[s.ordinal()]);
        return map;
    }
}
