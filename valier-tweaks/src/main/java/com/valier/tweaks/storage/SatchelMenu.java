package com.valier.tweaks.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.valier.tweaks.pipe.ValierMenu;

/**
 * A Satchel's or Flask's menu (right-click in the air). Its slots are drawn by the screen from the item itself (vanilla
 * sends the item, NBT and all, whenever it changes), so counts of any size show; clicks on them come as actions:
 * <ul>
 * <li>Satchel: click takes a stack (with one on the cursor: puts it in), right-click one, shift-click a stack into
 * the inventory (keeping the slot's keep amount), ctrl-click selects the slot (for placing and the settings).</li>
 * <li>Flask: click with a bucket, cell or other container fills or empties it; ctrl-click selects.</li>
 * <li>The selected slot's settings (keep, ore dictionary, void overflow, lock) and the Satchel's pickup switches.</li>
 * </ul>
 * The Satchel's own inventory slot can't be clicked, swapped or dropped while its menu is open.
 */
public class SatchelMenu extends Container implements ValierMenu {

    public static final int TAKE = 50, PUT = 51, SHIFT = 52, SELECT = 53, KEEP = 54, ORE = 55, VOID = 56, LOCK = 57, PICKUP = 58,
        PICKNEW = 59, CONTAINER = 60;

    public final EntityPlayer player;
    /** The player's inventory slot holding it. */
    public final int at;
    private final ItemStack opened;

    public SatchelMenu(EntityPlayer player, int at) {
        this.player = player;
        this.at = at;
        this.opened = player.inventory.getStackInSlot(at);
        SatchelInventory inv = SatchelInventory.of(opened);
        int rows = inv == null ? 1 : (inv.size + 8) / 9;
        int invY = invY(rows);
        InventoryPlayer pi = player.inventory;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(slot(pi, 9 + r * 9 + c, 8 + c * 18, invY + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(slot(pi, c, 8 + c * 18, invY + 58));
    }

    /** Where the player's inventory starts, under the rows of slots and the settings row. */
    public static int invY(int rows) {
        return 18 + rows * 18 + 36;
    }

    private Slot slot(InventoryPlayer pi, int index, int x, int y) {
        return index == at ? new Held(pi, index, x, y) : new Slot(pi, index, x, y);
    }

    /** The open Satchel (null if it is gone from its slot: the menu closes). */
    public SatchelInventory inv() {
        ItemStack now = player.inventory.getStackInSlot(at);
        if (now == null || !(now.getItem() instanceof ItemSatchel)) return null;
        if (!player.worldObj.isRemote && now != opened) return null;
        return SatchelInventory.of(now);
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return inv() != null;
    }

    /** Number keys can't swap the open Satchel out of its slot. */
    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer p) {
        if (mode == 2 && button == at) return null;
        if (slot >= 0 && slot < inventorySlots.size() && inventorySlots.get(slot) instanceof Held) return null;
        return super.slotClick(slot, button, mode, p);
    }

    /** Shift-click in the inventory: into the Satchel (its slot for that item, else an empty one). */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot s = (Slot) inventorySlots.get(index);
        if (s == null || s instanceof Held || !s.getHasStack() || p.worldObj.isRemote) return null;
        SatchelInventory inv = inv();
        if (inv == null) return null;
        ItemStack st = s.getStack();
        if (inv.fluid) {
            fillFromContainer(inv, s);
            return null;
        }
        int took = inv.insert(st, st.stackSize, false, false);
        if (took > 0) {
            st.stackSize -= took;
            if (st.stackSize <= 0) s.putStack(null);
            else s.onSlotChanged();
        }
        return null;
    }

    @Override
    public void apply(EntityPlayer p, int action, int a, int b, ItemStack stack, String text) {
        SatchelInventory inv = inv();
        if (inv == null || p.worldObj.isRemote) return;
        boolean slotOk = a >= 0 && a < inv.size;
        ItemStack cursor = p.inventory.getItemStack();
        switch (action) {
            case TAKE: // a: slot, b: 0 a stack, 1 one
                if (!slotOk || inv.fluid) return;
                if (cursor == null) {
                    int n = b == 1 ? 1 : inv.items[a] == null ? 0 : inv.items[a].getMaxStackSize();
                    ItemStack out = inv.extract(a, n, false, false);
                    if (out != null) p.inventory.setItemStack(out);
                } else putCursor(inv, a, b == 1);
                break;
            case PUT:
                if (!slotOk || inv.fluid || cursor == null) return;
                putCursor(inv, a, b == 1);
                break;
            case SHIFT: { // a stack (over the keep amount) into the inventory
                if (!slotOk || inv.fluid || inv.items[a] == null) return;
                ItemStack out = inv.extract(a, inv.items[a].getMaxStackSize(), true, true);
                if (out == null) return;
                ItemStack copy = out.copy();
                if (!p.inventory.addItemStackToInventory(copy)) return;
                inv.extract(a, out.stackSize - (copy.stackSize > 0 ? copy.stackSize : 0), true, false);
                break;
            }
            case SELECT:
                if (!slotOk) return;
                inv.selected = a;
                inv.save();
                break;
            case KEEP:
                inv.keep[inv.selected] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) inv.keep[inv.selected] + Math.max(-1_000_000,
                    Math.min(1_000_000, a))));
                inv.save();
                break;
            case ORE:
                if (inv.fluid) return;
                inv.ore[inv.selected] = !inv.ore[inv.selected];
                inv.save();
                break;
            case VOID:
                inv.voids[inv.selected] = !inv.voids[inv.selected];
                inv.save();
                break;
            case LOCK: {
                int i = inv.selected;
                inv.lock[i] = !inv.lock[i];
                if (!inv.lock[i] && inv.isEmpty(i)) {
                    inv.items[i] = null;
                    inv.fluids[i] = null;
                }
                inv.save();
                break;
            }
            case PICKUP:
                inv.pickup = !inv.pickup;
                inv.save();
                break;
            case PICKNEW:
                inv.pickNew = !inv.pickNew;
                inv.save();
                break;
            case CONTAINER: // a Flask tank and a container on the cursor
                if (!slotOk || !inv.fluid || cursor == null) return;
                inv.selected = a;
                useContainer(inv, a, p);
                break;
            default:
                return;
        }
        if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer)) ((EntityPlayerMP) p).updateHeldItem();
        detectAndSendChanges();
    }

    private void putCursor(SatchelInventory inv, int a, boolean one) {
        ItemStack cursor = player.inventory.getItemStack();
        boolean fits = inv.items[a] == null ? !inv.lock[a] : SatchelInventory.same(inv.items[a], cursor);
        if (!fits) return;
        int took = inv.insert(cursor, one ? 1 : cursor.stackSize, false, false);
        cursor.stackSize -= took;
        if (cursor.stackSize <= 0) player.inventory.setItemStack(null);
    }

    /** A bucket, cell or other container on the cursor, clicked on tank a: empty it into the tank, or fill it from it. */
    private void useContainer(SatchelInventory inv, int a, EntityPlayer p) {
        ItemStack c = p.inventory.getItemStack();
        ItemStack one = c.copy();
        one.stackSize = 1;
        FluidStack in = containerFluid(one);
        ItemStack result = null;
        if (in != null && in.amount > 0) { // full: pour it in
            if (inv.fill(in, false) < in.amount) return;
            inv.fill(in, true);
            result = emptied(one);
        } else if (!inv.isEmpty(a)) { // empty: fill it
            FluidStack have = inv.drain(a, Integer.MAX_VALUE, false, false);
            if (one.getItem() instanceof IFluidContainerItem) {
                IFluidContainerItem ci = (IFluidContainerItem) one.getItem();
                int n = ci.fill(one, have, true);
                if (n <= 0) return;
                inv.drain(a, n, false, true);
                result = one;
            } else {
                ItemStack filled = FluidContainerRegistry.fillFluidContainer(have, one);
                FluidStack f = filled == null ? null : FluidContainerRegistry.getFluidForFilledItem(filled);
                if (f == null) return;
                inv.drain(a, f.amount, false, true);
                result = filled;
            }
        } else return;
        c.stackSize--;
        if (c.stackSize <= 0) p.inventory.setItemStack(result);
        else if (result != null && !p.inventory.addItemStackToInventory(result)) p.dropPlayerItemWithRandomChoice(result, false);
    }

    static FluidStack containerFluid(ItemStack one) {
        if (one.getItem() instanceof IFluidContainerItem) return ((IFluidContainerItem) one.getItem()).getFluid(one);
        return FluidContainerRegistry.getFluidForFilledItem(one);
    }

    static ItemStack emptied(ItemStack one) {
        if (one.getItem() instanceof IFluidContainerItem) {
            ((IFluidContainerItem) one.getItem()).drain(one, Integer.MAX_VALUE, true);
            return one;
        }
        return FluidContainerRegistry.drainFluidContainer(one);
    }

    /** Shift-click of a container in the inventory into a Flask: it is emptied in. */
    private void fillFromContainer(SatchelInventory inv, Slot s) {
        ItemStack st = s.getStack();
        ItemStack one = st.copy();
        one.stackSize = 1;
        FluidStack in = containerFluid(one);
        if (in == null || in.amount <= 0 || inv.fill(in, false) < in.amount) return;
        inv.fill(in, true);
        ItemStack empty = emptied(one);
        st.stackSize--;
        if (st.stackSize <= 0) s.putStack(null);
        else s.onSlotChanged();
        if (empty != null && !player.inventory.addItemStackToInventory(empty)) player.dropPlayerItemWithRandomChoice(empty, false);
    }

    @Override
    public void receive(NBTTagCompound t) {} // the screen reads the item itself

    /** The slot of the open Satchel: shown, never taken. */
    static final class Held extends Slot {

        Held(InventoryPlayer inv, int i, int x, int y) {
            super(inv, i, x, y);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return false;
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return false;
        }
    }
}
