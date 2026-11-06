package gtnhserver.mmfreepower;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.oredict.OreDictionary;

import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Each player's void list for the Valier MM Mk IV: items the Mk IV deletes instead of handing back when it removes
 * blocks. Every removed block's drops go through MMInventory.givePlayerItems, which {@link #filter} trims
 * ({@link gtnhserver.mmfreepower.mixin.MixinMMInventory}).
 * <p>
 * An entry is {@code modid:name} (any metadata), {@code modid:name:meta}, or {@code ore:<ore dictionary name>}. The
 * list lives in the player's persistent data, so it survives deaths and restarts.
 */
public final class MkIVVoid {

    /** Every kind of cobblestone that the ore dictionary calls cobblestone. */
    public static final String COBBLE = "ore:cobblestone";
    private static final String TAG = "ValierMMVoid";

    private MkIVVoid() {}

    // ---- The list ----

    private static NBTTagCompound data(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        NBTTagCompound data = persisted.getCompoundTag(TAG);
        persisted.setTag(TAG, data);
        return data;
    }

    public static List<String> entries(EntityPlayer player) {
        NBTTagList list = data(player).getTagList("Items", 8);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) out.add(list.getStringTagAt(i));
        return out;
    }

    private static void save(EntityPlayer player, List<String> entries) {
        NBTTagList list = new NBTTagList();
        for (String entry : entries) list.appendTag(new NBTTagString(entry));
        data(player).setTag("Items", list);
    }

    /** Whether voiding is on (it is unless the player paused it). */
    public static boolean isOn(EntityPlayer player) {
        return !data(player).getBoolean("Off");
    }

    public static void setOn(EntityPlayer player, boolean on) {
        data(player).setBoolean("Off", !on);
    }

    /** Adds an entry; false if it was already there. */
    public static boolean add(EntityPlayer player, String entry) {
        List<String> entries = entries(player);
        for (String e : entries) if (e.equalsIgnoreCase(entry)) return false;
        entries.add(entry);
        save(player, entries);
        return true;
    }

    /** Removes an entry; false if it wasn't there. */
    public static boolean remove(EntityPlayer player, String entry) {
        List<String> entries = entries(player);
        boolean removed = entries.removeIf(e -> e.equalsIgnoreCase(entry));
        if (removed) save(player, entries);
        return removed;
    }

    /** Adds the entry, or removes it if it's there. Returns whether it is on the list now. */
    public static boolean toggle(EntityPlayer player, String entry) {
        if (remove(player, entry)) return false;
        add(player, entry);
        return true;
    }

    public static void clear(EntityPlayer player) {
        save(player, new ArrayList<>());
    }

    // ---- Entries ----

    /** The entry for exactly this item: {@code modid:name}, with {@code :meta} for items that have subtypes. */
    public static String entryFor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (id == null) return null;
        String name = id.modId + ":" + id.name;
        return stack.getHasSubtypes() ? name + ":" + stack.getItemDamage() : name;
    }

    /** An entry as typed: null if it isn't one ({@code ore:name}, {@code modid:name} or {@code modid:name:meta}). */
    public static String parse(String text) {
        String t = text.trim();
        if (t.toLowerCase(Locale.ROOT)
            .startsWith("ore:")) return t.length() > 4 ? "ore:" + t.substring(4) : null;
        String[] parts = t.split(":");
        if (parts.length == 3) {
            try {
                Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (parts.length != 2) return null;
        return GameRegistry.findItem(parts[0], parts[1]) != null ? t : null;
    }

    public static boolean matches(String entry, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (entry.regionMatches(true, 0, "ore:", 0, 4)) {
            String ore = entry.substring(4);
            for (int id : OreDictionary.getOreIDs(stack)) if (OreDictionary.getOreName(id)
                .equalsIgnoreCase(ore)) return true;
            return false;
        }
        String[] parts = entry.split(":");
        if (parts.length < 2) return false;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null || item != stack.getItem()) return false;
        return parts.length < 3 || parts[2].equals(Integer.toString(stack.getItemDamage()));
    }

    public static boolean matchesAny(List<String> entries, ItemStack stack) {
        for (String entry : entries) if (matches(entry, stack)) return true;
        return false;
    }

    /** What a player sees for an entry: the entry, plus the item's name when it names one item. */
    public static String describe(String entry) {
        if (entry.regionMatches(true, 0, "ore:", 0, 4)) return entry + " (every item with that ore name)";
        String[] parts = entry.split(":");
        Item item = parts.length >= 2 ? GameRegistry.findItem(parts[0], parts[1]) : null;
        if (item == null) return entry + " (no such item now)";
        try {
            return entry + " ("
                + new ItemStack(item, 1, parts.length > 2 ? Integer.parseInt(parts[2]) : 0).getDisplayName()
                + (parts.length > 2 ? "" : ", any kind")
                + ")";
        } catch (RuntimeException e) {
            return entry;
        }
    }

    // ---- Voiding ----

    /**
     * The drops a manipulator hands back, minus what the player's void list deletes. Only while the player holds a
     * Valier MM Mk IV and voiding is on; otherwise the list comes back unchanged.
     */
    public static List<BigItemStack> filter(EntityPlayer player, List<BigItemStack> items) {
        if (player == null || items == null || items.isEmpty() || !MkIV.isMkIV(player.getHeldItem())) return items;
        if (!isOn(player)) return items;
        List<String> entries = entries(player);
        if (entries.isEmpty()) return items;
        List<BigItemStack> kept = null;
        for (int i = 0; i < items.size(); i++) {
            BigItemStack stack = items.get(i);
            boolean voided = stack != null && matchesAny(entries, stack.getItemStack());
            if (voided && kept == null) kept = new ArrayList<>(items.subList(0, i));
            else if (!voided && kept != null) kept.add(stack);
        }
        return kept == null ? items : kept;
    }

    /**
     * Deletes dropped items lying in loaded chunks (every dimension) that match any of these entries, the
     * manipulator's large drops included. Returns {entities removed, items in them}.
     */
    public static long[] purge(List<String> entries) {
        long entities = 0, items = 0;
        for (WorldServer world : MinecraftServer.getServer().worldServers) {
            for (Object o : new ArrayList<Object>(world.loadedEntityList)) {
                if (!(o instanceof EntityItem)) continue;
                EntityItem drop = (EntityItem) o;
                ItemStack stack = drop.getEntityItem();
                if (drop.isDead || stack == null || !matchesAny(entries, stack)) continue;
                drop.setDead();
                entities++;
                items += stack.stackSize;
            }
        }
        return new long[] { entities, items };
    }
}
