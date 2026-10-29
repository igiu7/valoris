package com.valoris.farming;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Bamboo never grows on a city's claimed land (config soil.bambooGrowsInClaims): neither the stalk (CropGrowEvent),
 * nor a shoot into a stalk (mixin.BambooSaplingBlockMixin), nor with bone meal.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class BambooRule {
    private BambooRule() {}

    public static boolean isBamboo(BlockState state) {
        return state.getBlock() instanceof BambooStalkBlock || state.getBlock() instanceof BambooSaplingBlock;
    }

    public static boolean blocked(LevelAccessor level, BlockPos pos) {
        return !Config.BAMBOO_GROWS_IN_CLAIMS.get() && SoilQuality.claimed(level, pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGrow(CropGrowEvent.Pre event) {
        if (event.getLevel().isClientSide() || !isBamboo(event.getState())) return;
        if (blocked(event.getLevel(), event.getPos())) event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.getLevel().isClientSide() || !isBamboo(event.getState())) return;
        // cancelled and not successful: nothing grows and the bone meal is not used up
        if (blocked(event.getLevel(), event.getPos())) event.setSuccessful(false);
    }
}
