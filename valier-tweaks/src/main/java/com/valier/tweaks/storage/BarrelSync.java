package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Tells loaded barrels that their store changed, at most every 10 ticks: their label on players' screens (only players
 * watching their chunk get anything) and comparators next to them. A store that changes a thousand times in those 10
 * ticks costs one update per barrel showing it. Also links barrels that just loaded to their store (a tick later, never
 * in the middle of a chunk load). Server thread only.
 */
public final class BarrelSync {

    private static final Set<BarrelStore> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<TileBarrel> LOADED = new ArrayList<>();
    /** Barrels placed while Forge could still undo it, with what their item held (see ItemBlockBarrel). */
    private static final List<Object[]> PLACED = new ArrayList<>();
    private static int tick;
    /** How long the last tick's work took (the barrel perf test reads it). */
    static long lastTickNanos;

    static void mark(BarrelStore s) {
        DIRTY.add(s);
    }

    static void loaded(TileBarrel t) {
        LOADED.add(t);
    }

    static void placed(TileBarrel t, net.minecraft.nbt.NBTTagCompound item, net.minecraft.entity.player.EntityPlayer by) {
        PLACED.add(new Object[] { t, item, by });
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long t0 = System.nanoTime();
        try {
            work();
        } finally {
            lastTickNanos = System.nanoTime() - t0;
        }
    }

    /** Placements that stood: what their items held goes in now (an undone one is gone, and its item kept it all). */
    static void flushPlaced() {
        if (PLACED.isEmpty()) return;
        Object[][] now = PLACED.toArray(new Object[0][]);
        PLACED.clear();
        for (Object[] p : now) {
            TileBarrel t = (TileBarrel) p[0];
            net.minecraft.world.World w = t.getWorldObj();
            if (t.isInvalid() || w == null || w.getTileEntity(t.xCoord, t.yCoord, t.zCoord) != t) continue;
            ItemBlockBarrel.fill(t, (net.minecraft.nbt.NBTTagCompound) p[1], (net.minecraft.entity.player.EntityPlayer) p[2]);
        }
    }

    private void work() {
        flushPlaced();
        if (!LOADED.isEmpty()) {
            TileBarrel[] now = LOADED.toArray(new TileBarrel[0]);
            LOADED.clear();
            for (TileBarrel t : now) if (!t.isInvalid() && t.getWorldObj() != null) t.store();
        }
        if (++tick % 10 != 0 || DIRTY.isEmpty()) return;
        BarrelStore[] now = DIRTY.toArray(new BarrelStore[0]);
        DIRTY.clear();
        for (BarrelStore s : now) {
            s.dirty = false;
            for (TileBarrel t : s.viewers.toArray(new TileBarrel[0])) t.storeShown();
            for (BarrelStore.Watcher w : s.watchers.toArray(new BarrelStore.Watcher[0])) w.storeChanged();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote || event.world.provider.dimensionId != 0) return;
        BarrelChannels.forget();
        DIRTY.clear();
        LOADED.clear();
        PLACED.clear();
    }
}
