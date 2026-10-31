package com.valoris.registry;

import java.util.Map;

import net.minecraft.world.item.ArmorItem;

/**
 * Durability of an armor piece of an era, for the armor sets that have none of their own era (registry.JapaneseArmory,
 * compat.SamuraiDynastyRules): the median of the era armor of Epic Knights and its addons (their armor configs).
 */
public final class EraDurability {
    private EraDurability() {}

    /** helmet, chestplate, leggings, boots */
    private static final Map<String, int[]> ERAS = Map.of(
            "bronze", new int[]{160, 200, 200, 100},
            "iron", new int[]{200, 240, 235, 200},
            "steel", new int[]{275, 335, 315, 230});

    public static int of(String era, ArmorItem.Type type) {
        int[] v = ERAS.get(era);
        return v[switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            default -> 3;
        }];
    }
}
