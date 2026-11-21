package com.valier.tweaks.storage;

import com.valier.tweaks.TweaksConfig;

/**
 * Barrel tiers (block metadata 0 to 6): Basic (wood) and LV to LuV, each holding 8 times the one before. Capacity is in
 * stacks of the item held (a Basic barrel holds 64 stacks: 4,096 iron ingots, 1,024 ender pearls), from the config.
 */
public final class BarrelTier {

    public static final int BASIC = 0, LV = 1, MV = 2, HV = 3, EV = 4, IV = 5, LUV = 6, COUNT = 7;
    public static final String[] NAMES = { "Basic", "LV", "MV", "HV", "EV", "IV", "LuV" };
    public static final int[] STACKS = { 64, 512, 4096, 32768, 262144, 2097152, 16777216 };
    /** The plates an upgrade to each tier takes (ore dictionary). */
    public static final String[] PLATES = { "plankWood", "plateSteel", "plateAluminium", "plateStainlessSteel", "plateTitanium",
        "plateTungstenSteel", "plateChrome" };

    private BarrelTier() {}

    public static int clamp(int tier) {
        return Math.max(0, Math.min(COUNT - 1, tier));
    }

    /** Stacks a barrel of this tier holds. */
    public static long stacks(int tier) {
        int t = clamp(tier);
        int[] v = TweaksConfig.barrelStacks;
        return v == null || t >= v.length ? STACKS[t] : Math.max(1, v[t]);
    }
}
