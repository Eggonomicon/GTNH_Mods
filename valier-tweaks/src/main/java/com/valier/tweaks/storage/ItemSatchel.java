package com.valier.tweaks.storage;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.IFluidHandler;

import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Satchel (items) and Valier Flask (fluids), metadata the tier ({@link SatchelTier}); what they hold is in
 * the item ({@link SatchelInventory}).
 * <ul>
 * <li>Satchel: picks up what it already holds (and, if set, new items) before they reach the inventory; right-click a
 * block to place or use the selected item; right-click the air for its menu; sneak + mouse wheel picks the slot.</li>
 * <li>Flask: right-click a fluid source to pick it up, a block to place the selected fluid there; right-click a tank or
 * machine to fill it from the selected tank, sneaking to drain it into the Flask; right-click the air for its menu.</li>
 * </ul>
 */
public class ItemSatchel extends Item {

    public final boolean fluid;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemSatchel(boolean fluid) {
        this.fluid = fluid;
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName(ValierTweaks.MODID + (fluid ? ".flask" : ".satchel"));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + SatchelTier.clamp(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < SatchelTier.COUNT; t++) list.add(new ItemStack(item, 1, t));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[SatchelTier.COUNT];
        for (int t = 0; t < SatchelTier.COUNT; t++) icons[t] = r.registerIcon(ValierTweaks.MODID + ":" + (fluid ? "flask_" : "satchel_") + t);
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[SatchelTier.clamp(meta)];
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return stack.getItemDamage() == SatchelTier.CREATIVE;
    }

    // ---- in hand -----------------------------------------------------------------------------------------------------

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy,
        float hz) {
        SatchelInventory inv = SatchelInventory.of(stack);
        if (inv == null) return false;
        if (fluid) return useOnTank(inv, player, world, x, y, z, side);
        int i = inv.selected;
        if (inv.available(i, true) <= 0) return false;
        ItemStack one = inv.items[i].copy();
        one.stackSize = 1;
        ItemStack before = one.copy();
        boolean used = one.tryPlaceItemIntoWorld(player, world, x, y, z, side, hx, hy, hz);
        if (!used || world.isRemote) return used;
        if (one.stackSize <= 0) inv.extract(i, 1, true, false);
        else if (!ItemStack.areItemStacksEqual(one, before) && inv.extract(i, 1, true, false) != null) {
            // Worn or changed by its use (a tool, flint and steel): that one comes out of the Satchel, into the inventory.
            if (!player.inventory.addItemStackToInventory(one)) player.dropPlayerItemWithRandomChoice(one, false);
        }
        return used;
    }

    /** A Flask on a tank or machine: fill it from the selected tank, or (sneaking) drain it into the Flask. */
    private boolean useOnTank(SatchelInventory inv, EntityPlayer player, World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IFluidHandler)) return false;
        if (world.isRemote) return true;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, x, y, z)) return true; // a claim keeps it
        IFluidHandler h = (IFluidHandler) te;
        ForgeDirection from = ForgeDirection.getOrientation(side);
        if (player.isSneaking()) {
            for (int pass = 0; pass < 4; pass++) {
                FluidStack offer = h.drain(from, Integer.MAX_VALUE / 2, false);
                if (offer == null || offer.amount <= 0) break;
                int room = inv.fill(offer, false);
                if (room <= 0) break;
                FluidStack got = h.drain(from, room, true);
                if (got == null || got.amount <= 0) break;
                inv.fill(got, true);
            }
        } else {
            FluidStack offer = inv.drain(inv.selected, Integer.MAX_VALUE, true, false);
            if (offer != null) {
                int n = h.fill(from, offer, true);
                if (n > 0) inv.drain(inv.selected, n, true, true);
            }
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (fluid) {
            MovingObjectPosition mop = getMovingObjectPositionFromPlayer(world, player, true);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (!world.isRemote) fluidClick(stack, world, player, mop);
                return stack;
            }
        }
        if (!world.isRemote) player.openGui(ValierTweaks.instance, ValierGui.SATCHEL, world, player.inventory.currentItem, 0, 0);
        return stack;
    }

    /** Picks up the fluid source looked at, or places the selected fluid against the block looked at. */
    void fluidClick(ItemStack stack, World world, EntityPlayer player, MovingObjectPosition mop) {
        SatchelInventory inv = SatchelInventory.of(stack);
        int x = mop.blockX, y = mop.blockY, z = mop.blockZ;
        if (!world.canMineBlock(player, x, y, z)) return;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, x, y, z)) return; // claims
        Block b = world.getBlock(x, y, z);
        Fluid f = FluidRegistry.lookupFluidForBlock(b);
        boolean source = f != null && (b instanceof IFluidBlock ? ((IFluidBlock) b).canDrain(world, x, y, z)
            : world.getBlockMetadata(x, y, z) == 0);
        if (source && !player.isSneaking()) {
            FluidStack got = new FluidStack(f, 1000);
            if (inv.fill(got, false) >= 1000) {
                inv.fill(got, true);
                world.setBlockToAir(x, y, z);
            }
            return;
        }
        ForgeDirection d = ForgeDirection.getOrientation(mop.sideHit);
        int px = x + d.offsetX, py = y + d.offsetY, pz = z + d.offsetZ;
        if (!player.canPlayerEdit(px, py, pz, mop.sideHit, stack)) return;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, px, py, pz)) return; // claims
        FluidStack sel = inv.drain(inv.selected, 1000, true, false);
        if (sel == null || sel.amount < 1000 || sel.getFluid() == null || sel.getFluid()
            .getBlock() == null) return;
        Block target = world.getBlock(px, py, pz);
        if (!world.isAirBlock(px, py, pz) && !target.isReplaceable(world, px, py, pz)) return;
        if (world.provider.isHellWorld && sel.getFluid() == FluidRegistry.WATER) {
            inv.drain(inv.selected, 1000, true, true); // evaporates, as a bucket of water does there
            return;
        }
        world.setBlock(px, py, pz, sel.getFluid()
            .getBlock(), 0, 3);
        inv.drain(inv.selected, 1000, true, true);
    }

    // ---- tooltip -----------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = SatchelTier.clamp(stack.getItemDamage());
        boolean creative = t == SatchelTier.CREATIVE;
        String each = creative ? "endless" : String.format("%,d", SatchelTier.capacity(t, fluid)) + (fluid ? " mB" : "");
        list.add(EnumChatFormatting.GRAY + "" + SatchelTier.slots(t, fluid) + (fluid ? " tanks of " : " slots of ") + each + " each");
        SatchelInventory inv = SatchelInventory.of(stack);
        if (inv != null) {
            int used = 0;
            for (int i = 0; i < inv.size; i++) if (!inv.isEmpty(i)) used++;
            if (used > 0) list.add(EnumChatFormatting.YELLOW + "" + used + " in use; selected: " + selectedText(inv));
            if (!fluid) list.add(EnumChatFormatting.DARK_GRAY + "Pickup " + (inv.pickup ? "on" : "off") + (inv.pickNew ? ", new items too" : ""));
        }
        if (fluid) {
            list.add(EnumChatFormatting.DARK_GRAY + "Right-click: fluid in / place; on a tank: fill it");
            list.add(EnumChatFormatting.DARK_GRAY + "(sneaking: drain it). In the air: its menu.");
        } else {
            list.add(EnumChatFormatting.DARK_GRAY + "Right-click a block: place the selected item.");
            list.add(EnumChatFormatting.DARK_GRAY + "In the air: its menu. Sneak + wheel: pick a slot.");
        }
    }

    static String selectedText(SatchelInventory inv) {
        int i = inv.selected;
        if (inv.isEmpty(i)) return "slot " + (i + 1) + " (empty)";
        if (inv.fluid) return inv.fluids[i].getLocalizedName() + ", " + (inv.creative ? "endless" : String.format("%,d mB", inv.fluids[i].amount));
        return inv.items[i].getDisplayName() + " × " + (inv.creative ? "endless" : String.format("%,d", inv.counts[i]));
    }
}
