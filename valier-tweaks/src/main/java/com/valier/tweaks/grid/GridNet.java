package com.valier.tweaks.grid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.valier.tweaks.TweaksConfig;
import com.valier.tweaks.pipe.TeamScope;

/**
 * One Grid network: its loaded plugs, points, cells and controllers, and the energy moving between them, once a tick.
 * <ol>
 * <li>What there is to give: the plugs' buffers (EU, and RF kept apart unless {@code gridRfToEu}) and what the cells
 * can give this tick. Nothing to give: nothing else is done (no point is even asked).</li>
 * <li>Points by priority, highest first (Surge before all); points of the same priority share what is left in
 * proportion to what each wants (all of it when there is enough).</li>
 * <li>What was used comes out of the plugs first, then the cells; what the plugs still hold charges the cells.</li>
 * <li>Every 10 ticks, controllers charge their team's players' items.</li>
 * </ol>
 * The cost is one pass over its blocks a tick; neighbours are looked up again only when they change.
 */
public final class GridNet {

    final String scope, name;
    final List<TileGridPlug> plugs = new ArrayList<>();
    final List<TileGridPoint> points = new ArrayList<>();
    final List<TileGridCell> cells = new ArrayList<>();
    final List<TileGridController> controllers = new ArrayList<>();
    private boolean sorted;
    /** Which point of a priority goes first with what is left over (it moves on each tick). */
    private long turn;
    // Statistics: EU (RF counted as EU) in and out, per second, over the last minute.
    final long[] inPerSecond = new long[60], outPerSecond = new long[60];
    /** What each point of one priority wants and got this tick (kept, so a tick allocates nothing). */
    private long[] wantBuf = new long[8], gotBuf = new long[8];
    private long inNow, outNow;
    /** EU (RF counted as EU) lost on the way ({@code gridLossPercent}) this second, and last second. */
    long lostNow, lastLost;
    /** Its colour (a dye number), kept here for WAILA, which asks from its own thread ({@link GridColours} on the server's). */
    volatile int colour;
    private int ticks;
    long lastIn, lastOut;

    GridNet(String scope, String name) {
        this.scope = scope;
        this.name = name;
    }

    void add(TileGrid t) {
        remove(t);
        switch (t.kind()) {
            case TileGrid.PLUG:
                plugs.add((TileGridPlug) t);
                break;
            case TileGrid.POINT:
                points.add((TileGridPoint) t);
                break;
            case TileGrid.CELL:
                cells.add((TileGridCell) t);
                break;
            default:
                controllers.add((TileGridController) t);
        }
        sorted = false;
    }

    void remove(TileGrid t) {
        plugs.remove(t);
        points.remove(t);
        cells.remove(t);
        controllers.remove(t);
    }

    void resort() {
        sorted = false;
    }

    boolean isEmpty() {
        return plugs.isEmpty() && points.isEmpty() && cells.isEmpty() && controllers.isEmpty();
    }

    public int blocks() {
        return plugs.size() + points.size() + cells.size() + controllers.size();
    }

    long stored() {
        long s = 0;
        for (TileGridCell c : cells) s += c.stored;
        return s;
    }

    long capacity() {
        long s = 0;
        for (TileGridCell c : cells) s += c.capacity();
        return s;
    }

    long minuteIn() {
        long s = 0;
        for (long v : inPerSecond) s += v;
        return s;
    }

    long minuteOut() {
        long s = 0;
        for (long v : outPerSecond) s += v;
        return s;
    }

    private static final Comparator<TileGrid> BY_PRIORITY = (a, b) -> Integer.compare(b.effectivePriority(), a.effectivePriority());

    void tick(long worldTick) {
        if (!sorted) {
            points.sort(BY_PRIORITY);
            cells.sort(BY_PRIORITY);
            sorted = true;
        }
        double per = GridEnergy.rfPerEu();
        long supply = 0, rf = 0;
        for (TileGridPlug p : plugs) {
            if (TweaksConfig.gridRfToEu && p.rf > 0) { // RF may become EU (off by default, as in GregTech)
                long eu = Math.min((long) (p.rf / per), p.euCap() - p.eu);
                if (eu > 0) {
                    p.eu += eu;
                    p.rf -= (long) Math.ceil(eu * per);
                    if (p.rf < 0) p.rf = 0;
                }
            }
            supply += p.eu;
            rf += p.rf;
            inNow += p.inThisTick + (long) (p.rfThisTick / per);
        }
        long cellGive = 0;
        for (TileGridCell c : cells) if (c.active()) cellGive += Math.min(c.stored, c.maxPerTick());
        long out = 0, rfOut = 0;
        if (supply + cellGive > 0 || rf > 0) {
            // With a loss, what may arrive is what there is less the loss on it (what arrives costs that much more).
            int loss = TweaksConfig.gridLossPercent;
            long pool = loss > 0 ? (supply + cellGive) * 100 / (100 + loss) : supply + cellGive;
            long rfPool = loss > 0 ? rf * 100 / (100 + loss) : rf;
            int i = 0, n = points.size();
            while (i < n && (pool > 0 || rfPool > 0)) {
                int pr = points.get(i)
                    .effectivePriority();
                int j = i;
                while (j < n && points.get(j)
                    .effectivePriority() == pr) j++;
                if (wantBuf.length < j - i) {
                    wantBuf = new long[j - i];
                    gotBuf = new long[j - i];
                }
                long[] want = wantBuf, got = gotBuf;
                java.util.Arrays.fill(got, 0, j - i, 0L);
                long total = 0;
                for (int k = i; k < j; k++) {
                    want[k - i] = points.get(k)
                        .demand();
                    total += want[k - i];
                }
                // Short of energy: first each gets its part of what there is (by what it wants); GregTech moves EU in
                // whole packets, so what is left (parts too small for a packet) then goes round, starting with another
                // point each tick, so over time equal points get equal amounts.
                if (total > pool) {
                    for (int k = i; k < j && pool > 0; k++) {
                        long share = total > 0 ? (long) ((double) pool * want[k - i] / total) : 0;
                        if (share <= 0) continue;
                        long used = points.get(k)
                            .deliver(Math.min(share, pool), 0);
                        pool -= used;
                        out += used;
                        got[k - i] += used;
                        points.get(k).moved += used;
                    }
                }
                int g = j - i, start = (int) (turn++ % Math.max(1, g));
                for (int r = 0; r < g && (pool > 0 || rfPool > 0); r++) {
                    int k = i + (start + r) % g;
                    TileGridPoint p = points.get(k);
                    long rest = Math.min(pool, Math.max(0, want[k - i] - got[k - i]));
                    if (rest <= 0 && rfPool <= 0) continue;
                    long used = p.deliver(rest, rfPool), usedRf = p.deliveredRf;
                    pool -= used;
                    rfPool -= usedRf;
                    out += used;
                    rfOut += usedRf;
                    p.moved += used + (long) (usedRf / per);
                }
                i = j;
            }
            if (controllers.size() > 0 && worldTick % 10 == 0 && pool > 0) out += charge(pool);
            long lost = loss > 0 ? lossOn(out, loss) : 0, lostRf = loss > 0 ? lossOn(rfOut, loss) : 0;
            lostNow += lost + (long) (lostRf / per);
            take(out + lost, rfOut + lostRf);
        }
        // What the plugs still hold charges the cells.
        long surplus = 0;
        for (TileGridPlug p : plugs) surplus += p.eu;
        for (TileGridCell c : cells) {
            if (surplus <= 0) break;
            if (!c.active()) continue;
            long put = Math.min(surplus, Math.min(c.capacity() - c.stored, c.maxPerTick()));
            if (put <= 0) continue;
            c.stored += put;
            c.moved += put;
            surplus -= put;
            c.markChunk();
            drainPlugs(put);
        }
        outNow += out + (long) (rfOut / per);
        for (TileGridPlug p : plugs) {
            p.moved += p.inThisTick;
            p.newTick();
        }
        if (++ticks % 20 == 0) {
            int s = (ticks / 20) % 60;
            inPerSecond[s] = inNow;
            outPerSecond[s] = outNow;
            lastIn = inNow;
            lastOut = outNow;
            lastLost = lostNow;
            inNow = 0;
            outNow = 0;
            lostNow = 0;
            for (TileGrid t : all()) {
                t.movedLast = t.moved;
                t.moved = 0;
            }
        }
    }

    /** The loss on delivering n at loss percent (rounded up). */
    static long lossOn(long n, int loss) {
        return n <= 0 ? 0 : (n * loss + 99) / 100;
    }

    List<TileGrid> all() {
        List<TileGrid> out = new ArrayList<>(blocks());
        out.addAll(plugs);
        out.addAll(points);
        out.addAll(cells);
        out.addAll(controllers);
        return out;
    }

    /** EU used this tick comes out of the plugs, then the cells; RF out of the plugs' RF. */
    private void take(long eu, long rfUsed) {
        long left = drainPlugs(eu);
        for (TileGridCell c : cells) {
            if (left <= 0) break;
            if (!c.active()) continue;
            long k = Math.min(left, Math.min(c.stored, c.maxPerTick()));
            if (k <= 0) continue;
            c.stored -= k;
            c.moved += k;
            left -= k;
            c.markChunk();
        }
        for (TileGridPlug p : plugs) {
            if (rfUsed <= 0) break;
            long k = Math.min(rfUsed, p.rf);
            p.rf -= k;
            rfUsed -= k;
        }
    }

    /** Takes eu out of the plugs' buffers; returns what they didn't have. */
    private long drainPlugs(long eu) {
        for (TileGridPlug p : plugs) {
            if (eu <= 0) break;
            long k = Math.min(eu, p.eu);
            p.eu -= k;
            eu -= k;
        }
        return eu;
    }

    /** Controllers charge the team's online players' items; returns EU used. */
    private long charge(long pool) {
        long budget = 0;
        for (TileGridController c : controllers) if (c.active()) budget += c.maxPerTick() * 10;
        budget = Math.min(budget, pool);
        if (budget <= 0) return 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return 0;
        TileGridController first = controllers.get(0);
        long used = 0;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (used >= budget) break;
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (!scope.equals(String.valueOf(TeamScope.of(p.getUniqueID())))) continue;
            boolean skip = false;
            for (TileGridController c : controllers) skip |= c.skip.contains(p.getUniqueID());
            if (skip) continue;
            if (!TweaksConfig.gridControllerCrossDimension && p.worldObj != first.getWorldObj()) continue;
            long n = GridEnergy.charge(p, budget - used);
            if (n > 0) {
                used += n;
                p.inventoryContainer.detectAndSendChanges();
            }
        }
        for (TileGridController c : controllers) c.moved += used / Math.max(1, controllers.size());
        return used;
    }
}
