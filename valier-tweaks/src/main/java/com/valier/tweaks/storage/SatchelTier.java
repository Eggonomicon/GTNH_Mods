package com.valier.tweaks.storage;

import com.valier.tweaks.TweaksConfig;

/**
 * Satchel and Flask tiers (item metadata 0 to 6): LV, MV, HV, EV, IV, LuV and Creative. Slots and how much each holds
 * come from the config; Creative is endless (taking from it never empties it).
 */
public final class SatchelTier {

    public static final int LV = 0, MV = 1, HV = 2, EV = 3, IV = 4, LUV = 5, CREATIVE = 6, COUNT = 7;
    public static final String[] NAMES = { "LV", "MV", "HV", "EV", "IV", "LuV", "Creative" };
    public static final int[] SLOTS = { 9, 18, 27, 36, 45, 54, 54 };
    public static final int[] PER_SLOT = { 1024, 4096, 16384, 65536, 262144, Integer.MAX_VALUE, Integer.MAX_VALUE };
    public static final int[] TANKS = { 9, 9, 18, 18, 27, 27, 27 };
    public static final int[] PER_TANK = { 64000, 256000, 1024000, 4096000, 16384000, Integer.MAX_VALUE, Integer.MAX_VALUE };
    /** The plates (ore dictionary) and GregTech voltage tier (1: LV) of each tier's recipes. */
    public static final String[] PLATES = { "plateSteel", "plateAluminium", "plateStainlessSteel", "plateTitanium",
        "plateTungstenSteel", "plateChrome", "plateChrome" };

    private SatchelTier() {}

    public static int clamp(int tier) {
        return Math.max(0, Math.min(COUNT - 1, tier));
    }

    public static int slots(int tier, boolean fluid) {
        int t = clamp(tier);
        int[] v = fluid ? TweaksConfig.flaskTanks : TweaksConfig.satchelSlots;
        int[] d = fluid ? TANKS : SLOTS;
        int n = v == null || t >= v.length ? d[t] : v[t];
        return Math.max(1, Math.min(54, n));
    }

    /** Items a slot holds, or mB a tank holds. */
    public static int capacity(int tier, boolean fluid) {
        int t = clamp(tier);
        int[] v = fluid ? TweaksConfig.flaskPerTank : TweaksConfig.satchelPerSlot;
        int[] d = fluid ? PER_TANK : PER_SLOT;
        return Math.max(1, v == null || t >= v.length ? d[t] : v[t]);
    }
}
