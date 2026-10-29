package com.valoris.rules;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lava cannot be picked up with a bucket (also not from a lava cauldron). Dispensers and mods that use the vanilla
 * bucket pickup are stopped by LiquidBlockMixin. A lava bucket made any other way (e.g. filled by a Create spout,
 * found in a chest) cannot exist in survival: it vanishes from the ground and from inventories.
 * Creative players are not affected.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class LavaRule {
    private LavaRule() {}

    static void deny(Player player) {
        player.displayClientMessage(Component.literal("Lava cannot be picked up.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (Config.BLOCK_LAVA_BUCKET.get() && !event.getLevel().isClientSide()
                && event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.LAVA_BUCKET)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (Config.BLOCK_LAVA_BUCKET.get() && !event.getPlayer().isCreative()
                && event.getItemEntity().getItem().is(Items.LAVA_BUCKET)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** A lava bucket in a survival inventory turns back into an empty bucket. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!Config.BLOCK_LAVA_BUCKET.get() || player.isCreative() || player.isSpectator()) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.LAVA_BUCKET)) inv.setItem(i, new ItemStack(Items.BUCKET, stack.getCount()));
        }
    }

    @SubscribeEvent
    public static void onCauldron(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.BLOCK_LAVA_BUCKET.get() || event.getEntity().isCreative()) return;
        if (!event.getItemStack().is(Items.BUCKET)) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.LAVA_CAULDRON)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide()) deny(event.getEntity());
    }
}
