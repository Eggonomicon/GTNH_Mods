package com.valier.tweaks.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Barrel Controller block (2.4.0, {@link TileBarrelController}). Right-click with an item: it goes to its
 * barrel (a quick second right-click: everything you carry that the barrels hold). Right-click with an empty hand: its
 * menu (only for players who may change it). It holds nothing itself, so it breaks like any block.
 */
public class BlockBarrelController extends BlockContainer {

    private static final Map<UUID, long[]> lastClick = new HashMap<>();

    @SideOnly(Side.CLIENT)
    private IIcon side, top, front;

    public BlockBarrelController() {
        super(Material.rock);
        setHardness(3.0F);
        setResistance(15.0F);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 0);
        setBlockName(ValierTweaks.MODID + ".barrel_controller");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileBarrelController();
    }

    // ---- looks ------------------------------------------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        side = r.registerIcon(ValierTweaks.MODID + ":barrel_controller_side");
        top = r.registerIcon(ValierTweaks.MODID + ":barrel_controller_top");
        front = r.registerIcon(ValierTweaks.MODID + ":barrel_controller_front");
        blockIcon = side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return icon(s, 3);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        TileEntity te = w.getTileEntity(x, y, z);
        return icon(s, te instanceof TileBarrelController ? ((TileBarrelController) te).facing : 3);
    }

    @SideOnly(Side.CLIENT)
    private IIcon icon(int s, int facing) {
        if (s == 0 || s == 1) return top;
        return s == facing ? front : side;
    }

    /** The front faces the player who placed it. */
    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileBarrelController)) return;
        int dir = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ((TileBarrelController) te).facing = new int[] { 2, 5, 3, 4 }[dir];
        w.markBlockForUpdate(x, y, z);
    }

    // ---- clicks -----------------------------------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileBarrelController) rightClick(p, (TileBarrelController) te);
        return true;
    }

    static void rightClick(EntityPlayer p, TileBarrelController t) {
        ItemStack held = p.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSatchel) return; // a Satchel moves its items its own way
        boolean twice = twice(p, t);
        if (held == null && !twice) {
            if (LootSpawners.mayChange(p, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
                p.openGui(ValierTweaks.instance, ValierGui.BARREL_CONTROLLER, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord);
            } else BarrelClicks.tell(p, EnumChatFormatting.RED, "You can't change this controller: it's protected.");
            return;
        }
        if (held != null) {
            long n = t.insert(held, held.stackSize, false, false);
            held.stackSize -= (int) n;
            if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            else if (n <= 0 && !twice) BarrelClicks.tell(p, EnumChatFormatting.GRAY, "No barrel here takes that"
                + (t.fillEmpty ? " (and no empty barrel is left)." : ". (\"Fill empty barrels\" is off in its menu.)"));
        }
        if (twice) insertAll(p, t);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** Everything the player carries that a barrel already holds goes in (never into empty barrels). */
    static int insertAll(EntityPlayer p, TileBarrelController t) {
        int moved = 0;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() instanceof ItemSatchel) continue;
            long n = t.insert(s, s.stackSize, false, false, false);
            if (n <= 0) continue;
            s.stackSize -= (int) n;
            moved += (int) n;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
        return moved;
    }

    private static boolean twice(EntityPlayer p, TileBarrelController t) {
        long now = t.getWorldObj()
            .getTotalWorldTime();
        long at = ((long) t.xCoord & 0x3FFFFFF) << 38 | ((long) t.zCoord & 0x3FFFFFF) << 12 | (t.yCoord & 0xFFF);
        long[] last = lastClick.get(p.getUniqueID());
        lastClick.put(p.getUniqueID(), new long[] { at, now });
        return last != null && last[0] == at && now - last[1] <= 10;
    }
}
