package com.valoris.registry;

import com.valoris.armory.ArmoryTableBlock;
import com.valoris.city.CityBannerBlock;
import com.valoris.valoris2;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(valoris2.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(valoris2.MODID);

    /** Sword in the stone. Hard to break and blast-proof; only besiegers may break it (see SiegeEvents). */
    public static final DeferredBlock<CityBannerBlock> CITY_BANNER = BLOCKS.register("city_banner",
            () -> new CityBannerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(6.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> CITY_BANNER_ITEM = ITEMS.register("city_banner",
            () -> new BlockItem(CITY_BANNER.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Workbench where all Epic Knights / siege machine gear is made. */
    public static final DeferredBlock<ArmoryTableBlock> ARMORY_TABLE = BLOCKS.register("armory_table",
            () -> new ArmoryTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> ARMORY_TABLE_ITEM = ITEMS.register("armory_table",
            () -> new BlockItem(ARMORY_TABLE.get(), new Item.Properties()));

    /** Large fire two blocks tall: warms and lets players rest within 10 blocks (heal.BonfireBlock). */
    public static final DeferredBlock<com.valoris.heal.BonfireBlock> BONFIRE = BLOCKS.register("bonfire",
            () -> new com.valoris.heal.BonfireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(s -> 15)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> BONFIRE_ITEM = ITEMS.register("bonfire",
            () -> new BlockItem(BONFIRE.get(), new Item.Properties()));

    /** Castle gate: a dark iron grid (made of bronze ingots); connected blocks open and close together (gate.Gates). */
    public static final DeferredBlock<com.valoris.gate.PortcullisBlock> PORTCULLIS = BLOCKS.register("portcullis",
            () -> new com.valoris.gate.PortcullisBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> PORTCULLIS_ITEM = ITEMS.register("portcullis",
            () -> new BlockItem(PORTCULLIS.get(), new Item.Properties()));

    /** A treaty being written (treaty.TreatyItem); saved it becomes a Treaty, which the two parties sign. */
    public static final DeferredItem<com.valoris.treaty.TreatyItem> TREATY_AND_QUILL = ITEMS.register("treaty_and_quill",
            () -> new com.valoris.treaty.TreatyItem(true, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.valoris.treaty.TreatyItem> TREATY = ITEMS.register("treaty",
            () -> new com.valoris.treaty.TreatyItem(false, new Item.Properties().stacksTo(1)));

    /** A burning arrow for bows and crossbows (archery.FireArrowItem). */
    public static final DeferredItem<com.valoris.archery.FireArrowItem> FIRE_ARROW = ITEMS.register("fire_arrow",
            () -> new com.valoris.archery.FireArrowItem(new Item.Properties()));

    /** A clay pot of burning pitch, thrown a few blocks; it sets the ground on fire (archery.FirePotItem). */
    public static final DeferredItem<com.valoris.archery.FirePotItem> FIRE_POT = ITEMS.register("fire_pot",
            () -> new com.valoris.archery.FirePotItem(new Item.Properties().stacksTo(16)));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
