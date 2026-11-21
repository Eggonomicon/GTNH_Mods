package com.valier.tweaks.pipe;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.world.World;

import com.valier.tweaks.TweaksConfig;

/**
 * Moves items for item pipe faces, straight from block to block.
 * <ul>
 * <li>It takes out first (how many really came out is what counts: some inventories give less, or more, than they
 * show), then puts in as a vanilla hopper does: onto stacks of the same item first, never past the inventory's stack
 * limit, then {@code markDirty}. What nothing took goes back where it came from; if it can't, the face holds it and
 * hands it on first next time (it drops if the pipe breaks).</li>
 * <li>Slots follow the block's own rules for the side the pipe touches. A double chest is one inventory.</li>
 * <li>A face never moves items into the block it takes from.</li>
 * <li>Lists: Deny wins; what a pulling face's Void list names is destroyed in its block (over the entry's amount);
 * what a pushing face's Void list names is destroyed when it doesn't fit. Allow amounts: a pulling or providing face
 * only gives what is over its amount; a pushing face fills up to it; a Request face keeps it stocked.</li>
 * <li>Priorities go strictly from high to low; within one, the pulling face's spread setting decides.</li>
 * </ul>
 * Every step is bounded: one face's turn looks at its own block's slots once and at each destination's slots at most
 * twice (room, then the move). Nothing is allocated per slot.
 */
final class ItemMover {

    private ItemMover() {}

    static int work(PipeNetwork.Face f) {
        IInventory inv = own(f);
        if (inv == null) return 0;
        int budget = f.cfg.perTransfer(PipeKind.ITEM, f.tier);
        int moved = handHeld(f, budget);
        if (moved >= budget) return moved;
        if (f.cfg.mode.pulls()) moved += extract(f, inv, budget - moved);
        else if (f.cfg.mode == FaceMode.REQUEST) moved += request(f, inv, budget - moved);
        return moved;
    }

    // ---- Pull --------------------------------------------------------------------------------------------------------

    /**
     * Up to {@code budget} items out of the block: what its Void list names is destroyed (over the entry's amount);
     * what may pass goes into Push faces, Request faces short of it, then overflow Provide faces.
     */
    static int extract(PipeNetwork.Face f, IInventory src, int budget) {
        List<PipeNetwork.Face> inserts = PipeNetworks.partners(f, FaceMode.INSERT, false);
        List<PipeNetwork.Face> requests = PipeNetworks.partners(f, FaceMode.REQUEST, false);
        List<PipeNetwork.Face> overflow = PipeNetworks.partners(f, FaceMode.PROVIDE, true);
        boolean anywhere = !(inserts.isEmpty() && requests.isEmpty() && overflow.isEmpty());
        boolean voiding = f.cfg.voids.active();
        if (!anywhere && !voiding) return 0;
        World w = f.pipe.getWorldObj();
        long tick = w.getTotalWorldTime();
        Object srcKey = key(w, f.tx, f.ty, f.tz);
        int moved = 0;
        int[] slots = slots(src, f.access);
        for (int k = 0; k < slots.length && moved < budget; k++) {
            int slot = slots[k];
            ItemStack s = src.getStackInSlot(slot);
            if (s == null || s.stackSize <= 0) continue;
            if (src instanceof ISidedInventory && !((ISidedInventory) src).canExtractItem(slot, s, f.access)) continue;
            if (voiding && f.cfg.voids(s, src)) {
                int n = Math.min(budget - moved, Math.min(s.stackSize, voidSpare(f.cfg, src, s)));
                if (n <= 0) continue;
                ItemStack gone = src.decrStackSize(slot, n);
                if (gone == null || gone.stackSize <= 0) continue;
                src.markDirty();
                f.cfg.flow.add(gone, gone.stackSize, true, tick);
                moved += gone.stackSize;
                continue;
            }
            if (!anywhere || !f.cfg.passes(s, src)) continue;
            int want = Math.min(budget - moved, Math.min(s.stackSize, spare(f.cfg, src, s)));
            if (want <= 0) continue;
            int room = room(srcKey, s, want, inserts, requests, overflow);
            int n = Math.min(want, room);
            if (n <= 0) continue;
            ItemStack got = src.decrStackSize(slot, n);
            if (got == null || got.stackSize <= 0) continue;
            int took = got.stackSize;
            ItemStack left = deliver(f, srcKey, got, inserts, requests, overflow);
            src.markDirty();
            int back = left == null ? 0 : left.stackSize;
            f.cfg.flow.add(got, took - back, false, tick);
            if (f.source != null) left = null; // a creative source: what nobody took just goes
            if (left != null) left = putBack(src, slot, f.access, left);
            if (left != null) hold(f, left);
            moved += took - back;
        }
        return moved;
    }

    /** How many of s a pulling or providing face may give: all, or what is over its Allow amount for s. */
    static int spare(FaceConfig cfg, IInventory inv, ItemStack s) {
        int keep = cfg.stockOf(s);
        return keep <= 0 ? Integer.MAX_VALUE : count(inv, cfg.allow, s) - keep;
    }

    /** How many of s a pulling face may destroy: all, or what is over its Void entry's amount. */
    static int voidSpare(FaceConfig cfg, IInventory inv, ItemStack s) {
        int keep = cfg.voids.stockOf(s);
        return keep <= 0 ? Integer.MAX_VALUE : count(inv, cfg.voids, s) - keep;
    }

    /** How many of {@code s} (up to want) the destinations would take. */
    @SuppressWarnings("unchecked")
    static int room(Object srcKey, ItemStack s, int want, List<PipeNetwork.Face> inserts, List<PipeNetwork.Face> requests,
        List<PipeNetwork.Face> overflow) {
        int room = 0;
        for (List<PipeNetwork.Face> group : new List[] { inserts, requests, overflow }) {
            for (PipeNetwork.Face d : group) {
                if (room >= want) return room;
                room += accepts(d, srcKey, s, want - room);
            }
        }
        return room;
    }

    /**
     * How many of s a destination face takes now: a Request face's shortfall; a face whose Void list names it, all of
     * it (what doesn't fit is destroyed); otherwise what its Allow list, amounts and room let in.
     */
    static int accepts(PipeNetwork.Face d, Object srcKey, ItemStack s, int max) {
        World w = d.pipe.getWorldObj();
        if (max <= 0 || !w.blockExists(d.tx, d.ty, d.tz)) return 0;
        IInventory inv = inventory(w, d.tx, d.ty, d.tz);
        if (inv == null || same(srcKey, key(w, d.tx, d.ty, d.tz))) return 0;
        if (d.cfg.mode == FaceMode.REQUEST) {
            int i = d.cfg.allow.indexOf(s);
            if (i < 0 || d.cfg.deny.matches(s, inv)) return 0;
            int short_ = d.cfg.allow.requestOf(i) - count(inv, d.cfg.allow, d.cfg.allow.filter[i]);
            if (short_ <= 0) return 0;
            return space(inv, d.access, s, Math.min(max, short_));
        }
        if (d.cfg.voids(s, inv)) return max;
        return fits(d, inv, s, max);
    }

    /** What a pushing face's block takes of s by the face's lists and amounts (up to max). */
    static int fits(PipeNetwork.Face d, IInventory inv, ItemStack s, int max) {
        if (!d.cfg.passes(s, inv)) return 0;
        int keep = d.cfg.stockOf(s);
        if (keep > 0) max = Math.min(max, keep - count(inv, d.cfg.allow, s));
        if (max <= 0) return 0;
        return space(inv, d.access, s, max);
    }

    /**
     * Puts the stack into the destinations; returns what's left. Groups (Push faces, Request faces, overflow) go in
     * that order, and within a group the priorities strictly from high to low; inside one priority the face's spread
     * setting decides: in order, taking turns (round robin), or an even split.
     */
    static ItemStack deliver(PipeNetwork.Face f, Object srcKey, ItemStack stack, List<PipeNetwork.Face> inserts,
        List<PipeNetwork.Face> requests, List<PipeNetwork.Face> overflow) {
        long tick = f.pipe.getWorldObj()
            .getTotalWorldTime();
        deliverGroup(f, srcKey, stack, inserts, tick);
        deliverGroup(f, srcKey, stack, requests, tick);
        deliverGroup(f, srcKey, stack, overflow, tick);
        f.cfg.turn++;
        return stack == null || stack.stackSize <= 0 ? null : stack;
    }

    private static void deliverGroup(PipeNetwork.Face f, Object srcKey, ItemStack stack, List<PipeNetwork.Face> g,
        long tick) {
        int n = g.size();
        for (int i = 0; i < n && stack.stackSize > 0;) {
            int j = i + 1;
            int p = g.get(i).cfg.priority;
            while (j < n && g.get(j).cfg.priority == p) j++;
            int k = j - i;
            if (f.cfg.spread == FaceConfig.Spread.EVEN && k > 1) even(f, srcKey, stack, g, i, k, tick);
            else {
                int start = f.cfg.spread == FaceConfig.Spread.ROUND_ROBIN ? Math.floorMod(f.cfg.turn, k) : 0;
                for (int x = 0; x < k && stack.stackSize > 0; x++) give(g.get(i + (start + x) % k), srcKey, stack, stack.stackSize, tick);
            }
            i = j;
        }
    }

    /** One priority's faces share the stack equally (the odd ones go round); what one can't take goes to the others. */
    private static void even(PipeNetwork.Face f, Object srcKey, ItemStack stack, List<PipeNetwork.Face> g, int from, int k,
        long tick) {
        int[] room = new int[k];
        int live = 0;
        for (int x = 0; x < k; x++) if ((room[x] = accepts(g.get(from + x), srcKey, stack, stack.stackSize)) > 0) live++;
        int start = Math.floorMod(f.cfg.turn, k);
        while (stack.stackSize > 0 && live > 0) {
            int share = Math.max(1, stack.stackSize / live);
            int gave = 0;
            for (int x = 0; x < k && stack.stackSize > 0; x++) {
                int i = (start + x) % k;
                if (room[i] <= 0) continue;
                int offer = Math.min(share, room[i]);
                int took = give(g.get(from + i), srcKey, stack, offer, tick);
                room[i] = took < offer ? 0 : room[i] - took;
                if (room[i] <= 0) live--;
                gave += took;
            }
            if (gave == 0) break;
        }
    }

    /**
     * Gives up to max of the stack to one destination (taking it off the stack): into its block what fits there, and
     * if its Void list names it, the rest is destroyed. Returns how many left the stack.
     */
    static int give(PipeNetwork.Face d, Object srcKey, ItemStack stack, int max, long tick) {
        int take = accepts(d, srcKey, stack, Math.min(max, stack.stackSize));
        if (take <= 0) return 0;
        IInventory inv = inventory(d.pipe.getWorldObj(), d.tx, d.ty, d.tz);
        boolean voids = d.cfg.mode != FaceMode.REQUEST && d.cfg.voids(stack, inv);
        ItemStack part = stack.splitStack(take);
        int fit = voids ? fits(d, inv, part, part.stackSize) : part.stackSize;
        ItemStack in = part.splitStack(Math.min(fit, part.stackSize)); // part keeps what is to be destroyed
        int before = in.stackSize;
        ItemStack left = in.stackSize > 0 ? insert(inv, d.access, in) : null;
        int inserted = before - (left == null ? 0 : left.stackSize);
        if (left != null) {
            if (voids) part.stackSize += left.stackSize;
            else stack.stackSize += left.stackSize;
        }
        if (inserted > 0 && !d.cfg.mode.active()) d.cfg.flow.add(in, inserted, false, tick);
        if (voids && part.stackSize > 0) d.cfg.flow.add(part, part.stackSize, true, tick);
        return take - (left != null && !voids ? left.stackSize : 0);
    }

    // ---- Request -----------------------------------------------------------------------------------------------------

    /** Tops up each filter item to its amount from Provide faces (only what they have over theirs), within budget. */
    static int request(PipeNetwork.Face f, IInventory dst, int budget) {
        List<PipeNetwork.Face> providers = PipeNetworks.partners(f, FaceMode.PROVIDE, false);
        if (providers.isEmpty() || !f.cfg.allow.hasEntries()) return 0;
        World w = f.pipe.getWorldObj();
        long tick = w.getTotalWorldTime();
        Object dstKey = key(w, f.tx, f.ty, f.tz);
        int moved = 0;
        for (int i = 0; i < FaceConfig.FILTER && moved < budget; i++) {
            ItemStack want = f.cfg.allow.filter[i];
            if (want == null || f.cfg.deny.matches(want, dst)) continue;
            int short_ = f.cfg.allow.requestOf(i) - count(dst, f.cfg.allow, want);
            if (short_ <= 0) continue;
            int need = Math.min(short_, budget - moved);
            for (PipeNetwork.Face p : providers) {
                if (need <= 0) break;
                World pw = p.pipe.getWorldObj();
                if (!pw.blockExists(p.tx, p.ty, p.tz)) continue;
                IInventory src = own(p);
                if (src == null || same(dstKey, key(pw, p.tx, p.ty, p.tz))) continue;
                int[] slots = slots(src, p.access);
                for (int k = 0; k < slots.length && need > 0; k++) {
                    int slot = slots[k];
                    ItemStack s = src.getStackInSlot(slot);
                    if (s == null || s.stackSize <= 0 || !f.cfg.allow.same(want, s) || !p.cfg.passes(s, src)) continue;
                    if (src instanceof ISidedInventory && !((ISidedInventory) src).canExtractItem(slot, s, p.access)) continue;
                    int n = Math.min(need, Math.min(s.stackSize, Math.min(spare(p.cfg, src, s), space(dst, f.access, s, need))));
                    if (n <= 0) continue;
                    ItemStack got = src.decrStackSize(slot, n);
                    if (got == null || got.stackSize <= 0) continue;
                    int took = got.stackSize;
                    ItemStack left = insert(dst, f.access, got);
                    int back = left == null ? 0 : left.stackSize;
                    f.cfg.flow.add(got, took - back, false, tick);
                    if (p.source != null) left = null;
                    if (left != null) left = putBack(src, slot, p.access, left);
                    if (left != null) hold(f, left);
                    src.markDirty();
                    moved += took - back;
                    need -= took - back;
                }
            }
        }
        return moved;
    }

    // ---- held items -------------------------------------------------------------------------------------------------

    /** What the face held back goes on to its destinations (pulling faces) or into its own block first. */
    static int handHeld(PipeNetwork.Face f, int budget) {
        if (f.cfg.heldItems.isEmpty()) return 0;
        int moved = 0;
        World w = f.pipe.getWorldObj();
        IInventory own = own(f);
        Object ownKey = key(w, f.tx, f.ty, f.tz);
        for (int i = 0; i < f.cfg.heldItems.size() && moved < budget; i++) {
            ItemStack s = f.cfg.heldItems.get(i);
            int before = s.stackSize;
            ItemStack left;
            if (f.cfg.mode.pulls()) {
                left = deliver(f, ownKey, s, PipeNetworks.partners(f, FaceMode.INSERT, false),
                    PipeNetworks.partners(f, FaceMode.REQUEST, false), PipeNetworks.partners(f, FaceMode.PROVIDE, true));
                if (left != null && own != null) left = insert(own, f.access, left);
            } else left = own == null ? s : insert(own, f.access, s);
            int after = left == null ? 0 : left.stackSize;
            moved += before - after;
            if (left == null) f.cfg.heldItems.remove(i--);
            else f.cfg.heldItems.set(i, left);
        }
        if (moved > 0) f.pipe.markDirty();
        return moved;
    }

    static void hold(PipeNetwork.Face f, ItemStack s) {
        for (ItemStack h : f.cfg.heldItems) {
            if (h.isItemEqual(s) && ItemStack.areItemStackTagsEqual(h, s) && h.stackSize < h.getMaxStackSize()) {
                int n = Math.min(s.stackSize, h.getMaxStackSize() - h.stackSize);
                h.stackSize += n;
                s.stackSize -= n;
                if (s.stackSize <= 0) break;
            }
        }
        if (s.stackSize > 0) f.cfg.heldItems.add(s);
        f.pipe.markDirty();
        if (f.cfg.heldItems.size() > Math.max(1, TweaksConfig.pipeHeldStacks)) {
            // More than it should ever hold: the oldest drops (a block that keeps taking less than it said).
            ItemStack drop = f.cfg.heldItems.remove(0);
            com.valier.tweaks.LootSpawners.dropAt(f.pipe.getWorldObj(), f.pipe.xCoord, f.pipe.yCoord, f.pipe.zCoord, drop);
        }
    }

    // ---- inventories --------------------------------------------------------------------------------------------

    /** The inventory a face works on: the block it touches, or a creative pipe's endless source. */
    static IInventory own(PipeNetwork.Face f) {
        return f.source != null ? f.source : inventory(f.pipe.getWorldObj(), f.tx, f.ty, f.tz);
    }

    /** The inventory at a place (a double chest as one), or null; never loads a chunk. */
    static IInventory inventory(World w, int x, int y, int z) {
        if (y < 0 || y >= w.getHeight() || !w.blockExists(x, y, z)) return null;
        TileEntity te = w.getTileEntity(x, y, z);
        if (te == null || te.isInvalid() || te instanceof TilePipe || !(te instanceof IInventory)) return null;
        if (te instanceof TileEntityChest) {
            IInventory big = TileEntityHopper.func_145893_b(w, x, y, z);
            if (big != null) return big;
        }
        return (IInventory) te;
    }

    /** Tells inventories apart: a double chest's two halves are the same. */
    static Object key(World w, int x, int y, int z) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileEntityChest) {
            TileEntityChest c = (TileEntityChest) te;
            c.checkForAdjacentChests();
            if (c.adjacentChestXNeg != null) return c.adjacentChestXNeg;
            if (c.adjacentChestZNeg != null) return c.adjacentChestZNeg;
        }
        return te;
    }

    static boolean same(Object a, Object b) {
        return a != null && a == b;
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

    static boolean canInsert(IInventory inv, int slot, ItemStack s, int side) {
        if (!inv.isItemValidForSlot(slot, s)) return false;
        return !(inv instanceof ISidedInventory) || ((ISidedInventory) inv).canInsertItem(slot, s, side);
    }

    static boolean stackable(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** How many of s (up to max) fit, by the inventory's rules for that side, without changing anything. */
    static int space(IInventory inv, int side, ItemStack s, int max) {
        if (max <= 0) return 0;
        int limit = Math.min(s.getMaxStackSize(), inv.getInventoryStackLimit());
        int room = 0;
        for (int slot : slots(inv, side)) {
            if (room >= max) break;
            ItemStack in = inv.getStackInSlot(slot);
            if (in == null) {
                if (canInsert(inv, slot, s, side)) room += limit;
            } else if (stackable(in, s) && in.stackSize < limit && canInsert(inv, slot, s, side)) room += limit - in.stackSize;
        }
        return Math.min(room, max);
    }

    /** Puts s in as a hopper does (stacks first, then empty slots); returns what didn't fit. */
    static ItemStack insert(IInventory inv, int side, ItemStack s) {
        if (inv == null || s == null) return s;
        int limit = Math.min(s.getMaxStackSize(), inv.getInventoryStackLimit());
        boolean changed = false;
        int[] slots = slots(inv, side);
        for (int slot : slots) {
            if (s.stackSize <= 0) break;
            ItemStack in = inv.getStackInSlot(slot);
            if (in == null || !stackable(in, s) || in.stackSize >= limit || !canInsert(inv, slot, s, side)) continue;
            int n = Math.min(s.stackSize, limit - in.stackSize);
            in.stackSize += n;
            s.stackSize -= n;
            changed = true;
        }
        for (int slot : slots) {
            if (s.stackSize <= 0) break;
            if (inv.getStackInSlot(slot) != null || !canInsert(inv, slot, s, side)) continue;
            inv.setInventorySlotContents(slot, s.splitStack(Math.min(s.stackSize, limit)));
            changed = true;
        }
        if (changed) inv.markDirty();
        return s.stackSize <= 0 ? null : s;
    }

    /** What the destinations didn't take goes back into the slot it came from, or anywhere it fits; returns the rest. */
    static ItemStack putBack(IInventory inv, int slot, int side, ItemStack s) {
        ItemStack in = inv.getStackInSlot(slot);
        if (in == null) {
            inv.setInventorySlotContents(slot, s);
            inv.markDirty();
            return null;
        }
        if (stackable(in, s)) {
            int n = Math.min(s.stackSize, Math.max(0, in.getMaxStackSize() - in.stackSize));
            in.stackSize += n;
            s.stackSize -= n;
            inv.markDirty();
            if (s.stackSize <= 0) return null;
        }
        return insert(inv, side, s);
    }

    /** Items matching a list entry (by that list's matching rules) in the whole inventory. */
    static int count(IInventory inv, FilterList list, ItemStack want) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && list.same(want, s)) n += s.stackSize;
        }
        return n;
    }
}
