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
 * took goes back, or the face holds it until next time. A filter item holding a fluid (a bucket, a cell, an NEI fluid)
 * names that fluid; its amount (mB) works as for items: pulling and providing faces give only what is over it, pushing
 * faces fill up to it, Request faces keep it (1,000 mB when not set).
 */
final class FluidMover {

    private FluidMover() {}

    static int work(PipeNetwork.Face f) {
        IFluidHandler tank = tank(f.pipe.getWorldObj(), f.tx, f.ty, f.tz);
        if (tank == null) return 0;
        int budget = f.cfg.perTransfer(PipeKind.FLUID, f.tier);
        int moved = handHeld(f, tank, budget);
        if (moved >= budget) return moved;
        if (f.cfg.mode.pulls()) moved += extract(f, tank, budget - moved);
        else if (f.cfg.mode == FaceMode.REQUEST) moved += request(f, tank, budget - moved);
        return moved;
    }

    @SuppressWarnings("unchecked")
    static int extract(PipeNetwork.Face f, IFluidHandler src, int budget) {
        List<PipeNetwork.Face> inserts = PipeNetworks.partners(f, FaceMode.INSERT, false);
        List<PipeNetwork.Face> requests = PipeNetworks.partners(f, FaceMode.REQUEST, false);
        List<PipeNetwork.Face> overflow = PipeNetworks.partners(f, FaceMode.PROVIDE, true);
        if (inserts.isEmpty() && requests.isEmpty() && overflow.isEmpty()) return 0;
        ForgeDirection side = ForgeDirection.getOrientation(f.access);
        List<FluidStack> holds = contents(src, side);
        int moved = 0;
        for (FluidStack held : holds) {
            if (moved >= budget) break;
            if (!f.cfg.passes(held, holds)) continue;
            int spare = spare(f.cfg, holds, held);
            FluidStack can = src.drain(side, Fluids.copy(held, Math.min(spare, budget - moved)), false);
            if (can == null || can.amount <= 0 || can.getFluid() != held.getFluid()) continue;
            int room = 0;
            for (List<PipeNetwork.Face> group : new List[] { inserts, requests, overflow })
                for (PipeNetwork.Face d : group) if (room < can.amount) room += accepts(d, src, Fluids.copy(can, can.amount - room));
            int n = Math.min(can.amount, room);
            if (n <= 0) continue;
            FluidStack got = src.drain(side, Fluids.copy(can, n), true);
            if (got == null || got.amount <= 0) continue;
            got = got.copy();
            int took = got.amount;
            FluidStack left = deliver(f, src, got, inserts, requests, overflow);
            int back = left == null ? 0 : left.amount;
            if (left != null) {
                int returned = src.fill(side, left.copy(), true);
                left.amount -= returned;
                if (left.amount > 0) hold(f, left);
            }
            moved += took - back;
        }
        return moved;
    }

    /** mB of a fluid a pulling or providing face may give: all, or what is over its filter amount. */
    static int spare(FaceConfig cfg, List<FluidStack> holds, FluidStack fluid) {
        int keep = cfg.stockOf(fluid);
        if (keep <= 0) return Integer.MAX_VALUE;
        int have = 0;
        for (FluidStack s : holds) if (s.getFluid() == fluid.getFluid()) have += s.amount;
        return have - keep;
    }

    static int accepts(PipeNetwork.Face d, IFluidHandler src, FluidStack s) {
        if (s == null || s.amount <= 0) return 0;
        IFluidHandler t = tank(d.pipe.getWorldObj(), d.tx, d.ty, d.tz);
        if (t == null || t == src) return 0;
        ForgeDirection side = ForgeDirection.getOrientation(d.access);
        int max = s.amount;
        if (d.cfg.mode == FaceMode.REQUEST) {
            int short_ = shortOf(d, t, s);
            if (short_ <= 0) return 0;
            max = Math.min(max, short_);
        } else {
            List<FluidStack> holds = contents(t, side);
            if (!d.cfg.passes(s, holds)) return 0;
            int keep = d.cfg.stockOf(s);
            if (keep > 0) {
                int have = 0;
                for (FluidStack h : holds) if (h.getFluid() == s.getFluid()) have += h.amount;
                max = Math.min(max, keep - have);
            }
            if (max <= 0) return 0;
        }
        return Math.max(0, t.fill(side, Fluids.copy(s, max), false));
    }

    @SuppressWarnings("unchecked")
    static FluidStack deliver(PipeNetwork.Face f, IFluidHandler src, FluidStack s, List<PipeNetwork.Face> inserts,
        List<PipeNetwork.Face> requests, List<PipeNetwork.Face> overflow) {
        for (List<PipeNetwork.Face> group : new List[] { inserts, requests, overflow }) {
            int n = group.size();
            if (n == 0) continue;
            int startAt = Math.floorMod(f.turn, n);
            for (int i = 0; i < n && s.amount > 0; i++) {
                PipeNetwork.Face d = group.get((startAt + i) % n);
                int take = accepts(d, src, s);
                if (take <= 0) continue;
                IFluidHandler t = tank(d.pipe.getWorldObj(), d.tx, d.ty, d.tz);
                int filled = t.fill(ForgeDirection.getOrientation(d.access), Fluids.copy(s, take), true);
                s.amount -= Math.max(0, Math.min(filled, take));
            }
            f.turn++;
        }
        return s.amount <= 0 ? null : s;
    }

    /** Request: each fluid its filter names, topped up to its amount from Provide faces (what they have spare). */
    static int request(PipeNetwork.Face f, IFluidHandler dst, int budget) {
        List<PipeNetwork.Face> providers = PipeNetworks.partners(f, FaceMode.PROVIDE, false);
        if (providers.isEmpty()) return 0;
        ForgeDirection dside = ForgeDirection.getOrientation(f.access);
        int moved = 0;
        for (int i = 0; i < FaceConfig.FILTER && moved < budget; i++) {
            FluidStack want = Fluids.of(f.cfg.filter[i]);
            if (want == null) continue;
            int need = Math.min(shortOf(f, dst, want), budget - moved);
            for (PipeNetwork.Face p : providers) {
                if (need <= 0) break;
                IFluidHandler src = tank(p.pipe.getWorldObj(), p.tx, p.ty, p.tz);
                if (src == null || src == dst) continue;
                ForgeDirection sside = ForgeDirection.getOrientation(p.access);
                List<FluidStack> holds = contents(src, sside);
                if (!p.cfg.passes(want, holds)) continue;
                int spare = spare(p.cfg, holds, want);
                FluidStack can = src.drain(sside, Fluids.copy(want, Math.min(need, spare)), false);
                if (can == null || can.amount <= 0 || can.getFluid() != want.getFluid()) continue;
                int room = dst.fill(dside, can.copy(), false);
                int n = Math.min(can.amount, room);
                if (n <= 0) break;
                FluidStack got = src.drain(sside, Fluids.copy(can, n), true);
                if (got == null || got.amount <= 0) continue;
                got = got.copy();
                int filled = dst.fill(dside, got.copy(), true);
                FluidStack left = Fluids.copy(got, got.amount - filled);
                if (left != null) {
                    left.amount -= src.fill(sside, left.copy(), true);
                    if (left.amount > 0) hold(f, left);
                }
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
            FluidStack w = Fluids.of(f.cfg.filter[i]);
            if (w == null || w.getFluid() != fluid.getFluid()) continue;
            named = true;
            keep = Math.max(keep, f.cfg.count[i] > 0 ? f.cfg.count[i] : 1000);
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
