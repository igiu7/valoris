package com.valoris.bucket;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The bronze bucket, empty or with water: works like the iron one (vanilla BucketItem.use), but it only ever holds
 * water - lava, powder snow and the like cannot be picked up - and it gives a bronze bucket back when emptied.
 */
public class BronzeBucketItem extends BucketItem {
    public BronzeBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player,
                this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        // the event Architectury fires for the iron bucket (FTB Chunks protects claims with it)
        var event = dev.architectury.event.events.common.PlayerEvent.FILL_BUCKET.invoker().fill(player, level, stack, hit);
        if (event.interruptsFurtherEvaluation() && event.value() != null) return event.asMinecraft();
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        BlockPos pos = hit.getBlockPos();
        Direction direction = hit.getDirection();
        BlockPos next = pos.relative(direction);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(next, direction, stack)) return InteractionResultHolder.fail(stack);

        if (this.content == Fluids.EMPTY) {
            BlockState state = level.getBlockState(pos);
            // water only: a source block or a waterlogged block
            if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()
                    || !(state.getBlock() instanceof BucketPickup pickup)) return InteractionResultHolder.fail(stack);
            ItemStack picked = pickup.pickupBlock(player, level, pos, state);
            if (!picked.is(Items.WATER_BUCKET)) return InteractionResultHolder.fail(stack);
            ItemStack filled = new ItemStack(BronzeBuckets.WATER_BUCKET.get());
            player.awardStat(Stats.ITEM_USED.get(this));
            pickup.getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
            if (!level.isClientSide) CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, filled);
            return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
        }

        BlockState state = level.getBlockState(pos);
        BlockPos target = canBlockContainFluid(player, level, pos, state) ? pos : next;
        if (!this.emptyContents(player, level, target, hit, stack)) return InteractionResultHolder.fail(stack);
        this.checkExtraContent(player, level, stack, target);
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, target, stack);
        player.awardStat(Stats.ITEM_USED.get(this));
        ItemStack empty = player.hasInfiniteMaterials() ? stack : new ItemStack(BronzeBuckets.BUCKET.get());
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(stack, player, empty), level.isClientSide());
    }
}
