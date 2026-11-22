package com.valier.tweaks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code /valier cost} (2.3.3): how much server time Valier's own blocks and tweaks take, so lag can be pinned on them
 * or ruled out without a profiler. Every Valier tick handler (and the one Valier tile that ticks, the loot spawner) is
 * timed all the time: two clock reads around its work, added to its part's total for the tick. At the start of each
 * server tick the last tick's totals go into a one-minute ring; the command reads the ring (the last 10 seconds, the
 * last minute and the worst tick of the minute) and counts what is loaded.
 * <p>
 * Not counted here: time other mods spend calling into Valier blocks (a hopper or AE2 filling a barrel is in their
 * time; Valier pipes filling one is in the pipes' time).
 * <p>
 * Cost: about 50 ns a timed handler a tick, and a 76 KB ring. Server thread only: every caller checks it is on the server
 * side first.
 */
public final class ValierCost {

    /** What is timed, in the order the command lists them when they cost the same. */
    public enum Part {

        PIPES("Pipes"),
        GRID("Grid"),
        BARRELS("Barrels"),
        SATCHELS("Satchels"),
        LOOT("Loot spawners"),
        XP("XP crafters"),
        TREES("Tree felling"),
        PEACEFUL("Peaceful dimensions");

        final String label;

        Part(String label) {
            this.label = label;
        }
    }

    static final int MINUTE = 1200, SHORT = 200; // ticks: a minute, 10 seconds
    private static final Part[] PARTS = Part.values();
    static final long[] now = new long[PARTS.length]; // this tick so far (package: the self-test puts its own back)
    private static final long[][] ring = new long[PARTS.length][MINUTE];
    private static int at; // the ring slot the last finished tick went into
    private static long ticks; // finished ticks in the ring (it holds the last MINUTE of them)

    /** The clock, to hand to {@link #add} when the work is done. */
    public static long start() {
        return System.nanoTime();
    }

    /** Work for part p that began at t0 ({@link #start}) is done. Server thread only. */
    public static void add(Part p, long t0) {
        now[p.ordinal()] += System.nanoTime() - t0;
    }

    /** First thing each tick: the tick that just ended goes into the ring. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) roll();
    }

    static void roll() {
        at = (at + 1) % MINUTE;
        for (int i = 0; i < PARTS.length; i++) {
            ring[i][at] = now[i];
            now[i] = 0;
        }
        ticks++;
    }

    /** Part p's time over the last n finished ticks, in nanoseconds (n at most what has been measured). */
    static long sum(Part p, int n) {
        long s = 0;
        long[] r = ring[p.ordinal()];
        for (int k = 0; k < n; k++) s += r[Math.floorMod(at - k, MINUTE)];
        return s;
    }

    /** Part p's worst tick of the last n, in nanoseconds. */
    static long worst(Part p, int n) {
        long w = 0;
        long[] r = ring[p.ordinal()];
        for (int k = 0; k < n; k++) w = Math.max(w, r[Math.floorMod(at - k, MINUTE)]);
        return w;
    }

    /** The worst tick of the last n for everything together, in nanoseconds. */
    static long worstAll(int n) {
        long w = 0;
        for (int k = 0; k < n; k++) {
            long s = 0;
            for (long[] r : ring) s += r[Math.floorMod(at - k, MINUTE)];
            w = Math.max(w, s);
        }
        return w;
    }

    /** Ticks measured so far, up to n. */
    static int have(int n) {
        return (int) Math.min(n, ticks);
    }

    static String ms(long nanos, int ticks) {
        return String.format("%.3f", ticks <= 0 ? 0 : nanos / 1e6 / ticks);
    }

    /** What the command says: a header, a line a part (costliest first), and the total against the server's tick. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        int n10 = have(SHORT), n60 = have(MINUTE);
        if (n10 == 0) {
            out.add("Valier cost: nothing measured yet; try again in a few seconds.");
            return out;
        }
        String[] what = loaded();
        Part[] order = PARTS.clone();
        final int n = n10;
        Arrays.sort(order, (a, b) -> Long.compare(sum(b, n), sum(a, n)));
        out.add("Valier cost, ms of server time a tick: last " + (n10 / 20) + " s, last " + secs(n60) + ", worst tick.");
        long all10 = 0, all60 = 0;
        for (Part p : order) {
            long s10 = sum(p, n10), s60 = sum(p, n60);
            all10 += s10;
            all60 += s60;
            out.add(" " + p.label + ": " + ms(s10, n10) + ", " + ms(s60, n60) + ", worst " + ms(worst(p, n60), 1)
                + (what[p.ordinal()].isEmpty() ? "" : " (" + what[p.ordinal()] + ")"));
        }
        double tick = serverTickMs();
        double mine = all10 / 1e6 / n10;
        String share = tick > 0 ? String.format(", %.1f%% of the server's %.1f ms tick (%.1f TPS)", 100 * mine / tick, tick,
            Math.min(20, 1000 / Math.max(tick, 1e-3))) : "";
        out.add("In all: " + ms(all10, n10) + " ms a tick, " + ms(all60, n60) + " over the minute, worst tick "
            + ms(worstAll(n60), 1) + share + ".");
        return out;
    }

    static String secs(int ticks) {
        return ticks >= MINUTE ? "minute" : (ticks / 20) + " s";
    }

    /** The server's mean tick over its last 100, in ms (0: not known). */
    static double serverTickMs() {
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null || s.tickTimeArray == null || s.tickTimeArray.length == 0) return 0;
        long sum = 0;
        for (long t : s.tickTimeArray) sum += t;
        return sum / (double) s.tickTimeArray.length / 1e6;
    }

    /**
     * What is loaded, by part (empty: nothing to say). One pass over the loaded chunks' tiles, only when the command runs:
     * the world's ticking list would miss pipes, barrels and the rest, which never tick.
     */
    static String[] loaded() {
        int pipes = 0, grid = 0, barrels = 0, docks = 0, spawners = 0, crafters = 0, satchels = 0;
        for (WorldServer w : DimensionManager.getWorlds()) {
            for (Object c : w.theChunkProviderServer.loadedChunks) for (Object o : ((Chunk) c).chunkTileEntityMap.values()) {
                TileEntity te = (TileEntity) o;
                if (te.isInvalid()) continue;
                if (te instanceof com.valier.tweaks.pipe.TilePipe) pipes++;
                else if (te instanceof com.valier.tweaks.grid.TileGrid) grid++;
                else if (te instanceof com.valier.tweaks.storage.TileBarrel) barrels++;
                else if (te instanceof com.valier.tweaks.storage.TileSatchelDock) docks++;
                else if (te instanceof LootSpawnerTile) spawners++;
                else if (te instanceof TileXpCrafter) crafters++;
            }
            for (Object o : w.playerEntities) {
                for (ItemStack s : ((EntityPlayer) o).inventory.mainInventory) {
                    if (s != null && s.getItem() instanceof com.valier.tweaks.storage.ItemSatchel) {
                        satchels++;
                        break;
                    }
                }
            }
        }
        String[] out = new String[PARTS.length];
        Arrays.fill(out, "");
        out[Part.PIPES.ordinal()] = pipes + " pipes in " + com.valier.tweaks.pipe.PipeNetworks.networks() + " networks";
        out[Part.GRID.ordinal()] = grid + " blocks in " + com.valier.tweaks.grid.GridNets.networks() + " networks";
        out[Part.BARRELS.ordinal()] = barrels + " loaded, " + com.valier.tweaks.storage.BarrelChannels.count() + " channels";
        out[Part.SATCHELS.ordinal()] = satchels + (satchels == 1 ? " player carries one" : " players carry one")
            + (docks > 0 ? ", " + docks + " docks" : "");
        out[Part.LOOT.ordinal()] = spawners + " loaded";
        out[Part.XP.ordinal()] = crafters + " loaded";
        out[Part.PEACEFUL.ordinal()] = PeacefulDims.count() + " dimensions peaceful";
        return out;
    }
}
