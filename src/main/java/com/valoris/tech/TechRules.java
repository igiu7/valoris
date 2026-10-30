package com.valoris.tech;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Decides which technology (if any) an item needs before it can be crafted.
 * Underscores are ignored when comparing type keys, so "bronze_muscle_cuirass" matches "bronzeMuscleCuirass".
 */
public final class TechRules {
    /** Marker for items that can never be crafted (alwaysLocked). */
    public static final String NEVER = "__never__";
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    private static final Map<String, String> exact = new HashMap<>();
    private static final Map<String, String> mods = new HashMap<>();
    private static final Map<String, String> armor = new HashMap<>();
    private static final Map<String, String> shields = new HashMap<>();
    private static final Map<String, String> shieldMaterials = new HashMap<>();
    private static final Map<String, String> materials = new HashMap<>();
    private static final Set<String> shieldTypes = new HashSet<>();
    private static final Map<String, String> neverMods = new HashMap<>();
    private static final Map<String, String> neverExact = new HashMap<>();
    private static final Map<Item, Optional<String>> cache = new HashMap<>();

    private TechRules() {}

    static String norm(String s) {
        return s.replace("_", "").toLowerCase();
    }

    static synchronized void rebuild() {
        exact.clear(); mods.clear(); armor.clear(); shields.clear(); shieldMaterials.clear(); materials.clear();
        shieldTypes.clear(); neverMods.clear(); neverExact.clear(); cache.clear();
        for (TechNode node : TechConfig.nodes().values()) {
            for (String rule : node.unlocks()) add(rule, node.id(), exact, mods);
        }
        for (String rule : TechConfig.alwaysLocked()) add(rule, NEVER, neverExact, neverMods);
        // every shield type the config knows, so "<material>_<shield type>" can be recognised
        shieldTypes.addAll(shields.keySet());
        shieldTypes.addAll(java.util.List.of("buckler", "target", "roundshield", "heatershield", "kiteshield",
                "corruptedroundshield", "tartsche", "ellipticalshield", "rondache", "pavise",
                "republicscutum", "imperialscutum", "tureos", "hoplon", "pelta"));
    }

    private static void add(String rule, String target, Map<String, String> exactMap, Map<String, String> modMap) {
        int colon = rule.indexOf(':');
        if (colon < 0) return;
        String kind = rule.substring(0, colon);
        String value = rule.substring(colon + 1).toLowerCase();
        switch (kind) {
            case "item" -> exactMap.putIfAbsent(value, target);
            case "mod" -> modMap.putIfAbsent(value, target);
            case "armor" -> armor.putIfAbsent(norm(value), target);
            case "shield" -> shields.putIfAbsent(norm(value), target);
            case "shieldmaterial" -> shieldMaterials.putIfAbsent(value, target);
            case "material" -> materials.putIfAbsent(value, target);
            default -> { }
        }
    }

    /** The technology id needed to craft this stack, NEVER, or empty when it is always allowed. */
    public static synchronized Optional<String> requiredFor(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return cache.computeIfAbsent(stack.getItem(), item -> compute(item, stack.isDamageableItem()));
    }

    private static Optional<String> compute(Item item, boolean damageable) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String full = id.toString();
        String path = id.getPath();
        String flat = norm(path);
        int us = path.indexOf('_');
        String prefix = us > 0 ? path.substring(0, us) : "";
        String rest = us > 0 ? norm(path.substring(us + 1)) : "";

        String hit = exact.get(full);
        if (hit != null) return Optional.of(hit);

        // armor: "key" or "key_<slot>"
        hit = armor.get(flat);
        if (hit == null) {
            for (String slot : SLOTS) {
                if (path.endsWith("_" + slot)) {
                    hit = armor.get(norm(path.substring(0, path.length() - slot.length() - 1)));
                    if (hit != null) break;
                }
            }
        }
        if (hit != null) return Optional.of(hit);

        // shields: "type" or "<material>_<type>"
        hit = shields.get(flat);
        if (hit == null && !rest.isEmpty() && shieldTypes.contains(rest)) {
            hit = shieldMaterials.get(prefix);
            if (hit == null) hit = shields.get(rest);
        }
        if (hit != null) return Optional.of(hit);

        // weapons/tools/armor by material prefix ("steel_zweihander")
        if (damageable && !prefix.isEmpty()) {
            hit = materials.get(prefix);
            if (hit != null) return Optional.of(hit);
        }

        // an item named in alwaysLocked stays locked even when a technology unlocks its whole mod
        if (neverExact.containsKey(full)) return Optional.of(NEVER);

        hit = mods.get(id.getNamespace());
        if (hit != null) return Optional.of(hit);

        if (neverMods.containsKey(id.getNamespace())) return Optional.of(NEVER);
        return Optional.empty();
    }
}
