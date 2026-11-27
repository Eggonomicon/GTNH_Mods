package com.valier.tweaks.client;

import com.valier.tweaks.CommonProxy;
import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.ContainerPipeFace;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/** Players' computers (and single player): what only a client draws. */
public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        BlockPipe.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderPipe(BlockPipe.renderId));
        com.valier.tweaks.grid.BlockGrid.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderGrid(com.valier.tweaks.grid.BlockGrid.renderId)); // network colours
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(com.valier.tweaks.storage.TileBarrel.class,
            new RenderBarrel()); // barrel labels
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SatchelHud()); // sneak + wheel, the held satchel's slot
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientSpectator.Events()); // spectator: hello, no overlay
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new ClientSpectator.Ticks()); // spectator: through blocks
        if (DevClient.ENABLED) DevClient.register();
        else if (DevClient.JOIN) DevClient.registerJoin();
        if (cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) NeiPipes.register(); // drag items into pipe filters
    }

    @Override
    public Object pipeGui(ContainerPipeFace container) {
        return new GuiPipeFace(container);
    }

    @Override
    public Object lootGui(com.valier.tweaks.LootMenu container) {
        return new GuiLootSpawner(container);
    }

    @Override
    public Object satchelGui(com.valier.tweaks.storage.SatchelMenu container) {
        return new GuiSatchel(container);
    }

    @Override
    public Object gridGui(com.valier.tweaks.grid.GridMenu container) {
        return new GuiGrid(container);
    }

    @Override
    public Object barrelGui(com.valier.tweaks.storage.BarrelMenu container) {
        return new GuiBarrel(container);
    }
}
