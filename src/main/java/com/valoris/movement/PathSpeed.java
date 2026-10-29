package com.valoris.movement;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Roads: on dirt paths (made with a shovel) players and horses (also donkeys, mules, camels - ridden with Horse
 * Combat Controls too, which drives the horse by its movement speed) move faster; so do Trotting Wagons
 * (compatmixin.AbstractWagonMixin). Applied on both sides, so a horse steered by its rider speeds up at once.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class PathSpeed {
    private PathSpeed() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "dirt_path_speed");

    /** Standing on a dirt path (its top is 1/16 lower than a full block, so the feet are inside the path block). */
    public static boolean onPath(Entity e) {
        Level level = e.level();
        BlockPos pos = e.blockPosition();
        return level.getBlockState(pos).is(Blocks.DIRT_PATH) || level.getBlockState(pos.below()).is(Blocks.DIRT_PATH);
    }

    /** Speed multiplier for things that move by themselves (wagons): 1 off the path. */
    public static double multiplier(Entity e) {
        return onPath(e) ? 1.0 + Config.PATH_SPEED_BONUS.get() : 1.0;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!(living instanceof Player) && !(living instanceof AbstractHorse)) return;
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double bonus = Config.PATH_SPEED_BONUS.get();
        boolean on = bonus > 0 && onPath(living);
        AttributeModifier current = speed.getModifier(ID);
        if (on && (current == null || current.amount() != bonus)) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!on && current != null) {
            speed.removeModifier(ID);
        }
    }
}
