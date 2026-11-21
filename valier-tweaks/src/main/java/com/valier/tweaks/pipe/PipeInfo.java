package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

/**
 * What a pipe is doing, for WAILA ({@link PipeWaila} hands it over; nothing here needs WAILA) ({@code client.PipeHud} draws it): every face touching a block (mode, tag, priority,
 * spread, list sizes, what it moved and voided over the last minute, the last thing it moved, what it holds back), a
 * creative pipe's source, and the network (pipes, working and sleeping faces, and what is flowing through it: the
 * things its working faces moved most over the last minute). Pipes hold nothing in transit, so that flow is "what's in
 * the pipes".
 * <p>
 * WAILA asks on its network thread and the pipes aren't thread-safe, so asking only marks the pipe as looked at;
 * {@link #refresh} (server thread, every few ticks) builds a fresh compound for each pipe looked at in the last two
 * seconds, and asking hands back a copy of the latest. Items go as items and fluids by registry name, so each player's
 * own game names them.
 */
public final class PipeInfo {

    private PipeInfo() {}

    private static final Map<String, NBTTagCompound> READY = new ConcurrentHashMap<>();
    private static final Map<String, Long> WANTED = new ConcurrentHashMap<>();

    static String key(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    /** WAILA's thread: the pipe at x y z is being looked at; the latest compound for it, or null. */
    static NBTTagCompound wanted(int dim, int x, int y, int z) {
        String k = key(dim, x, y, z);
        WANTED.put(k, System.currentTimeMillis());
        NBTTagCompound ready = READY.get(k);
        return ready == null ? null : (NBTTagCompound) ready.copy();
    }

    /** Server thread: fresh compounds for the pipes someone looked at lately; forgets the rest. */
    static void refresh(long tick) {
        if (WANTED.isEmpty() || tick % 5 != 0) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Long>> it = WANTED.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<String, Long> e = it.next();
            String k = e.getKey();
            if (now - e.getValue() > 2000) {
                it.remove();
                READY.remove(k);
                continue;
            }
            String[] p = k.split(":");
            WorldServer w = DimensionManager.getWorld(Integer.parseInt(p[0]));
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            NBTTagCompound t = w == null || !w.blockExists(x, y, z) ? null : snapshot(w, x, y, z);
            if (t == null) READY.remove(k);
            else READY.put(k, t);
        }
    }

    /** What WAILA shows for the pipe at x y z (server thread), or null if there's no pipe. */
    public static NBTTagCompound snapshot(World w, int x, int y, int z) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return null;
        TilePipe p = (TilePipe) te;
        long tick = w.getTotalWorldTime();
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("Tier", (byte) p.tier());
        t.setBoolean("Fluid", p.kind() == PipeKind.FLUID);
        NBTTagList faces = new NBTTagList();
        for (int s = 0; s <= TilePipe.SOURCE; s++) {
            FaceConfig f = p.face(s); // null where no block is (and side 6 on all but creative pipes)
            if (f == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("Side", (byte) s);
            c.setByte("Mode", (byte) f.mode.ordinal());
            if (!f.tag.isEmpty()) c.setString("Tag", f.tag);
            if (f.priority != 0) c.setInteger("Priority", f.priority);
            c.setByte("Spread", (byte) f.spread.ordinal());
            c.setIntArray("Lists", new int[] { count(f.allow), count(f.deny), count(f.voids) });
            c.setLong("Moved", f.flow.movedPerMinute(tick));
            c.setLong("Voided", f.flow.voidedPerMinute(tick));
            if (f.flow.last != null && f.flow.lastTick >= 0) {
                NBTTagCompound last = thing(f.flow.last);
                last.setInteger("Amount", f.flow.lastAmount);
                last.setBoolean("Void", f.flow.lastVoided);
                last.setLong("Ago", Math.max(0, (tick - f.flow.lastTick) / 20));
                c.setTag("Last", last);
            }
            int held = 0;
            for (ItemStack h : f.heldItems) held += h == null ? 0 : h.stackSize;
            if (held > 0) c.setInteger("HeldItems", held);
            if (f.heldFluid != null && f.heldFluid.amount > 0) c.setInteger("HeldFluid", f.heldFluid.amount);
            faces.appendTag(c);
        }
        t.setTag("Faces", faces);
        PipeNetwork net = PipeNetworks.networkAt(w, x, y, z);
        if (net != null) {
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("Pipes", net.pipes.size());
            n.setBoolean("TooBig", net.tooBig);
            int working = 0, sleeping = 0;
            long voided = 0;
            List<Object> things = new ArrayList<>();
            List<long[]> rates = new ArrayList<>();
            for (PipeNetwork.Face f : net.all) {
                voided += f.cfg.flow.voidedPerMinute(tick);
                if (!f.cfg.mode.active()) continue;
                working++;
                if (f.idle > 0) sleeping++;
                for (int i = 0; i < Flow.THINGS; i++) {
                    Object s = f.cfg.flow.sample(i);
                    long r = f.cfg.flow.perMinute(i, tick);
                    if (s == null || r <= 0) continue;
                    int at = indexOf(things, s);
                    if (at < 0) {
                        things.add(s);
                        rates.add(new long[] { r });
                    } else rates.get(at)[0] += r;
                }
            }
            n.setInteger("Working", working);
            n.setInteger("Sleeping", sleeping);
            n.setLong("Voided", voided);
            NBTTagList flow = new NBTTagList();
            for (int k = 0; k < 4 && !things.isEmpty(); k++) {
                int best = 0;
                for (int i = 1; i < rates.size(); i++) if (rates.get(i)[0] > rates.get(best)[0]) best = i;
                NBTTagCompound c = thing(things.remove(best));
                c.setLong("PerMin", rates.remove(best)[0]);
                flow.appendTag(c);
            }
            n.setTag("Flow", flow);
            t.setTag("Net", n);
        }
        return t;
    }

    private static int count(FilterList l) {
        return l.kind == FaceConfig.Kind.EXISTING ? -1 : l.entries();
    }

    private static int indexOf(List<Object> things, Object s) {
        for (int i = 0; i < things.size(); i++) {
            Object o = things.get(i);
            if (o instanceof ItemStack && s instanceof ItemStack) {
                ItemStack a = (ItemStack) o, b = (ItemStack) s;
                if (a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()) return i;
            } else if (o instanceof FluidStack && s instanceof FluidStack
                && ((FluidStack) o).getFluid() == ((FluidStack) s).getFluid()) return i;
        }
        return -1;
    }

    /** A thing as players' games can name it: an item (1 of it), or a fluid's registry name. */
    private static NBTTagCompound thing(Object s) {
        NBTTagCompound c = new NBTTagCompound();
        if (s instanceof ItemStack) c.setTag("Item", ((ItemStack) s).writeToNBT(new NBTTagCompound()));
        else if (s instanceof FluidStack && ((FluidStack) s).getFluid() != null) c.setString("Fluid", ((FluidStack) s).getFluid()
            .getName());
        return c;
    }
}
