package gtnhserver.mmfreepower;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.oredict.OreDictionary;

import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;

/**
 * The Valier MM Mk IV: a Matter Manipulator MK III marked with a tag, which places blocks without machines for free.
 * Server side only: the name, lore and glint are plain item NBT that any client shows.
 */
public final class MkIV {

    /** Root NBT tag marking a manipulator as a Mk IV. */
    public static final String TAG = "ValierMk4";
    public static final String NAME = "§r§5Valier MM Mk IV";
    private static final String[] LORE = { "§7Places blocks for free", "§8Machines, chests and resource "
        + "blocks still cost items" };
    /**
     * Root tags the manipulator itself doesn't keep when it saves its state, so they're put back: the Mk IV marker,
     * a name and lore, enchantments (Fortune, the Mk IV's glint) and the anvil's repair cost. Kept on every manipulator.
     */
    private static final String[] KEPT_TAGS = { TAG, "display", "ench", "RepairCost" };

    private static final ThreadLocal<List<NBTTagCompound>> SAVED = ThreadLocal.withInitial(ArrayList::new);

    private MkIV() {}

    public static boolean isMkIV(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .getBoolean(TAG);
    }

    /** Turns a manipulator into a Mk IV: the marker, its name and lore, and an enchantment glint. */
    public static ItemStack mark(ItemStack stack) {
        ItemStack out = stack.copy();
        out.stackSize = 1;
        NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
        tag.setBoolean(TAG, true);
        NBTTagCompound display = tag.getCompoundTag("display");
        display.setString("Name", NAME);
        NBTTagList lore = new NBTTagList();
        for (String line : LORE) lore.appendTag(new NBTTagString(line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        if (!tag.hasKey("ench", 9)) tag.setTag("ench", new NBTTagList()); // a glint with no enchantments listed
        out.setTagCompound(tag);
        return out;
    }

    // ---- Keeping the marker when the manipulator rewrites its NBT ----

    /** Before ItemMatterManipulator.setState replaces the item's NBT: remember the tags it would drop. */
    public static void beforeSetState(ItemStack stack) {
        NBTTagCompound kept = null;
        if (stack != null && stack.hasTagCompound()) {
            kept = new NBTTagCompound();
            for (String key : KEPT_TAGS) {
                NBTBase value = stack.getTagCompound()
                    .getTag(key);
                if (value != null) kept.setTag(key, value.copy());
            }
        }
        SAVED.get()
            .add(kept);
    }

    /** After setState: put them back. */
    public static void afterSetState(ItemStack stack) {
        List<NBTTagCompound> saved = SAVED.get();
        if (saved.isEmpty()) return;
        NBTTagCompound kept = saved.remove(saved.size() - 1);
        if (kept == null || stack == null) return;
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        for (Object key : kept.func_150296_c()) tag.setTag((String) key, kept.getTag((String) key));
        stack.setTagCompound(tag);
    }

    // ---- Free placement ----

    /** Whether the Mk IV places this item's block for free. */
    public static boolean isFree(ItemStack stack) {
        return whyNotFree(stack) == null;
    }

    /** Null if the Mk IV places this item's block for free, otherwise the reason it costs items. */
    public static String whyNotFree(ItemStack stack) {
        if (!MkIVConfig.enabled) return "Mk IV switched off";
        if (stack == null) return "no item";
        Item item = stack.getItem();
        if (!(item instanceof ItemBlock)) return "not a block item"; // redstone dust, seeds, doors, beds...
        Block block = Block.getBlockFromItem(item);
        if (block == null || block == Blocks.air) return "not a block item";
        if (block.hasTileEntity(item.getMetadata(stack.getItemDamage()))) return "tile entity"; // machines, chests...
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
        if (id == null) return "unregistered";
        String name = id.modId + ":" + id.name;
        if (MkIVConfig.matches(name, MkIVConfig.alwaysFree)) return null;
        for (String mod : MkIVConfig.excludedMods) if (mod.trim()
            .equalsIgnoreCase(id.modId)) return "excluded mod " + id.modId;
        if (MkIVConfig.matches(name, MkIVConfig.excludedBlocks)) return "excluded block";
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            String ore = OreDictionary.getOreName(oreId);
            if (isExcludedOreName(ore)) return "ore name " + ore;
        }
        return null;
    }

    static boolean isExcludedOreName(String ore) {
        if (MkIVConfig.matches(ore, MkIVConfig.allowedOreNames)) return false;
        for (String prefix : MkIVConfig.excludedOrePrefixes) {
            String p = prefix.trim()
                .toLowerCase(Locale.ROOT);
            if (ore.length() > p.length() && ore.toLowerCase(Locale.ROOT)
                .startsWith(p) && Character.isUpperCase(ore.charAt(p.length()))) return true;
        }
        return false;
    }

    /**
     * The Mk IV's part of MMInventory.tryConsumeItems: free items count as taken without touching any inventory (the
     * way the manipulator treats a creative player), and only the rest is taken for real. Returns null to let the
     * manipulator handle the request itself (not a Mk IV, or nothing free in it).
     */
    public static BooleanObjectImmutablePair<List<BigItemStack>> tryConsume(IPseudoInventory inventory,
        EntityPlayer player, List<BigItemStack> items, int flags) {
        if (player == null || items == null || !isMkIV(player.getHeldItem())) return null;
        List<BigItemStack> free = new ArrayList<>(), costly = new ArrayList<>();
        for (BigItemStack stack : items) {
            if (stack != null && stack.getStackSize() > 0 && isFree(stack.getItemStack())) free.add(stack.copy());
            else costly.add(stack);
        }
        if (free.isEmpty()) return null;
        if (costly.isEmpty()) return BooleanObjectImmutablePair.of(true, free);
        BooleanObjectImmutablePair<List<BigItemStack>> rest = inventory.tryConsumeItems(costly, flags);
        List<BigItemStack> taken = new ArrayList<>(free);
        if (rest.right() != null) taken.addAll(rest.right());
        return BooleanObjectImmutablePair.of(rest.leftBoolean(), taken);
    }
}
