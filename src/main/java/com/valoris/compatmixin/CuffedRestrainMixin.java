package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.compat.PlayerReviveCompat;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Cuffed: a player is handcuffed only while downed (PlayerRevive bleeding out), whatever the Cuffed config says; he is
 * revived at once, with his hands cuffed. Once restrained the rest can follow (his legs). Nobody restrains himself;
 * commands (no captor) are not affected. A downed player lies on the ground, so a click anywhere on him cuffs his
 * hands (not his legs). During an active siege handcuffing takes time (war.SiegeHandcuffs). See compat.CuffedRules.
 */
@Mixin(value = RestrainableCapability.class, remap = false)
public abstract class CuffedRestrainMixin {
    @Shadow public abstract boolean isRestrained();
    @Shadow public abstract void EquipRestraint(ServerPlayer player, ServerPlayer captor, AbstractArmRestraint restraint);
    @Shadow public abstract void EquipRestraint(ServerPlayer player, ServerPlayer captor, AbstractLegRestraint restraint);
    @Shadow public abstract void EquipRestraint(ServerPlayer player, ServerPlayer captor, AbstractHeadRestraint restraint);

    private boolean valoris$redirected;

    @Inject(method = "onInteractedByOther", at = @At("HEAD"), cancellable = true)
    private void valoris$cuffHands(ServerPlayer player, ServerPlayer other, double height, net.minecraft.world.item.ItemStack stack,
                                    net.minecraft.world.InteractionHand hand, boolean avoidEscort, CallbackInfoReturnable<Boolean> cir) {
        if (valoris$redirected || isRestrained() || !com.lazrproductions.cuffed.restraints.RestraintAPI.isRestraintItem(stack)) return;
        if (!PlayerReviveCompat.IsBleedingOut(player)) return;
        valoris$redirected = true;
        try {
            cir.setReturnValue(((RestrainableCapability) (Object) this).onInteractedByOther(player, other, 1.0, stack, hand, avoidEscort));
        } finally {
            valoris$redirected = false;
        }
    }

    /** The hand the captor holds a restraint in, null for none. */
    private static InteractionHand valoris$cuffHand(ServerPlayer captor) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (com.lazrproductions.cuffed.restraints.RestraintAPI.isRestraintItem(captor.getItemInHand(hand))) return hand;
        }
        return null;
    }

    /** null: Cuffed decides as usual; otherwise whether the restraint goes on (Valoris equipped it already). */
    private Boolean valoris$decide(ServerPlayer player, ServerPlayer captor, Runnable equip) {
        if (captor == null) return null;
        if (captor == player) return false;
        if (isRestrained()) return null;
        boolean siege = com.valoris.war.SiegeService.inActiveSiege(captor) || com.valoris.war.SiegeService.inActiveSiege(player);
        if (PlayerReviveCompat.IsBleedingOut(player)) {
            // during a siege it takes time (war.SiegeHandcuffs): the cuffs go on when it is done
            if (siege && !com.valoris.war.SiegeHandcuffs.finishing(captor)) {
                InteractionHand hand = valoris$cuffHand(captor);
                if (hand != null) {
                    RestrainableCapability cap = (RestrainableCapability) (Object) this;
                    com.valoris.war.SiegeHandcuffs.start(captor, player, captor.getItemInHand(hand).getItem(),
                            () -> PlayerReviveCompat.IsBleedingOut(player) && !cap.isRestrained(), () -> {
                                InteractionHand h = valoris$cuffHand(captor);
                                if (h != null) cap.onInteractedByOther(player, captor, 1.0, captor.getItemInHand(h), h, false);
                            });
                }
                return false;
            }
            PlayerReviveCompat.Revive(player);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
            equip.run();
            return true;
        }
        captor.displayClientMessage(Component.literal("Only a downed player can be handcuffed.").withStyle(ChatFormatting.RED), true);
        return false;
    }

    @Inject(method = "TryEquipRestraint(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;Lcom/lazrproductions/cuffed/restraints/base/AbstractArmRestraint;)Z",
            at = @At("HEAD"), cancellable = true)
    private void valoris$arms(ServerPlayer player, ServerPlayer captor, AbstractArmRestraint restraint, CallbackInfoReturnable<Boolean> cir) {
        if (((RestrainableCapability) (Object) this).getRestraint(com.lazrproductions.cuffed.restraints.base.RestraintType.Arm) != null) return;
        Boolean r = valoris$decide(player, captor, () -> EquipRestraint(player, captor, restraint));
        if (r != null) cir.setReturnValue(r);
    }

    @Inject(method = "TryEquipRestraint(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;Lcom/lazrproductions/cuffed/restraints/base/AbstractLegRestraint;)Z",
            at = @At("HEAD"), cancellable = true)
    private void valoris$legs(ServerPlayer player, ServerPlayer captor, AbstractLegRestraint restraint, CallbackInfoReturnable<Boolean> cir) {
        if (((RestrainableCapability) (Object) this).getRestraint(com.lazrproductions.cuffed.restraints.base.RestraintType.Leg) != null) return;
        Boolean r = valoris$decide(player, captor, () -> EquipRestraint(player, captor, restraint));
        if (r != null) cir.setReturnValue(r);
    }

    @Inject(method = "TryEquipRestraint(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;Lcom/lazrproductions/cuffed/restraints/base/AbstractHeadRestraint;)Z",
            at = @At("HEAD"), cancellable = true)
    private void valoris$head(ServerPlayer player, ServerPlayer captor, AbstractHeadRestraint restraint, CallbackInfoReturnable<Boolean> cir) {
        if (((RestrainableCapability) (Object) this).getRestraint(com.lazrproductions.cuffed.restraints.base.RestraintType.Head) != null) return;
        Boolean r = valoris$decide(player, captor, () -> EquipRestraint(player, captor, restraint));
        if (r != null) cir.setReturnValue(r);
    }
}
