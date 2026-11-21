package com.valier.tweaks.pipe;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * A filter list as an inventory, for a menu's ghost slots ({@link ContainerPipeFace.Ghost}): vanilla then sends their
 * contents to the player. It shows whichever list the menu has picked (the supplier), takes nothing and gives nothing.
 */
public final class ListView implements IInventory {

    private final Supplier<FilterList> list;

    public ListView(Supplier<FilterList> list) {
        this.list = list;
    }

    @Override
    public int getSizeInventory() {
        return FaceConfig.FILTER;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return list.get().filter[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        list.get().filter[i] = s == null ? null : ContainerPipeFace.one(s);
    }

    @Override
    public String getInventoryName() {
        return "Filter";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return false;
    }
}
