package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.bucket.BronzeBuckets;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DrinkBeer's beer barrel gives the empty bucket back after brewing: a bronze one for a bronze water or milk bucket
 * (it always gave an iron one). See bucket.BronzeBuckets.
 */
@Pseudo
@Mixin(targets = "lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity")
public abstract class BeerBarrelBucketMixin {
    @Unique private boolean valoris$bronze;

    @Inject(method = "shouldReturnBucket", at = @At("HEAD"), remap = false)
    private void valoris$remember(ItemStack consumed, CallbackInfoReturnable<Boolean> cir) {
        valoris$bronze = BronzeBuckets.isBronze(consumed.getItem());
    }

    @Redirect(method = "completeBrewing", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/item/Item;getDefaultInstance()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack valoris$bucket(Item item) {
        return valoris$bronze ? new ItemStack(BronzeBuckets.BUCKET.get()) : item.getDefaultInstance();
    }
}
