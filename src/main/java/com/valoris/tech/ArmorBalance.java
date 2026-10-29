package com.valoris.tech;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.valoris.armory.ArmoryEntry;
import com.valoris.valoris2;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Era armor: every armor piece of the Armory Table's "armor" category gets the armor, toughness and knockback
 * resistance of its era (armory.json -> armorBalance), whatever mod it comes from; so does horse armor of the
 * "horse" category (body slot, "horse" value).
 * Values per era: helmet, chestplate, leggings, boots, toughness, knockback resistance, horse.
 * Set on the server from armory.json and on clients from the catalog packet.
 */
public final class ArmorBalance {
    private static final EquipmentSlotGroup[] GROUPS = {EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
            EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET, EquipmentSlotGroup.BODY};
    private static final String[] NAMES = {"helmet", "chestplate", "leggings", "boots", "horse"};
    /** Index of each slot's armor value in the era values (helmet..boots, then horse after toughness/knockback). */
    private static final int[] VALUE = {0, 1, 2, 3, 6};

    private static volatile Map<Item, double[]> byItem = Map.of();
    private static volatile Map<Item, String> eraOf = Map.of();

    private ArmorBalance() {}

    /** Era of an armor piece of the Armory Table's "armor" category, or null. */
    public static String era(Item item) {
        return eraOf.get(item);
    }

    public static synchronized void set(Map<String, double[]> eras, List<ArmoryEntry> entries) {
        Map<Item, double[]> map = new HashMap<>();
        Map<Item, String> era = new HashMap<>();
        for (ArmoryEntry e : entries) {
            boolean horse = e.category().equals("horse");
            if (!horse && !e.category().equals("armor")) continue;
            if (!horse) era.put(e.result().getItem(), e.era());
            double[] v = eras.get(e.era());
            // the saddle sits in the horse tab too, but it is no armor
            if (horse && !(e.result().getItem() instanceof net.minecraft.world.item.AnimalArmorItem)) continue;
            if (v != null && v.length >= (horse ? 7 : 6)) map.put(e.result().getItem(), v);
        }
        byItem = map;
        eraOf = era;
    }

    private static int slotIndex(ItemStack stack, ItemAttributeModifierEvent event) {
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
            for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i] == group) return i;
        }
        for (var e : event.getModifiers()) {
            if (!e.attribute().equals(Attributes.ARMOR)) continue;
            for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i] == e.slot()) return i;
        }
        return -1;
    }

    /** Replaces the armor attributes of era armor. Returns true if the item was era armor. */
    public static boolean apply(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        double[] v = byItem.get(stack.getItem());
        if (v == null) return false;
        int slot = slotIndex(stack, event);
        if (slot < 0) return true;
        EquipmentSlotGroup group = GROUPS[slot];
        event.removeIf(e -> e.attribute().equals(Attributes.ARMOR) || e.attribute().equals(Attributes.ARMOR_TOUGHNESS)
                || e.attribute().equals(Attributes.KNOCKBACK_RESISTANCE));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "era_armor_" + NAMES[slot]);
        double points = v.length > VALUE[slot] ? v[VALUE[slot]] : 0;
        if (points > 0) event.addModifier(Attributes.ARMOR,
                new AttributeModifier(id, points, AttributeModifier.Operation.ADD_VALUE), group);
        if (v[4] > 0) event.addModifier(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(id, v[4], AttributeModifier.Operation.ADD_VALUE), group);
        if (v[5] > 0) event.addModifier(Attributes.KNOCKBACK_RESISTANCE,
                new AttributeModifier(id, v[5], AttributeModifier.Operation.ADD_VALUE), group);
        return true;
    }
}
