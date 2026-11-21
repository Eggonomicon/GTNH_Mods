package com.valier.tweaks.grid;

import com.valier.tweaks.TweaksConfig;

/**
 * Valier Grid tiers (block metadata 0 to 7): LV to UV, GregTech's voltages. A plug takes and a point sends at most its
 * tier's voltage, with {@code gridAmps} amps (config); a cell holds 1,000,000 EU at LV and 4 times more each tier.
 */
public final class GridTier {

    public static final int COUNT = 8;
    public static final String[] NAMES = { "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV" };
    /** EU per amp at each tier. */
    public static final long[] VOLTS = { 32, 128, 512, 2048, 8192, 32768, 131072, 524288 };
    public static final long[] CELL_EU = { 1_000_000L, 4_000_000L, 16_000_000L, 64_000_000L, 256_000_000L, 1_024_000_000L,
        4_096_000_000L, 16_384_000_000L };
    /** GregTech's cable of each tier (ore dictionary), for the recipes. */
    public static final String[] CABLES = { "cableGt01Tin", "cableGt01Copper", "cableGt01Gold", "cableGt01Aluminium",
        "cableGt01Platinum", "cableGt01VanadiumGallium", "cableGt01Naquadah", "cableGt01NaquadahAlloy" };

    private GridTier() {}

    public static int clamp(int t) {
        return Math.max(0, Math.min(COUNT - 1, t));
    }

    public static long volts(int t) {
        return VOLTS[clamp(t)];
    }

    public static int amps() {
        return Math.max(1, TweaksConfig.gridAmps);
    }

    /** EU a plug or point moves a tick at most: its voltage times the amps. */
    public static long perTick(int t) {
        return volts(t) * amps();
    }

    public static long cellEu(int t) {
        int i = clamp(t);
        long[] v = TweaksConfig.gridCellEu;
        return v == null || i >= v.length ? CELL_EU[i] : Math.max(1, v[i]);
    }

    /** The tier whose voltage is v or more (a machine's input voltage), for IC2's tiers (LV = 1). */
    public static int ic2Tier(long v) {
        for (int i = 0; i < VOLTS.length; i++) if (v <= VOLTS[i]) return i + 1;
        return VOLTS.length;
    }
}
