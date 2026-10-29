package com.valoris.registry;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Overworld quartz ore (no mod in the pack adds one). Registered into ModBlocks' registers. */
public final class ModOres {
    private ModOres() {}

    public static final DeferredBlock<DropExperienceBlock> QUARTZ_ORE = ModBlocks.BLOCKS.register("quartz_ore",
            () -> new DropExperienceBlock(UniformInt.of(2, 5), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_QUARTZ_ORE = ModBlocks.BLOCKS.register("deepslate_quartz_ore",
            () -> new DropExperienceBlock(UniformInt.of(2, 5), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));

    public static final DeferredItem<BlockItem> QUARTZ_ORE_ITEM = ModBlocks.ITEMS.register("quartz_ore",
            () -> new BlockItem(QUARTZ_ORE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DEEPSLATE_QUARTZ_ORE_ITEM = ModBlocks.ITEMS.register("deepslate_quartz_ore",
            () -> new BlockItem(DEEPSLATE_QUARTZ_ORE.get(), new Item.Properties()));

    /** Loads this class so its entries join ModBlocks' registers before they are registered. */
    public static void init() {}
}
