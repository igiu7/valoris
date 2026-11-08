package com.valoris.tech;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.valoris.valoris2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Era-based melee damage: for an item '<material>_<type>' the damage is
 * materials[material] + (types[type] - referenceBase). A plain sword (type base 3.5) deals
 * 5 in bronze, 6 in iron and 7 in steel. Set on the server from the config and on clients from a packet.
 * Blocking of Epic Knights weapons (their WeaponType, shared by every material) can be changed per type too:
 * maxBlockDamage (0 = cannot block) and weight (a parry works when a roll of 0-17 is above it).
 */
public final class WeaponBalance {
    private static volatile double referenceBase = 3.5;
    private static volatile Map<String, Double> materials = Map.of();
    private static volatile Map<String, Double> types = Map.of();
    private static volatile Map<String, String> itemMaterials = Map.of();
    private static volatile Map<String, Double> maxBlockDamage = Map.of();
    private static volatile Map<String, Double> weight = Map.of();
    private static final Map<Item, Double> CACHE = new HashMap<>();
    /** WeaponType -> its own maxBlockDamage, weight, canBlock (1/0), to restore what the config no longer changes */
    private static final Map<Object, float[]> ORIGINAL = new IdentityHashMap<>();
    private static final String WEAPON_TYPE = "com.magistuarmory.item.WeaponType";
    private static final String WEAPON_ITEM = "com.magistuarmory.item.MedievalWeaponItem";

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
    public static Map<String, Double> maxBlockDamage() { return maxBlockDamage; }
    public static Map<String, Double> weight() { return weight; }

    /** The type of the item in the types table ("steel_rus_sword" -> "russword"), or null. */
    private static String typeKey(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        int split = path.indexOf('_');
        if (itemMaterials.containsKey(id.toString())) {
            // the type is the name without its material prefix, or the whole name
            if (split > 0 && materials.containsKey(path.substring(0, split))) {
                String type = path.substring(split + 1).replace("_", "");
                if (types.containsKey(type)) return type;
            }
            return path.replace("_", "");
        }
        if (split > 0 && materials.containsKey(path.substring(0, split))) return path.substring(split + 1).replace("_", "");
        return null;
    }

    /** Total attack damage for the item, or NaN if it is not balanced by era. */
    public static synchronized double damageFor(Item item) {
        Double cached = CACHE.get(item);
        if (cached != null) return cached;
        double value = Double.NaN;
        String type = typeKey(item);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String fixedMaterial = itemMaterials.get(id.toString());
        String path = id.getPath();
        int split = path.indexOf('_');
        Double material = fixedMaterial != null ? materials.get(fixedMaterial)
                : split > 0 ? materials.get(path.substring(0, split)) : null;
        Double base = type == null ? null : types.get(type);
        if (material != null && base != null) value = material + (base - referenceBase);
        CACHE.put(item, value);
        return value;
    }

    /** Sets the blocking changes and applies them to the weapon types of Epic Knights and its add-ons. */
    public static synchronized void setBlocking(Map<String, Double> block, Map<String, Double> weights) {
        maxBlockDamage = Map.copyOf(block);
        weight = Map.copyOf(weights);
        try {
            applyBlocking();
        } catch (ReflectiveOperationException | RuntimeException e) {
            valoris2.LOGGER.warn("Could not change the blocking of Epic Knights weapons", e);
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void applyBlocking() throws ReflectiveOperationException {
        // only the weapon class of Epic Knights (its add-ons extend it) is looked into: listing the fields of every
        // item would also touch armour classes with client-only fields (HumanoidModel), which on a server fails
        // and floods the log with errors
        Class<?> weaponClass;
        try {
            weaponClass = Class.forName(WEAPON_ITEM);
        } catch (ClassNotFoundException e) {
            return; // Epic Knights is not installed
        }
        Field typeField = weaponClass.getField("type");
        if (!typeField.getType().getName().equals(WEAPON_TYPE)) return;
        Class<?> typeClass = null;
        Field block = null, heavy = null, can = null;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!weaponClass.isInstance(item)) continue;
            Object type = typeField.get(item);
            if (type == null) continue;
            if (typeClass == null) {
                typeClass = type.getClass();
                block = field(typeClass, "maxBlockDamage");
                heavy = field(typeClass, "weight");
                can = field(typeClass, "canBlock");
            }
            String key = typeKey(item);
            if (key == null) key = BuiltInRegistries.ITEM.getKey(item).getPath().replace("_", "");
            Double b = maxBlockDamage.get(key), w = weight.get(key);
            float[] own = ORIGINAL.get(type);
            if (own == null && (b != null || w != null)) {
                own = new float[]{block.getFloat(type), heavy.getFloat(type), can.getBoolean(type) ? 1 : 0};
                ORIGINAL.put(type, own);
            }
            if (own == null) continue;
            block.setFloat(type, b != null ? b.floatValue() : own[0]);
            can.setBoolean(type, b != null ? b > 0 : own[2] > 0);
            heavy.setFloat(type, w != null ? w.floatValue() : own[1]);
        }
    }
}
