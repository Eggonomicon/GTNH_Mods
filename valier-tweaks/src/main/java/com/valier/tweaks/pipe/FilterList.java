package com.valier.tweaks.pipe;

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
 * One of a face's three filter lists (Allow, Deny, Void; see {@link FaceConfig}), each a whole filter of its own:
 * 18 entries (ghost items; for fluids, anything holding a fluid: buckets, cells, NEI's fluid drops), what an entry
 * matches ({@link FaceConfig.Kind}: the items, anything from their mods, or anything the block already holds), and
 * how ({@link FaceConfig.Damage}, NBT, ore dictionary). A number on an entry is an amount to keep in the block (Allow and
 * Void lists).
 */
public final class FilterList {

    public final ItemStack[] filter = new ItemStack[FaceConfig.FILTER];
    /** How many of each entry to keep in the block (0: no amount). */
    public final int[] count = new int[FaceConfig.FILTER];
    public FaceConfig.Kind kind = FaceConfig.Kind.ITEMS;
    public FaceConfig.Damage damage = FaceConfig.Damage.EXACT;
    public boolean matchNbt;
    public boolean oreDict;

    public boolean hasEntries() {
        for (ItemStack s : filter) if (s != null) return true;
        return false;
    }

    public int entries() {
        int n = 0;
        for (ItemStack s : filter) if (s != null) n++;
        return n;
    }

    /** Whether it names anything at all: entries, the block's own items, or (items) a wear setting on its own. */
    public boolean active() {
        return hasEntries() || kind == FaceConfig.Kind.EXISTING || damage.fuzzy();
    }

    public void clear() {
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            filter[i] = null;
            count[i] = 0;
        }
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    /**
     * Whether the list names this item (Deny and Void lists): an entry it matches, or anything the block holds
     * ({@link FaceConfig.Kind#EXISTING}), and always within the wear setting. An empty list with a wear setting (only
     * damaged, …) names every tool that fits it. {@code own} is the face's block (may be null: then nothing is there).
     */
    public boolean matches(ItemStack s, IInventory own) {
        if (s == null || !damage.fits(s)) return false;
        if (kind == FaceConfig.Kind.EXISTING) return own != null && holds(own, s);
        if (!hasEntries()) return damage.fuzzy();
        return indexOf(s) >= 0;
    }

    /** Whether the list lets this item through (the Allow list): as {@link #matches}, but an empty list lets all in. */
    public boolean allows(ItemStack s, IInventory own) {
        if (s == null) return false;
        if (kind != FaceConfig.Kind.EXISTING && !hasEntries()) return damage.fits(s);
        return matches(s, own);
    }

    /** The entry this item matches (by this list's rules), or -1. */
    public int indexOf(ItemStack s) {
        for (int i = 0; i < FaceConfig.FILTER; i++) if (filter[i] != null && same(filter[i], s)) return i;
        return -1;
    }

    /** Whether s matches entry f: the same item (by the damage, NBT and ore dictionary settings), or the same mod. */
    public boolean same(ItemStack f, ItemStack s) {
        if (f == null || s == null) return false;
        if (kind == FaceConfig.Kind.MODS) return modOf(f) != null && modOf(f).equals(modOf(s));
        return sameItem(f, s);
    }

    private boolean sameItem(ItemStack f, ItemStack s) {
        if (f.getItem() == s.getItem() && (damage != FaceConfig.Damage.EXACT || f.getItemDamage() == s.getItemDamage())
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

    /** Whether the inventory holds an item like s (by this list's item rules, as if s were listed). */
    boolean holds(IInventory inv, ItemStack s) {
        if (inv instanceof com.valier.tweaks.storage.ManyStorage && damage == FaceConfig.Damage.EXACT && !oreDict) {
            return !((com.valier.tweaks.storage.ManyStorage) inv).manyPeek(s, !matchNbt)
                .isEmpty();
        }
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack in = inv.getStackInSlot(i);
            if (in != null && sameItem(in, s)) return true;
        }
        return false;
    }

    /** How many of this item to keep in the block (0: no amount), from the entry it matches (item lists only). */
    public int stockOf(ItemStack s) {
        if (kind != FaceConfig.Kind.ITEMS) return 0;
        int i = indexOf(s);
        return i < 0 ? 0 : count[i];
    }

    /** A Request face: how many of entry i to keep stocked (its amount, else a stack). */
    public int requestOf(int i) {
        return count[i] > 0 ? count[i] : (filter[i] == null ? 64 : filter[i].getMaxStackSize());
    }

    // ---- fluids -----------------------------------------------------------------------------------------------------

    /** Whether the list names this fluid: an entry holding it, or (EXISTING) the tank already holds it. */
    public boolean matches(FluidStack fluid, List<FluidStack> own) {
        if (fluid == null) return false;
        if (kind == FaceConfig.Kind.EXISTING) {
            if (own != null) for (FluidStack f : own) if (f.getFluid() == fluid.getFluid()) return true;
            return false;
        }
        for (ItemStack s : filter) {
            FluidStack f = Fluids.of(s);
            if (f != null && f.getFluid() == fluid.getFluid()) return true;
        }
        return false;
    }

    /** Whether the list lets this fluid through: as {@link #matches}, but a list naming no fluid lets all in. */
    public boolean allows(FluidStack fluid, List<FluidStack> own) {
        if (fluid == null) return false;
        if (kind != FaceConfig.Kind.EXISTING && !namesFluids()) return true;
        return matches(fluid, own);
    }

    public boolean namesFluids() {
        for (ItemStack s : filter) if (Fluids.of(s) != null) return true;
        return false;
    }

    /** mB of this fluid to keep in the tank (0: no amount). */
    public int stockOf(FluidStack fluid) {
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            FluidStack f = Fluids.of(filter[i]);
            if (f != null && fluid != null && f.getFluid() == fluid.getFluid()) return count[i];
        }
        return 0;
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    /** Its settings into t (keys shared with the 2.0 format, so an old face reads as its Allow or Deny list). */
    public void write(NBTTagCompound t) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            if (filter[i] == null && count[i] == 0) continue;
            NBTTagCompound s = filter[i] == null ? new NBTTagCompound() : filter[i].writeToNBT(new NBTTagCompound());
            s.setByte("Slot", (byte) i);
            if (count[i] > 0) s.setInteger("Keep", count[i]); // not "Count": the item's own stack size is saved under that
            list.appendTag(s);
        }
        if (list.tagCount() > 0) t.setTag("Filter", list);
        if (kind != FaceConfig.Kind.ITEMS) t.setByte("Kind", (byte) kind.ordinal());
        if (damage != FaceConfig.Damage.EXACT) t.setByte("Damage", (byte) damage.ordinal());
        if (matchNbt) t.setBoolean("MatchNbt", true);
        if (oreDict) t.setBoolean("OreDict", true);
    }

    public void read(NBTTagCompound t) {
        clear();
        NBTTagList list = t.getTagList("Filter", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound s = list.getCompoundTagAt(i);
            int slot = s.getByte("Slot");
            if (slot < 0 || slot >= FaceConfig.FILTER) continue;
            filter[slot] = s.hasKey("id") ? ItemStack.loadItemStackFromNBT(s) : null;
            if (filter[slot] != null) filter[slot].stackSize = 1;
            count[slot] = Math.max(0, s.getInteger("Keep"));
        }
        FaceConfig.Kind[] kinds = FaceConfig.Kind.values();
        kind = kinds[Math.max(0, Math.min(kinds.length - 1, t.getByte("Kind")))];
        FaceConfig.Damage[] damages = FaceConfig.Damage.values();
        damage = damages[Math.max(0, Math.min(damages.length - 1, t.getByte("Damage")))];
        matchNbt = t.getBoolean("MatchNbt");
        oreDict = t.getBoolean("OreDict");
    }

    public void copyFrom(FilterList o) {
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            filter[i] = o.filter[i] == null ? null : o.filter[i].copy();
            count[i] = o.count[i];
        }
        kind = o.kind;
        damage = o.damage;
        matchNbt = o.matchNbt;
        oreDict = o.oreDict;
    }
}
