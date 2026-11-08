package com.valier.tweaks;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * Small server-side gameplay fixes for Valier: a Tinkers' Construct lumber axe fells the whole tree even when it breaks
 * partway ({@link TreeFinisher}), and players can make their Personal Space dimension peaceful ({@link PeacefulDims},
 * {@code /pdim}).
 */
@Mod(
    modid = ValierTweaks.MODID,
    version = Tags.VERSION,
    name = "Valier Tweaks",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class ValierTweaks {

    public static final String MODID = "valiertweaks";
    public static final Logger LOG = LogManager.getLogger("ValierTweaks");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        TweaksConfig.load(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        TreeFinisher finisher = new TreeFinisher();
        MinecraftForge.EVENT_BUS.register(finisher); // block breaks
        FMLCommonHandler.instance()
            .bus()
            .register(finisher); // server ticks
        PeacefulDims peaceful = new PeacefulDims();
        MinecraftForge.EVENT_BUS.register(peaceful); // spawns
        FMLCommonHandler.instance()
            .bus()
            .register(peaceful); // the sweep, on server ticks
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PdimCommand());
        if (SelfTest.ENABLED) event.registerServerCommand(new TweaksCommand());
    }
}
