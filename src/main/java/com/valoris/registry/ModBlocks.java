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

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
