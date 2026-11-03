package com.valier.worldfix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server-side admin tools for Valier's world: {@code /worldfix pregen}, a pregenerator that also finishes populating
 * chunks and only works while nobody is online (see {@link PregenTask}).
 */
@Mod(
    modid = ValierWorldFix.MODID,
    version = Tags.VERSION,
    name = "Valier World Fix",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class ValierWorldFix {

    public static final String MODID = "valierworldfix";
    public static final Logger LOG = LogManager.getLogger("ValierWorldFix");

    /** The running pregen, if any. */
    static PregenTask task;

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new WorldFixCommand());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (PregenTask.savedActive()) task = PregenTask.loadSaved();
        if (task != null) LOG.info("Carrying on with the pregen: " + task.progress());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (task != null && !task.finished()) task.save(true);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || task == null) return;
        task.tick(FMLCommonHandler.instance()
            .getMinecraftServerInstance());
        if (task.finished()) task = null;
    }
}
