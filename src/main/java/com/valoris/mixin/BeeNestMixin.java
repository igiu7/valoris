package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * Bee nests are taken whole without silk touch (loot table data/minecraft/loot_table/blocks/bee_nest.json keeps the
 * bees in the item), so their bees stay inside instead of flying out - else they would be both dropped and released.
 */
@Mixin(BeehiveBlock.class)
public abstract class BeeNestMixin {
    @Redirect(method = "playerDestroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;hasTag(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/tags/TagKey;)Z"))
    private boolean valoris$keepBees(ItemStack stack, TagKey<Enchantment> tag) {
        return (Object) this == Blocks.BEE_NEST || EnchantmentHelper.hasTag(stack, tag);
    }
}
