package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * Moves fluid for fluid pipe faces, straight from tank to tank. The fluid is picked from what the tank says it holds
 * and drained by kind (never "whatever comes out"); every stack is copied. Drained first, then filled; what nothing
 * took goes back, or the face holds it until next time. A list entry holding a fluid (a bucket, a cell, an NEI fluid)
 * names that fluid. The three lists work as for items (Deny wins; Void destroys; Allow lets through), and an Allow
 * amount (mB) too: pulling and providing faces give only what is over it, pushing faces fill up to it, Request faces
 * keep it (1,000 mB when not set).
 */
final class FluidMover {

    private FluidMover() {}

    static int work(PipeNetwork.Face f) {
        IFluidHandler tank = own(f);
        if (tank == null) return 0;
        int budget = f.cfg.perTransfer(PipeKind.FLUID, f.tier);
        int moved = handHeld(f, tank, budget);
        if (moved >= budget) return moved;
        if (f.cfg.mode.pulls()) moved += extract(f, tank, budget - moved);
        else if (f.cfg.mode == FaceMode.REQUEST) moved += request(f, tank, budget - moved);
        return moved;
    }

    /**
     * Up to {@code budget} mB out of the tank: what its Void list names is destroyed (over the entry's amount); what
     * may pass goes into Push faces, Request faces short of it, then overflow Provide faces.
     */
    static int extract(PipeNetwork.Face f, IFluidHandler src, int budget) {
        List<PipeNetwork.Face> inserts = PipeNetworks.partners(f, FaceMode.INSERT, false);
        List<PipeNetwork.Face> requests = PipeNetworks.partners(f, FaceMode.REQUEST, false);
        List<PipeNetwork.Face> overflow = PipeNetworks.partners(f, FaceMode.PROVIDE, true);
        boolean anywhere = !(inserts.isEmpty() && requests.isEmpty() && overflow.isEmpty());
        boolean voiding = f.cfg.voids.namesFluids() || f.cfg.voids.kind == FaceConfig.Kind.EXISTING;
        if (!anywhere && !voiding) return 0;
        long tick = f.pipe.getWorldObj()
            .getTotalWorldTime();
        ForgeDirection side = ForgeDirection.getOrientation(f.access);
        List<FluidStack> holds = contents(src, side);
        int moved = 0;
        for (FluidStack held : holds) {
            if (moved >= budget) break;
            if (voiding && f.cfg.voids(held, holds)) {
                int n = Math.min(budget - moved, voidSpare(f.cfg, holds, held));
                if (n <= 0) continue;
                FluidStack gone = src.drain(side, Fluids.copy(held, n), true);
                if (gone == null || gone.amount <= 0) continue;
                f.cfg.flow.add(gone, gone.amount, true, tick);
                moved += gone.amount;
                continue;
            }
            if (!anywhere || !f.cfg.passes(held, holds)) continue;
            int spare = Math.min(spare(f.cfg, holds, held), budget - moved);
            if (spare <= 0) continue;
            FluidStack can = src.drain(side, Fluids.copy(held, spare), false);
            if (can == null || can.amount <= 0 || can.getFluid() != held.getFluid()) continue;
            int room = room(src, can, inserts, requests, overflow);
            int n = Math.min(can.amount, room);
            if (n <= 0) continue;
            FluidStack got = src.drain(side, Fluids.copy(can, n), true);
            if (got == null || got.amount <= 0) continue;
            got = got.copy();
            int took = got.amount;
            FluidStack left = deliver(f, src, got, inserts, requests, overflow);
            int back = left == null ? 0 : left.amount;
            f.cfg.flow.add(got, took - back, false, tick);
            if (f.source != null) left = null; // a creative source: what nobody took just goes
            if (left != null) {
                int returned = src.fill(side, left.copy(), true);
                left.amount -= returned;
                if (left.amount > 0) hold(f, left);
            }
            moved += took - back;
        }
        return moved;
    }

    /** mB of a fluid a pulling or providing face may give: all, or what is over its Allow amount. */
    static int spare(FaceConfig cfg, List<FluidStack> holds, FluidStack fluid) {
        return over(cfg.stockOf(fluid), holds, fluid);
    }

    /** mB of a fluid a pulling face may destroy: all, or what is over its Void entry's amount. */
    static int voidSpare(FaceConfig cfg, List<FluidStack> holds, FluidStack fluid) {
        return over(cfg.voids.stockOf(fluid), holds, fluid);
    }

    private static int over(int keep, List<FluidStack> holds, FluidStack fluid) {
        if (keep <= 0) return Integer.MAX_VALUE;
        return have(holds, fluid) - keep;
    }

    static int have(List<FluidStack> holds, FluidStack fluid) {
        int have = 0;
        for (FluidStack s : holds) if (s.getFluid() == fluid.getFluid()) have += s.amount;
        return have;
    }

    @SuppressWarnings("unchecked")
    static int room(IFluidHandler src, FluidStack can, List<PipeNetwork.Face> inserts, List<PipeNetwork.Face> requests,
        List<PipeNetwork.Face> overflow) {
        int room = 0;
        for (List<PipeNetwork.Face> group : new List[] { inserts, requests, overflow })
            for (PipeNetwork.Face d : group) if (room < can.amount) room += accepts(d, src, Fluids.copy(can, can.amount - room));
        return room;
    }

    /**
     * How much of s a destination face takes now: a Request face's shortfall; a face whose Void list names it, all of
     * it (what doesn't fit is destroyed); otherwise what its Allow list, amounts and tank let in.
     */
    static int accepts(PipeNetwork.Face d, IFluidHandler src, FluidStack s) {
        if (s == null || s.amount <= 0) return 0;
        IFluidHandler t = tank(d.pipe.getWorldObj(), d.tx, d.ty, d.tz);
        if (t == null || t == src) return 0;
        ForgeDirection side = ForgeDirection.getOrientation(d.access);
        List<FluidStack> holds = contents(t, side);
        if (d.cfg.mode == FaceMode.REQUEST) {
            if (d.cfg.deny.matches(s, holds)) return 0;
            int short_ = shortOf(d, t, s);
            if (short_ <= 0) return 0;
            return Math.max(0, t.fill(side, Fluids.copy(s, Math.min(s.amount, short_)), false));
        }
        if (d.cfg.voids(s, holds)) return s.amount;
        return fits(d, t, holds, s, s.amount);
    }

    /** What a pushing face's tank takes of s by the face's lists and amounts (up to max). */
    static int fits(PipeNetwork.Face d, IFluidHandler t, List<FluidStack> holds, FluidStack s, int max) {
        if (!d.cfg.passes(s, holds)) return 0;
        int keep = d.cfg.stockOf(s);
        if (keep > 0) max = Math.min(max, keep - have(holds, s));
        if (max <= 0) return 0;
        return Math.max(0, t.fill(ForgeDirection.getOrientation(d.access), Fluids.copy(s, max), false));
    }

    /** As {@link ItemMover#deliver}: groups in order, priorities high to low, the spread setting within one. */
    static FluidStack deliver(PipeNetwork.Face f, IFluidHandler src, FluidStack s, List<PipeNetwork.Face> inserts,
        List<PipeNetwork.Face> requests, List<PipeNetwork.Face> overflow) {
        long tick = f.pipe.getWorldObj()
            .getTotalWorldTime();
        deliverGroup(f, src, s, inserts, tick);
        deliverGroup(f, src, s, requests, tick);
        deliverGroup(f, src, s, overflow, tick);
        f.cfg.turn++;
        return s.amount <= 0 ? null : s;
    }

    private static void deliverGroup(PipeNetwork.Face f, IFluidHandler src, FluidStack s, List<PipeNetwork.Face> g,
        long tick) {
        int n = g.size();
        for (int i = 0; i < n && s.amount > 0;) {
            int j = i + 1;
            int p = g.get(i).cfg.priority;
            while (j < n && g.get(j).cfg.priority == p) j++;
            int k = j - i;
            if (f.cfg.spread == FaceConfig.Spread.EVEN && k > 1) even(f, src, s, g, i, k, tick);
            else {
                int start = f.cfg.spread == FaceConfig.Spread.ROUND_ROBIN ? Math.floorMod(f.cfg.turn, k) : 0;
                for (int x = 0; x < k && s.amount > 0; x++) give(g.get(i + (start + x) % k), src, s, s.amount, tick);
            }
            i = j;
        }
    }

    private static void even(PipeNetwork.Face f, IFluidHandler src, FluidStack s, List<PipeNetwork.Face> g, int from,
        int k, long tick) {
        int[] room = new int[k];
        int live = 0;
        for (int x = 0; x < k; x++) if ((room[x] = accepts(g.get(from + x), src, s)) > 0) live++;
        int start = Math.floorMod(f.cfg.turn, k);
        while (s.amount > 0 && live > 0) {
            int share = Math.max(1, s.amount / live);
            int gave = 0;
            for (int x = 0; x < k && s.amount > 0; x++) {
                int i = (start + x) % k;
                if (room[i] <= 0) continue;
                int offer = Math.min(share, room[i]);
                int took = give(g.get(from + i), src, s, offer, tick);
                room[i] = took < offer ? 0 : room[i] - took;
                if (room[i] <= 0) live--;
                gave += took;
            }
            if (gave == 0) break;
        }
    }

    /** Gives up to max mB of s to one destination (taking it off s): into its tank what fits, the rest destroyed if its Void list names it. */
    static int give(PipeNetwork.Face d, IFluidHandler src, FluidStack s, int max, long tick) {
        FluidStack offer = Fluids.copy(s, Math.min(max, s.amount));
        int take = Math.min(offer == null ? 0 : offer.amount, accepts(d, src, offer));
        if (take <= 0) return 0;
        IFluidHandler t = tank(d.pipe.getWorldObj(), d.tx, d.ty, d.tz);
        ForgeDirection side = ForgeDirection.getOrientation(d.access);
        List<FluidStack> holds = contents(t, side);
        boolean voids = d.cfg.mode != FaceMode.REQUEST && d.cfg.voids(s, holds);
        int fit = voids ? fits(d, t, holds, s, take) : take;
        int filled = fit > 0 ? Math.max(0, Math.min(fit, t.fill(side, Fluids.copy(s, fit), true))) : 0;
        if (filled > 0 && !d.cfg.mode.active()) d.cfg.flow.add(s, filled, false, tick);
        int gone = voids ? take : filled;
        if (voids && take > filled) d.cfg.flow.add(s, take - filled, true, tick);
        s.amount -= gone;
        return gone;
    }

    /** Request: each fluid its filter names, topped up to its amount from Provide faces (what they have spare). */
    static int request(PipeNetwork.Face f, IFluidHandler dst, int budget) {
        List<PipeNetwork.Face> providers = PipeNetworks.partners(f, FaceMode.PROVIDE, false);
        if (providers.isEmpty()) return 0;
        ForgeDirection dside = ForgeDirection.getOrientation(f.access);
        long tick = f.pipe.getWorldObj()
            .getTotalWorldTime();
        int moved = 0;
        for (int i = 0; i < FaceConfig.FILTER && moved < budget; i++) {
            FluidStack want = Fluids.of(f.cfg.allow.filter[i]);
            if (want == null || f.cfg.deny.matches(want, contents(dst, dside))) continue;
            int need = Math.min(shortOf(f, dst, want), budget - moved);
            for (PipeNetwork.Face p : providers) {
                if (need <= 0) break;
                IFluidHandler src = own(p);
                if (src == null || src == dst) continue;
                ForgeDirection sside = ForgeDirection.getOrientation(p.access);
                List<FluidStack> holds = contents(src, sside);
                if (!p.cfg.passes(want, holds)) continue;
                int spare = Math.min(need, spare(p.cfg, holds, want));
                if (spare <= 0) continue;
                FluidStack can = src.drain(sside, Fluids.copy(want, spare), false);
                if (can == null || can.amount <= 0 || can.getFluid() != want.getFluid()) continue;
                int room = dst.fill(dside, can.copy(), false);
                int n = Math.min(can.amount, room);
                if (n <= 0) break;
                FluidStack got = src.drain(sside, Fluids.copy(can, n), true);
                if (got == null || got.amount <= 0) continue;
                got = got.copy();
                int filled = dst.fill(dside, got.copy(), true);
                FluidStack left = p.source != null ? null : Fluids.copy(got, got.amount - filled);
                if (left != null) {
                    left.amount -= src.fill(sside, left.copy(), true);
                    if (left.amount > 0) hold(f, left);
                }
                f.cfg.flow.add(got, filled, false, tick);
                moved += filled;
                need -= filled;
            }
        }
        return moved;
    }

    /** How much more of that fluid a Request face wants in its tank (its amount, 1,000 mB when not set). */
    static int shortOf(PipeNetwork.Face f, IFluidHandler t, FluidStack fluid) {
        int keep = 0;
        boolean named = false;
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            FluidStack w = Fluids.of(f.cfg.allow.filter[i]);
            if (w == null || w.getFluid() != fluid.getFluid()) continue;
            named = true;
            keep = Math.max(keep, f.cfg.allow.count[i] > 0 ? f.cfg.allow.count[i] : 1000);
        }
        if (!named) return 0;
        int have = 0;
        for (FluidStack s : contents(t, ForgeDirection.getOrientation(f.access))) if (s.getFluid() == fluid.getFluid()) have += s.amount;
        return keep - have;
    }

    static int handHeld(PipeNetwork.Face f, IFluidHandler own, int budget) {
        FluidStack h = f.cfg.heldFluid;
        if (h == null || h.amount <= 0) return 0;
        int before = h.amount;
        if (f.cfg.mode.pulls()) {
            f.cfg.heldFluid = deliver(f, own, h.copy(), PipeNetworks.partners(f, FaceMode.INSERT, false),
                PipeNetworks.partners(f, FaceMode.REQUEST, false), PipeNetworks.partners(f, FaceMode.PROVIDE, true));
        }
        if (f.cfg.heldFluid != null) {
            int in = own.fill(ForgeDirection.getOrientation(f.access), f.cfg.heldFluid.copy(), true);
            f.cfg.heldFluid.amount -= in;
            if (f.cfg.heldFluid.amount <= 0) f.cfg.heldFluid = null;
        }
        f.pipe.markDirty();
        return before - (f.cfg.heldFluid == null ? 0 : f.cfg.heldFluid.amount);
    }

    static void hold(PipeNetwork.Face f, FluidStack s) {
        if (f.cfg.heldFluid == null) f.cfg.heldFluid = s.copy();
        else if (f.cfg.heldFluid.isFluidEqual(s)) f.cfg.heldFluid.amount += s.amount;
        // another fluid than it already holds: lost (only a tank that gives a different fluid than it said can do this)
        f.pipe.markDirty();
    }

    /** The tank a face works on: the block it touches, or a creative pipe's endless source. */
    static IFluidHandler own(PipeNetwork.Face f) {
        return f.source != null ? f.source : tank(f.pipe.getWorldObj(), f.tx, f.ty, f.tz);
    }

    static IFluidHandler tank(World w, int x, int y, int z) {
        if (y < 0 || y >= w.getHeight() || !w.blockExists(x, y, z)) return null;
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof IFluidHandler && !te.isInvalid() && !(te instanceof TilePipe) ? (IFluidHandler) te : null;
    }

    /** Copies of what the tank holds, as it says, from that side. */
    static List<FluidStack> contents(IFluidHandler t, ForgeDirection side) {
        List<FluidStack> out = new ArrayList<>();
        FluidTankInfo[] info;
        try {
            info = t.getTankInfo(side);
        } catch (Throwable e) {
            return out;
        }
        if (info == null) return out;
        for (FluidTankInfo i : info) if (i != null && i.fluid != null && i.fluid.amount > 0 && i.fluid.getFluid() != null) out.add(i.fluid.copy());
        return out;
    }
}
