package com.valoris.rules;

import java.util.List;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Food balance:
 * - bone meal does not exist in survival: it does nothing, cannot be crafted, picked up or kept (it vanishes);
 * - thrown eggs never hatch chicks;
 * - a mooshroom gives stew only once every 20 minutes (config);
 * - suspicious stew no longer gives Regeneration.
 * Bees no longer grow crops (empty minecraft:bee_growables tag); mushrooms spread slower (MushroomBlockMixin).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class FoodRules {
    private FoodRules() {}

    private static final String STEW_UNTIL = "valoris2_stew_until";

    // ------------------------------------------------------------------ bone meal

    private static boolean bonemeal(ItemStack stack) {
        return Config.BAN_BONE_MEAL.get() && stack.is(Items.BONE_MEAL);
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (Config.BAN_BONE_MEAL.get()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && bonemeal(item.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (bonemeal(event.getItemEntity().getItem()) && !event.getPlayer().isCreative()) event.setCanPickup(TriState.FALSE);
    }

    /** Bone meal that got into an inventory some other way (machines, chests) vanishes. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        if (!Config.BAN_BONE_MEAL.get() || player.isCreative() || player.isSpectator()) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.BONE_MEAL)) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ eggs

    @SubscribeEvent
    public static void onEggHit(ProjectileImpactEvent event) {
        if (!Config.EGGS_NO_CHICKS.get() || !(event.getProjectile() instanceof ThrownEgg egg)) return;
        if (egg.level().isClientSide()) return;
        event.setCanceled(true);
        egg.level().broadcastEntityEvent(egg, (byte) 3); // egg shell particles
        egg.discard();
    }

    // ------------------------------------------------------------------ mooshroom stew

    public static long stewRemainingTicks(MushroomCow cow) {
        return Math.max(0, cow.getPersistentData().getLong(STEW_UNTIL) - cow.level().getGameTime());
    }

    @SubscribeEvent
    public static void onMilk(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof MushroomCow cow) || cow.isBaby()) return;
        if (!event.getItemStack().is(Items.BOWL)) return;
        long left = stewRemainingTicks(cow);
        if (left > 0) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.getEntity().displayClientMessage(Component.literal("This mooshroom gives stew again in "
                    + com.valoris.net.AnimalInfoPayload.time(left / 20)).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        cow.getPersistentData().putLong(STEW_UNTIL,
                cow.level().getGameTime() + Config.MOOSHROOM_STEW_MINUTES.get() * 60L * 20L);
    }

    // ------------------------------------------------------------------ suspicious stew

    @SubscribeEvent
    public static void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!Config.STEW_NO_REGENERATION.get()) return;
        ItemStack stack = event.getItem();
        SuspiciousStewEffects effects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (effects == null) return;
        List<SuspiciousStewEffects.Entry> kept = effects.effects().stream()
                .filter(e -> !e.effect().equals(MobEffects.REGENERATION)).toList();
        if (kept.size() != effects.effects().size()) {
            stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(kept));
        }
    }
}
