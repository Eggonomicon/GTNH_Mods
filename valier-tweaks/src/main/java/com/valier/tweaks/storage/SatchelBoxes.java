package com.valier.tweaks.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

/**
 * A Satchel in hand, sneak + right-click on a container (2.3.3): what happens is the Satchel's container mode
 * ({@link SatchelInventory#box}, a switch in its menu):
 * <ul>
 * <li>put the selected item in, or put everything in (each slot leaving its keep amount);</li>
 * <li>take out: everything the Satchel would pick up (what it holds, and new items too if that is on), never destroying
 * any (void overflow is for pickups off the ground);</li>
 * <li>off: the click places the selected item, as before.</li>
 * </ul>
 * Containers: anything with an inventory (a double chest as one), sided ones by the face clicked; a Valier barrel by its
 * store, so any count moves at once. Claims are respected (the click needs the right to change blocks there).
 */
final class SatchelBoxes {

    private SatchelBoxes() {}

    /** Whether a sneak + right-click on te is a container click (the client asks too, so it doesn't place a block). */
    static boolean applies(SatchelInventory inv, TileEntity te) {
        if (inv == null || inv.fluid || inv.box == SatchelInventory.BOX_OFF) return false;
        return te instanceof TileBarrel || te instanceof IInventory && ((IInventory) te).getSizeInventory() > 0;
    }

    /** The server's click: true when it was a container click (done, or told why not). */
    static boolean click(ItemStack stack, EntityPlayer p, World world, int x, int y, int z, int side) {
        SatchelInventory inv = SatchelInventory.of(stack);
        TileEntity te = world.getTileEntity(x, y, z);
        if (!applies(inv, te)) return false;
        if (!com.valier.tweaks.LootSpawners.mayChange(p, world, x, y, z)) {
            ItemSatchel.tell(p, "That's protected.");
            return true;
        }
        int moved = move(inv, te, side, p);
        if (moved > 0) world.playSoundAtEntity(p, "random.pop", 0.2F, 1.4F);
        return true;
    }

    /** Does inv's container mode on te; returns how many items moved (and tells p, if given, when none did). */
    static int move(SatchelInventory inv, TileEntity te, int side, EntityPlayer p) {
        if (inv.box == SatchelInventory.BOX_TAKE) {
            int n = te instanceof TileBarrel ? takeFromBarrel(inv, ((TileBarrel) te).store()) : takeFrom(inv, inventory(te), side);
            if (n <= 0) ItemSatchel.tell(p, inv.pickNew ? "Nothing there fits in the Satchel."
                : "The Satchel holds none of what's there (turn on New items in its menu to take anything).");
            return n;
        }
        int moved = 0;
        boolean all = inv.box == SatchelInventory.BOX_ALL;
        for (int i = all ? 0 : inv.selected; i < (all ? inv.size : inv.selected + 1); i++) {
            int n = te instanceof TileBarrel ? putInBarrel(inv, i, ((TileBarrel) te).store()) : putIn(inv, i, inventory(te), side);
            moved += n;
        }
        if (moved <= 0) {
            if (!all && inv.available(inv.selected, true) <= 0) ItemSatchel.tell(p, inv.isEmpty(inv.selected)
                ? "The Satchel's selected slot is empty: pick one (sneak + mouse wheel, or its menu)."
                : "The Satchel's selected slot keeps what is left in it.");
            else ItemSatchel.tell(p, all ? "Nothing went in: it's full, or takes none of it (each slot keeps its keep amount)."
                : "That doesn't take it, or is full.");
        }
        return moved;
    }

    /** The inventory a click on te reaches: a double chest as one. */
    static IInventory inventory(TileEntity te) {
        if (te instanceof TileEntityChest) {
            TileEntityChest c = (TileEntityChest) te;
            c.checkForAdjacentChests();
            if (c.adjacentChestXNeg != null) return new InventoryLargeChest("", c.adjacentChestXNeg, c);
            if (c.adjacentChestXPos != null) return new InventoryLargeChest("", c, c.adjacentChestXPos);
            if (c.adjacentChestZNeg != null) return new InventoryLargeChest("", c.adjacentChestZNeg, c);
            if (c.adjacentChestZPos != null) return new InventoryLargeChest("", c, c.adjacentChestZPos);
            return c;
        }
        return te instanceof IInventory ? (IInventory) te : null;
    }

    static int[] slots(IInventory inv, int side) {
        if (inv instanceof ISidedInventory) {
            int[] s = ((ISidedInventory) inv).getAccessibleSlotsFromSide(side);
            return s == null ? new int[0] : s;
        }
        int[] s = new int[inv.getSizeInventory()];
        for (int i = 0; i < s.length; i++) s[i] = i;
        return s;
    }

    // ---- putting in --------------------------------------------------------------------------------------------------

    /** Slot i (over its keep amount) into a container; returns how many went. */
    static int putIn(SatchelInventory bag, int i, IInventory inv, int side) {
        if (inv == null) return 0;
        ItemStack offer = bag.extract(i, Integer.MAX_VALUE, true, true);
        if (offer == null) return 0;
        int n = putInto(inv, side, offer);
        if (n > 0) bag.extract(i, n, true, false);
        return n;
    }

    /** Puts s (its stack size, any) into inv from side: onto stacks of it first, then empty slots. Returns how many. */
    static int putInto(IInventory inv, int side, ItemStack s) {
        int left = s.stackSize;
        int limit = Math.min(inv.getInventoryStackLimit(), s.getMaxStackSize());
        int[] slots = slots(inv, side);
        ItemStack one = s.copy();
        one.stackSize = 1;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int k : slots) {
                if (left <= 0) break;
                ItemStack in = inv.getStackInSlot(k);
                if (pass == 0 ? in == null || !SatchelInventory.same(in, s) : in != null) continue;
                if (!inv.isItemValidForSlot(k, one)) continue;
                if (inv instanceof ISidedInventory && !((ISidedInventory) inv).canInsertItem(k, one, side)) continue;
                int room = limit - (in == null ? 0 : in.stackSize);
                if (room <= 0) continue;
                int n = Math.min(room, left);
                ItemStack now = in == null ? s.copy() : in;
                now.stackSize = (in == null ? 0 : in.stackSize) + n;
                inv.setInventorySlotContents(k, now);
                left -= n;
            }
        }
        if (left != s.stackSize) inv.markDirty();
        return s.stackSize - left;
    }

    static int putInBarrel(SatchelInventory bag, int i, BarrelStore st) {
        ItemStack offer = bag.extract(i, Integer.MAX_VALUE, true, true);
        if (offer == null || !st.takes(offer)) return 0;
        long room = st.room(offer, offer.stackSize); // a barrel with void overflow on is a sink: it takes it all, as by hand
        if (room <= 0) return 0;
        int n = (int) st.insert(offer, room, false);
        if (n > 0) bag.extract(i, n, true, false);
        return n;
    }

    // ---- taking out --------------------------------------------------------------------------------------------------

    /** What the Satchel would pick up, out of a container; returns how many. */
    static int takeFrom(SatchelInventory bag, IInventory inv, int side) {
        if (inv == null) return 0;
        int moved = 0;
        for (int k : slots(inv, side)) {
            ItemStack in = inv.getStackInSlot(k);
            if (in == null || in.stackSize <= 0) continue;
            if (inv instanceof ISidedInventory && !((ISidedInventory) inv).canExtractItem(k, in, side)) continue;
            int n = bag.take(in, in.stackSize, bag.pickNew, true);
            if (n <= 0) continue;
            ItemStack got = inv.decrStackSize(k, n);
            if (got == null || got.stackSize <= 0) continue;
            moved += bag.take(got, got.stackSize, bag.pickNew, false);
        }
        if (moved > 0) inv.markDirty();
        return moved;
    }

    static int takeFromBarrel(SatchelInventory bag, BarrelStore st) {
        if (st.item == null) return 0;
        long have = st.available(false);
        if (have <= 0) return 0;
        int n = bag.take(st.item, (int) Math.min(Integer.MAX_VALUE, have), bag.pickNew, true);
        if (n <= 0) return 0;
        ItemStack got = st.extract(n, false, false);
        return got == null ? 0 : bag.take(got, got.stackSize, bag.pickNew, false);
    }
}
