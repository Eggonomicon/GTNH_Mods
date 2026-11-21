package com.valier.tweaks.storage;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * What a Satchel (items) or Flask (fluids) holds, read from its item's NBT ({@value #TAG}) and written back on every
 * change. Each slot holds one item (up to the tier's amount, counted as an int, never as a stack size) or one fluid,
 * with its own settings: keep (what automation, shift-clicks and placing leave), ore dictionary (a picked-up item
 * sharing an ore name is stored as this one), void overflow (picked-up matches are destroyed when it is full) and lock
 * (it keeps its item at 0). The whole: auto-pickup, and pick up new items into empty slots.
 * <p>
 * One is kept per item stack (weakly), and read again only when the stack's NBT was replaced by something else.
 */
public final class SatchelInventory {

    public static final String TAG = "ValierSatchel";
    private static final Map<ItemStack, SatchelInventory> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Its item, weakly: the cache's value must not hold its own key, or no entry would ever go. */
    private final WeakReference<ItemStack> ref;
    /** Each slot's compound as saved, so a changed amount rewrites one number instead of the whole list. */
    private final NBTTagCompound[] slotTags;
    public final boolean fluid, creative;
    public final int tier, size, capacity;
    public final ItemStack[] items;
    public final FluidStack[] fluids;
    public final int[] counts, keep;
    public final boolean[] ore, voids, lock;
    public int selected;
    public boolean pickup = true, pickNew;
    public long id;
    private NBTTagCompound read;
    /**
     * Which slots hold each item, the ore-dictionary slots with their items' ore ids, and the first empty slot: built
     * when first needed after what the slots hold changed (every such change saves, and saving drops it), so a pickup
     * is one map lookup instead of a look at every slot (a LuV Satchel has 54).
     */
    private IdentityHashMap<Item, int[]> byItem;
    private int[] oreSlots;
    private int[][] slotOres;
    private int firstEmpty;

    private SatchelInventory(ItemStack stack) {
        this.ref = new WeakReference<>(stack);
        this.fluid = stack.getItem() instanceof ItemSatchel && ((ItemSatchel) stack.getItem()).fluid;
        this.tier = SatchelTier.clamp(stack.getItemDamage());
        this.creative = tier == SatchelTier.CREATIVE;
        this.size = SatchelTier.slots(tier, fluid);
        this.capacity = SatchelTier.capacity(tier, fluid);
        items = new ItemStack[size];
        fluids = new FluidStack[size];
        counts = new int[size];
        keep = new int[size];
        ore = new boolean[size];
        voids = new boolean[size];
        lock = new boolean[size];
        slotTags = new NBTTagCompound[size];
        load();
    }

    /** The inventory of this Satchel or Flask item (null for anything else). */
    public static SatchelInventory of(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemSatchel)) return null;
        SatchelInventory c = CACHE.get(stack);
        NBTTagCompound now = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(TAG) : null;
        if (c != null && c.read == now && c.tier == SatchelTier.clamp(stack.getItemDamage())) return c;
        c = new SatchelInventory(stack);
        CACHE.put(stack, c);
        return c;
    }

    private void load() {
        ItemStack stack = ref.get();
        NBTTagCompound t = stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(TAG) : null;
        read = t;
        byItem = null;
        for (int i = 0; i < size; i++) voids[i] = !fluid; // items: void overflow on by default
        if (t == null) return;
        id = t.getLong("Id");
        selected = Math.max(0, Math.min(size - 1, t.getByte("Sel")));
        pickup = !t.getBoolean("NoPickup");
        pickNew = t.getBoolean("PickNew");
        NBTTagList list = t.getTagList("Slots", 10);
        for (int k = 0; k < list.tagCount(); k++) {
            NBTTagCompound s = list.getCompoundTagAt(k);
            int i = s.getByte("S") & 255;
            if (i >= size) continue; // a smaller tier can't happen (upgrades only grow), but stay safe
            if (fluid) {
                fluids[i] = s.hasKey("Fluid", 10) ? FluidStack.loadFluidStackFromNBT(s.getCompoundTag("Fluid")) : null;
                if (fluids[i] != null) fluids[i].amount = Math.max(0, s.getInteger("N"));
            } else {
                items[i] = s.hasKey("Item", 10) ? ItemStack.loadItemStackFromNBT(s.getCompoundTag("Item")) : null;
                if (items[i] != null) items[i].stackSize = 1;
                counts[i] = items[i] == null ? 0 : Math.max(0, s.getInteger("N"));
            }
            keep[i] = Math.max(0, s.getInteger("Keep"));
            ore[i] = s.getBoolean("Ore");
            voids[i] = s.hasKey("Void") ? s.getBoolean("Void") : !fluid;
            lock[i] = s.getBoolean("Lock");
            slotTags[i] = s;
        }
    }

    /** Writes it back into its item (after any change). */
    public void save() {
        ItemStack stack = ref.get();
        byItem = null; // what the slots hold may have changed
        if (stack == null) return; // its item is gone: nobody can see this any more
        Arrays.fill(slotTags, null);
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound t = new NBTTagCompound();
        if (id == 0) id = System.nanoTime() ^ ((long) System.identityHashCode(this) << 32);
        t.setLong("Id", id);
        if (selected > 0) t.setByte("Sel", (byte) selected);
        if (!pickup) t.setBoolean("NoPickup", true);
        if (pickNew) t.setBoolean("PickNew", true);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < size; i++) {
            boolean has = fluid ? fluids[i] != null : items[i] != null;
            boolean defaults = keep[i] == 0 && !ore[i] && voids[i] == !fluid && !lock[i];
            if (!has && defaults) continue;
            NBTTagCompound s = new NBTTagCompound();
            slotTags[i] = s;
            s.setByte("S", (byte) i);
            if (fluid && fluids[i] != null) {
                FluidStack f = fluids[i].copy();
                f.amount = 1;
                s.setTag("Fluid", f.writeToNBT(new NBTTagCompound()));
                s.setInteger("N", fluids[i].amount);
            } else if (!fluid && items[i] != null) {
                s.setTag("Item", items[i].writeToNBT(new NBTTagCompound()));
                s.setInteger("N", counts[i]); // not "Count": the item compound's own stack size is saved under that
            }
            if (keep[i] > 0) s.setInteger("Keep", keep[i]);
            if (ore[i]) s.setBoolean("Ore", true);
            if (voids[i] != !fluid) s.setBoolean("Void", voids[i]);
            if (lock[i]) s.setBoolean("Lock", true);
            list.appendTag(s);
        }
        t.setTag("Slots", list);
        root.setTag(TAG, t);
        stack.setTagCompound(root);
        read = t;
    }

    /** Only slot i's amount changed: rewrites that one number in its item's NBT (or all of it, if that can't be). */
    private void saveCount(int i, int n) {
        ItemStack stack = ref.get();
        NBTTagCompound now = stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(TAG) : null;
        if (slotTags[i] == null || now == null || now != read) {
            save();
            return;
        }
        slotTags[i].setInteger("N", n);
    }

    // ---- items -------------------------------------------------------------------------------------------------------

    static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** Builds the lookups ({@link #byItem}) if a change dropped them. */
    private void index() {
        if (byItem != null) return;
        IdentityHashMap<Item, int[]> by = new IdentityHashMap<>();
        int ores = 0;
        firstEmpty = -1;
        for (int i = 0; i < size; i++) {
            if (items[i] == null) {
                if (firstEmpty < 0 && !lock[i]) firstEmpty = i;
                continue;
            }
            int[] had = by.get(items[i].getItem());
            int[] now = had == null ? new int[1] : Arrays.copyOf(had, had.length + 1); // slots in order: the first wins
            now[now.length - 1] = i;
            by.put(items[i].getItem(), now);
            if (ore[i]) ores++;
        }
        oreSlots = new int[ores];
        slotOres = new int[ores][];
        int k = 0;
        for (int i = 0; i < size && k < ores; i++) {
            if (items[i] == null || !ore[i]) continue;
            oreSlots[k] = i;
            slotOres[k++] = OreDictionary.getOreIDs(items[i]);
        }
        byItem = by;
    }

    /** The slot holding s (or, with its ore dictionary setting on, an item sharing an ore name with s), else -1. */
    public int find(ItemStack s) {
        if (fluid || s == null || s.getItem() == null) return -1;
        index();
        int[] at = byItem.get(s.getItem());
        if (at != null) for (int i : at) if (same(items[i], s)) return i;
        if (oreSlots.length == 0) return -1;
        int[] ids = OreDictionary.getOreIDs(s);
        if (ids.length == 0) return -1;
        for (int k = 0; k < oreSlots.length; k++) for (int a : slotOres[k]) for (int b : ids) if (a == b) return oreSlots[k];
        return -1;
    }

    private int empty() {
        index();
        return firstEmpty;
    }

    /**
     * Puts up to n of s in: into its slot (pickups: only one already holding it, unless new items may go into empty
     * slots), else an empty one. Returns how many it took (what overflows a voiding slot counts: it is destroyed).
     */
    public int insert(ItemStack s, int n, boolean pickupOnly, boolean simulate) {
        if (fluid || s == null || n <= 0) return 0;
        int i = find(s);
        if (i < 0 && (!pickupOnly || pickNew)) i = empty();
        if (i < 0) return 0;
        long room = creative ? Long.MAX_VALUE : (long) capacity - counts[i];
        int take = (int) Math.min(n, Math.max(0, room));
        int took = voids[i] && pickupOnly ? n : take; // void overflow: picked-up items only, never what is put in
        if (simulate || took <= 0 || take <= 0) return took;
        boolean had = items[i] != null;
        if (!had) {
            items[i] = s.copy();
            items[i].stackSize = 1;
        }
        if (!creative) counts[i] += take;
        else counts[i] = Integer.MAX_VALUE;
        if (had) saveCount(i, counts[i]);
        else save();
        return took;
    }

    /** How many slot i can give (automation leaves its keep amount). */
    public int available(int i, boolean automation) {
        if (fluid || i < 0 || i >= size || items[i] == null) return 0;
        if (creative) return Integer.MAX_VALUE;
        return Math.max(0, counts[i] - (automation ? keep[i] : 0));
    }

    /** Takes up to n from slot i; null if none can be. */
    public ItemStack extract(int i, int n, boolean automation, boolean simulate) {
        int k = Math.min(n, available(i, automation));
        if (k <= 0) return null;
        ItemStack out = items[i].copy();
        out.stackSize = k;
        if (simulate || creative) return out;
        counts[i] -= k;
        if (counts[i] <= 0 && !lock[i]) {
            counts[i] = 0;
            items[i] = null;
            save();
        } else saveCount(i, counts[i]);
        return out;
    }

    // ---- fluids ------------------------------------------------------------------------------------------------------

    public int findFluid(FluidStack f) {
        if (!fluid || f == null) return -1;
        for (int i = 0; i < size; i++) if (fluids[i] != null && fluids[i].isFluidEqual(f)) return i;
        return -1;
    }

    /** Puts up to f.amount in (its tank, else an empty one); returns mB taken (a voiding tank takes it all). */
    public int fill(FluidStack f, boolean doFill) {
        if (!fluid || f == null || f.amount <= 0) return 0;
        int i = findFluid(f);
        if (i < 0) for (int k = 0; k < size && i < 0; k++) if (fluids[k] == null && !lock[k]) i = k;
        if (i < 0) return 0;
        int have = fluids[i] == null ? 0 : fluids[i].amount;
        long room = creative ? Long.MAX_VALUE : (long) capacity - have;
        int take = (int) Math.min(f.amount, Math.max(0, room));
        int took = voids[i] ? f.amount : take;
        if (!doFill || took <= 0 || take <= 0) return took;
        boolean had = fluids[i] != null;
        if (!had) {
            fluids[i] = f.copy();
            fluids[i].amount = 0;
        }
        fluids[i].amount = creative ? Integer.MAX_VALUE : have + take;
        if (had) saveCount(i, fluids[i].amount);
        else save();
        return took;
    }

    public int availableFluid(int i, boolean automation) {
        if (!fluid || i < 0 || i >= size || fluids[i] == null) return 0;
        if (creative) return Integer.MAX_VALUE;
        return Math.max(0, fluids[i].amount - (automation ? keep[i] : 0));
    }

    /** Drains up to mB from tank i; null if none can be. */
    public FluidStack drain(int i, int mB, boolean automation, boolean doDrain) {
        int k = Math.min(mB, availableFluid(i, automation));
        if (k <= 0) return null;
        FluidStack out = fluids[i].copy();
        out.amount = k;
        if (!doDrain || creative) return out;
        fluids[i].amount -= k;
        if (fluids[i].amount <= 0 && !lock[i]) {
            fluids[i] = null;
            save();
        } else {
            if (fluids[i].amount < 0) fluids[i].amount = 0;
            saveCount(i, fluids[i].amount);
        }
        return out;
    }

    public boolean isEmpty(int i) {
        return fluid ? fluids[i] == null || fluids[i].amount <= 0 : items[i] == null || counts[i] <= 0;
    }
}
