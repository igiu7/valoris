package com.valoris.bucket;

import com.valoris.registry.ModBlocks;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Bronze buckets: empty, with water, with milk - the iron bucket's work in bronze, without lava (nothing in this
 * package picks it up, and there is no lava bucket). Every recipe that takes an iron bucket takes the bronze one too
 * (mixin.IngredientMixin), and every container left over is bronze again: crafting (crafting remainder), the
 * DrinkBeer barrel (compatmixin.BeerBarrelBucketMixin), cauldrons, dispensers, cows and goats, fluid tanks.
 */
public final class BronzeBuckets {
    private BronzeBuckets() {}

    public static final DeferredItem<BronzeBucketItem> BUCKET = ModBlocks.ITEMS.register("bronze_bucket",
            () -> new BronzeBucketItem(Fluids.EMPTY, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BronzeBucketItem> WATER_BUCKET = ModBlocks.ITEMS.register("bronze_water_bucket",
            () -> new BronzeBucketItem(Fluids.WATER, new Item.Properties().stacksTo(1).craftRemainder(BUCKET.get())));
    public static final DeferredItem<BronzeMilkBucketItem> MILK_BUCKET = ModBlocks.ITEMS.register("bronze_milk_bucket",
            () -> new BronzeMilkBucketItem(new Item.Properties().stacksTo(1).craftRemainder(BUCKET.get())));

    /** Loads this class so its items join ModBlocks' register before it is registered. */
    public static void init() {}

    /** The bronze counterpart of an iron bucket (empty, water, milk), or null. */
    public static Item bronzeOf(Item iron) {
        if (!BUCKET.isBound()) return null;
        if (iron == Items.BUCKET) return BUCKET.get();
        if (iron == Items.WATER_BUCKET) return WATER_BUCKET.get();
        if (iron == Items.MILK_BUCKET) return MILK_BUCKET.get();
        return null;
    }

    public static boolean isBronze(Item item) {
        return BUCKET.isBound() && (item == BUCKET.get() || item == WATER_BUCKET.get() || item == MILK_BUCKET.get());
    }

    // ------------------------------------------------------------------ cows and goats

    @EventBusSubscriber(modid = valoris2.MODID)
    public static final class Milking {
        private Milking() {}

        @SubscribeEvent
        public static void onInteract(PlayerInteractEvent.EntityInteract event) {
            Player player = event.getEntity();
            ItemStack stack = event.getItemStack();
            if (!stack.is(BUCKET.get())) return;
            boolean cow = event.getTarget() instanceof Cow c && !c.isBaby(); // mooshrooms too
            boolean goat = event.getTarget() instanceof Goat g && !g.isBaby();
            if (!cow && !goat) return;
            player.playSound(cow ? SoundEvents.COW_MILK : SoundEvents.GOAT_MILK, 1.0F, 1.0F);
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(stack, player, new ItemStack(MILK_BUCKET.get())));
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ fluid handlers, cauldrons, dispensers

    @EventBusSubscriber(modid = valoris2.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new BronzeBucketWrapper(stack),
                    BUCKET.get(), WATER_BUCKET.get(), MILK_BUCKET.get());
        }

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                CauldronInteraction fillWater = (state, level, pos, player, hand, stack) -> {
                    if (!level.isClientSide) {
                        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(BUCKET.get())));
                        player.awardStat(Stats.FILL_CAULDRON);
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
                        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                    }
                    return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
                };
                for (CauldronInteraction.InteractionMap map : new CauldronInteraction.InteractionMap[]{
                        CauldronInteraction.EMPTY, CauldronInteraction.WATER, CauldronInteraction.LAVA, CauldronInteraction.POWDER_SNOW}) {
                    map.map().put(WATER_BUCKET.get(), fillWater);
                }
                CauldronInteraction.WATER.map().put(BUCKET.get(), (state, level, pos, player, hand, stack) ->
                        CauldronInteraction.fillBucket(state, level, pos, player, hand, stack, new ItemStack(WATER_BUCKET.get()),
                                s -> s.getValue(LayeredCauldronBlock.LEVEL) == 3, SoundEvents.BUCKET_FILL));

                DispenserBlock.registerBehavior(WATER_BUCKET.get(), new DefaultDispenseItemBehavior() {
                    private final DefaultDispenseItemBehavior drop = new DefaultDispenseItemBehavior();

                    @Override
                    public ItemStack execute(BlockSource source, ItemStack stack) {
                        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                        Level level = source.level();
                        if (WATER_BUCKET.get().emptyContents(null, level, pos, null, stack)) {
                            return this.consumeWithRemainder(source, stack, new ItemStack(BUCKET.get()));
                        }
                        return drop.dispense(source, stack);
                    }
                });
                DispenserBlock.registerBehavior(BUCKET.get(), new DefaultDispenseItemBehavior() {
                    @Override
                    public ItemStack execute(BlockSource source, ItemStack stack) {
                        Level level = source.level();
                        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                        BlockState state = level.getBlockState(pos);
                        if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource()
                                && state.getBlock() instanceof BucketPickup pickup
                                && pickup.pickupBlock(null, level, pos, state).is(Items.WATER_BUCKET)) {
                            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                            return this.consumeWithRemainder(source, stack, new ItemStack(WATER_BUCKET.get()));
                        }
                        return super.execute(source, stack);
                    }
                });
            });
        }
    }
}
