package com.valier.tweaks.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valier.tweaks.TweaksConfig;

import gregtech.api.enums.OrePrefixes;
import gregtech.common.tileentities.boilers.MTEBoilerBronze;

/**
 * GregTech's small coal boiler and high pressure coal boiler (which extends it): after burning a piece of fuel,
 * {@code combustFuel} picks how much ash goes into the output slot (a tiny, small or full dust, or none). With
 * {@code noBoilerAsh} on it is always none. The fuel still burns as before: whether an item is fuel, and the steam it
 * gives, are worked out elsewhere.
 */
@Mixin(value = MTEBoilerBronze.class, remap = false)
public abstract class MixinBoilerBronze {

    @Inject(method = "combustFuel", at = @At("HEAD"), cancellable = true)
    private static void valiertweaks$noAsh(int burnTime, boolean isBlock,
        CallbackInfoReturnable<Optional<OrePrefixes>> cir) {
        if (TweaksConfig.noBoilerAsh) cir.setReturnValue(Optional.empty());
    }
}
