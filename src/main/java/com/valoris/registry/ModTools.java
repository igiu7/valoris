package com.valoris.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Bronze tools: between stone and iron; the first tools that can mine iron ore (stone ones no longer can).
 * Steel tools: as good as diamond ones (diamond and netherite tools cannot be crafted).
 */
public final class ModTools {
    private ModTools() {}

    private static final TagKey<Item> BRONZE_INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/bronze"));
    /** Blocks bronze tools cannot mine: those that need iron or diamond tools (iron ore is mineable). */
    public static final TagKey<net.minecraft.world.level.block.Block> INCORRECT_FOR_BRONZE = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("valoris2", "incorrect_for_bronze_tool"));

    /** Stone: 131 uses, speed 4, +1 damage. Iron: 250 uses, speed 6, +2 damage. */
    public static final Tier BRONZE = new SimpleTier(INCORRECT_FOR_BRONZE, 190, 5.0F, 1.5F, 10,
            () -> Ingredient.of(BRONZE_INGOTS));

    public static final DeferredItem<PickaxeItem> BRONZE_PICKAXE = ModBlocks.ITEMS.register("bronze_pickaxe",
            () -> new PickaxeItem(BRONZE, new Item.Properties().attributes(PickaxeItem.createAttributes(BRONZE, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> BRONZE_AXE = ModBlocks.ITEMS.register("bronze_axe",
            () -> new AxeItem(BRONZE, new Item.Properties().attributes(AxeItem.createAttributes(BRONZE, 6.5F, -3.15F))));
    public static final DeferredItem<ShovelItem> BRONZE_SHOVEL = ModBlocks.ITEMS.register("bronze_shovel",
            () -> new ShovelItem(BRONZE, new Item.Properties().attributes(ShovelItem.createAttributes(BRONZE, 1.5F, -3.0F))));
    public static final DeferredItem<HoeItem> BRONZE_HOE = ModBlocks.ITEMS.register("bronze_hoe",
            () -> new HoeItem(BRONZE, new Item.Properties().attributes(HoeItem.createAttributes(BRONZE, -1.5F, -1.5F))));

    private static final TagKey<Item> STEEL_INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/steel"));

    /** Same numbers as the diamond tier: mines everything diamond mines, 1561 uses, speed 8. */
    public static final Tier STEEL = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 8.0F, 3.0F, 10,
            () -> Ingredient.of(STEEL_INGOTS));

    public static final DeferredItem<PickaxeItem> STEEL_PICKAXE = ModBlocks.ITEMS.register("steel_pickaxe",
            () -> new PickaxeItem(STEEL, new Item.Properties().attributes(PickaxeItem.createAttributes(STEEL, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> STEEL_AXE = ModBlocks.ITEMS.register("steel_axe",
            () -> new AxeItem(STEEL, new Item.Properties().attributes(AxeItem.createAttributes(STEEL, 5.0F, -3.0F))));
    public static final DeferredItem<ShovelItem> STEEL_SHOVEL = ModBlocks.ITEMS.register("steel_shovel",
            () -> new ShovelItem(STEEL, new Item.Properties().attributes(ShovelItem.createAttributes(STEEL, 1.5F, -3.0F))));
    public static final DeferredItem<HoeItem> STEEL_HOE = ModBlocks.ITEMS.register("steel_hoe",
            () -> new HoeItem(STEEL, new Item.Properties().attributes(HoeItem.createAttributes(STEEL, -3.0F, 0.0F))));

    /** Loads this class so its entries join ModBlocks' registers before they are registered. */
    public static void init() {}
}
