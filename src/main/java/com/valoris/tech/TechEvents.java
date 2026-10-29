package com.valoris.tech;

import com.valoris.net.ValorisNet;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = valoris2.MODID)
public class TechEvents {
    private static final ResourceLocation BASE_ATTACK_DAMAGE = ResourceLocation.withDefaultNamespace("base_attack_damage");

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        TechConfig.load();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) TechService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TechService.cacheSkills(player);
            ValorisNet.sendWeaponBalance(player);
            ValorisNet.sendArmoryCatalog(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TechService.cacheSkills(player);
    }

    // ------------------------------------------------------------------ disabled blocks (TaCZ gun smith table)

    private static boolean disabled(BlockState state) {
        if (TechConfig.disabledBlocks().isEmpty()) return false;
        return TechConfig.disabledBlocks().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (disabled(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.literal("This block is disabled on this server.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (disabled(event.getPlacedBlock())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ era weapon damage

    /**
     * Era armor and era weapon damage (both replace the item's own values), then the reach limit. Runs on both
     * sides.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (ArmorBalance.apply(event)) return;
        eraDamage(event);
        capReach(event);
    }

    private static final double MAX_REACH = 1.5;
    private static final ResourceLocation REACH = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "reach_limit");
    private static final ResourceLocation REACH_DAMAGE = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "reach_limit_damage");
    private static final ResourceLocation REACH_SPEED = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "reach_limit_speed");

    /**
     * No weapon reaches more than +1.5 blocks. What a long weapon loses in reach it gets back as +0.5 damage and
     * +0.1 attack speed per block taken away.
     */
    private static void capReach(ItemAttributeModifierEvent event) {
        double reach = 0;
        for (var e : event.getModifiers()) {
            if (e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && e.slot() == EquipmentSlotGroup.MAINHAND
                    && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) reach += e.modifier().amount();
        }
        if (reach <= MAX_REACH) return;
        double excess = reach - MAX_REACH;
        event.removeIf(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && e.slot() == EquipmentSlotGroup.MAINHAND
                && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE);
        event.addModifier(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(REACH, MAX_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(REACH_DAMAGE, 0.5 * excess, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ATTACK_SPEED,
                new AttributeModifier(REACH_SPEED, 0.1 * excess, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }

    /** Replaces the attack damage of '<material>_<type>' weapons with the era value. */
    private static void eraDamage(ItemAttributeModifierEvent event) {
        double damage = WeaponBalance.damageFor(event.getItemStack().getItem());
        if (Double.isNaN(damage)) return;
        boolean isWeapon = event.getModifiers().stream()
                .anyMatch(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE) && e.slot() == EquipmentSlotGroup.MAINHAND);
        if (!isWeapon) return;
        event.removeIf(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE) && e.slot() == EquipmentSlotGroup.MAINHAND);
        // the player's own base damage is 1, so the item adds damage - 1
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE, damage - 1.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
}
