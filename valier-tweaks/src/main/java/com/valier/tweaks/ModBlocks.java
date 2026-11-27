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
    public static com.valier.tweaks.pipe.ItemPipeConfigurator pipeConfigurator;
    public static com.valier.tweaks.storage.BlockBarrel barrel;
    public static com.valier.tweaks.storage.ItemBarrelUpgrade barrelUpgrade;
    public static com.valier.tweaks.storage.BlockBarrelController barrelController;
    public static com.valier.tweaks.storage.ItemSatchel satchel, flask;
    public static com.valier.tweaks.storage.ItemSatchelUpgrade satchelUpgrade;
    public static com.valier.tweaks.storage.BlockSatchelDock satchelDock;
    public static com.valier.tweaks.grid.BlockGrid gridPlug, gridPoint, gridCell, gridController;

    private ModBlocks() {}

    private static com.valier.tweaks.grid.BlockGrid grid(int kind, String name) {
        com.valier.tweaks.grid.BlockGrid b = new com.valier.tweaks.grid.BlockGrid(kind);
        b.setCreativeTab(tab);
        GameRegistry.registerBlock(b, com.valier.tweaks.grid.ItemBlockGrid.class, name);
        return b;
    }

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
        pipeConfigurator = new com.valier.tweaks.pipe.ItemPipeConfigurator();
        pipeConfigurator.setCreativeTab(tab);
        GameRegistry.registerItem(pipeConfigurator, "pipe_configurator");
        barrel = new com.valier.tweaks.storage.BlockBarrel(); // 2.3.0
        barrel.setCreativeTab(tab);
        GameRegistry.registerBlock(barrel, com.valier.tweaks.storage.ItemBlockBarrel.class, "barrel");
        GameRegistry.registerTileEntity(com.valier.tweaks.storage.TileBarrel.class, ValierTweaks.MODID + ".Barrel");
        barrelController = new com.valier.tweaks.storage.BlockBarrelController(); // 2.4.0
        barrelController.setCreativeTab(tab);
        GameRegistry.registerBlock(barrelController, "barrel_controller");
        GameRegistry.registerTileEntity(com.valier.tweaks.storage.TileBarrelController.class, ValierTweaks.MODID + ".BarrelController");
        barrelUpgrade = new com.valier.tweaks.storage.ItemBarrelUpgrade();
        barrelUpgrade.setCreativeTab(tab);
        GameRegistry.registerItem(barrelUpgrade, "barrel_upgrade");
        satchel = new com.valier.tweaks.storage.ItemSatchel(false);
        satchel.setCreativeTab(tab);
        GameRegistry.registerItem(satchel, "satchel");
        flask = new com.valier.tweaks.storage.ItemSatchel(true);
        flask.setCreativeTab(tab);
        GameRegistry.registerItem(flask, "flask");
        satchelUpgrade = new com.valier.tweaks.storage.ItemSatchelUpgrade();
        satchelUpgrade.setCreativeTab(tab);
        GameRegistry.registerItem(satchelUpgrade, "satchel_upgrade");
        satchelDock = new com.valier.tweaks.storage.BlockSatchelDock();
        satchelDock.setCreativeTab(tab);
        GameRegistry.registerBlock(satchelDock, "satchel_dock");
        GameRegistry.registerTileEntity(com.valier.tweaks.storage.TileSatchelDock.class, ValierTweaks.MODID + ".SatchelDock");
        gridPlug = grid(com.valier.tweaks.grid.TileGrid.PLUG, "grid_plug");
        gridPoint = grid(com.valier.tweaks.grid.TileGrid.POINT, "grid_point");
        gridCell = grid(com.valier.tweaks.grid.TileGrid.CELL, "grid_cell");
        gridController = grid(com.valier.tweaks.grid.TileGrid.CONTROLLER, "grid_controller");
        GameRegistry.registerTileEntity(com.valier.tweaks.grid.TileGridPlug.class, ValierTweaks.MODID + ".GridPlug");
        GameRegistry.registerTileEntity(com.valier.tweaks.grid.TileGridPoint.class, ValierTweaks.MODID + ".GridPoint");
        GameRegistry.registerTileEntity(com.valier.tweaks.grid.TileGridCell.class, ValierTweaks.MODID + ".GridCell");
        GameRegistry.registerTileEntity(com.valier.tweaks.grid.TileGridController.class, ValierTweaks.MODID + ".GridController");
    }
}
