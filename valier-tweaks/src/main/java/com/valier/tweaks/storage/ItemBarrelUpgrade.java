package com.valier.tweaks.storage;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Barrel Upgrades, LV to LuV (metadata 1 to 6: the tier a barrel becomes). Right-click a barrel of the tier below with
 * one and it becomes that tier where it stands, keeping what it holds (a linked barrel's channel grows too).
 */
public class ItemBarrelUpgrade extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemBarrelUpgrade() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setUnlocalizedName(ValierTweaks.MODID + ".barrel_upgrade");
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + tier(stack);
    }

    static int tier(ItemStack stack) {
        return Math.max(1, Math.min(BarrelTier.COUNT - 1, stack.getItemDamage()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 1; t < BarrelTier.COUNT; t++) list.add(new ItemStack(item, 1, t));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[BarrelTier.COUNT];
        for (int t = 1; t < BarrelTier.COUNT; t++) icons[t] = r.registerIcon(ValierTweaks.MODID + ":barrel_upgrade_" + t);
        icons[0] = icons[1];
        itemIcon = icons[1];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[Math.max(1, Math.min(BarrelTier.COUNT - 1, meta))];
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = tier(stack);
        list.add(EnumChatFormatting.GRAY + "Right-click " + BarrelClicks.an(BarrelTier.NAMES[t - 1]) + " barrel with it:");
        list.add(EnumChatFormatting.GRAY + "it becomes " + BarrelClicks.an(BarrelTier.NAMES[t]) + " barrel, " + String.format("%,d",
            BarrelTier.stacks(t)) + " stacks.");
    }
}
