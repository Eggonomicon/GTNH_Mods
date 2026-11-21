package com.valier.tweaks.grid;

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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Grid blocks: Grid Plug, Grid Point and Grid Cell (metadata: the tier, LV to UV) and the Grid Controller
 * (one tier). Right-click opens the menu (network, priority, limit, redstone...); a wrench (sneaking) picks one up, and
 * broken any way it drops as an item keeping its settings and network (and a cell its energy).
 */
public class BlockGrid extends BlockContainer {

    public static final String ITEM_TAG = "ValierGrid";
    static final String[] NAMES = { "grid_plug", "grid_point", "grid_cell", "grid_controller" };

    public final int kind;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    /** The ring of light drawn in a block's network colour ({@code client.RenderGrid}). */
    @SideOnly(Side.CLIENT)
    public static IIcon light;
    /** Set by the client proxy (players' computers only). */
    public static int renderId = -1;

    public BlockGrid(int kind) {
        super(Material.iron);
        this.kind = kind;
        setHardness(4.0F);
        setResistance(20.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setBlockName(ValierTweaks.MODID + "." + NAMES[kind]);
    }

    public int tiers() {
        return kind == TileGrid.CONTROLLER ? 1 : GridTier.COUNT;
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        TileGrid t;
        switch (kind) {
            case TileGrid.PLUG:
                t = new TileGridPlug();
                break;
            case TileGrid.POINT:
                t = new TileGridPoint();
                break;
            case TileGrid.CELL:
                t = new TileGridCell();
                break;
            default:
                t = new TileGridController();
        }
        t.tier = kind == TileGrid.CONTROLLER ? 2 : GridTier.clamp(meta);
        return t;
    }

    @Override
    public int damageDropped(int meta) {
        return kind == TileGrid.CONTROLLER ? 0 : GridTier.clamp(meta);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < tiers(); t++) list.add(new ItemStack(item, 1, t));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        icons = new IIcon[tiers()];
        for (int t = 0; t < tiers(); t++) icons[t] = r.registerIcon(ValierTweaks.MODID + ":" + NAMES[kind] + (tiers() > 1 ? "_" + t : ""));
        blockIcon = icons[0];
        light = r.registerIcon(ValierTweaks.MODID + ":grid_light");
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[Math.max(0, Math.min(icons.length - 1, meta))];
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (w.isRemote || !(te instanceof TileGrid)) return;
        TileGrid t = (TileGrid) te;
        if (placer instanceof EntityPlayer) t.setOwner(((EntityPlayer) placer).getUniqueID());
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(ITEM_TAG) : null;
        if (tag != null) {
            t.readSettings(tag, false);
            if (t instanceof TileGridCell) ((TileGridCell) t).stored = Math.max(0, Math.min(((TileGridCell) t).capacity(), tag.getLong("Stored")));
            if (!t.net.isEmpty()) GridNets.link(t, t.owner != null ? t.ownerScope(t.owner) : t.scope, t.net);
        }
        t.markChunk();
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (p.isSneaking() && p.getHeldItem() != null) return false;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileGrid)) return true;
        if (LootSpawners.mayChange(p, w, x, y, z)) p.openGui(ValierTweaks.instance, ValierGui.GRID, w, x, y, z);
        return true;
    }

    @Override
    public void onNeighborBlockChange(World w, int x, int y, int z, Block b) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileGrid) ((TileGrid) te).neighbourChanged();
    }

    @Override
    public void onNeighborChange(IBlockAccess w, int x, int y, int z, int tx, int ty, int tz) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileGrid) ((TileGrid) te).nearDirty = true;
    }

    /** However it goes: it drops as an item keeping its settings, network and (a cell) its energy. */
    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        // restoringBlockSnapshots: Forge is undoing a placement another mod cancelled, and the player kept the item.
        if (!w.isRemote && !w.restoringBlockSnapshots && te instanceof TileGrid && !((TileGrid) te).pickedUp)
            LootSpawners.dropAt(w, x, y, z, toItem((TileGrid) te));
        super.breakBlock(w, x, y, z, block, meta);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<>();
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World w, int x, int y, int z) {
        return new ItemStack(this, 1, damageDropped(w.getBlockMetadata(x, y, z)));
    }

    /** The block as an item, with its settings, network and energy. */
    public ItemStack toItem(TileGrid t) {
        ItemStack s = new ItemStack(this, 1, kind == TileGrid.CONTROLLER ? 0 : t.tier);
        NBTTagCompound tag = new NBTTagCompound();
        t.writeSettings(tag);
        tag.removeTag("Owner");
        if (t instanceof TileGridCell && ((TileGridCell) t).stored > 0) tag.setLong("Stored", ((TileGridCell) t).stored);
        if (!tag.hasNoTags()) {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag(ITEM_TAG, tag);
            s.setTagCompound(root);
        }
        return s;
    }
}
