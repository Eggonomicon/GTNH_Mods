package com.valier.claims;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * Hostile mobs don't spawn in claimed chunks by default: every ServerUtilities team gets default spawn settings for its
 * claims, once, and can change them in its team settings (see {@link ClaimDefaults}).
 */
@Mod(
    modid = ValierClaims.MODID,
    version = Tags.VERSION,
    name = "Valier Claims",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:serverutilities")
public class ValierClaims {

    public static final String MODID = "valierclaims";
    public static final Logger LOG = LogManager.getLogger("ValierClaims");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ClaimsConfig.load(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        MinecraftForge.EVENT_BUS.register(new ClaimDefaults());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ClaimsCommand());
    }
}
