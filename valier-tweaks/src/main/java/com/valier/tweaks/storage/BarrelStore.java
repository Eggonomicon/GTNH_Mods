package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * What a barrel holds: one kind of item (a 1-item template) and how many, up to a capacity in stacks, with its
 * settings: locked (keeps its item at 0), keep (automation leaves that many), void overflow (matching items still go in
 * when full, and are destroyed). An unlinked barrel has its own; linked barrels share their channel's
 * ({@link BarrelChannels}), so taking one from any of them takes it from all.
 * <p>
 * Every change is O(1): arithmetic on a long. The barrels showing it ({@link #viewers}) are told at most every 10 ticks
 * ({@link BarrelSync}).
 */
public final class BarrelStore {

    /** The item held (stack size 1), or null when it holds nothing and isn't locked. */
    ItemStack item;
    long count;
    /** Capacity in stacks (the owner sets it: a barrel's tier, or the sum of a channel's barrels). */
    long stacks = BarrelTier.STACKS[0];
    boolean locked, voidOverflow;
    long keep;
    /** Loaded barrels showing it (server). */
    final List<TileBarrel> viewers = new ArrayList<>(1);
    /** Told when it changes (a channel marks its saved data dirty; a barrel its chunk). */
    Runnable onChange;
    /** Changed since the barrels showing it were last told. */
    boolean dirty;
    /**
     * A Creative barrel's (2.3.1): it gives its item forever (its count stays {@link #ENDLESS}, taking leaves it), and
     * takes nothing in, unless void overflow is on: then its item goes in and is destroyed. Its item is set by hand
     * ({@link #set}); it is never linked.
     */
    boolean creative;
    /** A Creative barrel's count (what the label, WAILA, AE2 and comparators see). */
    public static final long ENDLESS = Integer.MAX_VALUE;
    /** Told with the barrels (an AE2 storage bus's view, {@link BarrelAe}). */
    final List<Watcher> watchers = new ArrayList<>(0);

    /** Told when the store changed, at most every 10 ticks. */
    interface Watcher {

        void storeChanged();
    }

    public ItemStack item() {
        return item;
    }

    public long count() {
        return count;
    }

    public long capacity() {
        if (creative) return ENDLESS;
        return stacks * (item == null ? 64 : Math.max(1, item.getMaxStackSize()));
    }

    public boolean creative() {
        return creative;
    }

    /** Made a Creative barrel's (or not): its count is endless while it has an item. */
    void setCreative(boolean on) {
        creative = on;
        if (on) count = item == null ? 0 : ENDLESS;
    }

    /** A Creative barrel gives this from now on (one is kept as its item; the stack itself is left alone). */
    public void set(ItemStack s) {
        item = s.copy();
        item.stackSize = 1;
        count = ENDLESS;
        changed();
    }

    /** Whether s is the item held (same item, damage and NBT). */
    public boolean matches(ItemStack s) {
        return item != null && s != null && same(item, s);
    }

    static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** Whether s may go in at all (its item, or anything while empty and unlocked). */
    public boolean takes(ItemStack s) {
        if (s == null || s.getItem() == null) return false;
        return item == null ? !locked : same(item, s);
    }

    /** How many of s (up to n) it would take: what fits, or all of them with void overflow. */
    public long room(ItemStack s, long n) {
        if (n <= 0 || !takes(s)) return 0;
        if (creative) return voidOverflow && item != null ? n : 0;
        if (voidOverflow) return n;
        long cap = stacks * Math.max(1, s.getMaxStackSize());
        return Math.max(0, Math.min(n, cap - count));
    }

    /** Puts up to n of s in; returns how many it took (with void overflow, what doesn't fit is destroyed). */
    public long insert(ItemStack s, long n, boolean simulate) {
        long take = room(s, n);
        if (take <= 0 || simulate || creative) return take; // a Creative barrel destroys what it takes
        if (item == null) {
            item = s.copy();
            item.stackSize = 1;
        }
        long cap = capacity();
        count = Math.min(cap, count + take);
        changed();
        return take;
    }

    /** How many can be taken out (automation leaves the keep amount). */
    public long available(boolean automation) {
        if (item == null) return 0;
        if (creative) return ENDLESS;
        return Math.max(0, count - (automation ? keep : 0));
    }

    /** Takes up to n out; null when none can be. */
    public ItemStack extract(long n, boolean automation, boolean simulate) {
        long k = Math.min(n, available(automation));
        if (k <= 0) return null;
        ItemStack out = item.copy();
        out.stackSize = (int) Math.min(Integer.MAX_VALUE, k);
        if (simulate || creative) return out;
        count -= k;
        if (count <= 0 && !locked) {
            count = 0;
            item = null;
        }
        changed();
        return out;
    }

    /** Takes up to n out for automation (a long: AE2 asks in longs); returns how many. */
    long take(long n) {
        long k = Math.min(n, available(true));
        if (k <= 0 || creative) return Math.max(0, k);
        count -= k;
        if (count <= 0 && !locked) {
            count = 0;
            item = null;
        }
        changed();
        return k;
    }

    /** A comparator's level: 0 when empty, 1 to 15 by how full. */
    public int comparator() {
        if (item == null || count <= 0) return 0;
        long cap = capacity();
        return (int) Math.max(1, Math.min(15, 1 + (count * 14) / Math.max(1, cap)));
    }

    void changed() {
        if (onChange != null) onChange.run();
        if (!dirty) {
            dirty = true;
            BarrelSync.mark(this);
        }
    }

    void setLocked(boolean on) {
        locked = on;
        if (!on && count <= 0) item = null;
        changed();
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    public NBTTagCompound write(NBTTagCompound t) {
        if (item != null) t.setTag("Item", item.writeToNBT(new NBTTagCompound()));
        if (count > 0) t.setLong("Amount", count); // not "Count": an item compound's own stack size is saved there
        if (locked) t.setBoolean("Locked", true);
        if (voidOverflow) t.setBoolean("Void", true);
        if (keep > 0) t.setLong("Keep", keep);
        return t;
    }

    public void read(NBTTagCompound t) {
        item = t.hasKey("Item", 10) ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Item")) : null;
        if (item != null) item.stackSize = 1;
        count = Math.max(0, t.getLong("Amount"));
        if (item == null) count = 0;
        locked = t.getBoolean("Locked");
        voidOverflow = t.getBoolean("Void");
        keep = Math.max(0, t.getLong("Keep"));
    }

    /** Settings (not contents) from another store. */
    void copySettings(BarrelStore o) {
        locked = o.locked;
        voidOverflow = o.voidOverflow;
        keep = o.keep;
    }

    public boolean isEmpty() {
        return item == null || count <= 0;
    }
}
