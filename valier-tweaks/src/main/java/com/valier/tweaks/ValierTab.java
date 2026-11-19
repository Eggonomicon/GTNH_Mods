package com.valier.tweaks;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * The "Valier" creative tab: every block and item of this mod. Made on both sides (a creative tab is a plain object);
 * only clients draw it.
 */
public final class ValierTab extends CreativeTabs {

    public ValierTab() {
        super("valier");
    }

    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(ModBlocks.lootSpawner);
    }

}
