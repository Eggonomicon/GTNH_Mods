package com.valier.tweaks.pipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/** Small fluid helpers. */
public final class Fluids {

    private Fluids() {}

    /** The fluid an item holds (a bucket, a cell, a GregTech capsule), or null. */
    public static FluidStack of(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        try {
            FluidStack f = FluidContainerRegistry.getFluidForFilledItem(s);
            if (f == null && s.getItem() instanceof IFluidContainerItem) f = ((IFluidContainerItem) s.getItem()).getFluid(s);
            if (f == null) f = gtDisplay(s);
            return f == null || f.getFluid() == null ? null : f;
        } catch (Throwable e) {
            return null;
        }
    }

    private static java.lang.reflect.Method gtDisplay;
    private static boolean gtLooked;

    /** GregTech's fluid display items (what NEI shows for a fluid), when GregTech is there. */
    static FluidStack gtDisplay(ItemStack s) {
        if (!gtLooked) {
            gtLooked = true;
            try {
                gtDisplay = Class.forName("gregtech.api.util.GTUtility")
                    .getMethod("getFluidFromDisplayStack", ItemStack.class);
            } catch (Throwable e) {
                gtDisplay = null;
            }
        }
        if (gtDisplay == null) return null;
        try {
            return (FluidStack) gtDisplay.invoke(null, s);
        } catch (Throwable e) {
            return null;
        }
    }

    public static FluidStack copy(FluidStack f, int amount) {
        if (f == null || amount <= 0) return null;
        FluidStack c = f.copy();
        c.amount = amount;
        return c;
    }
}
