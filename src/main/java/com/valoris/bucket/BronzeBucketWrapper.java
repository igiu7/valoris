package com.valoris.bucket;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/**
 * Fluid handler of the bronze buckets (tanks, Create's spouts and drains...): like the iron bucket's, but it takes
 * only water (milk is drained, never filled, like the vanilla milk bucket) and stays a bronze bucket.
 */
public class BronzeBucketWrapper extends FluidBucketWrapper {
    public BronzeBucketWrapper(ItemStack container) {
        super(container);
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid) {
        return fluid.is(Fluids.WATER);
    }

    @Override
    protected void setFluid(FluidStack fluid) {
        if (fluid.isEmpty()) container = new ItemStack(BronzeBuckets.BUCKET.get());
        else if (fluid.is(Fluids.WATER)) container = new ItemStack(BronzeBuckets.WATER_BUCKET.get());
        else if (NeoForgeMod.MILK.isBound() && fluid.is(NeoForgeMod.MILK.get())) container = new ItemStack(BronzeBuckets.MILK_BUCKET.get());
    }
}
