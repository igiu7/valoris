package com.valoris.events;

import com.valoris.city.CityBanner;
import com.valoris.city.CityService;
import com.valoris.diplomacy.Requests;
import com.valoris.war.ConquestService;
import com.valoris.registry.ModBlocks;
import com.valoris.valoris2;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = valoris2.MODID)
public class CityEvents {

    /** The city banner can never be thrown away. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!CityBanner.isCityBanner(stack)) return;
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        // Cancelling stops the drop but the stack is already out of the inventory, so give it back.
        event.setCanceled(true);
        player.getInventory().add(stack.copy());
    }

    /** ...and it is not lost on death either. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.getDrops().removeIf(drop -> CityBanner.isCityBanner(drop.getItem()));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreBanner(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Requests.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreBanner(player);
            ConquestService.onLogin(player);
        }
    }

    private static void restoreBanner(ServerPlayer player) {
        CityService.cityOf(player).filter(c -> !c.founded).ifPresent(c -> CityBanner.giveIfMissing(player));
    }

    /** Placing the city banner (sword in the stone) founds the city, or registers a replacement banner. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getPlacedBlock();
        if (!state.is(ModBlocks.CITY_BANNER.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || CityService.cityOf(player).isEmpty()) {
            event.setCanceled(true); // only city rulers may place a city banner
            return;
        }
        if (!CityService.tryFound(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
