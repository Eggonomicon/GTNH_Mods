package com.valier.tweaks.pipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * What one face moved and voided over the last minute (twelve 5-second buckets), for WAILA. Kept with the face's
 * settings (so a network rebuild doesn't clear it), never saved. Cheap on the hot path: a transfer adds to a couple of
 * longs and compares item and meta; names are only made when someone looks (on the server thread).
 */
public final class Flow {

    static final int BUCKETS = 12, BUCKET_TICKS = 100, THINGS = 4;

    private long bucket = Long.MIN_VALUE;
    private final long[] moved = new long[BUCKETS], voided = new long[BUCKETS];
    /** Up to four things it moved lately: a sample (a 1-item copy or a 1 mB copy) and its totals per bucket. */
    private final Object[] samples = new Object[THINGS];
    private final long[][] byThing = new long[THINGS][BUCKETS];

    /** The last thing it moved or voided (a copy), how much, and when. */
    public Object last;
    public int lastAmount;
    public boolean lastVoided;
    public long lastTick = -1;

    public void add(ItemStack s, int amount, boolean voids, long tick) {
        if (s == null || amount <= 0) return;
        add(s, amount, voids, tick, true);
    }

    public void add(FluidStack f, int amount, boolean voids, long tick) {
        if (f == null || amount <= 0) return;
        add(f, amount, voids, tick, false);
    }

    private void add(Object thing, int amount, boolean voids, long tick, boolean item) {
        roll(tick);
        int b = (int) Math.floorMod(bucket, BUCKETS);
        (voids ? voided : moved)[b] += amount;
        int i = slotFor(thing, item);
        if (i < 0) {
            i = freeSlot();
            samples[i] = copy(thing, item);
            java.util.Arrays.fill(byThing[i], 0);
        }
        if (!voids) byThing[i][b] += amount;
        last = samples[i];
        lastAmount = amount;
        lastVoided = voids;
        lastTick = tick;
    }

    private static Object copy(Object thing, boolean item) {
        if (item) {
            ItemStack c = ((ItemStack) thing).copy();
            c.stackSize = 1;
            return c;
        }
        FluidStack c = ((FluidStack) thing).copy();
        c.amount = 1;
        return c;
    }

    private static boolean alike(Object sample, Object thing, boolean item) {
        if (sample == null) return false;
        if (item) {
            if (!(sample instanceof ItemStack)) return false;
            ItemStack a = (ItemStack) sample, b = (ItemStack) thing;
            return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
        }
        return sample instanceof FluidStack && ((FluidStack) sample).getFluid() == ((FluidStack) thing).getFluid();
    }

    private int slotFor(Object thing, boolean item) {
        for (int i = 0; i < THINGS; i++) if (alike(samples[i], thing, item)) return i;
        return -1;
    }

    /** An empty slot, else the one that moved least over the minute. */
    private int freeSlot() {
        int best = 0;
        long least = Long.MAX_VALUE;
        for (int i = 0; i < THINGS; i++) {
            if (samples[i] == null) return i;
            long t = sum(byThing[i]);
            if (t < least) {
                least = t;
                best = i;
            }
        }
        return best;
    }

    private void roll(long tick) {
        long now = tick / BUCKET_TICKS;
        if (bucket == Long.MIN_VALUE) {
            bucket = now;
            return;
        }
        if (now <= bucket) return;
        long steps = Math.min(BUCKETS, now - bucket);
        for (long k = 1; k <= steps; k++) {
            int b = (int) Math.floorMod(bucket + k, BUCKETS);
            moved[b] = 0;
            voided[b] = 0;
            for (long[] t : byThing) t[b] = 0;
        }
        bucket = now;
    }

    private static long sum(long[] a) {
        long s = 0;
        for (long v : a) s += v;
        return s;
    }

    public long movedPerMinute(long tick) {
        roll(tick);
        return sum(moved);
    }

    public long voidedPerMinute(long tick) {
        roll(tick);
        return sum(voided);
    }

    /** How many of thing i it moved over the minute (0 if that slot is empty). */
    public long perMinute(int i, long tick) {
        roll(tick);
        return samples[i] == null ? 0 : sum(byThing[i]);
    }

    /** Thing i (a 1-item ItemStack or a 1 mB FluidStack), or null. */
    public Object sample(int i) {
        return samples[i];
    }
}
