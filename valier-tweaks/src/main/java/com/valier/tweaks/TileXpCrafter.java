package com.valier.tweaks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The Valier XP Crafter block's tile (since 2.0.0; before, a crafter was a GregTech tank). It holds the liquid XP tank
 * and two slots; what the crafter makes and knows is still its record in {@link XpCrafters.Data}, by position.
 * <ul>
 * <li>Tank: 32,000 mB (or what a converted GregTech tank held, if more). It takes only liquid XP, from pipes, buckets
 * and the menu; anything can be drained back out (a converted tank may hold another fluid).</li>
 * <li>Slot 0, input: only liquid XP containers (emptied into the tank each second, the empties going to slot 1) and
 * solid XP (essence berries, used as XP when the tank runs short). Pipes and hoppers can't put anything else in, and
 * can't take from it.</li>
 * <li>Slot 1: empty containers. Slots 2 to 10: the crafter's store (what it made and nothing took yet). Pipes, hoppers
 * and the like can take from these.</li>
 * </ul>
 * Never ticks: {@link XpCrafters} runs every crafter once a second.
 */
public class TileXpCrafter extends TileEntity implements ISidedInventory, IFluidHandler {

    public static final int CAPACITY = 32000;
    static final int IN = 0, OUT = 1, STORE = 2, SIZE = STORE + XpCrafters.Crafter.STORE;
    private static final int[] ALL = new int[SIZE];
    static {
        for (int i = 0; i < SIZE; i++) ALL[i] = i;
    }

    FluidStack fluid;
    final ItemStack[] slots = new ItemStack[2];
    /** Set while the block is replaced on purpose: then breaking it drops nothing (a copy carries it on). */
    boolean moving;

    @Override
    public boolean canUpdate() {
        return false;
    }

    /** Its crafter record (server only; null on players' computers or if it has none). */
    XpCrafters.Crafter crafter() {
        return worldObj == null || worldObj.isRemote ? null : XpCrafters.crafterAt(worldObj, xCoord, yCoord, zCoord);
    }

    // ---- the tank (named as GregTech's tanks name theirs) -------------------------------------------------------

    public FluidStack getFluid() {
        return fluid;
    }

    public FluidStack getDrainableStack() {
        return fluid;
    }

    int amount() {
        return fluid == null ? 0 : fluid.amount;
    }

    /** 32,000 mB, or what it holds if more (a converted GregTech tank). */
    public int getCapacity() {
        return Math.max(CAPACITY, amount());
    }

    /** Liquid XP only, and only onto liquid XP of the same kind. */
    public int fill(FluidStack in, boolean doFill) {
        if (in == null || in.amount <= 0 || !XpCrafters.isXp(in.getFluid())) return 0;
        if (fluid != null && fluid.amount > 0 && !fluid.isFluidEqual(in)) return 0;
        int n = Math.min(in.amount, getCapacity() - amount());
        if (n <= 0) return 0;
        if (doFill) {
            if (fluid == null || fluid.amount <= 0) fluid = new FluidStack(in, n);
            else fluid.amount += n;
            changed();
        }
        return n;
    }

    public FluidStack drain(int max, boolean doDrain) {
        if (fluid == null || fluid.amount <= 0 || max <= 0) return null;
        int n = Math.min(max, fluid.amount);
        FluidStack out = new FluidStack(fluid, n);
        if (doDrain) {
            fluid.amount -= n;
            if (fluid.amount <= 0) fluid = null;
            changed();
        }
        return out;
    }

    /** What a converted GregTech tank held (any fluid, any amount) and its input and output slots. */
    void setContents(FluidStack f, ItemStack in, ItemStack out) {
        fluid = f == null || f.amount <= 0 ? null : f.copy();
        slots[IN] = in;
        slots[OUT] = out;
        changed();
    }

    /** Liquid XP containers in the input slot into the tank, the empties into slot 1, as long as both have room. */
    void emptyContainers() {
        ItemStack s = slots[IN];
        boolean any = false;
        while (s != null && s.stackSize > 0) {
            ItemStack one = s.copy();
            one.stackSize = 1;
            FluidStack in = null;
            ItemStack empty = null;
            if (FluidContainerRegistry.isFilledContainer(one)) {
                in = FluidContainerRegistry.getFluidForFilledItem(one);
                empty = FluidContainerRegistry.drainFluidContainer(one);
            } else if (one.getItem() instanceof IFluidContainerItem) {
                IFluidContainerItem item = (IFluidContainerItem) one.getItem();
                FluidStack held = item.getFluid(one);
                in = held == null ? null : item.drain(one, held.amount, true);
                empty = one;
            }
            if (in == null || in.amount <= 0 || !XpCrafters.isXp(in.getFluid()) || fill(in, false) < in.amount) break;
            if (empty != null) {
                ItemStack o = slots[OUT];
                if (o == null) slots[OUT] = empty;
                else if (o.isItemEqual(empty) && ItemStack.areItemStackTagsEqual(o, empty)
                    && o.stackSize < Math.min(o.getMaxStackSize(), getInventoryStackLimit())) o.stackSize++;
                else break; // no room for the empty: wait
            }
            fill(in, true);
            s.stackSize--;
            any = true;
            if (s.stackSize <= 0) slots[IN] = s = null;
        }
        if (any) changed();
    }

    private void changed() {
        if (worldObj != null) super.markDirty();
    }

    // ---- IFluidHandler (pipes) ------------------------------------------------------------------------------------

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || fluid == null || !fluid.isFluidEqual(resource)) return null;
        return drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return drain(maxDrain, doDrain);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid f) {
        return XpCrafters.isXp(f) && (fluid == null || fluid.getFluid() == f);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid f) {
        return fluid != null && (f == null || fluid.getFluid() == f);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { new FluidTankInfo(fluid == null ? null : fluid.copy(), getCapacity()) };
    }

    // ---- inventory ------------------------------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        if (i < 0 || i >= SIZE) return null;
        if (i < STORE) return slots[i];
        XpCrafters.Crafter c = crafter();
        return c == null ? null : c.store[i - STORE];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        ItemStack s = getStackInSlot(i);
        if (s == null || n <= 0) return null;
        ItemStack out;
        if (s.stackSize <= n) {
            out = s;
            setInventorySlotContents(i, null);
        } else {
            out = s.splitStack(n);
            markDirty();
        }
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        if (i < 0 || i >= SIZE) return;
        if (s != null && s.stackSize <= 0) s = null;
        if (i < STORE) slots[i] = s;
        else {
            XpCrafters.Crafter c = crafter();
            if (c == null) return;
            c.store[i - STORE] = s;
        }
        markDirty();
    }

    /** Also marks the crafter records dirty (its store lives there). */
    @Override
    public void markDirty() {
        if (worldObj == null) return;
        super.markDirty();
        if (worldObj.isRemote) return;
        XpCrafters.Data d = XpCrafters.data(worldObj, false);
        if (d != null) d.markDirty();
    }

    @Override
    public String getInventoryName() {
        return XpCrafters.NAME;
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
    public boolean isUseableByPlayer(EntityPlayer player) {
        return false; // its menu is XpMenu
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return i == IN && s != null && (XpCrafters.xpAmount(s) > 0 || XpCrafters.solidXp(s) > 0);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return ALL;
    }

    @Override
    public boolean canInsertItem(int i, ItemStack s, int side) {
        return isItemValidForSlot(i, s);
    }

    @Override
    public boolean canExtractItem(int i, ItemStack s, int side) {
        return i != IN;
    }

    // ---- saving ---------------------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (fluid != null && fluid.amount > 0) nbt.setTag("Fluid", fluid.writeToNBT(new NBTTagCompound()));
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound t = slots[i].writeToNBT(new NBTTagCompound());
            t.setByte("Slot", (byte) i);
            list.appendTag(t);
        }
        if (list.tagCount() > 0) nbt.setTag("Items", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        fluid = nbt.hasKey("Fluid", 10) ? FluidStack.loadFluidStackFromNBT(nbt.getCompoundTag("Fluid")) : null;
        slots[IN] = slots[OUT] = null;
        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot");
            if (slot >= 0 && slot < slots.length) slots[slot] = ItemStack.loadItemStackFromNBT(t);
        }
    }
}
