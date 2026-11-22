package com.valier.tweaks.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Every loaded Grid network, by scope and name, and the one loop that runs them all on the server tick (after the
 * worlds have ticked, so generators have pushed their EU into the plugs). Blocks join when they load (a tick later) and
 * leave when they unload. Server thread only.
 */
public final class GridNets {

    static final Map<String, GridNet> NETS = new HashMap<>();

    /** How many Grid networks there are (/valier cost). */
    public static int networks() {
        return NETS.size();
    }
    private static final List<TileGrid> LOADED = new ArrayList<>();
    /** Nanoseconds spent on the Grid and the ticks counted (the perf test resets and reads them). */
    static long timed, timedTicks;
    private static long tick;

    static String key(String scope, String name) {
        return scope + "|" + name.toLowerCase(Locale.ROOT);
    }

    public static String clean(String name) {
        return com.valier.tweaks.storage.BarrelChannels.clean(name);
    }

    static void loaded(TileGrid t) {
        LOADED.add(t);
    }

    static void join(TileGrid t) {
        if (t.net.isEmpty() || t.isInvalid() || t.getWorldObj() == null) return;
        String k = key(t.scope, t.net);
        GridNet n = NETS.get(k);
        if (n == null) {
            n = new GridNet(t.scope, t.net);
            n.colour = GridColours.of(t.scope, t.net);
            NETS.put(k, n);
        }
        n.add(t);
        t.network = n;
        if (t instanceof TileGridPlug) ((TileGridPlug) t).joinIc2(true);
    }

    static void leave(TileGrid t) {
        GridNet n = t.network;
        if (n == null) return;
        n.remove(t);
        if (n.isEmpty()) NETS.remove(key(n.scope, n.name));
        t.network = null;
        if (t instanceof TileGridPlug) ((TileGridPlug) t).joinIc2(false);
    }

    /** Puts t on the network called name in scope ("" name: on none). */
    public static void link(TileGrid t, String scope, String name) {
        leave(t);
        t.scope = scope;
        t.net = clean(name);
        join(t);
        t.markChunk();
        if (t.getWorldObj() != null) t.getWorldObj()
            .markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord);
        ValierTweaks.LOG.info("Grid block at {} {} {} is on network \"{}\" now", t.xCoord, t.yCoord, t.zCoord, t.net);
    }

    public static GridNet find(String scope, String name) {
        return NETS.get(key(scope, name));
    }

    /** A scope's loaded networks, by name. */
    public static List<GridNet> of(String scope) {
        List<GridNet> out = new ArrayList<>();
        for (GridNet n : NETS.values()) if (n.scope.equals(scope)) out.add(n);
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long t0 = System.nanoTime();
        tick++;
        if (!LOADED.isEmpty()) {
            TileGrid[] now = LOADED.toArray(new TileGrid[0]);
            LOADED.clear();
            for (TileGrid t : now) {
                if (t.isInvalid() || t.getWorldObj() == null) continue;
                if (!t.getWorldObj()
                    .getChunkProvider()
                    .chunkExists(t.xCoord >> 4, t.zCoord >> 4)) continue; // its chunk went again before it could join
                t.neighbourChanged();
                join(t);
            }
        }
        if (!NETS.isEmpty()) {
            for (GridNet n : NETS.values()
                .toArray(new GridNet[0])) {
                try {
                    n.tick(tick);
                } catch (Throwable e) {
                    ValierTweaks.LOG.warn("Grid network " + n.name + " failed a tick", e);
                }
            }
        }
        timed += System.nanoTime() - t0;
        timedTicks++;
        com.valier.tweaks.ValierCost.add(com.valier.tweaks.ValierCost.Part.GRID, t0);
    }

    /**
     * A chunk unloads: its Grid blocks leave their networks now. Their own onChunkUnload comes only with the world's next
     * entity update, which a world nobody has been in for a minute skips; till then a network would go on using them
     * (draining a cell whose chunk is already saved: energy from nothing when it loads again).
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote || NETS.isEmpty()) return;
        for (Object o : event.getChunk().chunkTileEntityMap.values()) if (o instanceof TileGrid) leave((TileGrid) o);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        if (event.world.provider.dimensionId == 0) { // the server is stopping
            NETS.clear();
            LOADED.clear();
            return;
        }
        // Another dimension: its blocks leave (the chunk events above should have done it already).
        for (GridNet n : NETS.values()
            .toArray(new GridNet[0])) for (TileGrid t : n.all()) if (t.getWorldObj() == event.world) leave(t);
        LOADED.removeIf(t -> t.getWorldObj() == event.world);
    }
}
