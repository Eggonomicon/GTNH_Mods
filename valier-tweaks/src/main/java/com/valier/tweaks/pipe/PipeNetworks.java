package com.valier.tweaks.pipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.valier.tweaks.TweaksConfig;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The pipe networks of every world, and the one timer that runs them.
 * <ul>
 * <li>Pipes never tick. A pipe placed, broken, loaded, unloaded or changed only marks its place {@link #dirty}; at the
 * end of the tick the networks touching dirty places are found again (a flood fill), and nothing else is done.</li>
 * <li>Only Extract and Request faces work, each on its tier's interval, from a queue ordered by when they are due. A
 * face that found nothing to do waits twice as long each time, up to {@code pipeMaxSleepTicks}; a change in the block it
 * touches wakes it early ({@link #wake}). All of it within {@code pipeBudgetMicros} a tick: what's left waits.</li>
 * <li>Transfers are instant, from block to block ({@link ItemMover}, {@link FluidMover}); nothing ever travels.</li>
 * <li>Never loads a chunk: a face whose block is in an unloaded chunk waits, filed by chunk, until it loads.</li>
 * </ul>
 */
public class PipeNetworks {

    private static final Map<World, PipeNetworks> WORLDS = new WeakHashMap<>();
    /** Wireless faces with a tag, by scope (team), kind and tag, across worlds. */
    static final Map<String, List<PipeNetwork.Face>> WIRELESS = new HashMap<>();

    final World world;
    final Set<Long> dirty = new HashSet<>();
    final Map<Long, PipeNetwork> byPos = new HashMap<>();
    final PriorityQueue<PipeNetwork.Face> queue = new PriorityQueue<>((a, b) -> Long.compare(a.due, b.due));
    /** Faces woken by their block changing, put back in the queue at the start of the next run. */
    final List<PipeNetwork.Face> woken = new ArrayList<>();
    /** Faces waiting for the chunk their block is in, by chunk. */
    final Map<Long, List<PipeNetwork.Face>> waitingForChunk = new HashMap<>();
    long tick;

    // Numbers for /valiertweaks pipes.
    static long runs, moves, moved, nanos, overBudget;

    private PipeNetworks(World world) {
        this.world = world;
    }

    /** For the event buses: the handlers below run on this one; worlds have their own instances. */
    PipeNetworks() {
        this.world = null;
    }

    /** The event listener (registered once by the mod). */
    public static final class Events extends PipeNetworks {}

    static PipeNetworks of(World w) {
        PipeNetworks n = WORLDS.get(w);
        if (n == null) WORLDS.put(w, n = new PipeNetworks(w));
        return n;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int kx(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    static int ky(long k) {
        return (int) (k & 0xFFF);
    }

    static int kz(long k) {
        return (int) (k >> 12) << 6 >> 6;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Something at this pipe changed: its networks are found again at the end of the tick. */
    public static void dirty(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return;
        of(w).dirty.add(key(x, y, z));
    }

    /** The block on that side of this pipe changed inside: that face works at its next interval, not later. */
    public static void wake(World w, int x, int y, int z, int side) {
        PipeNetworks n = WORLDS.get(w);
        if (n == null) return;
        PipeNetwork net = n.byPos.get(key(x, y, z));
        if (net == null) return;
        for (PipeNetwork.Face f : net.all) {
            if (f.side == side && f.pipe.xCoord == x && f.pipe.yCoord == y && f.pipe.zCoord == z && f.idle > 0 && !f.dead) {
                n.woken.add(f);
                return;
            }
        }
    }

    /** How many times in a row that face found nothing to do (-1: no such active face). For the self-test. */
    public static int idleOf(World w, int x, int y, int z, int side) {
        PipeNetwork net = networkAt(w, x, y, z);
        if (net == null) return -1;
        for (PipeNetwork.Face f : net.all)
            if (f.side == side && f.pipe.xCoord == x && f.pipe.yCoord == y && f.pipe.zCoord == z) return f.idle;
        return -1;
    }

    /** The network a pipe is in (for menus, WAILA, the command), or null. */
    public static PipeNetwork networkAt(World w, int x, int y, int z) {
        PipeNetworks n = WORLDS.get(w);
        return n == null ? null : n.byPos.get(key(x, y, z));
    }

    // ---- finding networks -------------------------------------------------------------------------------------------

    void rebuild() {
        if (dirty.isEmpty()) return;
        Set<Long> seeds = new HashSet<>();
        for (long p : dirty) {
            seeds.add(p);
            take(p, seeds);
            int x = kx(p), y = ky(p), z = kz(p);
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) take(key(x + d.offsetX, y + d.offsetY, z + d.offsetZ), seeds);
        }
        dirty.clear();
        for (long s : seeds) {
            if (byPos.containsKey(s)) continue;
            TilePipe t = pipeAt(kx(s), ky(s), kz(s));
            if (t != null) flood(t);
        }
    }

    /** The network at p (if any) is dropped; its pipes are found again. */
    private void take(long p, Set<Long> seeds) {
        PipeNetwork old = byPos.get(p);
        if (old == null) return;
        for (long q : old.pipes) {
            byPos.remove(q);
            seeds.add(q);
        }
        for (PipeNetwork.Face f : old.all) {
            f.dead = true;
            if (f.scope != null) unregisterWireless(f);
        }
    }

    TilePipe pipeAt(int x, int y, int z) {
        if (y < 0 || y >= world.getHeight() || !world.blockExists(x, y, z)) return null;
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TilePipe && !te.isInvalid() ? (TilePipe) te : null;
    }

    private void flood(TilePipe start) {
        PipeNetwork net = new PipeNetwork(start.kind());
        ArrayDeque<TilePipe> todo = new ArrayDeque<>();
        todo.add(start);
        byPos.put(key(start.xCoord, start.yCoord, start.zCoord), net);
        int max = Math.max(1, TweaksConfig.pipeNetworkMax);
        List<TilePipe> members = new ArrayList<>();
        while (!todo.isEmpty()) {
            TilePipe p = todo.poll();
            members.add(p);
            net.pipes.add(key(p.xCoord, p.yCoord, p.zCoord));
            if (members.size() > max) net.tooBig = true;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if (!p.pipeOn(d.ordinal())) continue;
                long k = key(p.xCoord + d.offsetX, p.yCoord + d.offsetY, p.zCoord + d.offsetZ);
                if (byPos.containsKey(k)) continue;
                TilePipe q = pipeAt(p.xCoord + d.offsetX, p.yCoord + d.offsetY, p.zCoord + d.offsetZ);
                if (q == null || q.kind() != net.kind || !q.pipeOn(d.getOpposite()
                    .ordinal())) continue;
                byPos.put(k, net);
                todo.add(q);
            }
        }
        if (net.tooBig) {
            ValierTweaks.LOG.warn("A {} pipe network at {} {} {} has {} pipes, more than pipeNetworkMax ({}): it does nothing",
                net.kind.word, start.xCoord, start.yCoord, start.zCoord, members.size(), max);
            return;
        }
        for (TilePipe p : members) {
            for (int s = 0; s < 6; s++) {
                if (!p.blockOn(s) || p.face(s) == null || p.face(s).mode == FaceMode.OFF) continue;
                net.all.add(new PipeNetwork.Face(net, p, s));
            }
            if (p.creative()) {
                FaceConfig src = p.face(TilePipe.SOURCE);
                src.acceptOverflow = false; // a source never takes anything in
                if (src.mode == FaceMode.EXTRACT || src.mode == FaceMode.PROVIDE) net.all.add(new PipeNetwork.Face(net, p, TilePipe.SOURCE));
            }
        }
        net.sort();
        for (PipeNetwork.Face f : net.all) {
            if (f.wireless()) registerWireless(f);
            if (f.cfg.mode.active()) {
                // Spread first runs over the interval so a big build doesn't all work on one tick.
                f.due = tick + 1 + Math.floorMod(f.pipe.xCoord * 31 + f.pipe.zCoord * 17 + f.side, f.cfg.interval(f.tier));
                f.queued = true;
                queue.add(f);
            }
        }
    }

    // ---- wireless -------------------------------------------------------------------------------------------------

    static String wirelessKey(Object scope, PipeKind kind, String tag) {
        return scope + "|" + kind.word + "|" + tag;
    }

    void registerWireless(PipeNetwork.Face f) {
        f.scope = TeamScope.of(f.owner);
        WIRELESS.computeIfAbsent(wirelessKey(f.scope, f.net.kind, f.cfg.tag), k -> new ArrayList<>())
            .add(f);
    }

    static void unregisterWireless(PipeNetwork.Face f) {
        List<PipeNetwork.Face> l = WIRELESS.get(wirelessKey(f.scope, f.net.kind, f.cfg.tag));
        if (l != null) {
            l.remove(f);
            if (l.isEmpty()) WIRELESS.remove(wirelessKey(f.scope, f.net.kind, f.cfg.tag));
        }
    }

    /**
     * The faces of a role that {@code f} trades with: those of its own network with its tag, and for a Wireless face
     * with a tag also the Wireless faces of that tag (same team) on other networks, other dimensions too if the config
     * allows. Sorted by priority.
     */
    static List<PipeNetwork.Face> partners(PipeNetwork.Face f, FaceMode role, boolean overflowOnly) {
        List<PipeNetwork.Face> own = overflowOnly ? f.net.overflow
            : role == FaceMode.INSERT ? f.net.inserts : role == FaceMode.REQUEST ? f.net.requests : f.net.provides;
        List<PipeNetwork.Face> out = new ArrayList<>();
        for (PipeNetwork.Face o : own) if (o != f && o.sameTag(f) && !o.dead) out.add(o);
        if (f.wireless() && f.scope != null) {
            List<PipeNetwork.Face> far = WIRELESS.get(wirelessKey(f.scope, f.net.kind, f.cfg.tag));
            if (far != null) {
                for (PipeNetwork.Face o : far) {
                    boolean fits = role == FaceMode.INSERT ? o.cfg.mode.takesPushes() : o.cfg.mode == role;
                    if (o.net == f.net || o.dead || !fits || (overflowOnly && !o.cfg.acceptOverflow)) continue;
                    if (!TweaksConfig.pipeWirelessCrossDimension && o.pipe.getWorldObj() != f.pipe.getWorldObj()) continue;
                    out.add(o);
                }
                out.sort(PipeNetwork.BY_PRIORITY);
            }
        }
        return out;
    }

    // ---- running ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        PipeNetworks n = WORLDS.get(event.world);
        if (n == null) return;
        n.tick++;
        long start = System.nanoTime();
        try {
            n.rebuild();
            n.run(start);
        } catch (Throwable t) {
            ValierTweaks.LOG.error("Valier pipes failed in dim " + event.world.provider.dimensionId, t);
        }
        nanos += System.nanoTime() - start;
    }

    void run(long start) {
        if (!woken.isEmpty()) {
            for (PipeNetwork.Face f : woken) {
                if (f.dead || !f.queued) continue;
                long soon = tick + 1;
                if (f.due > soon) { // it was asleep: wake it
                    queue.remove(f);
                    f.idle = 0;
                    f.due = soon;
                    queue.add(f);
                }
            }
            woken.clear();
        }
        long budget = Math.max(100, TweaksConfig.pipeBudgetMicros) * 1000L;
        while (!queue.isEmpty() && queue.peek().due <= tick) {
            if (System.nanoTime() - start > budget) {
                overBudget++;
                return; // the rest waits for the next tick
            }
            PipeNetwork.Face f = queue.poll();
            f.queued = false;
            if (f.dead || f.pipe.isInvalid()) continue;
            int done = work(f);
            if (done > 0) f.idle = 0;
            else f.idle = Math.min(f.idle + 1, 16);
            int interval = f.cfg.interval(f.tier);
            long wait = done > 0 ? interval
                : Math.min(Math.max(interval, TweaksConfig.pipeMaxSleepTicks), (long) interval << Math.min(f.idle, 10));
            if (done < 0) { // its block's chunk isn't loaded: it waits for that chunk
                waitingForChunk.computeIfAbsent(chunkKey(f.tx >> 4, f.tz >> 4), k -> new ArrayList<>())
                    .add(f);
                continue;
            }
            f.due = tick + Math.max(1, wait);
            f.queued = true;
            queue.add(f);
        }
    }

    /** One face's turn: items or fluid moved (0: nothing to do; -1: its block's chunk isn't loaded). */
    int work(PipeNetwork.Face f) {
        if (!world.blockExists(f.tx, f.ty, f.tz)) return -1;
        if (!f.powered()) return 0;
        runs++;
        int n = f.net.kind == PipeKind.ITEM ? ItemMover.work(f) : FluidMover.work(f);
        if (n > 0) {
            moves++;
            moved += n;
        }
        return Math.max(0, n);
    }

    /** Packets handed over from netty's thread. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Runnable r;
        while ((r = PipeActionMessage.TASKS.poll()) != null) {
            try {
                r.run();
            } catch (Throwable t) {
                ValierTweaks.LOG.warn("A pipe menu change failed", t);
            }
        }
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server != null) PipeInfo.refresh(server.getTickCounter()); // WAILA's view of the pipes being looked at
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world.isRemote) return;
        PipeNetworks n = WORLDS.get(event.world);
        if (n == null) return;
        List<PipeNetwork.Face> faces = n.waitingForChunk.remove(chunkKey(event.getChunk().xPosition, event.getChunk().zPosition));
        if (faces == null) return;
        for (PipeNetwork.Face f : faces) {
            if (f.dead || f.queued) continue;
            f.idle = 0;
            f.due = n.tick + 1;
            f.queued = true;
            n.queue.add(f);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        PipeNetworks n = WORLDS.remove(event.world);
        if (n == null) return;
        for (PipeNetwork net : new HashSet<>(n.byPos.values())) for (PipeNetwork.Face f : net.all) {
            f.dead = true;
            if (f.scope != null) unregisterWireless(f);
        }
    }

    /** What /valiertweaks pipes says. */
    public static List<String> stats() {
        List<String> out = new ArrayList<>();
        int nets = 0, faces = 0, active = 0, asleep = 0, big = 0;
        for (PipeNetworks n : WORLDS.values()) {
            Set<PipeNetwork> seen = new HashSet<>(n.byPos.values());
            nets += seen.size();
            for (PipeNetwork net : seen) {
                if (net.tooBig) big++;
                faces += net.all.size();
                for (PipeNetwork.Face f : net.all) {
                    if (!f.cfg.mode.active()) continue;
                    active++;
                    if (f.idle > 0) asleep++;
                }
            }
        }
        out.add(nets + " pipe networks (" + big + " too big), " + faces + " faces touching blocks, " + active
            + " Extract/Request (" + asleep + " waiting for something to do)");
        out.add(runs + " face turns so far, " + moves + " moved something (" + moved + " items or mB); " + overBudget
            + " ticks hit the time budget");
        out.add(String.format("%.1f ms in all on pipes since start", nanos / 1e6));
        return out;
    }

    public static void resetStats() {
        runs = moves = moved = nanos = overBudget = 0;
    }
}
