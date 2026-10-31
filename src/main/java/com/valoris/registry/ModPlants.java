package com.valoris.registry;

import com.valoris.heal.Healing;
import com.valoris.heal.HealingBlockItem;
import com.valoris.heal.HealingItem;
import com.valoris.plant.DeadMedicinalHerbBlock;
import com.valoris.plant.FlaxCropBlock;
import com.valoris.plant.MedicinalHerbBushBlock;
import com.valoris.plant.RiceCropBlock;

import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Medicinal herb bush, flax, rice and the healing items and food made from them. Registered into ModBlocks' registers. */
public final class ModPlants {
    private ModPlants() {}

    public static final DeferredBlock<MedicinalHerbBushBlock> MEDICINAL_HERB_BUSH = ModBlocks.BLOCKS.register("medicinal_herb_bush",
            () -> new MedicinalHerbBushBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).randomTicks().noCollission().sound(SoundType.SWEET_BERRY_BUSH)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<DeadMedicinalHerbBlock> DEAD_MEDICINAL_HERB = ModBlocks.BLOCKS.register("dead_medicinal_herb",
            () -> new DeadMedicinalHerbBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).randomTicks().noCollission().sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<FlaxCropBlock> FLAX_CROP = ModBlocks.BLOCKS.register("flax",
            () -> new FlaxCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));

    public static final DeferredItem<HealingBlockItem> MEDICINAL_HERBS = ModBlocks.ITEMS.register("medicinal_herbs",
            () -> new HealingBlockItem(MEDICINAL_HERB_BUSH.get(), new Item.Properties().stacksTo(16), Healing.Kind.HERBS));

    public static final DeferredItem<HealingBlockItem> MEDICINAL_ROOT = ModBlocks.ITEMS.register("medicinal_root",
            () -> new HealingBlockItem(DEAD_MEDICINAL_HERB.get(), new Item.Properties().stacksTo(16), Healing.Kind.HERBS));

    public static final DeferredItem<Item> FLAX = ModBlocks.ITEMS.register("flax", () -> new Item(new Item.Properties()));

    public static final DeferredItem<ItemNameBlockItem> FLAX_SEEDS = ModBlocks.ITEMS.register("flax_seeds",
            () -> new ItemNameBlockItem(FLAX_CROP.get(), new Item.Properties()));

    public static final DeferredBlock<RiceCropBlock> RICE_CROP = ModBlocks.BLOCKS.register("rice",
            () -> new RiceCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));

    public static final DeferredItem<Item> RICE = ModBlocks.ITEMS.register("rice", () -> new Item(new Item.Properties()));

    public static final DeferredItem<ItemNameBlockItem> RICE_SEEDS = ModBlocks.ITEMS.register("rice_seeds",
            () -> new ItemNameBlockItem(RICE_CROP.get(), new Item.Properties()));

    /** Made like bread (three rice in a row) and just as filling. */
    public static final DeferredItem<Item> RICE_BREAD = ModBlocks.ITEMS.register("rice_bread",
            () -> new Item(new Item.Properties().food(Foods.BREAD)));

    public static final DeferredItem<HealingItem> LINEN_BANDAGE = ModBlocks.ITEMS.register("linen_bandage",
            () -> new HealingItem(new Item.Properties().stacksTo(16), Healing.Kind.BANDAGE));

    public static final DeferredItem<HealingItem> HONEY_POULTICE = ModBlocks.ITEMS.register("honey_poultice",
            () -> new HealingItem(new Item.Properties().stacksTo(16), Healing.Kind.POULTICE));

    public static final DeferredItem<HealingItem> HONEY_HERB_POULTICE = ModBlocks.ITEMS.register("honey_herb_poultice",
            () -> new HealingItem(new Item.Properties().stacksTo(16), Healing.Kind.HERB_POULTICE));

    public static final DeferredItem<HealingItem> HONEY_ROOT_POULTICE = ModBlocks.ITEMS.register("honey_root_poultice",
            () -> new HealingItem(new Item.Properties().stacksTo(16), Healing.Kind.HERB_POULTICE));

    public static final DeferredItem<HealingItem> HERB_ROOT_POULTICE = ModBlocks.ITEMS.register("herb_root_poultice",
            () -> new HealingItem(new Item.Properties().stacksTo(16), Healing.Kind.HERB_ROOT_POULTICE));

    /** Loads this class so its entries join ModBlocks' registers before they are registered. */
    public static void init() {}
}
