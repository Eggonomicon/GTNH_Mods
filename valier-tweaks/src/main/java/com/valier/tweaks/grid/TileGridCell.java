package com.valier.tweaks.grid;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A Grid Cell: the network's storage (EU). Points draw on cells when plugs don't give enough; what plugs give that no
 * point takes charges cells. It moves at most 4 times its tier's per-tick amount (or its own limit) a tick; broken or
 * picked up, it keeps its energy.
 */
public class TileGridCell extends TileGrid {

    public long stored;

    @Override
    public int kind() {
        return CELL;
    }

    public long capacity() {
        return GridTier.cellEu(tier);
    }

    @Override
    public long maxPerTick() {
        long m = GridTier.perTick(tier) * 4;
        return limit > 0 ? Math.min(limit, m) : m;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (stored > 0) nbt.setLong("Stored", stored);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        stored = Math.max(0, Math.min(capacity(), nbt.getLong("Stored")));
    }
}
