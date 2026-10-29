package com.valoris.furnace;

import java.util.function.Supplier;

import com.valoris.registry.ModBlocks;
import com.valoris.valoris2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FurnaceRegistry {
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, valoris2.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, valoris2.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, valoris2.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, valoris2.MODID);

    // ---- recipe types ("valoris2:bloomery" / "valoris2:steelmaking" in recipe json)
    public static final Supplier<RecipeType<ValorisCookingRecipe>> BLOOMERY = RECIPE_TYPES.register("bloomery",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "bloomery")));
    public static final Supplier<RecipeType<ValorisCookingRecipe>> STEELMAKING = RECIPE_TYPES.register("steelmaking",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "steelmaking")));

    // "valoris2:alloying": the bronze furnace (two inputs)
    public static final Supplier<RecipeType<AlloyRecipe>> ALLOYING = RECIPE_TYPES.register("alloying",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "alloying")));
    public static final Supplier<RecipeSerializer<AlloyRecipe>> ALLOYING_SERIALIZER = SERIALIZERS.register("alloying",
            AlloyRecipe.Serializer::new);

    public static final Supplier<RecipeSerializer<ValorisCookingRecipe>> BLOOMERY_SERIALIZER = SERIALIZERS.register("bloomery",
            () -> new SimpleCookingSerializer<>((group, category, ingredient, result, xp, time) ->
                    new ValorisCookingRecipe(false, group, category, ingredient, result, xp, time), 400));
    public static final Supplier<RecipeSerializer<ValorisCookingRecipe>> STEELMAKING_SERIALIZER = SERIALIZERS.register("steelmaking",
            () -> new SimpleCookingSerializer<>((group, category, ingredient, result, xp, time) ->
                    new ValorisCookingRecipe(true, group, category, ingredient, result, xp, time), 600));

    // ---- blocks and items
    public static final DeferredBlock<ValorisFurnaceBlock> BLOOMERY_BLOCK = ModBlocks.BLOCKS.register("bloomery",
            () -> new ValorisFurnaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS).noOcclusion()
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 13 : 0), false));
    public static final DeferredBlock<ValorisFurnaceBlock> STEEL_FURNACE_BLOCK = ModBlocks.BLOCKS.register("steel_furnace",
            () -> new ValorisFurnaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .strength(5.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 15 : 0), true));

    public static final DeferredBlock<BronzeFurnaceBlock> BRONZE_FURNACE_BLOCK = ModBlocks.BLOCKS.register("bronze_furnace",
            () -> new BronzeFurnaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(3.0F).requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS).noOcclusion()
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 12 : 0)));
    public static final DeferredItem<BlockItem> BRONZE_FURNACE_ITEM = ModBlocks.ITEMS.register("bronze_furnace",
            () -> new BlockItem(BRONZE_FURNACE_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> BLOOMERY_ITEM = ModBlocks.ITEMS.register("bloomery",
            () -> new BlockItem(BLOOMERY_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> STEEL_FURNACE_ITEM = ModBlocks.ITEMS.register("steel_furnace",
            () -> new BlockItem(STEEL_FURNACE_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ValorisFurnaceBlockEntity>> BLOOMERY_BE = BLOCK_ENTITIES.register("bloomery",
            () -> BlockEntityType.Builder.of(ValorisFurnaceBlockEntity::bloomery, BLOOMERY_BLOCK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ValorisFurnaceBlockEntity>> STEEL_FURNACE_BE = BLOCK_ENTITIES.register("steel_furnace",
            () -> BlockEntityType.Builder.of(ValorisFurnaceBlockEntity::steel, STEEL_FURNACE_BLOCK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<BronzeFurnaceBlockEntity>> BRONZE_FURNACE_BE = BLOCK_ENTITIES.register("bronze_furnace",
            () -> BlockEntityType.Builder.of(BronzeFurnaceBlockEntity::new, BRONZE_FURNACE_BLOCK.get()).build(null));
    public static final Supplier<MenuType<BronzeFurnaceMenu>> BRONZE_FURNACE_MENU = MENUS.register("bronze_furnace",
            () -> new MenuType<>(BronzeFurnaceMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<ValorisFurnaceMenu.Bloomery>> BLOOMERY_MENU = MENUS.register("bloomery",
            () -> new MenuType<>(ValorisFurnaceMenu.Bloomery::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<ValorisFurnaceMenu.Steel>> STEEL_FURNACE_MENU = MENUS.register("steel_furnace",
            () -> new MenuType<>(ValorisFurnaceMenu.Steel::new, FeatureFlags.DEFAULT_FLAGS));

    private FurnaceRegistry() {}

    /** Call before ModBlocks.register so the blocks and items above are added to its registers. */
    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(FurnaceRegistry::registerCapabilities);
    }

    /**
     * Hoppers already use the furnaces' vanilla sides; Create (funnels, chutes, mechanical arms...) and other mods use
     * the item handler, which NeoForge only gives the vanilla furnaces. Same sides as a vanilla furnace: input from the
     * top, fuel from the sides, result (and empty buckets) out of the bottom.
     */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        var items = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        for (var type : java.util.List.of(BLOOMERY_BE.get(), STEEL_FURNACE_BE.get())) {
            event.registerBlockEntity(items, type, (be, side) -> side == null
                    ? new net.neoforged.neoforge.items.wrapper.InvWrapper(be)
                    : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side));
        }
        event.registerBlockEntity(items, BRONZE_FURNACE_BE.get(), (be, side) -> side == null
                ? new net.neoforged.neoforge.items.wrapper.InvWrapper(be)
                : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side));
    }
}
