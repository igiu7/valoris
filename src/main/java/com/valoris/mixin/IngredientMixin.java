package com.valoris.mixin;

import java.util.Arrays;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.bucket.BronzeBuckets;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An ingredient that takes an iron bucket (empty, water or milk) takes the bronze one too (bucket.BronzeBuckets), in
 * every recipe of every mod: the bronze items join the ingredient's item list, which both matching (test, the
 * shapeless stacking ids) and recipe viewers read.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin {
    @Shadow @Nullable private ItemStack[] itemStacks;

    @Inject(method = "getItems", at = @At("RETURN"), cancellable = true)
    private void valoris$bronzeBuckets(CallbackInfoReturnable<ItemStack[]> cir) {
        ItemStack[] items = cir.getReturnValue();
        ItemStack[] extended = null;
        for (ItemStack stack : items) {
            Item bronze = BronzeBuckets.bronzeOf(stack.getItem());
            if (bronze == null) continue;
            ItemStack[] current = extended == null ? items : extended;
            boolean present = false;
            for (ItemStack s : current) if (s.is(bronze)) present = true;
            if (present) continue;
            extended = Arrays.copyOf(current, current.length + 1);
            extended[current.length] = new ItemStack(bronze);
        }
        if (extended == null) return;
        this.itemStacks = extended;
        cir.setReturnValue(extended);
    }
}
