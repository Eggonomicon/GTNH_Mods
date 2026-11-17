package com.valier.tweaks;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/**
 * Chain armour macerates into whole steel dust: GregTech gives 5 small piles for a chain helmet and 7 for chain
 * leggings (boots give 1 dust, a chestplate 2). With {@link TweaksConfig#chainArmourFullDust} the helmet gives 1 steel
 * dust and the leggings 2, so a full set still gives 6. Every GregTech macerator (and the macerating multiblocks) uses
 * the same recipes, which are changed at server start. Clients' NEI still shows the small piles.
 */
final class ChainArmour {

    private ChainArmour() {}

    /** Whole steel dust for a piece of chain armour, or 0 to leave its recipe alone. */
    static int dustFor(Item item) {
        if (item == Items.chainmail_helmet) return 1;
        if (item == Items.chainmail_leggings) return 2;
        return 0;
    }

    /** Changes the macerator recipes; returns how many. */
    static int apply() {
        if (!TweaksConfig.chainArmourFullDust) return 0;
        int changed = 0;
        for (GTRecipe r : RecipeMaps.maceratorRecipes.getAllRecipes()) {
            if (r.mInputs == null || r.mInputs.length != 1 || r.mInputs[0] == null) continue;
            int dust = dustFor(r.mInputs[0].getItem());
            if (dust == 0) continue;
            ItemStack steel = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Steel, dust);
            if (steel == null) continue;
            r.mOutputs = new ItemStack[] { steel };
            if (r.mOutputChances != null) r.mOutputChances = new int[] { 10000 };
            changed++;
        }
        ValierTweaks.LOG.info("Chain armour: {} macerator recipes now give whole steel dust", changed);
        return changed;
    }
}
