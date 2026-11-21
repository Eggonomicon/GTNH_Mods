package com.valier.tweaks.grid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.Optional;

/**
 * A Grid Plug: power goes into the network here. GregTech generators and cables push EU into it (up to its tier's
 * voltage: a higher voltage is refused, nothing explodes; up to {@code gridAmps} amps a tick), IC2 generators and
 * cables through IC2's energy net, RF generators push RF. It holds a few ticks' worth, so it only takes what the
 * network can pass on or store.
 */
@Optional.InterfaceList({
    @Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IEnergyConnected", modid = "gregtech"),
    @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = "IC2"),
    @Optional.Interface(iface = "cofh.api.energy.IEnergyReceiver", modid = "CoFHAPI|energy") })
public class TileGridPlug extends TileGrid implements gregtech.api.interfaces.tileentity.IEnergyConnected,
    ic2.api.energy.tile.IEnergySink, cofh.api.energy.IEnergyReceiver {

    long eu, rf;
    long ampsThisTick, inThisTick, rfThisTick;
    boolean ic2Joined;

    @Override
    public int kind() {
        return PLUG;
    }

    long euCap() {
        return maxPerTick() * 4;
    }

    long rfCap() {
        return (long) (maxPerTick() * 4 * GridEnergy.rfPerEu());
    }

    void newTick() {
        ampsThisTick = 0;
        inThisTick = 0;
        rfThisTick = 0;
    }

    private boolean takes() {
        return worldObj != null && !worldObj.isRemote && !net.isEmpty() && network != null && active();
    }

    /** EU in (any source), within this tick's allowance and its buffer. Returns EU taken. */
    long takeEu(long offered) {
        if (!takes()) return 0;
        long n = Math.min(offered, Math.min(maxPerTick() - inThisTick, euCap() - eu));
        if (n <= 0) return 0;
        eu += n;
        inThisTick += n;
        return n;
    }

    // ---- GregTech ------------------------------------------------------------------------------------------------------

    @Override
    @Optional.Method(modid = "gregtech")
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (voltage <= 0 || amperage <= 0 || voltage > GridTier.volts(tier) || !takes()) return 0;
        long a = Math.min(amperage, Math.min(GridTier.amps() - ampsThisTick, Math.min(maxPerTick() - inThisTick,
            euCap() - eu) / voltage));
        if (a <= 0) return 0;
        eu += a * voltage;
        inThisTick += a * voltage;
        ampsThisTick += a;
        return a;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean inputEnergyFrom(ForgeDirection side) {
        return true;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte getColorization() {
        return -1;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte setColorization(byte colour) {
        return -1;
    }

    // ---- IC2 -----------------------------------------------------------------------------------------------------------

    @Override
    @Optional.Method(modid = "IC2")
    public double getDemandedEnergy() {
        return takes() ? Math.max(0, Math.min(maxPerTick() - inThisTick, euCap() - eu)) : 0;
    }

    /** As high as it goes, so IC2 never blows it up; what is over its voltage is refused instead. */
    @Override
    @Optional.Method(modid = "IC2")
    public int getSinkTier() {
        return 14;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double injectEnergy(ForgeDirection from, double amount, double voltage) {
        if (voltage > GridTier.volts(tier)) return amount;
        long took = takeEu((long) Math.floor(amount));
        return amount - took;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection from) {
        return true;
    }

    // ---- RF ------------------------------------------------------------------------------------------------------------

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (!takes() || maxReceive <= 0) return 0;
        long allow = (long) (maxPerTick() * GridEnergy.rfPerEu()) - rfThisTick;
        int n = (int) Math.max(0, Math.min(maxReceive, Math.min(allow, rfCap() - rf)));
        if (!simulate && n > 0) {
            rf += n;
            rfThisTick += n;
        }
        return n;
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int getEnergyStored(ForgeDirection from) {
        return (int) Math.min(Integer.MAX_VALUE, rf);
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int getMaxEnergyStored(ForgeDirection from) {
        return (int) Math.min(Integer.MAX_VALUE, rfCap());
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    // ---- IC2's energy net, and saving ----------------------------------------------------------------------------------

    void joinIc2(boolean join) {
        if (join == ic2Joined || worldObj == null || worldObj.isRemote) return;
        ic2Joined = join;
        GridEnergy.ic2Load(this, join);
    }

    @Override
    public void invalidate() {
        joinIc2(false);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        joinIc2(false);
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (eu > 0) nbt.setLong("Eu", eu);
        if (rf > 0) nbt.setLong("Rf", rf);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        eu = Math.max(0, nbt.getLong("Eu"));
        rf = Math.max(0, nbt.getLong("Rf"));
    }
}
