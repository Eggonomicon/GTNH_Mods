package com.valier.client;

import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Optional client add-on for the Valier server's mods: WAILA shows a Valier XP Crafter as one ({@link XpCrafterHud}),
 * NEI shows its crafting recipe ({@link XpCrafterRecipe}), lists it ({@link NeiCompat}) and finds it with "@Valier"
 * ({@link ModSearch}), its tooltip's mod name says Valier Tweaks ({@link ModNameLine}), and a Valier creative tab
 * holds one ({@link ValierTab}). The server never needs it, and on a server without Valier
 * Tweaks it does nothing. It uses no client-only classes, so it is also harmless on a server.
 */
@Mod(
    modid = ValierClient.MODID,
    version = Tags.VERSION,
    name = "Valier Client",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "after:Waila")
public class ValierClient {

    public static final String MODID = "valierclient";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Loader.isModLoaded("Waila"))
            FMLInterModComms.sendMessage("Waila", "register", "com.valier.client.XpCrafterHud.callbackRegister");
        // Only for NEI on players' computers; where Valier Tweaks is loaded too (single player), its own recipe is there.
        if (event.getSide()
            .isClient() && Loader.isModLoaded("gregtech") && !Loader.isModLoaded("valiertweaks")) {
            IRecipe recipe = XpCrafterRecipe.make();
            if (recipe != null) GameRegistry.addRecipe(recipe);
        }
    }

    /** After WAILA's postInit, which adds its tooltip mod name handlers. Players' computers only. */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (!event.getSide()
            .isClient()) return;
        MinecraftForge.EVENT_BUS.register(new ModNameLine());
        Item tank = Loader.isModLoaded("gregtech") ? GameRegistry.findItem("gregtech", "gt.blockmachines") : null;
        if (tank != null) new ValierTab(tank);
        if (Loader.isModLoaded("NotEnoughItems") && Loader.isModLoaded("gregtech")) NeiCompat.register();
    }
}
