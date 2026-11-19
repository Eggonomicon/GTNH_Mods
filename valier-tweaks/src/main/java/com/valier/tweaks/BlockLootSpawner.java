package com.valier.tweaks;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The Valier Loot Spawner block (since 2.0.0; before, a loot spawner was a vanilla spawner with a different tile). A
 * spawner becomes one with the conversion click ({@link LootSpawners}); the old kind turns into this block where it
 * stands. Players can't break it, explosions and the Wither can't either: a wrench picks it up as an item, and creative
 * players can break it (what it holds and what was paid into it drop).
 */
public class BlockLootSpawner extends BlockContainer {

    public BlockLootSpawner() {
        super(Material.rock);
        setHardness(5.0F);
        setResistance(6000000.0F);
        setStepSound(soundTypeMetal);
        setBlockName(ValierTweaks.MODID + ".loot_spawner");
        setBlockTextureName(ValierTweaks.MODID + ":loot_spawner");
    }

    /** The server's working tile, or a plain spawner tile on a player's computer (vanilla draws its mob). */
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return world != null && world.isRemote ? new LootSpawnerClientTile() : new LootSpawnerTile();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /** Only creative players can break it; a wrench picks it up. */
    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        return player != null && player.capabilities.isCreativeMode ? super.getPlayerRelativeBlockHardness(player, world, x, y, z)
            : 0.0F;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null; // the item only comes from a wrench (it holds the spawner)
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    /** Whatever removes it (creative players, a Matter Manipulator, another mod): what it holds and was paid drops. */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof LootSpawnerTile) LootSpawners.spillAll(world, (LootSpawnerTile) te);
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** Pick-block (and WAILA's name): "Valier Loot Spawner (Zombie, Looting II)". */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof LootSpawnerTile) return LootSpawners.toItem((LootSpawnerTile) te);
        ItemStack stack = new ItemStack(this);
        if (te instanceof LootSpawnerClientTile) {
            LootSpawnerClientTile c = (LootSpawnerClientTile) te;
            stack.setStackDisplayName(LootSpawners.displayName(c.func_145881_a()
                .getEntityNameToSpawn(), c.looting, c.speed));
        }
        return stack;
    }
}
