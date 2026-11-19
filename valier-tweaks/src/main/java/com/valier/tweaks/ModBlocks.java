package com.valier.tweaks;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * This mod's blocks (since 2.0.0). Registered on both sides whatever the settings say: Forge refuses a player whose
 * blocks don't match the server's.
 */
public final class ModBlocks {

    public static ValierTab tab;
    public static BlockLootSpawner lootSpawner;
    public static BlockXpCrafter xpCrafter;
    public static com.valier.tweaks.pipe.BlockPipe itemPipe, fluidPipe;

    private ModBlocks() {}

    static void register() {
        tab = new ValierTab();
        lootSpawner = new BlockLootSpawner();
        lootSpawner.setCreativeTab(tab);
        GameRegistry.registerBlock(lootSpawner, ItemBlockLootSpawner.class, "loot_spawner");
        // The server's tile; clients get a plain spawner tile that draws the mob (BlockLootSpawner#createNewTileEntity).
        GameRegistry.registerTileEntity(LootSpawnerTile.class, ValierTweaks.MODID + ".LootSpawner");
        GameRegistry.registerTileEntity(LootSpawnerClientTile.class, ValierTweaks.MODID + ".LootSpawnerClient");
        xpCrafter = new BlockXpCrafter();
        xpCrafter.setCreativeTab(tab);
        GameRegistry.registerBlock(xpCrafter, ItemBlockXpCrafter.class, "xp_crafter");
        GameRegistry.registerTileEntity(TileXpCrafter.class, ValierTweaks.MODID + ".XpCrafter");
        itemPipe = new com.valier.tweaks.pipe.BlockPipe(com.valier.tweaks.pipe.PipeKind.ITEM);
        fluidPipe = new com.valier.tweaks.pipe.BlockPipe(com.valier.tweaks.pipe.PipeKind.FLUID);
        itemPipe.setCreativeTab(tab);
        fluidPipe.setCreativeTab(tab);
        GameRegistry.registerBlock(itemPipe, com.valier.tweaks.pipe.ItemBlockPipe.class, "item_pipe");
        GameRegistry.registerBlock(fluidPipe, com.valier.tweaks.pipe.ItemBlockPipe.class, "fluid_pipe");
        GameRegistry.registerTileEntity(com.valier.tweaks.pipe.TilePipe.class, ValierTweaks.MODID + ".Pipe");
    }
}
