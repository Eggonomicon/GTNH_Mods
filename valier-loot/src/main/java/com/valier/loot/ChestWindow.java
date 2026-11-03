package com.valier.loot;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;

/**
 * What a loot chest window shows: one player's copy of one half, or of both halves of a double chest.
 * <p>
 * It is an InventoryLargeChest even for a single chest because every 10 seconds a vanilla chest recounts the players
 * looking into it, and it only counts windows that are the chest itself or an InventoryLargeChest that says the chest
 * is part of it. Answering yes here keeps the lid open (and a trapped chest powered) while the window is open.
 */
public class ChestWindow extends InventoryLargeChest {

    private final LootView first;
    private final LootView second; // null for a single chest

    ChestWindow(LootView first, LootView second) {
        super(LootConfig.title, first, second == null ? first : second);
        this.first = first;
        this.second = second;
    }

    /** Whether this window shows (a half of) the loot chest at x, y, z. */
    public boolean shows(int x, int y, int z) {
        return first.isAt(x, y, z) || second != null && second.isAt(x, y, z);
    }

    @Override
    public boolean isPartOfLargeChest(IInventory inventory) {
        return inventory != null && (inventory == first.chest() || second != null && inventory == second.chest());
    }

    @Override
    public int getSizeInventory() {
        return second == null ? first.getSizeInventory() : super.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return second == null ? first.getStackInSlot(slot) : super.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        return second == null ? first.decrStackSize(slot, count) : super.decrStackSize(slot, count);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return second == null ? first.getStackInSlotOnClosing(slot) : super.getStackInSlotOnClosing(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (second == null) first.setInventorySlotContents(slot, stack);
        else super.setInventorySlotContents(slot, stack);
    }

    @Override
    public void openInventory() {
        if (second == null) first.openInventory();
        else super.openInventory();
    }

    @Override
    public void closeInventory() {
        if (second == null) first.closeInventory();
        else super.closeInventory();
    }
}
