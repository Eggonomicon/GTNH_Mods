package com.valier.loot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

/**
 * One player's copy of one loot chest half, shown in a vanilla chest window. Opening and closing it works the real
 * chest's lid (and a trapped chest's redstone) the same way a vanilla chest does.
 */
public class LootView implements IInventory {

    private final ItemStack[] stacks;
    private final LootData data;
    private final World world;
    private final int x, y, z;

    LootView(ItemStack[] stacks, LootData data, World world, int x, int y, int z) {
        this.stacks = stacks;
        this.data = data;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int getSizeInventory() {
        return stacks.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return stacks[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = stacks[slot];
        if (stack == null) return null;
        ItemStack taken;
        if (stack.stackSize <= count) {
            taken = stack;
            stacks[slot] = null;
        } else {
            taken = stack.splitStack(count);
        }
        markDirty();
        return taken;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = stacks[slot];
        stacks[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        stacks[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return LootConfig.title;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        data.markDirty(LootData.pack(x, y, z));
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return chest() != null && data.isLoot(LootData.pack(x, y, z))
            && player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {
        TileEntityChest chest = chest();
        if (chest != null) chest.openInventory();
    }

    @Override
    public void closeInventory() {
        TileEntityChest chest = chest();
        if (chest != null) chest.closeInventory();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    boolean isAt(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    /** The real chest block this copy belongs to, or null if it is gone. */
    TileEntityChest chest() {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityChest ? (TileEntityChest) te : null;
    }
}
