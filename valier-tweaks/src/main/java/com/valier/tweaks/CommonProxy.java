package com.valier.tweaks;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * What only one side does (FML picks this on a dedicated server, {@code client.ClientProxy} on players' computers).
 * Everything both sides need stays in {@link ValierTweaks}.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    /** A pipe face's menu screen (players' computers only). */
    public Object pipeGui(com.valier.tweaks.pipe.ContainerPipeFace container) {
        return null;
    }
}
