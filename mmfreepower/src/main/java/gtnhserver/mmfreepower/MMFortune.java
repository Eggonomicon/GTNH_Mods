package gtnhserver.mmfreepower;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;

/**
 * Fortune for Matter Manipulators: the blocks a manipulator removes drop as if mined with the Fortune level enchanted
 * on it (at least {@code mining/baseFortune}). Fortune goes on by crafting the manipulator with a Fortune book
 * ({@link FortuneRecipe}); a creative player's anvil works too. It is a plain "ench" entry, so clients show
 * "Fortune III" in the tooltip with no mod.
 */
public final class MMFortune {

    /** Forge's Block.harvesters: the player breaking the block, set by harvestBlock around the drop calculation. */
    private static final Field HARVESTERS = harvestersField();

    private MMFortune() {}

    private static Field harvestersField() {
        try {
            Field f = Block.class.getDeclaredField("harvesters");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MMFreePower.LOG.warn("Block.harvesters not found; GregTech ores mined by a manipulator won't get Fortune", e);
            return null;
        }
    }

    /** Sets the player as this block's harvester (as harvestBlock does); returns what was there to put back. */
    @SuppressWarnings("unchecked")
    public static Object beginHarvest(Block block, EntityPlayer player) {
        if (HARVESTERS == null || block == null) return null;
        try {
            ThreadLocal<EntityPlayer> harvesters = (ThreadLocal<EntityPlayer>) HARVESTERS.get(block);
            EntityPlayer previous = harvesters.get();
            harvesters.set(player);
            return previous;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static void endHarvest(Block block, Object previous) {
        if (HARVESTERS == null || block == null) return;
        try {
            ((ThreadLocal<EntityPlayer>) HARVESTERS.get(block)).set((EntityPlayer) previous);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    public static boolean isManipulator(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemMatterManipulator;
    }

    /** The Fortune level a player's manipulator mines with: what's on the one in hand, at least the base level. */
    public static int miningLevel(EntityPlayer player) {
        ItemStack held = player == null ? null : player.getHeldItem();
        int level = isManipulator(held) ? onItem(held) : 0;
        return Math.max(level, MkIVConfig.baseFortune);
    }

    public static int onItem(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, stack);
    }

    /** The highest Fortune level stored on an enchanted book, or 0. */
    public static int onBook(ItemStack book) {
        if (book == null || book.getItem() != Items.enchanted_book || !book.hasTagCompound()) return 0;
        NBTTagList stored = book.getTagCompound()
            .getTagList("StoredEnchantments", 10);
        int level = 0;
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound e = stored.getCompoundTagAt(i);
            if (e.getShort("id") == Enchantment.fortune.effectId) level = Math.max(level, e.getShort("lvl"));
        }
        return level;
    }

    /** A copy of the manipulator with Fortune at this level (any other enchantments and the Mk IV glint kept). */
    public static ItemStack withFortune(ItemStack manipulator, int level) {
        ItemStack out = manipulator.copy();
        out.stackSize = 1;
        NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
        NBTTagList old = tag.getTagList("ench", 10);
        NBTTagList ench = new NBTTagList();
        for (int i = 0; i < old.tagCount(); i++) {
            NBTTagCompound e = old.getCompoundTagAt(i);
            if (e.getShort("id") != Enchantment.fortune.effectId) ench.appendTag(e.copy());
        }
        NBTTagCompound fortune = new NBTTagCompound();
        fortune.setShort("id", (short) Enchantment.fortune.effectId);
        fortune.setShort("lvl", (short) level);
        ench.appendTag(fortune);
        tag.setTag("ench", ench);
        out.setTagCompound(tag);
        return out;
    }
}
