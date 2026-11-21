package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;

/**
 * Every Valier crafting recipe also has a GregTech assembler recipe, which costs less: about half the metal and other
 * bulk, no tools, while GT parts (circuits, conveyors, pumps, emitters) stay one each, since they are what gates a tier.
 * The assembler recipes are only made with GregTech loaded. {@link #made} lists their outputs, for the self-test.
 */
public final class ValierRecipes {

    /** Recipe EU/t by tier, as GregTech's own recipes use. */
    public static final long STEAM = 16, LV = 30, MV = 120, HV = 480, EV = 1920, IV = 7680, LUV = 30720, ZPM = 122880,
        UV = 491520;

    /** What the assembler recipes made so far make. */
    public static final List<ItemStack> made = new ArrayList<>();

    private ValierRecipes() {}

    /** An ore dictionary input: any item with that name, n of them. */
    public static Object[] ore(String name, int n) {
        return new Object[] { name, n };
    }

    /** "circuitBasic" and so on: GregTech's ore name for a circuit of tier 1 (LV) to 8 (UV). */
    public static String circuit(int tier) {
        return Gt.circuit(tier);
    }

    /** An assembler recipe (inputs: item stacks or {@link #ore} arrays), when GregTech is loaded. */
    public static void assembler(ItemStack out, int seconds, long eut, Object... inputs) {
        if (out == null || !Loader.isModLoaded("gregtech")) return;
        try {
            Gt.assembler(out, seconds, eut, inputs);
            made.add(out.copy());
        } catch (Throwable e) {
            ValierTweaks.LOG.warn("Couldn't add the assembler recipe for " + out.getDisplayName(), e);
        }
    }

    /** GregTech's classes (only touched with GregTech loaded). */
    private static final class Gt {

        private static final Materials[] TIERS = { Materials.ULV, Materials.LV, Materials.MV, Materials.HV, Materials.EV,
            Materials.IV, Materials.LuV, Materials.ZPM, Materials.UV };

        static String circuit(int tier) {
            return OrePrefixes.circuit.get(TIERS[Math.max(0, Math.min(TIERS.length - 1, tier))])
                .toString();
        }

        static void assembler(ItemStack out, int seconds, long eut, Object... inputs) {
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(out.copy())
                .duration(seconds * 20)
                .eut(eut)
                .addTo(RecipeMaps.assemblerRecipes);
        }
    }
}
