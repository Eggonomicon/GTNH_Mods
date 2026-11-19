package com.valier.tweaks;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier XP Crafter block (since 2.0.0; before, a crafter was a GregTech tank, which turns into this block where it
 * stands, keeping its liquid XP, slots and record). Crafted, clicked and run as before ({@link XpCrafters},
 * {@link XpMenu}); its tile is {@link TileXpCrafter}. Broken, it drops as the crafter item, knowing what it made, the
 * emerald block in it, the XP it was holding and the liquid XP in its tank; its store and slots drop too.
 */
public class BlockXpCrafter extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon top, bottom;

    public BlockXpCrafter() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
        setBlockName(ValierTweaks.MODID + ".xp_crafter");
        setBlockTextureName(ValierTweaks.MODID + ":xp_crafter_side");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileXpCrafter();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(ValierTweaks.MODID + ":xp_crafter_side");
        top = reg.registerIcon(ValierTweaks.MODID + ":xp_crafter_top");
        bottom = reg.registerIcon(ValierTweaks.MODID + ":xp_crafter_bottom");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 ? top : side == 0 ? bottom : blockIcon;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null; // breakBlock drops the item, with what it knows
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    /** However it goes (a player, an explosion, a Matter Manipulator): the crafter item, its store and slots drop. */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileXpCrafter && !((TileXpCrafter) te).moving)
            XpCrafters.broken(world, (TileXpCrafter) te);
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return XpCrafters.newItem(null);
    }
}
