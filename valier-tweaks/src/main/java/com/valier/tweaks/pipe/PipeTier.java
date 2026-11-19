package com.valier.tweaks.pipe;

import com.valier.tweaks.TweaksConfig;

/**
 * The four pipe tiers (block metadata 0 to 3). How much one transfer may move and how often an Extract or Request face
 * works come from the config ({@code pipes} section); the face's own amount can be set lower in its menu.
 */
public final class PipeTier {

    public static final int BASIC = 0, ADVANCED = 1, ELITE = 2, WIRELESS = 3, COUNT = 4;
    public static final String[] NAMES = { "Basic", "Advanced", "Elite", "Wireless" };

    private PipeTier() {}

    public static int clamp(int tier) {
        return Math.max(0, Math.min(COUNT - 1, tier));
    }

    /** Items one transfer may move. */
    public static int maxItems(int tier) {
        return pick(TweaksConfig.pipeItems, tier, 8);
    }

    /** Fluid (mB) one transfer may move. */
    public static int maxFluid(int tier) {
        return pick(TweaksConfig.pipeFluids, tier, 1000);
    }

    /** Ticks between an active face's transfers. */
    public static int interval(int tier) {
        return Math.max(1, pick(TweaksConfig.pipeTicks, tier, 20));
    }

    public static int max(PipeKind kind, int tier) {
        return kind == PipeKind.ITEM ? maxItems(tier) : maxFluid(tier);
    }

    private static int pick(int[] values, int tier, int fallback) {
        int t = clamp(tier);
        if (values == null || values.length == 0) return fallback;
        return Math.max(1, values[Math.min(t, values.length - 1)]);
    }
}
