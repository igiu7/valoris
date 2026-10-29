package com.valoris.tech;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Era-based melee damage: for an item '<material>_<type>' the damage is
 * materials[material] + (types[type] - referenceBase). A plain sword (type base 3.5) deals
 * 5 in bronze, 6 in iron and 7 in steel. Set on the server from the config and on clients from a packet.
 */
public final class WeaponBalance {
    private static volatile double referenceBase = 3.5;
    private static volatile Map<String, Double> materials = Map.of();
    private static volatile Map<String, Double> types = Map.of();
    private static volatile Map<String, String> itemMaterials = Map.of();
    private static final Map<Item, Double> CACHE = new HashMap<>();

    private WeaponBalance() {}

    public static synchronized void set(double reference, Map<String, Double> mats, Map<String, Double> typeTable,
                                        Map<String, String> perItem) {
        referenceBase = reference;
        materials = Map.copyOf(mats);
        types = Map.copyOf(typeTable);
        itemMaterials = Map.copyOf(perItem);
        CACHE.clear();
    }

    public static double referenceBase() { return referenceBase; }
    public static Map<String, Double> materials() { return materials; }
    public static Map<String, Double> types() { return types; }
    public static Map<String, String> itemMaterials() { return itemMaterials; }

    /** Total attack damage for the item, or NaN if it is not balanced by era. */
    public static synchronized double damageFor(Item item) {
        Double cached = CACHE.get(item);
        if (cached != null) return cached;
        double value = Double.NaN;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        String fixedMaterial = itemMaterials.get(id.toString());
        int split = path.indexOf('_');
        if (fixedMaterial != null) {
            Double material = materials.get(fixedMaterial);
            // the type is the name without its material prefix ("steel_rus_sword" -> "russword"), or the whole name
            Double type = split > 0 && materials.containsKey(path.substring(0, split))
                    ? types.get(path.substring(split + 1).replace("_", "")) : null;
            if (type == null) type = types.get(path.replace("_", ""));
            if (material != null && type != null) value = material + (type - referenceBase);
        } else if (split > 0) {
            Double material = materials.get(path.substring(0, split));
            Double type = types.get(path.substring(split + 1).replace("_", ""));
            if (material != null && type != null) value = material + (type - referenceBase);
        }
        CACHE.put(item, value);
        return value;
    }
}
