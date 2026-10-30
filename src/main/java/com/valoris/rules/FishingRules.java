package com.valoris.rules;

import com.valoris.Config;
import com.valoris.skill.Skill;
import com.valoris.skill.SkillService;
import com.valoris.skill.XpRules;
import com.valoris.valoris2;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/**
 * Fishing and fish:
 * - every catch with a fishing rod gives Hunter XP (a fish, more for treasure, a little for junk);
 * - nothing fished is ever enchanted: the treasure loot table has no enchanting (data override of
 *   gameplay/fishing/treasure), and anything enchanted that other mods add to the catch loses its enchantments
 *   (an enchanted book is not caught at all);
 * - fish killed in the water (cod, salmon, tropical fish, pufferfish) drop their loot only now and then
 *   (food.fishMobDropChance), so the rod is the way to get fish.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class FishingRules {
    private FishingRules() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFished(ItemFishedEvent event) {
        if (event.isCanceled()) return;
        // the event's list holds the very stacks that are dropped: changing them changes the catch
        boolean treasure = false, fish = false;
        for (ItemStack stack : event.getDrops()) {
            if (stack.is(Items.ENCHANTED_BOOK)) {
                stack.setCount(0);
                continue;
            }
            stack.remove(DataComponents.ENCHANTMENTS);
            stack.remove(DataComponents.STORED_ENCHANTMENTS);
            if (stack.is(ItemTags.FISHES)) fish = true;
            else if (stack.is(Items.NAME_TAG) || stack.is(Items.SADDLE) || stack.is(Items.NAUTILUS_SHELL)
                    || stack.is(Items.BOW) || stack.is(Items.FISHING_ROD) || stack.is(Items.BOOK)) treasure = true;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            double xp = treasure ? XpRules.FISH_TREASURE : fish ? XpRules.FISH : XpRules.FISH_JUNK;
            SkillService.addXp(player, Skill.HUNTER, xp);
        }
    }

    @SubscribeEvent
    public static void onFishDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof AbstractFish)) return;
        double chance = Config.FISH_MOB_DROP_CHANCE.get();
        if (chance >= 1.0) return;
        if (event.getEntity().getRandom().nextDouble() >= chance) event.getDrops().clear();
    }
}
