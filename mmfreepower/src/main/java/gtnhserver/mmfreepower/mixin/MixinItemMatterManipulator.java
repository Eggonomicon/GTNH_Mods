package gtnhserver.mmfreepower.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import gtnhserver.mmfreepower.MkIV;

/**
 * All of the manipulator's building/removing/moving work pays for itself through use() (AbstractBuildable
 * #tryConsumePower), so answering "yes, there was enough EU" there - without discharging - makes the tool free.
 * canUse() is covered too for anything that checks charge first.
 * <p>
 * setState() rebuilds the item's NBT from the manipulator's own state, dropping anything else on it; the Valier MM
 * Mk IV's marker, name and glint are put back afterwards.
 */
@Mixin(
    targets = "com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator",
    remap = false)
public abstract class MixinItemMatterManipulator {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmfreepower$useWithoutCharge(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmfreepower$alwaysCanUse(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "setState", at = @At("HEAD"), remap = false)
    private static void mmfreepower$rememberMkIV(ItemStack stack, MMState state, CallbackInfo ci) {
        MkIV.beforeSetState(stack);
    }

    @Inject(method = "setState", at = @At("RETURN"), remap = false)
    private static void mmfreepower$restoreMkIV(ItemStack stack, MMState state, CallbackInfo ci) {
        MkIV.afterSetState(stack);
    }
}
