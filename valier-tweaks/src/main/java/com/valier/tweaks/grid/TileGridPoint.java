package com.valier.tweaks.grid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.Optional;

/**
 * A Grid Point: power goes out of the network here, into what touches it. A GregTech machine gets EU at its own input
 * voltage (never more than the point's tier: nothing explodes) and up to its own amps; a GregTech cable at the voltage
 * set here ({@link #volts}, at most the tier's); IC2 machines EU in their tier's packets; RF machines RF (from RF that
 * came in, or EU at {@code gridRfPerEu}). To GregTech it is something that gives power (as a generator is), so cables
 * connect to it; it takes none that way.
 */
@Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IEnergyConnected", modid = "gregtech")
public class TileGridPoint extends TileGrid implements gregtech.api.interfaces.tileentity.IEnergyConnected {

    /** The voltage for neighbours that don't say what they take (GregTech cables); 0: the tier's. */
    public long volts;

    @Override
    public int kind() {
        return POINT;
    }

    long cableVolts() {
        long v = GridTier.volts(tier);
        return volts > 0 ? Math.min(volts, v) : v;
    }

    /** What its neighbours would take this tick (EU), at most its limit. */
    long demand() {
        if (!active()) return 0;
        refreshNear();
        long cap = maxPerTick(), want = 0;
        for (int s = 0; s < 6 && want < cap; s++) {
            if (nearKind[s] == 0) continue;
            want += GridEnergy.demand(near[s], nearKind[s], ForgeDirection.getOrientation(s), cap - want, GridTier.volts(tier),
                GridTier.amps());
        }
        return Math.min(cap, want);
    }

    /** RF the last {@link #deliver} sent (from the RF pool). */
    long deliveredRf;

    /**
     * Sends up to budget EU (and RF from rfPool first to RF machines) into its neighbours. Returns EU used; the RF used
     * is left in {@link #deliveredRf} (no array a call: this runs for every point every tick).
     */
    long deliver(long budget, long rfPool) {
        long used = 0, rfUsed = 0;
        refreshNear();
        double per = GridEnergy.rfPerEu();
        for (int s = 0; s < 6 && (used < budget || rfUsed < rfPool); s++) {
            int k = nearKind[s];
            if (k == 0) continue;
            TileEntity te = near[s];
            ForgeDirection d = ForgeDirection.getOrientation(s);
            if (k == 3) { // RF: what came in as RF first, then EU turned into RF
                int fromRf = (int) Math.min(Integer.MAX_VALUE, rfPool - rfUsed);
                if (fromRf > 0) rfUsed += GridEnergy.pushRf(te, d, fromRf);
                long euLeft = budget - used;
                if (euLeft > 0) {
                    int rf = (int) Math.min(Integer.MAX_VALUE, (long) (euLeft * per));
                    int took = GridEnergy.pushRf(te, d, rf);
                    used += (long) Math.ceil(took / per);
                }
                continue;
            }
            if (used >= budget) continue;
            used += GridEnergy.push(te, k, d, budget - used, GridTier.volts(tier), GridTier.amps(), cableVolts());
        }
        deliveredRf = rfUsed;
        return Math.min(used, budget);
    }

    @Override
    public void writeSettings(NBTTagCompound nbt) {
        super.writeSettings(nbt);
        if (volts > 0) nbt.setLong("Volts", volts);
    }

    @Override
    public void readSettings(NBTTagCompound nbt, boolean withOwner) {
        super.readSettings(nbt, withOwner);
        volts = Math.max(0, nbt.getLong("Volts"));
    }

    // ---- GregTech: cables connect to it as to a generator; nothing goes in this way --------------------------------

    @Override
    @Optional.Method(modid = "gregtech")
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        return 0;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean inputEnergyFrom(ForgeDirection side) {
        return false;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean outputsEnergyTo(ForgeDirection side) {
        return true;
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
}
