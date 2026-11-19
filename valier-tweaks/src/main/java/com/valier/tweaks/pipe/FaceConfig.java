package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The settings of one pipe face touching a block, and the little it holds (what it couldn't hand on or give back: a
 * machine or barrel that took less than it said, or gave more). Saved with the pipe.
 * <p>
 * Its filter is built in (no filter items), with what EnderIO's filters can do:
 * <ul>
 * <li><b>Kind:</b> {@link Kind#ITEMS} (the listed items), {@link Kind#MODS} (anything from the listed items' mods),
 * {@link Kind#EXISTING} (anything the block on this face already holds).</li>
 * <li>Whitelist or blacklist; damage: match exactly, ignore, or only undamaged / damaged / damaged by 25, 50 or 75% or
 * more (these work with an empty list too); NBT matched or ignored; ore dictionary.</li>
 * <li><b>Amounts</b> (item lists): a number on a filter slot is how many of that item to keep in this block. Extract
 * and Provide faces only give what is over it; Insert faces fill up to it; Request faces keep it stocked (a stack, or
 * 1,000 mB, when it is 0).</li>
 * </ul>
 * An empty item or mod list lets everything through, so a new face needs no setup.
 */
public final class FaceConfig {

    public static final int FILTER = 18;
    public static final int MAX_TAG = 32;
    /** Redstone: always, only with a signal, only without one. */
    public static final int ALWAYS = 0, WITH_SIGNAL = 1, WITHOUT_SIGNAL = 2;

    public enum Kind {

        ITEMS("Item list", "Only the items in the list (or all but them)"),
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

    public FaceMode mode = FaceMode.PROVIDE;
    /** Items (or mB) one transfer moves, up to the tier's most; 0: the tier's most. */
    public int amount;
    /** Higher first, for Push and Request faces and overflow Provide faces. */
    public int priority;
    /** Ticks between this face's transfers; 0 or less than the tier's interval: the tier's (it can only be slower). */
    public int ticks;
    /** Filter items (ghost copies, stack size 1); null slots are empty. */
    public final ItemStack[] filter = new ItemStack[FILTER];
    /** How many of each listed item to keep in this block (0: no amount; see the class comment). */
    public final int[] count = new int[FILTER];
    public Kind kind = Kind.ITEMS;
    public boolean blacklist;
    public Damage damage = Damage.EXACT;
    public boolean matchNbt;
    public boolean oreDict;
    public int redstone = ALWAYS;
    /** Faces only trade with faces of the same tag ("" with ""): channels, and on Wireless pipes, links. */
    public String tag = "";
    /** Provide faces: also take what Extract faces can't put anywhere else (a barrel as the network's buffer). */
    public boolean acceptOverflow;

    /** What it holds back (see the class comment), handed on first next time; drops when the pipe breaks. */
    public final List<ItemStack> heldItems = new ArrayList<>();
    public FluidStack heldFluid;

    public boolean hasEntries() {
        for (ItemStack s : filter) if (s != null) return true;
        return false;
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    /**
     * Whether an item passes this face's filter. {@code own} is the face's block (for {@link Kind#EXISTING}; may be
     * null when not known, then nothing counts as already there).
     */
    public boolean passes(ItemStack s, IInventory own) {
        if (s == null || !damage.fits(s)) return false;
        boolean match;
        if (kind == Kind.EXISTING) match = own != null && holds(own, s);
        else {
            if (!hasEntries()) return true;
            match = indexOf(s) >= 0;
        }
        return blacklist != match;
    }

    /** The filter slot this item matches (by this face's rules), or -1. */
    public int indexOf(ItemStack s) {
        for (int i = 0; i < FILTER; i++) if (filter[i] != null && same(filter[i], s)) return i;
        return -1;
    }

    /** Whether s matches entry f: the same item (by the damage, NBT and ore dictionary settings), or the same mod. */
    public boolean same(ItemStack f, ItemStack s) {
        if (f == null || s == null) return false;
        if (kind == Kind.MODS) return modOf(f) != null && modOf(f).equals(modOf(s));
        if (f.getItem() == s.getItem() && (damage != Damage.EXACT || f.getItemDamage() == s.getItemDamage())
            && (!matchNbt || ItemStack.areItemStackTagsEqual(f, s))) return true;
        if (!oreDict) return false;
        int[] a = OreDictionary.getOreIDs(f), b = OreDictionary.getOreIDs(s);
        for (int x : a) for (int y : b) if (x == y) return true;
        return false;
    }

    static String modOf(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(s.getItem());
        if (id != null) return id.modId;
        Object name = Item.itemRegistry.getNameForObject(s.getItem());
        String n = name == null ? null : name.toString();
        return n == null ? null : n.contains(":") ? n.substring(0, n.indexOf(':')) : "minecraft";
    }

    /** Whether the inventory holds an item like s (by this face's matching rules, as if s were listed). */
    boolean holds(IInventory inv, ItemStack s) {
        Kind k = kind;
        kind = Kind.ITEMS;
        try {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack in = inv.getStackInSlot(i);
                if (in != null && same(in, s)) return true;
            }
            return false;
        } finally {
            kind = k;
        }
    }

    /** How many of this item to keep in the block (0: no amount), from the slot it matches (item lists only). */
    public int stockOf(ItemStack s) {
        if (kind != Kind.ITEMS) return 0;
        int i = indexOf(s);
        return i < 0 ? 0 : count[i];
    }

    /** A Request face: how many of slot i to keep stocked (its amount, else a stack). */
    public int requestOf(int i) {
        return count[i] > 0 ? count[i] : (filter[i] == null ? 64 : filter[i].getMaxStackSize());
    }

    // ---- fluids ---------------------------------------------------------------------------------------------------

    /** Whether a fluid passes: filter items holding a fluid (buckets, cells, NEI's fluid drops) name it. */
    public boolean passes(FluidStack fluid, List<FluidStack> own) {
        if (fluid == null) return false;
        boolean match;
        if (kind == Kind.EXISTING) {
            match = false;
            if (own != null) for (FluidStack f : own) if (f.getFluid() == fluid.getFluid()) match = true;
        } else {
            boolean any = false;
            match = false;
            for (ItemStack s : filter) {
                FluidStack f = Fluids.of(s);
                if (f == null) continue;
                any = true;
                if (f.getFluid() == fluid.getFluid()) match = true;
            }
            if (!any) return true;
        }
        return blacklist != match;
    }

    /** mB of this fluid to keep in the tank (0: no amount). */
    public int stockOf(FluidStack fluid) {
        for (int i = 0; i < FILTER; i++) {
            FluidStack f = Fluids.of(filter[i]);
            if (f != null && fluid != null && f.getFluid() == fluid.getFluid()) return count[i];
        }
        return 0;
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
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < FILTER; i++) {
            if (filter[i] == null && count[i] == 0) continue;
            NBTTagCompound s = filter[i] == null ? new NBTTagCompound() : filter[i].writeToNBT(new NBTTagCompound());
            s.setByte("Slot", (byte) i);
            if (count[i] > 0) s.setInteger("Keep", count[i]); // not "Count": the item's own stack size is saved under that
            list.appendTag(s);
        }
        if (list.tagCount() > 0) t.setTag("Filter", list);
        if (kind != Kind.ITEMS) t.setByte("Kind", (byte) kind.ordinal());
        if (blacklist) t.setBoolean("Blacklist", true);
        if (damage != Damage.EXACT) t.setByte("Damage", (byte) damage.ordinal());
        if (matchNbt) t.setBoolean("MatchNbt", true);
        if (oreDict) t.setBoolean("OreDict", true);
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
        NBTTagList list = t.getTagList("Filter", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound s = list.getCompoundTagAt(i);
            int slot = s.getByte("Slot");
            if (slot < 0 || slot >= FILTER) continue;
            f.filter[slot] = s.hasKey("id") ? ItemStack.loadItemStackFromNBT(s) : null;
            if (f.filter[slot] != null) f.filter[slot].stackSize = 1;
            f.count[slot] = Math.max(0, s.getInteger("Keep"));
        }
        Kind[] kinds = Kind.values();
        f.kind = kinds[Math.max(0, Math.min(kinds.length - 1, t.getByte("Kind")))];
        f.blacklist = t.getBoolean("Blacklist");
        Damage[] damages = Damage.values();
        f.damage = damages[Math.max(0, Math.min(damages.length - 1, t.getByte("Damage")))];
        f.matchNbt = t.getBoolean("MatchNbt");
        f.oreDict = t.getBoolean("OreDict");
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
}
