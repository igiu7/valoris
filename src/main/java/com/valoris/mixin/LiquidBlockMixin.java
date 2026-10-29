package com.valoris.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lava source blocks cannot be scooped up by anything (players, dispensers, mods using the vanilla bucket pickup);
 * only creative players can.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
    @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
    private void valoris2$noLava(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state,
                                 CallbackInfoReturnable<ItemStack> cir) {
        if (!Config.BLOCK_LAVA_BUCKET.get()) return;
        if (!state.getFluidState().is(FluidTags.LAVA)) return;
        if (player != null && player.isCreative()) return;
        if (player != null && !level.isClientSide()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Lava cannot be picked up.")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
