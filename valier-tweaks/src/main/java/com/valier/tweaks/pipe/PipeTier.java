package com.valier.tweaks.pipe;

import com.valier.tweaks.TweaksConfig;

/**
 * The pipe tiers (block metadata 0 to 6; the ones added in 2.1 come after Wireless, so older pipes keep theirs). How much
 * one transfer may move and how often an active face works come from the config ({@code pipes} section); the face's own
 * amount can be set lower in its menu.
 */
public final class PipeTier {

    public static final int BASIC = 0, ADVANCED = 1, ELITE = 2, WIRELESS = 3, SUPERIOR = 4, ULTIMATE = 5, CREATIVE = 6,
        COUNT = 7;
    public static final String[] NAMES = { "Basic", "Advanced", "Elite", "Wireless", "Superior", "Ultimate", "Creative" };
    /** The creative tab's and NEI's order: by speed, then Wireless and Creative. */
    public static final int[] ORDER = { BASIC, ADVANCED, ELITE, SUPERIOR, ULTIMATE, WIRELESS, CREATIVE };
    /**
     * Defaults, by metadata. Superior and Ultimate get their speed from bigger batches at Elite's interval, not from
     * running more often, so a face costs the server about the same.
     */
    public static final int[] ITEMS = { 8, 32, 64, 64, 160, 320, 4096 };
    public static final int[] FLUIDS = { 1000, 4000, 16000, 16000, 40000, 160000, 1000000 };
    public static final int[] TICKS = { 20, 10, 5, 5, 5, 5, 5 };

    private PipeTier() {}

    public static int clamp(int tier) {
        return Math.max(0, Math.min(COUNT - 1, tier));
    }

    /** Tiers whose tagged faces link to faces of the same tag anywhere (same team). */
    public static boolean wireless(int tier) {
        return tier == WIRELESS || tier == CREATIVE;
    }

    /** Items one transfer may move. */
    public static int maxItems(int tier) {
        return pick(TweaksConfig.pipeItems, tier, ITEMS);
    }

    /** Fluid (mB) one transfer may move. */
    public static int maxFluid(int tier) {
        return pick(TweaksConfig.pipeFluids, tier, FLUIDS);
    }

    /** Ticks between an active face's transfers. */
    public static int interval(int tier) {
        return Math.max(1, pick(TweaksConfig.pipeTicks, tier, TICKS));
    }

    public static int max(PipeKind kind, int tier) {
        return kind == PipeKind.ITEM ? maxItems(tier) : maxFluid(tier);
    }

    private static int pick(int[] values, int tier, int[] defaults) {
        int t = clamp(tier);
        if (values == null || t >= values.length) return defaults[t];
        return Math.max(1, values[t]);
    }
}
