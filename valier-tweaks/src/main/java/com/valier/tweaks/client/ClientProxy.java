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
        if (DevClient.ENABLED) DevClient.register();
        if (cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) NeiPipes.register(); // drag items into pipe filters
    }

    @Override
    public Object pipeGui(ContainerPipeFace container) {
        return new GuiPipeFace(container);
    }
}
