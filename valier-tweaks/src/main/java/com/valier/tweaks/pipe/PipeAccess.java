package com.valier.tweaks.pipe;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** How Valier pipes reach into inventories, for the self-tests of other packages (barrels). */
public final class PipeAccess {

    private PipeAccess() {}

    public static int space(IInventory inv, int side, ItemStack s, int max) {
        return ItemMover.space(inv, side, s, max);
    }

    public static ItemStack insert(IInventory inv, int side, ItemStack s) {
        return ItemMover.insert(inv, side, s);
    }

    public static ItemStack peek(IInventory inv, int slot) {
        return ItemMover.peek(inv, slot);
    }

    public static ItemStack take(IInventory inv, int slot, int n) {
        return ItemMover.take(inv, slot, n);
    }
}
