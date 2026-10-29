package com.valoris.mobs;

import com.valoris.Config;
import com.valoris.net.AnimalInfoPayload;
import com.valoris.registry.ModAnimals;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Animals can breed again only after a long cooldown (config, default 2 hours of server time; it runs on even
 * when nobody is near). Looking at an animal shows whether it can breed (see client.AnimalHud). Wolves never
 * breed (and show no label); they drop fur.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Breeding {
    private Breeding() {}

    private static final String UNTIL = "valoris2_breed_until";

    public static final int READY = 0, COOLDOWN = 1, BABY = 2, IN_LOVE = 3;

    private static long cooldownTicks() {
        return Config.BREEDING_COOLDOWN_MINUTES.get() * 60L * 20L;
    }

    /** Ticks left until the animal can breed again (0 = it can). */
    public static long remaining(Animal animal) {
        long until = animal.getPersistentData().getLong(UNTIL);
        long left = Math.max(0, until - animal.level().getGameTime());
        if (!animal.isBaby() && animal.getAge() > 0) left = Math.max(left, animal.getAge());
        return left;
    }

    @SubscribeEvent
    public static void onBaby(BabyEntitySpawnEvent event) {
        // wolves never breed (they come only in wild packs)
        if (event.getParentA() instanceof Wolf || event.getParentB() instanceof Wolf) {
            event.setCanceled(true);
            return;
        }
        long until = event.getParentA().level().getGameTime() + cooldownTicks();
        for (Mob parent : new Mob[] {event.getParentA(), event.getParentB()}) {
            if (parent instanceof Animal a) a.getPersistentData().putLong(UNTIL, until);
        }
    }

    @SubscribeEvent
    public static void onFeed(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Animal animal) || animal.isBaby()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !animal.isFood(stack)) return;
        // wolves do not breed: meat only heals a hurt tamed wolf and never makes one fall in love
        if (animal instanceof Wolf wolf) {
            if (wolf.isTame() && wolf.getHealth() < wolf.getMaxHealth()) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.getEntity().displayClientMessage(Component.literal("Wolves do not breed.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long left = remaining(animal);
        if (left <= 0) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(Component.literal("This animal can breed again in "
                + AnimalInfoPayload.time(left / 20)).withStyle(ChatFormatting.GOLD), true);
    }

    /** Answer to a client looking at an animal. */
    public static void sendInfo(ServerPlayer player, int entityId) {
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof Animal animal) || animal instanceof Wolf || animal.distanceToSqr(player) > 12 * 12) return;
        int state;
        long seconds = 0;
        if (animal.isBaby()) {
            state = BABY;
            seconds = -animal.getAge() / 20L;
        } else if (animal.isInLove()) {
            state = IN_LOVE;
        } else {
            long left = remaining(animal);
            state = left > 0 ? COOLDOWN : READY;
            seconds = left / 20;
        }
        int stew = animal instanceof net.minecraft.world.entity.animal.MushroomCow cow && !cow.isBaby()
                ? (int) (com.valoris.rules.FoodRules.stewRemainingTicks(cow) / 20) : -1;
        PacketDistributor.sendToPlayer(player, new AnimalInfoPayload(entityId, state, (int) Math.min(Integer.MAX_VALUE, seconds), stew));
    }

    /**
     * Wolves drop fur and one piece of meat (great wolves two of each), horses (also donkeys and mules) one piece
     * of meat; cooked if burning.
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide() || entity.isBaby()) return;
        boolean fire = entity.isOnFire();
        if (entity instanceof Wolf wolf) {
            int n = GreatWolves.isGreat(wolf) ? 2 : 1;
            drop(event, new ItemStack(ModAnimals.FUR.get(), n));
            drop(event, new ItemStack(fire ? ModAnimals.COOKED_WOLF_MEAT.get() : ModAnimals.RAW_WOLF_MEAT.get(), n));
        } else if (entity instanceof net.minecraft.world.entity.animal.horse.Horse
                || entity instanceof net.minecraft.world.entity.animal.horse.Donkey
                || entity instanceof net.minecraft.world.entity.animal.horse.Mule) {
            drop(event, new ItemStack(fire ? ModAnimals.COOKED_HORSE_MEAT.get() : ModAnimals.RAW_HORSE_MEAT.get()));
        }
    }

    private static void drop(LivingDropsEvent event, ItemStack stack) {
        var e = event.getEntity();
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), stack));
    }
}
