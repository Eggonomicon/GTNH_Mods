package com.valier.tweaks.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.valier.tweaks.TweaksConfig;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtPlusPlus.xmod.gregtech.common.tileentities.generators.MTEAdvancedBoilerBase;

/**
 * GT++'s advanced (LV, MV, HV) solid fuel boilers: {@code consumeFuel} burns one item and, one time in three, puts a
 * tiny pile of ash or dark ash into the output slot. Those two {@code addStackToSlot} calls are its only ones; with
 * {@code noBoilerAsh} on they add nothing. The fuel is still taken and still heats the boiler.
 */
@Mixin(value = MTEAdvancedBoilerBase.class, remap = false)
public abstract class MixinAdvancedBoiler {

    @Redirect(
        method = "consumeFuel",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;addStackToSlot(ILnet/minecraft/item/ItemStack;)Z"))
    private boolean valiertweaks$noAsh(IGregTechTileEntity base, int slot, ItemStack ash) {
        return !TweaksConfig.noBoilerAsh && base.addStackToSlot(slot, ash);
    }
}
