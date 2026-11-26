package com.valier.tweaks.mixin;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valier.tweaks.PdimTerrain;

import me.eigenraven.personalspace.world.PersonalWorldProvider;

/**
 * Personal Space's world provider: a personal dimension set to real terrain ({@code /pdim terrain realistic},
 * {@link PdimTerrain}) gets RWG2's biome picker (registerWorldChunkManager) and RWG2's chunk generator
 * (createChunkGenerator) on the server. Every other personal dimension, and every client's copy, is unchanged. The
 * targets are Personal Space's overrides of WorldProvider's methods, by their runtime (SRG) names.
 */
@Mixin(value = PersonalWorldProvider.class, remap = false)
public abstract class MixinPersonalWorldProvider {

    @Inject(method = "func_76572_b", at = @At("TAIL"))
    private void valiertweaks$realisticBiomes(CallbackInfo ci) {
        WorldProvider self = (WorldProvider) (Object) this;
        if (self.worldObj == null || self.worldObj.isRemote || !PdimTerrain.isRealistic(self.dimensionId)) return;
        WorldChunkManager manager = PdimTerrain.manager(self.worldObj, ((PersonalWorldProvider) (Object) this).getConfig());
        if (manager != null) self.worldChunkMgr = manager;
    }

    @Inject(method = "func_76555_c", at = @At("HEAD"), cancellable = true)
    private void valiertweaks$realisticTerrain(CallbackInfoReturnable<IChunkProvider> cir) {
        WorldProvider self = (WorldProvider) (Object) this;
        if (self.worldObj == null || self.worldObj.isRemote || !PdimTerrain.isRealistic(self.dimensionId)) return;
        if (PdimTerrain.isOneBiome(self.worldChunkMgr)) cir.setReturnValue(PdimTerrain.generator(self.worldObj));
    }
}
