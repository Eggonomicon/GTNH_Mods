package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;


/**
 * The settings of one pipe face touching a block, and the little it holds (what it couldn't hand on or give back: a
 * machine or barrel that took less than it said, or gave more). Saved with the pipe.
 * <p>
 * Its filter is built in (no filter items): three lists at once, each a whole filter ({@link FilterList}: item list,
 * mod list or the block's existing items; damage, NBT, ore dictionary; amounts):
 * <ul>
 * <li><b>Deny</b> wins over everything: what it names is never moved or voided by this face.</li>
 * <li><b>Void</b>: a pulling face destroys what it names in its block (over the entry's amount, if one is set), within
 * its per-transfer amount; a pushing face destroys what it names that is sent to it and doesn't fit.</li>
 * <li><b>Allow</b>: only what it names may pass (empty: everything). Its amounts are how many to keep in this block:
 * pulling and providing faces only give what is over it; pushing faces fill up to it; Request faces keep it stocked (a
 * stack, or 1,000 mB, when it is 0).</li>
 * </ul>
 * A new face has empty lists, so it needs no setup. A 2.0 face's one list becomes its Allow list (whitelist) or its
 * Deny list (blacklist).
 */
public final class FaceConfig {

    public static final int FILTER = 18;
    public static final int MAX_TAG = 32;
    /** Redstone: always, only with a signal, only without one. */
    public static final int ALWAYS = 0, WITH_SIGNAL = 1, WITHOUT_SIGNAL = 2;

    public enum Kind {

        ITEMS("Item list", "The items in the list"),
        MODS("Mod list", "Anything from the mods of the items in the list"),
        EXISTING("Existing items", "Anything the block on this face already holds");

        public final String words, explain;

        Kind(String words, String explain) {
            this.words = words;
            this.explain = explain;
        }
    }

    public enum Damage {

        EXACT("Match damage", "The damage value (meta) must be the same"),
        ANY("Ignore damage", "Any damage value (meta)"),
        UNDAMAGED("Only undamaged", "Tools and armour that were never used"),
        DAMAGED("Only damaged", "Tools and armour used at least once"),
        D25("Damaged 25%+", "Tools and armour at least a quarter worn"),
        D50("Damaged 50%+", "Tools and armour at least half worn"),
        D75("Damaged 75%+", "Tools and armour at least three quarters worn");

        public final String words, explain;

        Damage(String words, String explain) {
            this.words = words;
            this.explain = explain;
        }

        boolean fuzzy() {
            return ordinal() >= UNDAMAGED.ordinal();
        }

        /** For the fuzzy modes: whether this item's wear fits (only tools and armour can fit a damaged mode). */
        boolean fits(ItemStack s) {
            if (!fuzzy()) return true;
            boolean tool = s.isItemStackDamageable() && s.getMaxDamage() > 0;
            if (this == UNDAMAGED) return !tool || s.getItemDamage() == 0;
            if (!tool) return false;
            double worn = (double) s.getItemDamage() / s.getMaxDamage();
            switch (this) {
                case DAMAGED:
                    return s.getItemDamage() > 0;
                case D25:
                    return worn >= 0.25;
                case D50:
                    return worn >= 0.5;
                default:
                    return worn >= 0.75;
            }
        }
    }

    /** How a pulling face shares what it moves between destinations of the same priority. */
    public enum Spread {

        IN_ORDER("In order", "The highest priority fills first; equal priorities fill one after another, always in the same order"),
        ROUND_ROBIN("Round robin", "The highest priority fills first; equal priorities take turns"),
        EVEN("Even split", "The highest priority fills first; each transfer is shared equally between equal priorities");

        public final String words, explain;

        Spread(String words, String explain) {
            this.words = words;
            this.explain = explain;
        }
    }

    /** Which list: the face's three filters, all at once. */
    public static final int ALLOW = 0, DENY = 1, VOID = 2;
    public static final String[] LIST_NAMES = { "Allow", "Deny", "Void" };

    public FaceMode mode = FaceMode.PROVIDE;
    /** Items (or mB) one transfer moves, up to the tier's most; 0: the tier's most. */
    public int amount;
    /** Higher first, for Push and Request faces and overflow Provide faces. */
    public int priority;
    /** Ticks between this face's transfers; 0 or less than the tier's interval: the tier's (it can only be slower). */
    public int ticks;
    /** What may pass (empty: everything), what never does (wins over both others), what is destroyed. */
    public final FilterList allow = new FilterList(), deny = new FilterList(), voids = new FilterList();
    public final FilterList[] lists = { allow, deny, voids };
    public Spread spread = Spread.ROUND_ROBIN;
    public int redstone = ALWAYS;
    /** Faces only trade with faces of the same tag ("" with ""): channels, and on Wireless pipes, links. */
    public String tag = "";
    /** Provide faces: also take what Extract faces can't put anywhere else (a barrel as the network's buffer). */
    public boolean acceptOverflow;

    /** What it holds back (see the class comment), handed on first next time; drops when the pipe breaks. */
    public final List<ItemStack> heldItems = new ArrayList<>();
    public FluidStack heldFluid;

    /** Not saved: whose turn it is (round robin), and what it moved lately (WAILA). */
    int turn;
    public final Flow flow = new Flow();

    public FilterList list(int i) {
        return lists[Math.max(0, Math.min(lists.length - 1, i))];
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    /**
     * Whether an item may pass this face: not denied, and allowed. {@code own} is the face's block (for the "existing
     * items" lists; may be null when not known, then nothing counts as already there).
     */
    public boolean passes(ItemStack s, IInventory own) {
        return s != null && !deny.matches(s, own) && allow.allows(s, own);
    }

    /** Whether this face destroys the item (its Void list names it and its Deny list doesn't). */
    public boolean voids(ItemStack s, IInventory own) {
        return s != null && voids.active() && !deny.matches(s, own) && voids.matches(s, own);
    }

    /** How many of this item to keep in the block (0: no amount), from the Allow list. */
    public int stockOf(ItemStack s) {
        return allow.stockOf(s);
    }

    // ---- fluids -----------------------------------------------------------------------------------------------------

    /** Whether a fluid may pass: not denied, and allowed (entries holding a fluid name it). */
    public boolean passes(FluidStack fluid, List<FluidStack> own) {
        return fluid != null && !deny.matches(fluid, own) && allow.allows(fluid, own);
    }

    public boolean voids(FluidStack fluid, List<FluidStack> own) {
        return fluid != null && !deny.matches(fluid, own) && voids.matches(fluid, own);
    }

    /** mB of this fluid to keep in the tank (0: no amount), from the Allow list. */
    public int stockOf(FluidStack fluid) {
        return allow.stockOf(fluid);
    }

    // ---- other ----------------------------------------------------------------------------------------------------

    /** Ticks between this face's transfers: its own setting, never faster than the tier allows. */
    public int interval(int tier) {
        return Math.max(PipeTier.interval(tier), ticks);
    }

    /** The amount one transfer moves: its own, at most the tier's most. */
    public int perTransfer(PipeKind kind, int tier) {
        int max = PipeTier.max(kind, tier);
        return amount <= 0 ? max : Math.min(amount, max);
    }

    public static String cleanTag(String t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : t.trim()
            .toCharArray()) {
            if (c >= 32 && c != 127 && c != '§') sb.append(c);
            if (sb.length() >= MAX_TAG) break;
        }
        return sb.toString();
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("Mode", (byte) mode.ordinal());
        if (amount > 0) t.setInteger("Amount", amount);
        if (priority != 0) t.setInteger("Priority", priority);
        if (ticks > 0) t.setInteger("Ticks", ticks);
        allow.write(t); // at the top level, as 2.0 saved its one list: a 2.0 whitelist reads as the Allow list
        if (deny.active() || deny.hasEntries()) {
            NBTTagCompound d = new NBTTagCompound();
            deny.write(d);
            t.setTag("Deny", d);
        }
        if (voids.active() || voids.hasEntries()) {
            NBTTagCompound v = new NBTTagCompound();
            voids.write(v);
            t.setTag("Void", v);
        }
        if (spread != Spread.ROUND_ROBIN) t.setByte("Spread", (byte) spread.ordinal());
        if (redstone != ALWAYS) t.setByte("Redstone", (byte) redstone);
        if (!tag.isEmpty()) t.setString("Tag", tag);
        if (acceptOverflow) t.setBoolean("Overflow", true);
        NBTTagList held = new NBTTagList();
        for (ItemStack s : heldItems) if (s != null) held.appendTag(s.writeToNBT(new NBTTagCompound()));
        if (held.tagCount() > 0) t.setTag("Held", held);
        if (heldFluid != null && heldFluid.amount > 0) t.setTag("HeldFluid", heldFluid.writeToNBT(new NBTTagCompound()));
        return t;
    }

    public static FaceConfig read(NBTTagCompound t) {
        FaceConfig f = new FaceConfig();
        f.mode = FaceMode.of(t.getByte("Mode"));
        f.amount = Math.max(0, t.getInteger("Amount"));
        f.priority = t.getInteger("Priority");
        f.ticks = Math.max(0, t.getInteger("Ticks"));
        f.allow.read(t);
        if (t.getBoolean("Blacklist")) {
            // 2.0's blacklist: its list becomes the Deny list; the wear setting stays on both (2.0 checked it first).
            f.deny.copyFrom(f.allow);
            f.allow.clear();
            f.allow.kind = Kind.ITEMS;
            f.allow.matchNbt = false;
            f.allow.oreDict = false;
        }
        if (t.hasKey("Deny", 10)) f.deny.read(t.getCompoundTag("Deny"));
        if (t.hasKey("Void", 10)) f.voids.read(t.getCompoundTag("Void"));
        Spread[] spreads = Spread.values();
        f.spread = t.hasKey("Spread") ? spreads[Math.max(0, Math.min(spreads.length - 1, t.getByte("Spread")))] : Spread.ROUND_ROBIN;
        f.redstone = Math.max(0, Math.min(2, t.getByte("Redstone")));
        f.tag = cleanTag(t.getString("Tag"));
        f.acceptOverflow = t.getBoolean("Overflow");
        NBTTagList held = t.getTagList("Held", 10);
        for (int i = 0; i < held.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(held.getCompoundTagAt(i));
            if (s != null) f.heldItems.add(s);
        }
        f.heldFluid = t.hasKey("HeldFluid", 10) ? FluidStack.loadFluidStackFromNBT(t.getCompoundTag("HeldFluid")) : null;
        return f;
    }

    /** Everything but what it holds and its live state: for the Pipe Configurator. */
    public NBTTagCompound settings() {
        NBTTagCompound t = write();
        t.removeTag("Held");
        t.removeTag("HeldFluid");
        return t;
    }

    /** Takes over another face's settings (not what this one holds). */
    public void applySettings(NBTTagCompound t) {
        FaceConfig o = read(t);
        mode = o.mode;
        amount = o.amount;
        priority = o.priority;
        ticks = o.ticks;
        for (int i = 0; i < lists.length; i++) lists[i].copyFrom(o.lists[i]);
        spread = o.spread;
        redstone = o.redstone;
        tag = o.tag;
        acceptOverflow = o.acceptOverflow;
    }
}
