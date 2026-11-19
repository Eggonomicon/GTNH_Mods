package com.valier.tweaks.mixin;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valier.tweaks.XpCrafters;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;

/**
 * GregTech's tanks (GT++'s ULV tank among them): {@code allowPutStack} decides whether an item may go into the tank's
 * slots from a pipe, a hopper or anything else that inserts the way they do. A tank that is an XP crafter only takes
 * liquid XP containers and solid XP (essence berries); everything else is refused, so pipes and other crafters can't
 * fill its input slot with dust. Other tanks are unchanged.
 */
@Mixin(value = MTEBasicTank.class, remap = false)
public abstract class MixinBasicTank {

    @Inject(method = "allowPutStack", at = @At("HEAD"), cancellable = true)
    private void valiertweaks$xpOnly(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack,
        CallbackInfoReturnable<Boolean> cir) {
        if (!XpCrafters.tankMayTake(base, stack)) cir.setReturnValue(false);
    }
}
