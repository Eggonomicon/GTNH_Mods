package com.valier.tweaks.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The Satchel Dock: holds one Satchel or Flask and lets automation reach what it holds. It never ticks.
 * <ul>
 * <li>A Satchel: an inventory with one slot per Satchel slot (each shows up to a stack of what automation may take,
 * keep amounts respected) plus one more, always empty, that takes anything the Satchel takes (hoppers and pipes put in
 * there).</li>
 * <li>A Flask: a tank handler over its tanks (fills go to the tank of that fluid, else an empty one; drains take from
 * the tank asked for, or the selected one).</li>
 * </ul>
 */
public class TileSatchelDock extends TileEntity implements ISidedInventory, IFluidHandler {

    ItemStack docked;
    private ItemStack[] views = new ItemStack[0];
    private int[] given = new int[0];
    private int[] slots = new int[0];

    @Override
    public boolean canUpdate() {
        return false;
    }

    public ItemStack docked() {
        return docked;
    }

    SatchelInventory inv() {
        return SatchelInventory.of(docked);
    }

    private SatchelInventory items() {
        SatchelInventory i = inv();
        return i != null && !i.fluid ? i : null;
    }

    private SatchelInventory tanks() {
        SatchelInventory i = inv();
        return i != null && i.fluid ? i : null;
    }

    void dock(ItemStack s) {
        docked = s;
        views = new ItemStack[0];
        given = new int[0];
        changed();
    }

    private void changed() {
        if (worldObj != null && !worldObj.isRemote) worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    // ---- items ---------------------------------------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        SatchelInventory i = items();
        return i == null ? 0 : i.size + 1;
    }

    private void sizeViews(int n) {
        if (views.length == n) return;
        views = new ItemStack[n];
        given = new int[n];
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SatchelInventory i = items();
        if (i == null || slot < 0 || slot >= i.size) return null;
        sizeViews(i.size);
        reconcile(i, slot);
        int n = Math.min(i.available(slot, true), i.items[slot] == null ? 0 : i.items[slot].getMaxStackSize());
        if (n <= 0) {
            views[slot] = null;
            given[slot] = 0;
            return null;
        }
        if (views[slot] == null || !SatchelInventory.same(views[slot], i.items[slot])) views[slot] = i.items[slot].copy();
        views[slot].stackSize = n;
        given[slot] = n;
        return views[slot];
    }

    /** Another mod changed a stack it was given in place: the Satchel follows. */
    private void reconcile(SatchelInventory i, int slot) {
        if (slot >= views.length || views[slot] == null || views[slot].stackSize == given[slot]) return;
        int delta = views[slot].stackSize - given[slot];
        given[slot] = views[slot].stackSize;
        if (delta < 0) i.extract(slot, -delta, false, false);
        else i.insert(views[slot], delta, false, false);
        changed();
    }

    @Override
    public ItemStack decrStackSize(int slot, int n) {
        SatchelInventory i = items();
        if (i == null || slot < 0 || slot >= i.size || n <= 0) return null;
        sizeViews(i.size);
        reconcile(i, slot);
        int shown = Math.min(i.available(slot, true), i.items[slot] == null ? 0 : i.items[slot].getMaxStackSize());
        ItemStack out = shown <= 0 ? null : i.extract(slot, Math.min(n, shown), true, false);
        views[slot] = null;
        // The slot now shows what it showed less what was taken. A hopper that can't pass the item on puts back the stack
        // it first saw: only what it took goes back in (counting from 0 here gave it a whole stack more).
        given[slot] = Math.max(0, shown - (out == null ? 0 : out.stackSize));
        if (out != null) changed();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack s) {
        SatchelInventory i = items();
        if (i == null || slot < 0 || slot > i.size) return;
        sizeViews(i.size);
        if (slot == i.size) { // the input slot
            if (s != null && s.stackSize > 0) i.insert(s, s.stackSize, false, false);
            changed();
            return;
        }
        int now = s == null ? 0 : s.stackSize, delta = now - given[slot];
        if (delta < 0) i.extract(slot, -delta, false, false);
        else if (delta > 0) i.insert(s, delta, false, false); // a mod that didn't ask first: it goes where the Satchel puts it
        views[slot] = null;
        given[slot] = 0;
        changed();
    }

    @Override
    public void markDirty() {
        SatchelInventory i = items();
        if (i != null) for (int k = 0; k < Math.min(views.length, i.size); k++) reconcile(i, k);
        changed();
    }

    @Override
    public String getInventoryName() {
        return "Satchel Dock";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack s) {
        SatchelInventory i = items();
        return i != null && slot == i.size && s != null && i.insert(s, s.stackSize, false, true) >= s.stackSize;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int n = getSizeInventory();
        if (slots.length != n) { // hoppers and pipes ask on every try: one array per size
            slots = new int[n];
            for (int k = 0; k < n; k++) slots[k] = k;
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack s, int side) {
        return isItemValidForSlot(slot, s);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack s, int side) {
        SatchelInventory i = items();
        return i != null && slot < i.size;
    }

    // ---- fluids --------------------------------------------------------------------------------------------------------

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        SatchelInventory t = tanks();
        if (t == null || resource == null) return 0;
        int n = t.fill(resource, doFill);
        if (doFill && n > 0) changed();
        return n;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        SatchelInventory t = tanks();
        if (t == null || resource == null) return null;
        int i = t.findFluid(resource);
        if (i < 0) return null;
        FluidStack out = t.drain(i, resource.amount, true, doDrain);
        if (doDrain && out != null) changed();
        return out;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        SatchelInventory t = tanks();
        if (t == null) return null;
        int i = t.availableFluid(t.selected, true) > 0 ? t.selected : -1;
        for (int k = 0; k < t.size && i < 0; k++) if (t.availableFluid(k, true) > 0) i = k;
        if (i < 0) return null;
        FluidStack out = t.drain(i, maxDrain, true, doDrain);
        if (doDrain && out != null) changed();
        return out;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        SatchelInventory t = tanks();
        return t != null && fluid != null && t.fill(new FluidStack(fluid, 1), false) > 0;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        SatchelInventory t = tanks();
        return t != null && (fluid == null || t.findFluid(new FluidStack(fluid, 1)) >= 0);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        SatchelInventory t = tanks();
        if (t == null) return new FluidTankInfo[0];
        FluidTankInfo[] out = new FluidTankInfo[t.size];
        for (int k = 0; k < t.size; k++) out[k] = new FluidTankInfo(t.fluids[k] == null ? null : t.fluids[k].copy(), t.capacity);
        return out;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (docked != null) nbt.setTag("Docked", docked.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        docked = nbt.hasKey("Docked", 10) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("Docked")) : null;
        views = new ItemStack[0];
        given = new int[0];
    }
}
