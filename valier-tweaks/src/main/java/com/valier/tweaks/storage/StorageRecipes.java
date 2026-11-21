package com.valier.tweaks.storage;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierRecipes;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;

/**
 * Barrel recipes, by hand and (cheaper) in GregTech's assembler:
 *
 * <pre>
 * Basic barrel:        4 wood slabs, 4 planks and a chest      (assembler: 2 slabs, 2 planks, the chest)
 * Barrel Upgrade, LV:  4 steel plates, 2 LV circuits, a chest, hammer and wrench
 * Barrel Upgrade, MV+: 4 of the tier's plates, 2 of its circuits, its conveyor, hammer and wrench
 *                      (assembler: 2 plates, 1 circuit, the chest or conveyor)
 * </pre>
 *
 * Plates: steel, aluminium, stainless steel, titanium, tungstensteel, chrome. Without GregTech (a test client): iron
 * ingots, redstone and a chest for every upgrade.
 */
public final class StorageRecipes {

    private StorageRecipes() {}

    public static void register() {
        boolean gt = Loader.isModLoaded("gregtech");
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.barrel, 1, BarrelTier.BASIC), "SPS", "PCP", "SPS", 'S',
            "slabWood", 'P', "plankWood", 'C', new ItemStack(Blocks.chest)));
        if (gt) ValierRecipes.assembler(new ItemStack(ModBlocks.barrel, 1, BarrelTier.BASIC), 10, ValierRecipes.STEAM,
            ValierRecipes.ore("slabWood", 2), ValierRecipes.ore("plankWood", 2), new ItemStack(Blocks.chest));
        long[] eut = { 0, ValierRecipes.LV, ValierRecipes.MV, ValierRecipes.HV, ValierRecipes.EV, ValierRecipes.IV, ValierRecipes.LUV };
        for (int t = 1; t < BarrelTier.COUNT; t++) {
            ItemStack out = new ItemStack(ModBlocks.barrelUpgrade, 1, t);
            if (!gt) {
                GameRegistry.addRecipe(new ShapedOreRecipe(out, "IRI", "RCR", "IRI", 'I', "ingotIron", 'R', "dustRedstone", 'C',
                    new ItemStack(Blocks.chest)));
                continue;
            }
            Object core = t == 1 ? new ItemStack(Blocks.chest) : Gt.conveyor(t);
            String plate = BarrelTier.PLATES[t], circuit = ValierRecipes.circuit(t);
            GameRegistry.addRecipe(new ShapedOreRecipe(out, "PhP", "CMC", "PwP", 'P', plate, 'C', circuit, 'M', core, 'h',
                "craftingToolHardHammer", 'w', "craftingToolWrench"));
            ValierRecipes.assembler(out, 10, eut[t], ValierRecipes.ore(plate, 2), ValierRecipes.ore(circuit, 1), core);
        }
        satchels(gt);
    }

    /**
     * <pre>
     * Satchel (LV):   4 steel plates, 2 LV circuits, an LV conveyor, a chest, hammer
     * Flask (LV):     4 steel plates, 2 LV circuits, an LV pump, a large steel fluid cell, hammer
     *                 (assembler: 2 plates, 1 circuit, the conveyor or pump, the chest or cell)
     * Upgrades:       4 of the tier's plates, 2 of its circuits, its conveyor (Satchel) or pump (Flask), hammer, wrench
     *                 (assembler: 2 plates, 1 circuit, the conveyor or pump); applied in a crafting grid
     * Satchel Dock:   an LV hull, 2 LV circuits and a chest (assembler: the hull, 1 circuit, the chest)
     * </pre>
     */
    static void satchels(boolean gt) {
        long[] eut = { ValierRecipes.LV, ValierRecipes.MV, ValierRecipes.HV, ValierRecipes.EV, ValierRecipes.IV, ValierRecipes.LUV };
        for (int f = 0; f < 2; f++) {
            boolean fluid = f == 1;
            ItemStack bag = new ItemStack(fluid ? ModBlocks.flask : ModBlocks.satchel, 1, SatchelTier.LV);
            if (!gt) {
                GameRegistry.addRecipe(new ShapedOreRecipe(bag, "III", "ICI", "III", 'I', "ingotIron", 'C',
                    fluid ? new ItemStack(net.minecraft.init.Items.bucket) : new ItemStack(Blocks.chest)));
                continue;
            }
            Object part = fluid ? Gt.pump(1) : Gt.conveyor(1), core = fluid ? Gt.cell() : new ItemStack(Blocks.chest);
            String circuit = ValierRecipes.circuit(1);
            GameRegistry.addRecipe(new ShapedOreRecipe(bag, "PCP", "XMh", "PCP", 'P', "plateSteel", 'C', circuit, 'X', part, 'M', core,
                'h', "craftingToolHardHammer"));
            ValierRecipes.assembler(bag, 10, ValierRecipes.LV, ValierRecipes.ore("plateSteel", 2), ValierRecipes.ore(circuit, 1), part, core);
            for (int t = SatchelTier.MV; t <= SatchelTier.LUV; t++) {
                ItemStack up = new ItemStack(ModBlocks.satchelUpgrade, 1, t + (fluid ? ItemSatchelUpgrade.FLUID : 0));
                Object x = fluid ? Gt.pump(t + 1) : Gt.conveyor(t + 1);
                String plate = SatchelTier.PLATES[t], c = ValierRecipes.circuit(t + 1);
                GameRegistry.addRecipe(new ShapedOreRecipe(up, "PCP", "hXw", "PCP", 'P', plate, 'C', c, 'X', x, 'h', "craftingToolHardHammer",
                    'w', "craftingToolWrench"));
                ValierRecipes.assembler(up, 10, eut[t], ValierRecipes.ore(plate, 2), ValierRecipes.ore(c, 1), x);
            }
        }
        ItemStack dock = new ItemStack(ModBlocks.satchelDock);
        if (!gt) {
            GameRegistry.addRecipe(new ShapedOreRecipe(dock, "III", "ICI", "III", 'I', "ingotIron", 'C', "dustRedstone"));
            return;
        }
        String c = ValierRecipes.circuit(1);
        GameRegistry.addRecipe(new ShapedOreRecipe(dock, "CHC", " M ", 'C', c, 'H', Gt.hull(), 'M', new ItemStack(Blocks.chest)));
        ValierRecipes.assembler(dock, 10, ValierRecipes.LV, Gt.hull(), ValierRecipes.ore(c, 1), new ItemStack(Blocks.chest));
    }

    /** GregTech's parts (only touched with GregTech loaded). */
    private static final class Gt {

        static ItemStack hull() {
            return ItemList.Hull_LV.get(1);
        }

        static ItemStack cell() {
            return ItemList.Large_Fluid_Cell_Steel.get(1);
        }

        /** The pump of a voltage tier (1: LV to 6: LuV). */
        static ItemStack pump(int tier) {
            switch (tier) {
                case 1:
                    return ItemList.Electric_Pump_LV.get(1);
                case 2:
                    return ItemList.Electric_Pump_MV.get(1);
                case 3:
                    return ItemList.Electric_Pump_HV.get(1);
                case 4:
                    return ItemList.Electric_Pump_EV.get(1);
                case 5:
                    return ItemList.Electric_Pump_IV.get(1);
                default:
                    return ItemList.Electric_Pump_LuV.get(1);
            }
        }

        /** The conveyor of a voltage tier (1: LV to 6: LuV). */
        static ItemStack conveyor(int tier) {
            switch (tier) {
                case 1:
                    return ItemList.Conveyor_Module_LV.get(1);
                case 2:
                    return ItemList.Conveyor_Module_MV.get(1);
                case 3:
                    return ItemList.Conveyor_Module_HV.get(1);
                case 4:
                    return ItemList.Conveyor_Module_EV.get(1);
                case 5:
                    return ItemList.Conveyor_Module_IV.get(1);
                default:
                    return ItemList.Conveyor_Module_LuV.get(1);
            }
        }
    }
}
