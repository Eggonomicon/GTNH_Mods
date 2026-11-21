package com.valier.tweaks.storage;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Satchel and Flask Upgrades, MV to LuV. Metadata: the tier it makes (1 to 5), plus 8 for a Flask upgrade. Put one in
 * a crafting grid with a Satchel (or Flask) of the tier below and out comes the next tier, holding everything it held
 * ({@link Recipe}).
 */
public class ItemSatchelUpgrade extends Item {

    public static final int FLUID = 8;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemSatchelUpgrade() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setUnlocalizedName(ValierTweaks.MODID + ".satchel_upgrade");
    }

    public static int tier(ItemStack s) {
        return Math.max(1, Math.min(SatchelTier.LUV, s.getItemDamage() & 7));
    }

    public static boolean fluid(ItemStack s) {
        return (s.getItemDamage() & FLUID) != 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + (fluid(stack) ? ".flask." : ".satchel.") + tier(stack);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int f = 0; f <= FLUID; f += FLUID) for (int t = 1; t <= SatchelTier.LUV; t++) list.add(new ItemStack(item, 1, f + t));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[16];
        for (int t = 1; t <= SatchelTier.LUV; t++) {
            icons[t] = r.registerIcon(ValierTweaks.MODID + ":satchel_upgrade_" + t);
            icons[FLUID + t] = r.registerIcon(ValierTweaks.MODID + ":flask_upgrade_" + t);
        }
        itemIcon = icons[1];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        IIcon i = icons[meta & 15];
        return i != null ? i : itemIcon;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = tier(stack);
        String what = fluid(stack) ? "Flask" : "Satchel";
        list.add(EnumChatFormatting.GRAY + "Craft it with " + BarrelClicks.an(SatchelTier.NAMES[t - 1]) + " " + what + ":");
        list.add(EnumChatFormatting.GRAY + "out comes " + BarrelClicks.an(SatchelTier.NAMES[t]) + " " + what + ", holding all it held.");
    }

    /** The upgrade in a crafting grid: one Satchel or Flask and one upgrade of the next tier, nothing else. */
    public static final class Recipe implements IRecipe {

        @Override
        public boolean matches(InventoryCrafting grid, World world) {
            return getCraftingResult(grid) != null;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting grid) {
            ItemStack bag = null, up = null;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                ItemStack s = grid.getStackInSlot(i);
                if (s == null) continue;
                if (s.getItem() instanceof ItemSatchel && bag == null) bag = s;
                else if (s.getItem() instanceof ItemSatchelUpgrade && up == null) up = s;
                else return null;
            }
            if (bag == null || up == null) return null;
            boolean fluid = ((ItemSatchel) bag.getItem()).fluid;
            if (fluid != fluid(up) || tier(up) != SatchelTier.clamp(bag.getItemDamage()) + 1) return null;
            ItemStack out = bag.copy();
            out.stackSize = 1;
            out.setItemDamage(tier(up));
            return out;
        }

        @Override
        public int getRecipeSize() {
            return 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return new ItemStack(ModBlocks.satchel, 1, SatchelTier.MV);
        }
    }
}
