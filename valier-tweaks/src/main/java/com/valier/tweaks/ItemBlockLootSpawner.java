package com.valier.tweaks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * The Valier Loot Spawner item: what a wrench gives ({@link LootSpawners#toItem}), holding the spawner (mob, settings,
 * what was paid into it). Placing it puts that loot spawner back. One without that (the creative tab's) places a pig
 * loot spawner.
 */
public class ItemBlockLootSpawner extends ItemBlock {

    public ItemBlockLootSpawner(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ, int meta) {
        if (!super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta)) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof LootSpawnerTile)
                LootSpawners.placeInto(world, (LootSpawnerTile) te, LootSpawners.savedSpawner(stack), player);
        }
        return true;
    }
}
