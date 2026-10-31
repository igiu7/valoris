package com.valoris.bucket;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;

/** Milk in a bronze bucket: drunk like the iron one (cures the effects milk cures), the bronze bucket comes back. */
public class BronzeMilkBucketItem extends MilkBucketItem {
    public BronzeMilkBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }
        if (!level.isClientSide) entity.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
        if (entity instanceof Player player) {
            return ItemUtils.createFilledResult(stack, player, new ItemStack(BronzeBuckets.BUCKET.get()), false);
        }
        stack.consume(1, entity);
        return stack;
    }
}
