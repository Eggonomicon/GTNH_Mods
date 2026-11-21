package com.valier.tweaks.storage;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Satchel Dock (metadata: 0 empty, 1 holding a Satchel, 2 a Flask, for its top). Right-click with a Satchel
 * or Flask to dock it, with an empty hand to take it back; broken, it drops what it holds. While docked, pipes, hoppers,
 * GregTech, AE2 and the like reach what it holds ({@link TileSatchelDock}).
 */
public class BlockSatchelDock extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon side, bottom;
    @SideOnly(Side.CLIENT)
    private IIcon[] top;

    public BlockSatchelDock() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(15.0F);
        setStepSound(soundTypeMetal);
        setBlockName(ValierTweaks.MODID + ".satchel_dock");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSatchelDock();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        side = r.registerIcon(ValierTweaks.MODID + ":satchel_dock_side");
        bottom = r.registerIcon(ValierTweaks.MODID + ":satchel_dock_bottom");
        top = new IIcon[] { r.registerIcon(ValierTweaks.MODID + ":satchel_dock_top"),
            r.registerIcon(ValierTweaks.MODID + ":satchel_dock_top_satchel"), r.registerIcon(ValierTweaks.MODID + ":satchel_dock_top_flask") };
        blockIcon = side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 1 ? top[Math.max(0, Math.min(2, meta))] : s == 0 ? bottom : side;
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileSatchelDock)) return true;
        TileSatchelDock d = (TileSatchelDock) te;
        ItemStack held = p.getHeldItem();
        if (d.docked == null && held != null && held.getItem() instanceof ItemSatchel) {
            d.dock(held);
            p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            w.setBlockMetadataWithNotify(x, y, z, ((ItemSatchel) held.getItem()).fluid ? 2 : 1, 3);
        } else if (d.docked != null && held == null) {
            ItemStack out = d.docked;
            d.dock(null);
            p.inventory.setInventorySlotContents(p.inventory.currentItem, out);
            w.setBlockMetadataWithNotify(x, y, z, 0, 3);
        }
        p.inventoryContainer.detectAndSendChanges();
        return true;
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!w.isRemote && te instanceof TileSatchelDock && ((TileSatchelDock) te).docked != null) {
            LootSpawners.dropAt(w, x, y, z, ((TileSatchelDock) te).docked);
            ((TileSatchelDock) te).docked = null;
        }
        super.breakBlock(w, x, y, z, block, meta);
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    /** 15 with something docked, 0 without (a quick check for redstone). */
    @Override
    public int getComparatorInputOverride(World w, int x, int y, int z, int s) {
        return w.getBlockMetadata(x, y, z) > 0 ? 15 : 0;
    }
}
