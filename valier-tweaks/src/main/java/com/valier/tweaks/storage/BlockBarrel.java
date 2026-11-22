package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Barrel block (metadata: its tier, {@link BarrelTier}). JABBA's controls: right-click puts the held stack
 * in (a quick second right-click puts in every matching item you carry), left-click takes a stack (sneaking: one), sneak
 * + right-click with an empty hand opens its menu ({@link BarrelMenu}), a wrench (sneaking) picks it up with what it
 * holds, a Barrel Upgrade makes it the next tier. Broken any way, it drops as a barrel item holding what it held.
 */
public class BlockBarrel extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon[] side, top, front;

    public BlockBarrel() {
        super(Material.wood);
        setHardness(2.5F);
        setResistance(15.0F);
        setStepSound(soundTypeWood);
        setHarvestLevel("axe", 0);
        setBlockName(ValierTweaks.MODID + ".barrel");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        TileBarrel t = new TileBarrel();
        t.tier = BarrelTier.clamp(meta);
        return t;
    }

    @Override
    public int damageDropped(int meta) {
        return BarrelTier.clamp(meta);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < BarrelTier.METAS; t++) list.add(new ItemStack(item, 1, t));
    }

    // ---- looks ------------------------------------------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        side = new IIcon[BarrelTier.METAS];
        top = new IIcon[BarrelTier.METAS];
        front = new IIcon[BarrelTier.METAS];
        for (int t = 0; t < BarrelTier.METAS; t++) {
            side[t] = r.registerIcon(ValierTweaks.MODID + ":barrel_side_" + t);
            top[t] = r.registerIcon(ValierTweaks.MODID + ":barrel_top_" + t);
            front[t] = r.registerIcon(ValierTweaks.MODID + ":barrel_front_" + t);
        }
        blockIcon = side[0];
    }

    /** As an item: its label faces south (towards the player in an inventory). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return icon(s, BarrelTier.clamp(meta), 3);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        TileEntity te = w.getTileEntity(x, y, z);
        int facing = te instanceof TileBarrel ? ((TileBarrel) te).facing : 3;
        return icon(s, BarrelTier.clamp(w.getBlockMetadata(x, y, z)), facing);
    }

    @SideOnly(Side.CLIENT)
    private IIcon icon(int s, int t, int facing) {
        if (s == 0 || s == 1) return top[t];
        return s == facing ? front[t] : side[t];
    }

    /** The label faces the player who placed it. */
    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileBarrel)) return;
        int dir = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ((TileBarrel) te).facing = new int[] { 2, 5, 3, 4 }[dir];
    }

    // ---- clicks -----------------------------------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer player, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileBarrel) BarrelClicks.rightClick(player, (TileBarrel) te);
        return true;
    }

    @Override
    public void onBlockClicked(World w, int x, int y, int z, EntityPlayer player) {
        if (w.isRemote) return;
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileBarrel) BarrelClicks.take(player, (TileBarrel) te, player.isSneaking());
    }

    /**
     * Creative players don't break a barrel holding something with a left-click (they take a stack, as in survival);
     * sneaking, they do.
     */
    @Override
    public boolean removedByPlayer(World w, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (player != null && player.capabilities.isCreativeMode && !player.isSneaking()) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof TileBarrel) {
                TileBarrel t = (TileBarrel) te;
                boolean full = w.isRemote ? t.shownItem != null && t.shownCount > 0 : !t.store()
                    .isEmpty();
                if (full) {
                    if (!w.isRemote) BarrelClicks.take(player, t, false);
                    return false;
                }
            }
        }
        return super.removedByPlayer(w, player, x, y, z, willHarvest);
    }

    /** However it goes (broken, blown up, removed by another mod): it drops as an item holding what it held. */
    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        // restoringBlockSnapshots: Forge is undoing a placement another mod cancelled, and the player kept the item.
        if (!w.isRemote && !w.restoringBlockSnapshots && te instanceof TileBarrel && !((TileBarrel) te).pickedUp) {
            ItemStack item = BarrelClicks.toItem((TileBarrel) te);
            LootSpawners.dropAt(w, x, y, z, item);
        }
        super.breakBlock(w, x, y, z, block, meta);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<>(); // breakBlock dropped it, with what it held
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World w, int x, int y, int z) {
        return new ItemStack(this, 1, BarrelTier.clamp(w.getBlockMetadata(x, y, z)));
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World w, int x, int y, int z, int s) {
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileBarrel && !w.isRemote ? ((TileBarrel) te).store()
            .comparator() : 0;
    }
}
