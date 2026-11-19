package com.valier.tweaks.pipe;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.valier.tweaks.ModBlocks;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;

/**
 * Pipe recipes (registered on both sides, so NEI shows them):
 *
 * <pre>
 * Basic (8):    bronze plates around a hopper (items) or bucket (fluid), glass panes left and right
 * Advanced (8): 8 Basic around an LV conveyor (items) or LV pump (fluid)
 * Elite (8):    8 Advanced around an MV conveyor or MV pump
 * Wireless (1): an Elite pipe, an ender pearl, an MV emitter and an MV sensor
 * </pre>
 *
 * Without GregTech (a test client): iron instead of bronze plates, a hopper or bucket for every part.
 */
public final class PipeRecipes {

    private PipeRecipes() {}

    public static void register() {
        boolean gt = Loader.isModLoaded("gregtech");
        for (PipeKind kind : PipeKind.values()) {
            BlockPipe block = kind == PipeKind.ITEM ? ModBlocks.itemPipe : ModBlocks.fluidPipe;
            Object core = kind == PipeKind.ITEM ? new ItemStack(Blocks.hopper) : new ItemStack(Items.bucket);
            Object lv = gt ? (kind == PipeKind.ITEM ? Gt.conveyorLV() : Gt.pumpLV()) : core;
            Object mv = gt ? (kind == PipeKind.ITEM ? Gt.conveyorMV() : Gt.pumpMV()) : core;
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.BASIC), "PPP", "GCG", "PPP", 'P',
                gt ? "plateBronze" : "ingotIron", 'G', "paneGlass", 'C', core));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.ADVANCED), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.BASIC), 'C', lv));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.ELITE), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.ADVANCED), 'C', mv));
            GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(block, 1, PipeTier.WIRELESS),
                new ItemStack(block, 1, PipeTier.ELITE), new ItemStack(Items.ender_pearl), gt ? Gt.emitterMV() : Items.ender_eye,
                gt ? Gt.sensorMV() : Items.ender_eye));
        }
    }

    /** GregTech's parts (only touched with GregTech loaded). */
    private static final class Gt {

        static ItemStack conveyorLV() {
            return ItemList.Conveyor_Module_LV.get(1);
        }

        static ItemStack conveyorMV() {
            return ItemList.Conveyor_Module_MV.get(1);
        }

        static ItemStack pumpLV() {
            return ItemList.Electric_Pump_LV.get(1);
        }

        static ItemStack pumpMV() {
            return ItemList.Electric_Pump_MV.get(1);
        }

        static ItemStack emitterMV() {
            return ItemList.Emitter_MV.get(1);
        }

        static ItemStack sensorMV() {
            return ItemList.Sensor_MV.get(1);
        }
    }
}
