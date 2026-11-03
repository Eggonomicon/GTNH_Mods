package gtnhserver.mmfreepower.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.building.MMInventory;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

import gtnhserver.mmfreepower.MkIV;
import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;

/**
 * Every item a manipulator places goes through MMInventory.tryConsumeItems. For a Valier MM Mk IV, free blocks count
 * as taken without touching any inventory; see {@link MkIV#tryConsume}.
 */
@Mixin(value = MMInventory.class, remap = false)
public abstract class MixinMMInventory {

    @Inject(method = "tryConsumeItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmfreepower$freeBlocks(List<BigItemStack> items, int flags,
        CallbackInfoReturnable<BooleanObjectImmutablePair<List<BigItemStack>>> cir) {
        MMInventory self = (MMInventory) (Object) this;
        BooleanObjectImmutablePair<List<BigItemStack>> result = MkIV
            .tryConsume((IPseudoInventory) self, self.player, items, flags);
        if (result != null) cir.setReturnValue(result);
    }
}
