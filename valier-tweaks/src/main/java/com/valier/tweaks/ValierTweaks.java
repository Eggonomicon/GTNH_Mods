package com.valier.tweaks;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Small server-side gameplay fixes for Valier: a Tinkers' Construct lumber axe fells the whole tree even when it breaks
 * partway ({@link TreeFinisher}), players can make their Personal Space dimension peaceful ({@link PeacefulDims},
 * {@code /pdim}), GregTech's solid fuel boilers make no ash ({@code mixin} package, loaded by
 * {@link LateMixinLoader}), Railcraft boilers don't explode ({@link RailcraftBoilers}), a spawner can become a loot spawner that makes its mobs' loot without the mobs
 * ({@link LootSpawners}), a GregTech tank can become an XP crafter that makes what the XP bucket recipes make from
 * liquid XP ({@link XpCrafters}), and chain armour macerates into whole steel dust ({@link ChainArmour}).
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
        // Loot spawners. The tile is always registered: it saves as a vanilla spawner but needs an id while saving.
        GameRegistry.registerTileEntity(LootSpawnerTile.class, MODID + ".LootSpawner");
        LootSpawners loot = new LootSpawners();
        MinecraftForge.EVENT_BUS.register(loot); // clicks, chunk loads
        FMLCommonHandler.instance()
            .bus()
            .register(loot); // the scan, on server ticks
        MinecraftForge.EVENT_BUS.register(new MobLoot.Guard()); // what a loot roll tries to add to the world
        if (Loader.isModLoaded("Railcraft")) {
            RailcraftBoilers boilers = RailcraftBoilers.make();
            if (boilers != null) MinecraftForge.EVENT_BUS.register(boilers); // explosions
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Loader.isModLoaded("gregtech")) XpCrafters.register(); // GregTech tanks; its recipe and crafting
    }

    /** Every mod's recipes are in by now. */
    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        if (!Loader.isModLoaded("gregtech")) return;
        ChainArmour.apply();
        XpCrafters.forget();
    }

    /** The liquid XP recipes are looked up now rather than at an XP crafter's first second. */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (Loader.isModLoaded("gregtech")) XpCrafters.recipes();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PdimCommand());
        if (SelfTest.ENABLED) event.registerServerCommand(new TweaksCommand());
    }
}
