package com.valier.client;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * A "Valier" creative tab holding a Valier XP Crafter, so players in creative can take one (it also shows in the
 * creative search). Made on players' computers only: creative tabs are drawn by the client, and the server takes any
 * item a creative player picks. The server's Valier Tweaks treats a ULV fluid tank carrying the crafter compound as a
 * crafter, so the one taken here places as a real crafter.
 */
final class ValierTab extends CreativeTabs {

    private final Item tank;

    ValierTab(Item tank) {
        super("valier");
        this.tank = tank;
    }

    ItemStack crafter() {
        return XpCrafterRecipe.crafter(new ItemStack(tank, 1, XpCrafterRecipe.TANK_META));
    }

    @Override
    public Item getTabIconItem() {
        return tank;
    }

    @Override
    public ItemStack getIconItemStack() {
        return crafter();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void displayAllReleventItems(List list) {
        list.add(crafter());
    }
}
