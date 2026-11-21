package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * What a creative pipe's centre draws from: an endless inventory (item pipes) or tank (fluid pipes) of whatever its
 * source settings' Allow list names. Every slot always reads full (one transfer's worth), taking from it never empties
 * it, and nothing can be put in (what a destination didn't take just goes away). It only exists for its face's network
 * ({@link PipeNetwork.Face#source}); nothing is saved.
 */
final class CreativeSource implements IInventory, IFluidHandler {

    private final FaceConfig cfg;
    private final PipeKind kind;
    private final int tier;

    CreativeSource(FaceConfig cfg, PipeKind kind, int tier) {
        this.cfg = cfg;
        this.kind = kind;
        this.tier = tier;
    }

    private int each() {
        return Math.max(1, cfg.perTransfer(kind, tier));
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return kind == PipeKind.ITEM ? FaceConfig.FILTER : 0;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return make(i, each());
    }

    private ItemStack make(int i, int n) {
        if (kind != PipeKind.ITEM || i < 0 || i >= FaceConfig.FILTER) return null;
        ItemStack f = cfg.allow.filter[i];
        if (f == null || f.getItem() == null || n <= 0) return null;
        ItemStack s = f.copy();
        s.stackSize = n;
        return s;
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        return make(i, Math.min(n, each()));
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {}

    @Override
    public String getInventoryName() {
        return "Creative source";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return false;
    }

    // ---- fluids -----------------------------------------------------------------------------------------------------

    private List<Fluid> fluids() {
        List<Fluid> out = new ArrayList<>();
        if (kind != PipeKind.FLUID) return out;
        for (ItemStack s : cfg.allow.filter) {
            FluidStack f = Fluids.of(s);
            if (f != null && f.getFluid() != null && !out.contains(f.getFluid())) out.add(f.getFluid());
        }
        return out;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0 || !fluids().contains(resource.getFluid())) return null;
        FluidStack out = resource.copy();
        out.amount = Math.min(resource.amount, each());
        return out;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        List<Fluid> f = fluids();
        if (f.isEmpty() || maxDrain <= 0) return null;
        return new FluidStack(f.get(0), Math.min(maxDrain, each()));
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return fluids().contains(fluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        List<Fluid> f = fluids();
        FluidTankInfo[] out = new FluidTankInfo[f.size()];
        for (int i = 0; i < out.length; i++) out[i] = new FluidTankInfo(new FluidStack(f.get(i), each()), each());
        return out;
    }
}
