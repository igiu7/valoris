package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * DrinkBeer Refill gives nothing away: no coloured gift after a Frothy Pink Eggnog, nothing out of a gift box (both go
 * through GiftRewards.giveOrDrop). See compat.DrinkBeerRules.
 */
@Pseudo
@Mixin(targets = "lekavar.lma.drinkbeer.utils.gift.GiftRewards")
public abstract class DrinkBeerGiftMixin {
    @Inject(method = "giveOrDrop", at = @At("HEAD"), cancellable = true, remap = false)
    private static void valoris$noGifts(Player player, ItemStack stack, CallbackInfo ci) {
        ci.cancel();
    }
}
