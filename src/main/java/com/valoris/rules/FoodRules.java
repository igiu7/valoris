package com.valoris.rules;

import java.util.List;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import com.valoris.plant.MedicinalHerbBushBlock;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Food balance:
 * - bone meal does not grow crops or tree saplings (grass, flowers and the like still work); the composter makes it;
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

    /** Crops (also of other mods), berry bushes, cocoa and tree saplings; grass, flowers and the like still take bone meal. */
    public static boolean noBonemeal(BlockState state) {
        Block b = state.getBlock();
        return state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || b instanceof CropBlock || b instanceof StemBlock
                || b instanceof SaplingBlock || b instanceof MangrovePropaguleBlock || b instanceof CocoaBlock
                || b instanceof SweetBerryBushBlock || b instanceof PitcherCropBlock || b instanceof MedicinalHerbBushBlock;
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        // not successful: nothing grows and the bone meal is not used up
        if (Config.BAN_BONE_MEAL.get() && noBonemeal(event.getState())) event.setSuccessful(false);
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
