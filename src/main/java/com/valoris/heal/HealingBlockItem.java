package com.valoris.heal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Medicinal herbs: used in the air they heal; used on dirt or grass they plant a new bush (like sweet berries). */
public class HealingBlockItem extends ItemNameBlockItem {
    private final Healing.Kind kind;

    public HealingBlockItem(Block block, Properties properties, Healing.Kind kind) {
        super(block, properties);
        this.kind = kind;
    }

    public Healing.Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        Healing.tooltip(kind, lines);
        lines.add(net.minecraft.network.chat.Component.literal("Use on grass or dirt to plant a bush")
                .withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
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
