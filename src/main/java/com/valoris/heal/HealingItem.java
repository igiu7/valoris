package com.valoris.heal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Linen bandage and honey poultice. */
public class HealingItem extends Item {
    private final Healing.Kind kind;

    public HealingItem(Properties properties, Healing.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Healing.Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        Healing.tooltip(kind, lines);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE; // the arm pose comes from HealAnimations
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return Healing.use(level, player, hand);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        Healing.tick(level, user, stack, remaining, kind);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        return Healing.finish(stack, level, user, this, kind);
    }
}
