package com.valier.tweaks.grid;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * A Grid Controller: charges the items of its network's team's online players, wherever they are (worn armour first,
 * then the inventory, then baubles), every half second, up to its limit a tick. Each player can opt out in its menu.
 */
public class TileGridController extends TileGrid {

    /** Players who said "don't charge me". */
    public final Set<UUID> skip = new HashSet<>();

    @Override
    public int kind() {
        return CONTROLLER;
    }

    @Override
    public long maxPerTick() {
        long m = GridTier.perTick(2) * 4; // HV-sized: 8,192 EU a tick
        return limit > 0 ? Math.min(limit, m) : m;
    }

    @Override
    public void writeSettings(NBTTagCompound nbt) {
        super.writeSettings(nbt);
        if (skip.isEmpty()) return;
        NBTTagList l = new NBTTagList();
        for (UUID u : skip) l.appendTag(new NBTTagString(u.toString()));
        nbt.setTag("Skip", l);
    }

    @Override
    public void readSettings(NBTTagCompound nbt, boolean withOwner) {
        super.readSettings(nbt, withOwner);
        skip.clear();
        NBTTagList l = nbt.getTagList("Skip", 8);
        for (int i = 0; i < l.tagCount(); i++) {
            try {
                skip.add(UUID.fromString(l.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
