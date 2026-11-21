package com.valier.tweaks;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.Side;

/**
 * {@code valiertweaks selftest mod}: the parts of 2.0.0's one-jar setup a dedicated server can check. The join check
 * (both ways), the stub that takes the old Valier Client's mod id, the language file, and the server's proxy.
 */
final class ModSelfTest {

    private ModSelfTest() {}

    static void run() {
        boolean server = ValierTweaks.compatible("2.0.0", Side.CLIENT, "2.0.5")
            && ValierTweaks.compatible("v2.0.1-dev", Side.CLIENT, "2.0.0") && !ValierTweaks.compatible("2.1.0", Side.CLIENT, "2.0.0")
            && !ValierTweaks.compatible("1.14.0", Side.CLIENT, "2.0.0") && !ValierTweaks.compatible(null, Side.CLIENT, "2.0.0")
            && !ValierTweaks.compatible("dev", Side.CLIENT, "2.0.0");
        SelfTest.check(server, "join check on the server: a player needs Valier Tweaks 2.0.x to join a 2.0 server (2.0.0, "
            + "v2.0.1-dev in; 2.1.0, 1.14.0, none, \"dev\" out)");
        boolean client = ValierTweaks.compatible(null, Side.SERVER, "2.0.0") && ValierTweaks.compatible("2.0.9", Side.SERVER, "2.0.0")
            && !ValierTweaks.compatible("2.1.0", Side.SERVER, "2.0.0");
        SelfTest.check(client, "join check on a player's side: a server without Valier Tweaks is fine, a 2.0.x one too, a "
            + "2.1 one isn't");
        String ours = ValierTweaks.majorMinor(Tags.VERSION);
        SelfTest.check(ours != null, "this build's version " + Tags.VERSION + " reads as major.minor " + ours);

        ModContainer stub = Loader.instance()
            .getIndexedModList()
            .get("valierclient");
        ModContainer tweaks = Loader.instance()
            .getIndexedModList()
            .get(ValierTweaks.MODID);
        SelfTest.check(stub != null && tweaks != null && stub.getSource()
            .equals(tweaks.getSource()),
            "the old Valier Client's mod id is taken by this jar, so an old valierclient jar next to it is a duplicate ("
                + (stub == null ? "none" : stub.getSource()) + ")");
        String name = StatCollector.translateToLocal("valiertweaks.xpcrafter.name");
        SelfTest.check("Valier XP Crafter".equals(name), "the language file is in this jar (" + name + ")");
        SelfTest.check(ValierTweaks.proxy != null && ValierTweaks.proxy.getClass() == CommonProxy.class,
            "a dedicated server gets the common proxy, never the client one (" + ValierTweaks.proxy + ")");
        recipes();
    }

    /** 2.2.0: every Valier item that can be crafted has a crafting recipe and a cheaper GregTech assembler recipe. */
    static void recipes() {
        java.util.List<net.minecraft.item.ItemStack> want = new java.util.ArrayList<>();
        for (com.valier.tweaks.pipe.BlockPipe b : new com.valier.tweaks.pipe.BlockPipe[] { ModBlocks.itemPipe, ModBlocks.fluidPipe })
            for (int tier = 0; tier < com.valier.tweaks.pipe.PipeTier.CREATIVE; tier++) want.add(new net.minecraft.item.ItemStack(b, 1, tier));
        want.add(new net.minecraft.item.ItemStack(ModBlocks.pipeConfigurator));
        if (TweaksConfig.xpCrafter) want.add(new net.minecraft.item.ItemStack(ModBlocks.xpCrafter));
        // 2.3.0: barrels, satchels and flasks, the dock, the Grid.
        want.add(new net.minecraft.item.ItemStack(ModBlocks.barrel, 1, 0));
        for (int t = 1; t < 7; t++) want.add(new net.minecraft.item.ItemStack(ModBlocks.barrelUpgrade, 1, t));
        want.add(new net.minecraft.item.ItemStack(ModBlocks.satchel, 1, 0));
        want.add(new net.minecraft.item.ItemStack(ModBlocks.flask, 1, 0));
        for (int t = 1; t <= 5; t++) {
            want.add(new net.minecraft.item.ItemStack(ModBlocks.satchelUpgrade, 1, t));
            want.add(new net.minecraft.item.ItemStack(ModBlocks.satchelUpgrade, 1, t + 8));
        }
        want.add(new net.minecraft.item.ItemStack(ModBlocks.satchelDock));
        for (int t = 0; t < 8; t++) for (com.valier.tweaks.grid.BlockGrid b : new com.valier.tweaks.grid.BlockGrid[] { ModBlocks.gridPlug,
            ModBlocks.gridPoint, ModBlocks.gridCell }) want.add(new net.minecraft.item.ItemStack(b, 1, t));
        want.add(new net.minecraft.item.ItemStack(ModBlocks.gridController));
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (net.minecraft.item.ItemStack w : want) {
            boolean crafted = false, assembled = false;
            for (Object r : net.minecraft.item.crafting.CraftingManager.getInstance()
                .getRecipeList()) {
                net.minecraft.item.ItemStack out = ((net.minecraft.item.crafting.IRecipe) r).getRecipeOutput();
                crafted |= out != null && out.getItem() == w.getItem() && out.getItemDamage() == w.getItemDamage();
            }
            for (gregtech.api.util.GTRecipe r : gregtech.api.recipe.RecipeMaps.assemblerRecipes.getAllRecipes()) {
                if (r.mOutputs == null) continue;
                for (net.minecraft.item.ItemStack out : r.mOutputs)
                    assembled |= out != null && out.getItem() == w.getItem() && out.getItemDamage() == w.getItemDamage();
            }
            if (!crafted || !assembled) missing.add(w.getDisplayName() + (crafted ? "" : " (no crafting)") + (assembled ? "" : " (no assembler)"));
        }
        SelfTest.check(missing.isEmpty(), "all " + want.size() + " craftable Valier items have a crafting and an assembler recipe "
            + (missing.isEmpty() ? "" : "(missing: " + missing + ")"));
        // Cheaper: Advanced item pipes from 6 Basic in the assembler (8 by hand).
        int basics = -1;
        for (gregtech.api.util.GTRecipe r : gregtech.api.recipe.RecipeMaps.assemblerRecipes.getAllRecipes()) {
            if (r.mOutputs == null || r.mOutputs.length == 0 || r.mOutputs[0] == null) continue;
            net.minecraft.item.ItemStack out = r.mOutputs[0];
            if (out.getItem() != net.minecraft.item.Item.getItemFromBlock(ModBlocks.itemPipe)
                || out.getItemDamage() != com.valier.tweaks.pipe.PipeTier.ADVANCED) continue;
            for (net.minecraft.item.ItemStack in : r.mInputs)
                if (in != null && in.getItem() == out.getItem() && in.getItemDamage() == com.valier.tweaks.pipe.PipeTier.BASIC) basics = in.stackSize;
        }
        SelfTest.check(basics == 6, "the assembler makes 8 Advanced item pipes from 6 Basic ones (" + basics + ")");
    }
}
