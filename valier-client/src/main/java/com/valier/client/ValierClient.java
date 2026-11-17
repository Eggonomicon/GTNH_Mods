package com.valier.client;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;

/**
 * Optional client add-on for the Valier server's mods: WAILA shows a Valier XP Crafter as one ({@link XpCrafterHud}).
 * The server never needs it, and on a server without Valier Tweaks it does nothing. It uses no client-only classes, so
 * it is also harmless on a server.
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
    }
}
