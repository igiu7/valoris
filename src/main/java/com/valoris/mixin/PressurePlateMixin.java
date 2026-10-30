package com.valoris.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.valoris.war.SiegeEvents;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BasePressurePlateBlock;

/**
 * Pressure plates (all kinds, also weighted ones) do not count attackers standing on them in a city under an active
 * siege, so the besiegers cannot open the defenders' gates by stepping on their plates.
 */
@Mixin(BasePressurePlateBlock.class)
public abstract class PressurePlateMixin {
    @ModifyArg(method = "getEntityCount", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            index = 2)
    private static Predicate<Entity> valoris$ignoreAttackers(Predicate<Entity> predicate) {
        return predicate.and(e -> !SiegeEvents.blocksAttacker(e));
    }
}
