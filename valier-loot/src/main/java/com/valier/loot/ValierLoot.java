package com.valier.loot;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.valier.loot.selftest.SelfTest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Per-player loot chests, server side only: worldgen chests show each player their own copy of the loot in a normal
 * chest window, so clients need nothing installed.
 */
@Mod(
    modid = ValierLoot.MODID,
    version = Tags.VERSION,
    name = "Valier Loot",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class ValierLoot {

    public static final String MODID = "valierloot";
    public static final Logger LOG = LogManager.getLogger("ValierLoot");
    public static LootEvents events;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LootConfig.load(event.getSuggestedConfigurationFile());
        events = new LootEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        GameRegistry.registerWorldGenerator(events.generator, LootEvents.GENERATOR_WEIGHT);
        if (SelfTest.ENABLED) SelfTest.registerWorldGenerator();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LootCommand());
    }

    /** Custom loot pools go in once the server has started, after TooMuchLoot has (re)loaded its tables. */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        LootConfig.registerCustomPools();
    }
}
