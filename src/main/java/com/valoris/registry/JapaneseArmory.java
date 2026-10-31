package com.valoris.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import com.valoris.valoris2;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

/**
 * Armor and weapons of feudal Japan, ported from "Epic Knights: Japanese Armory" 1.6.2 by chivas477 (Academic Free
 * License 3.0, see assets/valoris2/japanese_armory_LICENSE.txt). Made at the Armory Table (armory.json) with the
 * technologies of the other armor and weapons: the samurai set exists in every era (bronze, iron, steel), the
 * shinobi set and the masks belong to the iron age, the samurai helmet with the oni mask to the steel age; their
 * armor points come from the era (tech.ArmorBalance). Every metal weapon exists in bronze, iron and steel with the
 * era damage of tech.WeaponBalance (steel_katana = steel + katana). The values below only matter outside of those
 * systems. Model of the Shinobi chest piece: client.JapaneseArmorModels.
 */
public final class JapaneseArmory {
    private JapaneseArmory() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, valoris2.MODID);

    // ------------------------------------------------------------------ materials

    public static final DeferredItem<Item> FABRIC = ModBlocks.ITEMS.registerSimpleItem("japanese_fabric");
    public static final DeferredItem<Item> LEATHER_STRIP = ModBlocks.ITEMS.registerSimpleItem("japanese_leather_strip");
    public static final DeferredItem<Item> HILT = ModBlocks.ITEMS.registerSimpleItem("japanese_hilt");

    // ------------------------------------------------------------------ armor

    /** Armor material whose texture is textures/models/armor/<id>_layer_1/2.png. */
    private static Holder<ArmorMaterial> material(String id, int helmet, int chest, int legs, int boots, int enchant,
                                                  Holder<SoundEvent> sound, float toughness, float knockback,
                                                  Supplier<? extends ItemLike> repair) {
        return MATERIALS.register(id, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.BODY, chest);
            return new ArmorMaterial(defense, enchant, sound, () -> Ingredient.of(repair.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, id))),
                    toughness, knockback);
        });
    }

    private static Holder<ArmorMaterial> mask(String id) {
        return material(id, 3, 0, 0, 0, 25, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.1F, () -> Items.IRON_INGOT);
    }

    // samurai armor in every era (the values of armory.json's armorBalance; textures recoloured from the iron set)
    public static final Holder<ArmorMaterial> BRONZE_SAMURAI = material("japanese_bronze_samurai", 2, 6, 4, 2, 12,
            SoundEvents.ARMOR_EQUIP_GOLD, 0.0F, 0.0F, () -> Items.COPPER_INGOT);
    public static final Holder<ArmorMaterial> SAMURAI = material("japanese_samurai", 3, 6, 5, 2, 9,
            SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, () -> Items.IRON_INGOT);
    public static final Holder<ArmorMaterial> STEEL_SAMURAI = material("japanese_steel_samurai", 3, 8, 6, 3, 10,
            SoundEvents.ARMOR_EQUIP_IRON, 1.5F, 0.0F, () -> Items.IRON_INGOT);
    public static final Holder<ArmorMaterial> SHINOBI = material("japanese_shinobi", 3, 7, 5, 2, 25,
            SoundEvents.ARMOR_EQUIP_LEATHER, 1.4F, 0.02F, () -> Items.IRON_INGOT);
    public static final Holder<ArmorMaterial> SHINOBI_CHEST = material("japanese_shinobi_chest", 3, 7, 5, 2, 25,
            SoundEvents.ARMOR_EQUIP_LEATHER, 1.4F, 0.02F, () -> Items.IRON_INGOT);
    public static final Holder<ArmorMaterial> SAMURAI_ONI = material("japanese_samurai_oni", 4, 0, 0, 0, 25,
            SoundEvents.ARMOR_EQUIP_IRON, 1.8F, 0.05F, () -> Items.IRON_INGOT);
    public static final Holder<ArmorMaterial> ONI = mask("japanese_oni");
    public static final Holder<ArmorMaterial> HANNYA = mask("japanese_hannya");
    public static final Holder<ArmorMaterial> HYOTTOKO = mask("japanese_hyottoko");
    public static final Holder<ArmorMaterial> TENGU = mask("japanese_tengu");
    public static final Holder<ArmorMaterial> KITSUNE = mask("japanese_kitsune");

    private static DeferredItem<ArmorItem> piece(String id, Holder<ArmorMaterial> material, ArmorItem.Type type, String era) {
        return ModBlocks.ITEMS.register(id, () -> new ArmorItem(material, type,
                new Item.Properties().durability(EraDurability.of(era, type))));
    }

    /** "<era>_samurai_helmet" ... "<era>_samurai_boots". */
    private static void samuraiSet(String era, Holder<ArmorMaterial> material) {
        piece(era + "_samurai_helmet", material, ArmorItem.Type.HELMET, era);
        piece(era + "_samurai_chestplate", material, ArmorItem.Type.CHESTPLATE, era);
        piece(era + "_samurai_leggings", material, ArmorItem.Type.LEGGINGS, era);
        piece(era + "_samurai_boots", material, ArmorItem.Type.BOOTS, era);
    }

    static {
        samuraiSet("bronze", BRONZE_SAMURAI);
        samuraiSet("iron", SAMURAI);
        samuraiSet("steel", STEEL_SAMURAI);
    }
    public static final DeferredItem<ArmorItem> SAMURAI_ONI_HELMET = piece("samurai_oni_helmet", SAMURAI_ONI, ArmorItem.Type.HELMET, "steel");
    public static final DeferredItem<ArmorItem> SHINOBI_HELMET = piece("shinobi_helmet", SHINOBI, ArmorItem.Type.HELMET, "iron");
    public static final DeferredItem<ArmorItem> SHINOBI_CHESTPLATE = piece("shinobi_chestplate", SHINOBI_CHEST, ArmorItem.Type.CHESTPLATE, "iron");
    public static final DeferredItem<ArmorItem> SHINOBI_LEGGINGS = piece("shinobi_leggings", SHINOBI, ArmorItem.Type.LEGGINGS, "iron");
    public static final DeferredItem<ArmorItem> SHINOBI_BOOTS = piece("shinobi_boots", SHINOBI, ArmorItem.Type.BOOTS, "iron");
    public static final DeferredItem<ArmorItem> ONI_MASK = piece("oni_mask", ONI, ArmorItem.Type.HELMET, "iron");
    public static final DeferredItem<ArmorItem> HANNYA_MASK = piece("hannya_mask", HANNYA, ArmorItem.Type.HELMET, "iron");
    public static final DeferredItem<ArmorItem> HYOTTOKO_MASK = piece("hyottoko_mask", HYOTTOKO, ArmorItem.Type.HELMET, "iron");
    public static final DeferredItem<ArmorItem> TENGU_MASK = piece("tengu_mask", TENGU, ArmorItem.Type.HELMET, "iron");
    public static final DeferredItem<ArmorItem> KITSUNE_MASK = piece("kitsune_mask", KITSUNE, ArmorItem.Type.HELMET, "iron");

    // ------------------------------------------------------------------ weapons

    private static Tier tier(int uses, int enchant, Supplier<? extends ItemLike> repair, TagKey<Block> incorrect) {
        return new Tier() {
            @Override public int getUses() { return uses; }
            @Override public float getSpeed() { return 4.0F; }
            @Override public float getAttackDamageBonus() { return 0.0F; }
            @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrect; }
            @Override public int getEnchantmentValue() { return enchant; }
            @Override public Ingredient getRepairIngredient() { return Ingredient.of(repair.get()); }
        };
    }

    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "japanese_reach");

    /**
     * damage: total attack damage (with the player's own 1), speed: attacks per second, reach: extra blocks.
     * Era weapons get their damage from tech.WeaponBalance instead.
     */
    private static DeferredItem<SwordItem> weapon(String id, Tier tier, double damage, double speed, double reach) {
        return ModBlocks.ITEMS.register(id, () -> {
            ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - 1.0,
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed - 4.0,
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            if (reach > 0) b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, reach,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            return new SwordItem(tier, new Item.Properties().attributes(b.build()));
        });
    }

    private static final Tier WOOD = tier(100, 15, () -> Items.OAK_PLANKS, BlockTags.INCORRECT_FOR_WOODEN_TOOL);

    /**
     * One metal weapon in every era: "bronze_<type>", "iron_<type>", "steel_<type>". Their damage comes from
     * tech.WeaponBalance (material + type); damage here is the iron value used without it (bronze -1, steel +1).
     * uses: durability of the iron one (bronze x0.7, steel x1.5).
     */
    private static void eraWeapon(String type, int uses, double damage, double speed, double reach) {
        weapon("bronze_" + type, tier((int) (uses * 0.7), 18, () -> Items.COPPER_INGOT, BlockTags.INCORRECT_FOR_STONE_TOOL),
                damage - 1, speed, reach);
        weapon("iron_" + type, tier(uses, 14, () -> Items.IRON_INGOT, BlockTags.INCORRECT_FOR_IRON_TOOL), damage, speed, reach);
        weapon("steel_" + type, tier((int) (uses * 1.5), 12, () -> Items.IRON_INGOT, BlockTags.INCORRECT_FOR_DIAMOND_TOOL),
                damage + 1, speed, reach);
    }

    static {
        eraWeapon("katana", 300, 7.5, 1.4, 0);
        eraWeapon("wakizashi", 300, 6.5, 1.5, 0);
        eraWeapon("kunai", 200, 4.0, 2.0, 0);
        eraWeapon("kama", 200, 5.0, 1.9, 0);
        eraWeapon("sai", 200, 5.0, 2.0, 0);
        eraWeapon("jutte", 200, 5.1, 1.8, 0);
        eraWeapon("shuriken", 150, 4.5, 2.0, 0);
        eraWeapon("naginata", 400, 10.0, 0.8, 1.0);
        eraWeapon("yari", 400, 9.3, 0.9, 1.0);
        eraWeapon("sasumata", 400, 9.0, 0.8, 1.0);
        eraWeapon("tetsubo", 400, 10.0, 0.8, 0);
    }

    public static final DeferredItem<SwordItem> WOODEN_KATANA = weapon("wooden_katana", WOOD, 4.0, 1.4, 0);
    public static final DeferredItem<SwordItem> WOODEN_NAGINATA = weapon("wooden_naginata", WOOD, 4.0, 0.8, 1.0);
    public static final DeferredItem<SwordItem> WOODEN_TONFA = weapon("wooden_tonfa", WOOD, 3.5, 2.0, 0);

    /** Registers the armor materials; the items join ModBlocks' item register. Call before ModBlocks.register. */
    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
    }
}
