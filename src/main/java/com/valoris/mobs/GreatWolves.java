package com.valoris.mobs;

import com.valoris.Config;
import com.valoris.farming.SoilQuality;
import com.valoris.valoris2;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Great wolves: ordinary wolves (same look, packs, leaders, taming, hunting rules) that are bigger, made with the
 * vanilla scale attribute (hitbox and model grow with it, synced to clients), with more health and attack damage.
 * They live only in tier 4 land (deserts, badlands, snow and ice, peaks), where their packs replace the normal
 * ones, and drop 2 fur and 2 wolf meat (see Breeding.onDrops). Wolves do not breed (Breeding.onBaby).
 * Health and damage are modifiers on top of the vanilla values, so taming (which resets the base health) keeps them;
 * they follow the config every time the wolf is loaded.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class GreatWolves {
    private GreatWolves() {}

    private static final String GREAT = "valoris2_great_wolf";
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "great_wolf_health");
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "great_wolf_damage");

    public static boolean isGreat(Wolf wolf) {
        return wolf.getPersistentData().getBoolean(GREAT);
    }

    /** Tier 4 land: great wolf packs spawn there instead of normal ones. */
    public static boolean greatLand(Holder<Biome> biome) {
        return SoilQuality.tier(biome) == 4;
    }

    /** Turns a wolf into a great wolf (before it is added to the world), at full health. */
    public static void make(Wolf wolf) {
        wolf.getPersistentData().putBoolean(GREAT, true);
        apply(wolf);
        wolf.setHealth(wolf.getMaxHealth());
    }

    /** Size, health and damage from the config. */
    private static void apply(Wolf wolf) {
        AttributeInstance scale = wolf.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(Config.GREAT_WOLF_SCALE.get());
        multiply(wolf, Attributes.MAX_HEALTH, HEALTH, Config.GREAT_WOLF_HEALTH.get());
        multiply(wolf, Attributes.ATTACK_DAMAGE, DAMAGE, Config.GREAT_WOLF_DAMAGE.get());
        if (wolf.getHealth() > wolf.getMaxHealth()) wolf.setHealth(wolf.getMaxHealth());
    }

    private static void multiply(Wolf wolf, Holder<Attribute> attribute, ResourceLocation id, double factor) {
        AttributeInstance inst = wolf.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        if (factor != 1.0) {
            inst.addPermanentModifier(new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Great wolves spawned before (or with an older config) get the current values. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Wolf wolf && isGreat(wolf)) apply(wolf);
    }
}
