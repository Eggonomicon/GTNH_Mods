package com.valier.tweaks.grid;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierRecipes;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * Grid recipes, each tier from that tier's GregTech parts, by hand and (cheaper) in the assembler:
 *
 * <pre>
 * Grid Plug:       its hull, its emitter, 2 of its circuits, 2 of its cables   (assembler: hull, emitter, 1 circuit)
 * Grid Point:      its hull, its sensor, 2 of its circuits, 2 of its cables    (assembler: hull, sensor, 1 circuit)
 * Grid Cell:       its hull, its battery, 2 of its circuits                    (assembler: hull, battery, 1 circuit)
 * Grid Controller: an MV hull, 2 MV emitters, an MV field generator            (assembler: hull, field generator, 1 emitter)
 * </pre>
 *
 * Without GregTech (a test client): redstone, iron and the like.
 */
public final class GridRecipes {

    private GridRecipes() {}

    public static void register() {
        if (!Loader.isModLoaded("gregtech")) {
            for (int t = 0; t < GridTier.COUNT; t++) {
                GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.gridPlug, 1, t), "IRI", "RGR", "IRI", 'I', "ingotIron", 'R',
                    "dustRedstone", 'G', new ItemStack(Items.gold_ingot)));
                GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.gridPoint, 1, t), "IRI", "RDR", "IRI", 'I', "ingotIron", 'R',
                    "dustRedstone", 'D', new ItemStack(Items.diamond)));
                GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.gridCell, 1, t), "IRI", "RBR", "IRI", 'I', "ingotIron", 'R',
                    "dustRedstone", 'B', new ItemStack(Blocks.redstone_block)));
            }
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.gridController), "IRI", "RER", "IRI", 'I', "ingotIron", 'R',
                "dustRedstone", 'E', new ItemStack(Items.ender_eye)));
            return;
        }
        long[] eut = { ValierRecipes.LV, ValierRecipes.MV, ValierRecipes.HV, ValierRecipes.EV, ValierRecipes.IV, ValierRecipes.LUV,
            ValierRecipes.ZPM, ValierRecipes.UV };
        for (int t = 0; t < GridTier.COUNT; t++) {
            String circuit = ValierRecipes.circuit(t + 1), cable = GridTier.CABLES[t];
            ItemStack hull = Gt.part("Hull_", t), emitter = Gt.part("Emitter_", t), sensor = Gt.part("Sensor_", t);
            String battery = Gt.battery(t);
            ItemStack plug = new ItemStack(ModBlocks.gridPlug, 1, t), point = new ItemStack(ModBlocks.gridPoint, 1, t),
                cell = new ItemStack(ModBlocks.gridCell, 1, t);
            GameRegistry.addRecipe(new ShapedOreRecipe(plug, "CEC", "WHW", 'C', circuit, 'E', emitter, 'W', cable, 'H', hull));
            GameRegistry.addRecipe(new ShapedOreRecipe(point, "CSC", "WHW", 'C', circuit, 'S', sensor, 'W', cable, 'H', hull));
            GameRegistry.addRecipe(new ShapedOreRecipe(cell, "CBC", " H ", 'C', circuit, 'B', battery, 'H', hull));
            ValierRecipes.assembler(plug, 10, eut[t], hull, emitter, ValierRecipes.ore(circuit, 1));
            ValierRecipes.assembler(point, 10, eut[t], hull, sensor, ValierRecipes.ore(circuit, 1));
            ValierRecipes.assembler(cell, 10, eut[t], hull, ValierRecipes.ore(battery, 1), ValierRecipes.ore(circuit, 1));
        }
        ItemStack ctrl = new ItemStack(ModBlocks.gridController);
        ItemStack hull = Gt.part("Hull_", 1), emitter = Gt.part("Emitter_", 1), field = Gt.part("Field_Generator_", 1);
        GameRegistry.addRecipe(new ShapedOreRecipe(ctrl, "EFE", " H ", 'E', emitter, 'F', field, 'H', hull));
        ValierRecipes.assembler(ctrl, 15, ValierRecipes.MV, hull, field, emitter);
    }

    /** GregTech's parts (only touched with GregTech loaded). */
    private static final class Gt {

        private static final Materials[] TIERS = { Materials.LV, Materials.MV, Materials.HV, Materials.EV, Materials.IV, Materials.LuV,
            Materials.ZPM, Materials.UV };

        /** "Hull_LV" and the like, for tier t (0: LV). */
        static ItemStack part(String prefix, int t) {
            return ItemList.valueOf(prefix + GridTier.NAMES[GridTier.clamp(t)])
                .get(1);
        }

        static String battery(int t) {
            return OrePrefixes.battery.get(TIERS[GridTier.clamp(t)])
                .toString();
        }
    }
}
