package com.valier.tweaks.storage;

import com.valier.tweaks.TweaksConfig;

/**
 * Barrel tiers (block metadata 0 to 6): Basic (wood) and LV to LuV, each holding 8 times the one before. Capacity is in
 * stacks of the item held (a Basic barrel holds 64 stacks: 4,096 iron ingots, 1,024 ender pearls), from the config.
 * Metadata 7 is the Creative barrel (2.3.1): creative only, no recipe or upgrade, it gives its item forever
 * ({@link BarrelStore#creative}).
 */
public final class BarrelTier {

    /** COUNT: the tiers that are crafted and upgraded (Basic to LuV). */
    public static final int BASIC = 0, LV = 1, MV = 2, HV = 3, EV = 4, IV = 5, LUV = 6, COUNT = 7;
    /** The Creative barrel's metadata, and how many there are with it. */
    public static final int CREATIVE = 7, METAS = 8;
    public static final String[] NAMES = { "Basic", "LV", "MV", "HV", "EV", "IV", "LuV", "Creative" };
    public static final int[] STACKS = { 64, 512, 4096, 32768, 262144, 2097152, 16777216 };
    /** The plates an upgrade to each tier takes (ore dictionary). */
    public static final String[] PLATES = { "plankWood", "plateSteel", "plateAluminium", "plateStainlessSteel", "plateTitanium",
        "plateTungstenSteel", "plateChrome" };

    private BarrelTier() {}

    public static int clamp(int tier) {
        return Math.max(0, Math.min(METAS - 1, tier));
    }

    /** Stacks a barrel of this tier holds. */
    public static long stacks(int tier) {
        int t = clamp(tier);
        if (t == CREATIVE) return BarrelStore.ENDLESS / 64;
        int[] v = TweaksConfig.barrelStacks;
        return v == null || t >= v.length ? STACKS[t] : Math.max(1, v[t]);
    }
}
