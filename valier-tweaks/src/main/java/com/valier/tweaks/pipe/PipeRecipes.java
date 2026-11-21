package com.valier.tweaks.pipe;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierRecipes;

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
 * Superior (8): 8 Elite around an HV conveyor or HV pump
 * Ultimate (8): 8 Superior around an EV conveyor or EV pump
 * Wireless (1): an Elite pipe, an ender pearl, an MV emitter and an MV sensor
 * Creative:     none (creative tab and NEI only)
 * Pipe Configurator: a Basic item pipe, a Basic fluid pipe, redstone and paper (shapeless)
 * </pre>
 *
 * With GregTech, each also has a cheaper assembler recipe ({@link ValierRecipes}): Basic from 3 bronze plates, a glass
 * pane and the hopper or bucket; each next tier from 6 of the one below and the part; Wireless 2 at a time from 2
 * Elite, one ender pearl, the emitter and the sensor; the Pipe Configurator 2 at a time.
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
            Object hv = gt ? (kind == PipeKind.ITEM ? Gt.conveyorHV() : Gt.pumpHV()) : core;
            Object ev = gt ? (kind == PipeKind.ITEM ? Gt.conveyorEV() : Gt.pumpEV()) : core;
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.BASIC), "PPP", "GCG", "PPP", 'P',
                gt ? "plateBronze" : "ingotIron", 'G', "paneGlass", 'C', core));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.ADVANCED), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.BASIC), 'C', lv));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.ELITE), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.ADVANCED), 'C', mv));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.SUPERIOR), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.ELITE), 'C', hv));
            GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(block, 8, PipeTier.ULTIMATE), "BBB", "BCB", "BBB", 'B',
                new ItemStack(block, 1, PipeTier.SUPERIOR), 'C', ev));
            GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(block, 1, PipeTier.WIRELESS),
                new ItemStack(block, 1, PipeTier.ELITE), new ItemStack(Items.ender_pearl), gt ? Gt.emitterMV() : Items.ender_eye,
                gt ? Gt.sensorMV() : Items.ender_eye));
            if (!gt) continue;
            ValierRecipes.assembler(new ItemStack(block, 8, PipeTier.BASIC), 5, ValierRecipes.STEAM, ValierRecipes.ore("plateBronze", 3),
                ValierRecipes.ore("paneGlass", 1), core);
            ValierRecipes.assembler(new ItemStack(block, 8, PipeTier.ADVANCED), 10, ValierRecipes.LV,
                new ItemStack(block, 6, PipeTier.BASIC), lv);
            ValierRecipes.assembler(new ItemStack(block, 8, PipeTier.ELITE), 10, ValierRecipes.MV,
                new ItemStack(block, 6, PipeTier.ADVANCED), mv);
            ValierRecipes.assembler(new ItemStack(block, 8, PipeTier.SUPERIOR), 10, ValierRecipes.HV,
                new ItemStack(block, 6, PipeTier.ELITE), hv);
            ValierRecipes.assembler(new ItemStack(block, 8, PipeTier.ULTIMATE), 10, ValierRecipes.EV,
                new ItemStack(block, 6, PipeTier.SUPERIOR), ev);
            ValierRecipes.assembler(new ItemStack(block, 2, PipeTier.WIRELESS), 10, ValierRecipes.MV,
                new ItemStack(block, 2, PipeTier.ELITE), new ItemStack(Items.ender_pearl), Gt.emitterMV(), Gt.sensorMV());
        }
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(ModBlocks.pipeConfigurator), new ItemStack(ModBlocks.itemPipe, 1,
            PipeTier.BASIC), new ItemStack(ModBlocks.fluidPipe, 1, PipeTier.BASIC), "dustRedstone", new ItemStack(Items.paper)));
        if (gt) ValierRecipes.assembler(new ItemStack(ModBlocks.pipeConfigurator, 2), 5, ValierRecipes.STEAM,
            new ItemStack(ModBlocks.itemPipe, 1, PipeTier.BASIC), new ItemStack(ModBlocks.fluidPipe, 1, PipeTier.BASIC),
            ValierRecipes.ore("dustRedstone", 1), new ItemStack(Items.paper));
    }

    /** GregTech's parts (only touched with GregTech loaded). */
    private static final class Gt {

        static ItemStack conveyorLV() {
            return ItemList.Conveyor_Module_LV.get(1);
        }

        static ItemStack conveyorMV() {
            return ItemList.Conveyor_Module_MV.get(1);
        }

        static ItemStack conveyorHV() {
            return ItemList.Conveyor_Module_HV.get(1);
        }

        static ItemStack conveyorEV() {
            return ItemList.Conveyor_Module_EV.get(1);
        }

        static ItemStack pumpHV() {
            return ItemList.Electric_Pump_HV.get(1);
        }

        static ItemStack pumpEV() {
            return ItemList.Electric_Pump_EV.get(1);
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
