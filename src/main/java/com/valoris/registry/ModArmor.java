package com.valoris.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import com.valoris.valoris2;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fur armor made at the Armory Table: wolf fur (Bronze Age) and bear fur (Iron Age). Armor points come from the
 * era (tech.ArmorBalance); the values here only matter if the Armory entries are removed. Model: client.FurArmorModel.
 */
public final class ModArmor {
    private ModArmor() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, valoris2.MODID);

    private static Holder<ArmorMaterial> material(String id, int helmet, int chest, int legs, int boots, Supplier<? extends ItemLike> repair) {
        return MATERIALS.register(id, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.BODY, chest);
            return new ArmorMaterial(defense, 12, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(repair.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, id))), 0.0F, 0.0F);
        });
    }

    public static final Holder<ArmorMaterial> WOLF_FUR = material("wolf_fur", 2, 6, 4, 2, ModAnimals.FUR);
    public static final Holder<ArmorMaterial> BEAR_FUR = material("bear_fur", 3, 6, 5, 2, ModAnimals.BEAR_FUR);

    private static DeferredItem<ArmorItem> piece(String id, Holder<ArmorMaterial> material, ArmorItem.Type type, int durability) {
        return ModBlocks.ITEMS.register(id, () -> new ArmorItem(material, type,
                new Item.Properties().durability(type.getDurability(durability))));
    }

    public static final DeferredItem<ArmorItem> WOLF_FUR_HELMET = piece("wolf_fur_helmet", WOLF_FUR, ArmorItem.Type.HELMET, 10);
    public static final DeferredItem<ArmorItem> WOLF_FUR_CHESTPLATE = piece("wolf_fur_chestplate", WOLF_FUR, ArmorItem.Type.CHESTPLATE, 10);
    public static final DeferredItem<ArmorItem> WOLF_FUR_LEGGINGS = piece("wolf_fur_leggings", WOLF_FUR, ArmorItem.Type.LEGGINGS, 10);
    public static final DeferredItem<ArmorItem> WOLF_FUR_BOOTS = piece("wolf_fur_boots", WOLF_FUR, ArmorItem.Type.BOOTS, 10);
    public static final DeferredItem<ArmorItem> BEAR_FUR_HELMET = piece("bear_fur_helmet", BEAR_FUR, ArmorItem.Type.HELMET, 15);
    public static final DeferredItem<ArmorItem> BEAR_FUR_CHESTPLATE = piece("bear_fur_chestplate", BEAR_FUR, ArmorItem.Type.CHESTPLATE, 15);
    public static final DeferredItem<ArmorItem> BEAR_FUR_LEGGINGS = piece("bear_fur_leggings", BEAR_FUR, ArmorItem.Type.LEGGINGS, 15);
    public static final DeferredItem<ArmorItem> BEAR_FUR_BOOTS = piece("bear_fur_boots", BEAR_FUR, ArmorItem.Type.BOOTS, 15);

    /** Registers the materials; the items join ModBlocks' item register. Call before ModBlocks.register. */
    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
    }
}
