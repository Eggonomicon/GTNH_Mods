package com.valier.tweaks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityMobSpawner;

/**
 * A loot spawner as players' computers have it: a plain spawner tile, so vanilla's renderer draws its mob spinning in
 * the cage, plus the Looting and Speed levels the server sends for the name players see. Never saved; the server's
 * tile is {@link LootSpawnerTile}.
 */
public class LootSpawnerClientTile extends TileEntityMobSpawner {

    int looting, speed;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        looting = nbt.getInteger("ValierLooting");
        speed = nbt.getInteger("ValierSpeed");
    }
}
