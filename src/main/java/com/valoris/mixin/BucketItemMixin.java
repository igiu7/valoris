package com.valoris.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.Config;
import com.valoris.data.ValorisData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Lava can be picked up and kept in a bucket (fuel, Create...), but never poured out into the world: not by players,
 * dispensers or mods that empty buckets the vanilla way. Pouring it into a cauldron still works; creative players
 * pour as usual. Config rules.noLavaPouring.
 * Nothing at all can be poured onto the land of a city under an active siege (players in creative mode still can).
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Shadow @Final public Fluid content;

    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void valoris2$noLavaPouring(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result,
                                        @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.NO_LAVA_POURING.get() || !content.is(FluidTags.LAVA)) return;
        if (player != null && player.isCreative()) return;
        if (player != null && !level.isClientSide()) {
            player.displayClientMessage(Component.literal("Lava cannot be poured out.").withStyle(ChatFormatting.RED), true);
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void valoris2$noPouringInSiege(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result,
                                           @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        if (content == Fluids.EMPTY || !(level instanceof ServerLevel server)) return;
        if (player != null && player.isCreative()) return;
        if (com.valoris.war.SiegeEvents.activeSiegeAt(ValorisData.get(server.getServer()), server, pos).isEmpty()) return;
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("You cannot pour fluids in a city under siege.").withStyle(ChatFormatting.RED), true);
            // the client has already poured it on its side: put the block and the bucket back
            sp.connection.send(new ClientboundBlockUpdatePacket(server, pos));
            sp.containerMenu.sendAllDataToRemote();
        }
        cir.setReturnValue(false);
    }
}
