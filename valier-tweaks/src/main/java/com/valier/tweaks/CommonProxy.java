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

    /** A loot spawner's menu screen (players' computers only). */
    public Object lootGui(LootMenu container) {
        return null;
    }

    /** A Satchel's or Flask's menu screen (players' computers only). */
    public Object satchelGui(com.valier.tweaks.storage.SatchelMenu container) {
        return null;
    }

    /** A Grid block's menu screen (players' computers only). */
    public Object gridGui(com.valier.tweaks.grid.GridMenu container) {
        return null;
    }

    /** A barrel's menu screen (players' computers only). */
    public Object barrelGui(com.valier.tweaks.storage.BarrelMenu container) {
        return null;
    }
}
