package com.valoris.archery;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fire arrow: an ordinary arrow that leaves the bow, crossbow (Epic Knights' too: it is in #minecraft:arrows) or
 * dispenser already burning, so it sets what it hits on fire. Siege machines burn much harder, players take half the
 * damage of the hit (FireArrows).
 */
public class FireArrowItem extends ArrowItem {
    public FireArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
        arrow.igniteForSeconds(100.0F);
        arrow.getPersistentData().putBoolean(FireArrows.TAG, true);
        return arrow;
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        Projectile arrow = super.asProjectile(level, pos, stack, direction);
        arrow.igniteForSeconds(100.0F);
        arrow.getPersistentData().putBoolean(FireArrows.TAG, true);
        return arrow;
    }
}
