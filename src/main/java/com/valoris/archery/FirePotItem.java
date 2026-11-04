package com.valoris.archery;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Fire pot: thrown like a snowball, but only a few blocks; it bursts into fire where it lands (FirePot). */
public class FirePotItem extends Item {
    private static final float VELOCITY = 0.7F; // a snowball: 1.5
    private static final int COOLDOWN_TICKS = 600; // 30 s

    public FirePotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                0.6F, 0.5F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide()) {
            FirePot pot = new FirePot(level, player);
            pot.setItem(stack);
            pot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, VELOCITY, 1.0F);
            level.addFreshEntity(pot);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
