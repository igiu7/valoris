package com.valoris.heal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shared behaviour of the healing items: they are applied over a short time (with an animation every player
 * sees, see client.HealAnimations), then heal and go on cooldown. Natural regeneration is off, so these are the
 * way to heal.
 */
public final class Healing {
    /** How an item heals: health points (2 = one heart), cooldown and use time in ticks, its sounds and pose. */
    public enum Kind {
        HERBS(4.0F, 15 * 20, 32, SoundEvents.AZALEA_LEAVES_HIT, SoundEvents.GRASS_BREAK, false),
        BANDAGE(4.0F, 10 * 20, 40, SoundEvents.WOOL_HIT, SoundEvents.ARMOR_EQUIP_LEATHER.value(), true),
        POULTICE(8.0F, 10 * 20, 50, SoundEvents.HONEY_BLOCK_SLIDE, SoundEvents.ARMOR_EQUIP_LEATHER.value(), true),
        HERB_POULTICE(9.0F, 6 * 20, 70, SoundEvents.HONEY_BLOCK_SLIDE, SoundEvents.ARMOR_EQUIP_LEATHER.value(), true),
        HERB_ROOT_POULTICE(10.0F, 5 * 20, 90, SoundEvents.HONEY_BLOCK_SLIDE, SoundEvents.ARMOR_EQUIP_LEATHER.value(), true);

        public final float heal;
        public final int cooldown;
        public final int useTicks;
        public final SoundEvent workSound;
        public final SoundEvent doneSound;
        /** true: wrapping a bandage around the arm; false: rubbing herbs on a wound. */
        public final boolean wrapping;

        Kind(float heal, int cooldown, int useTicks, SoundEvent workSound, SoundEvent doneSound, boolean wrapping) {
            this.heal = heal;
            this.cooldown = cooldown;
            this.useTicks = useTicks;
            this.workSound = workSound;
            this.doneSound = doneSound;
            this.wrapping = wrapping;
        }
    }

    private Healing() {}

    /** Every healing item; they share one cooldown. */
    public static java.util.List<Item> healingItems() {
        return java.util.List.of(com.valoris.registry.ModPlants.MEDICINAL_HERBS.get(),
                com.valoris.registry.ModPlants.LINEN_BANDAGE.get(),
                com.valoris.registry.ModPlants.HONEY_POULTICE.get(),
                com.valoris.registry.ModPlants.HONEY_HERB_POULTICE.get(),
                com.valoris.registry.ModPlants.MEDICINAL_ROOT.get(),
                com.valoris.registry.ModPlants.HONEY_ROOT_POULTICE.get(),
                com.valoris.registry.ModPlants.HERB_ROOT_POULTICE.get());
    }

    public static void tooltip(Kind kind, java.util.List<Component> lines) {
        float hearts = kind.heal / 2.0F;
        lines.add(Component.literal("Heals " + (hearts == (int) hearts ? String.valueOf((int) hearts) : String.valueOf(hearts))
                + (hearts == 1 ? " heart" : " hearts")).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("Cooldown " + kind.cooldown / 20 + " s (all healing items)  -  takes " + String.format("%.1f", kind.useTicks / 20.0) + " s")
                .withStyle(ChatFormatting.GRAY));
    }

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("You are not hurt.").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** While applying: soft sounds and bits of the item, heard and seen by everyone around. */
    public static void tick(Level level, LivingEntity user, ItemStack stack, int remaining, Kind kind) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;
        int used = kind.useTicks - remaining;
        if (used % 8 != 4) return;
        level.playSound(null, user.getX(), user.getY(), user.getZ(), kind.workSound, SoundSource.PLAYERS, 0.55F,
                0.9F + level.random.nextFloat() * 0.2F);
        double yaw = Math.toRadians(user.getYRot());
        double fx = -Math.sin(yaw) * 0.35, fz = Math.cos(yaw) * 0.35;
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), user.getX() + fx, user.getY() + 1.05,
                user.getZ() + fz, 3, 0.12, 0.08, 0.12, 0.02);
    }

    public static ItemStack finish(ItemStack stack, Level level, LivingEntity user, Item item, Kind kind) {
        if (level.isClientSide) return stack;
        user.heal(kind.heal);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), kind.doneSound, SoundSource.PLAYERS, 0.8F, 1.1F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, user.getX(), user.getY() + 1.9, user.getZ(),
                    Math.max(1, (int) (kind.heal / 2)), 0.35, 0.15, 0.35, 0.0);
        }
        if (user instanceof Player player) {
            // one shared cooldown: after healing, no healing item can be used until it runs out
            for (Item healing : healingItems()) player.getCooldowns().addCooldown(healing, kind.cooldown);
            player.getCooldowns().addCooldown(item, kind.cooldown);
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(item));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        } else {
            stack.shrink(1);
        }
        return stack;
    }
}
