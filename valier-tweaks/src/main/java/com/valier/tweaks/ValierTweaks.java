package com.valier.tweaks;

import java.io.File;
import java.util.Map;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * Small server-side gameplay fixes for Valier: a Tinkers' Construct lumber axe fells the whole tree even when it breaks
 * partway ({@link TreeFinisher}), players can make their Personal Space dimension peaceful ({@link PeacefulDims},
 * {@code /pdim}), GregTech's solid fuel boilers make no ash ({@code mixin} package, loaded by
 * {@link LateMixinLoader}), Railcraft boilers don't explode ({@link RailcraftBoilers}), a spawner can become a loot spawner that makes its mobs' loot without the mobs
 * ({@link LootSpawners}), a GregTech tank can become an XP crafter that makes what the XP bucket recipes make from
 * liquid XP ({@link XpCrafters}), and chain armour macerates into whole steel dust ({@link ChainArmour}).
 * <p>
 * Since 2.0.0 it is needed on players' computers too (it has blocks; it took in the old Valier Client mod). The join
 * check ({@link #checkRemote}) asks a joining player for the same major.minor version; a player with it can still join
 * servers without it.
 */
@Mod(
    modid = ValierTweaks.MODID,
    version = Tags.VERSION,
    name = "Valier Tweaks",
    acceptedMinecraftVersions = "[1.7.10]")
public class ValierTweaks {

    public static final String MODID = "valiertweaks";

    @Mod.Instance(MODID)
    public static ValierTweaks instance;
    /** This mod's packets (pipe menus). */
    public static final cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper NET = cpw.mods.fml.common.network.NetworkRegistry.INSTANCE
        .newSimpleChannel(MODID);
    public static final Logger LOG = LogManager.getLogger("ValierTweaks");

    @SidedProxy(clientSide = "com.valier.tweaks.client.ClientProxy", serverSide = "com.valier.tweaks.CommonProxy")
    public static CommonProxy proxy;

    /**
     * FML's join check, both ways. A server asks a joining player for Valier Tweaks with the same major.minor version
     * (2.0.x fixes that only change the server don't need a new download); a player accepts a server without it, so the
     * jar doesn't stop anyone joining other servers.
     */
    @NetworkCheckHandler
    public boolean checkRemote(Map<String, String> mods, Side remote) {
        return compatible(mods.get(MODID), remote, Tags.VERSION);
    }

    /** Whether the other side's version ({@code theirs}, null if it hasn't the mod) can play with {@code ours}. */
    static boolean compatible(String theirs, Side remote, String ours) {
        if (theirs == null) return remote == Side.SERVER;
        String a = majorMinor(theirs), b = majorMinor(ours);
        return a != null && a.equals(b);
    }

    /** "2.0" for "2.0.3", "v2.0.3-dev" or "2.0"; null if it doesn't start with two numbers. */
    static String majorMinor(String version) {
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        String[] parts = v.split("[.\\-+ ]");
        if (parts.length < 2 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+")) return null;
        return Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]);
    }

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
        ModBlocks.register(); // both sides, whatever the settings: blocks must match the server's
        NET.registerMessage(com.valier.tweaks.pipe.PipeFaceMessage.Handler.class, com.valier.tweaks.pipe.PipeFaceMessage.class, 0,
            Side.CLIENT);
        NET.registerMessage(com.valier.tweaks.pipe.PipeActionMessage.Handler.class, com.valier.tweaks.pipe.PipeActionMessage.class, 1,
            Side.SERVER);
        com.valier.tweaks.pipe.PipeNetworks pipes = new com.valier.tweaks.pipe.PipeNetworks.Events();
        MinecraftForge.EVENT_BUS.register(pipes); // chunk loads, world unloads
        MinecraftForge.EVENT_BUS.register(new com.valier.tweaks.pipe.PipeClicks.Events()); // sneak + wrench on pipes
        FMLCommonHandler.instance()
            .bus()
            .register(pipes); // the pipe timer, on world ticks
        LootSpawners loot = new LootSpawners();
        MinecraftForge.EVENT_BUS.register(loot); // clicks, chunk loads
        FMLCommonHandler.instance()
            .bus()
            .register(loot); // the scan, on server ticks
        MinecraftForge.EVENT_BUS.register(new MobLoot.Guard()); // what a loot roll tries to add to the world
        proxy.preInit(event);
        if (Loader.isModLoaded("Railcraft")) {
            RailcraftBoilers boilers = RailcraftBoilers.make();
            if (boilers != null) MinecraftForge.EVENT_BUS.register(boilers); // explosions
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Loader.isModLoaded("gregtech")) XpCrafters.register(); // GregTech tanks; its recipe and crafting
        com.valier.tweaks.pipe.PipeRecipes.register();
        if (Loader.isModLoaded("Waila")) { // what pipes are doing, for WAILA (server data; players' lines)
            cpw.mods.fml.common.event.FMLInterModComms.sendMessage("Waila", "register",
                "com.valier.tweaks.pipe.PipeWaila.callbackRegister");
            cpw.mods.fml.common.event.FMLInterModComms.sendMessage("Waila", "register",
                "com.valier.tweaks.client.PipeHud.callbackRegister");
        }
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, new com.valier.tweaks.pipe.PipeGui());
        proxy.init(event);
    }

    /** After WAILA's and NEI's own postInit. */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
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
        event.registerServerCommand(new TweaksCommand()); // pipes (and, on a test server, selftest)
    }
}
