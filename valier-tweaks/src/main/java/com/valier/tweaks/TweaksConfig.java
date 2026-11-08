package com.valier.tweaks;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Settings from config/valiertweaks.cfg. */
public final class TweaksConfig {

    public static boolean finishLumberAxeTrees = true;
    public static int maxExtraLogs = 1024;
    public static int logsPerTick = 128;
    public static boolean peacefulDimensions = true;

    private TweaksConfig() {}

    static void load(File file) {
        Configuration c = new Configuration(file);
        String cat = "lumberaxe";
        finishLumberAxeTrees = c.getBoolean(
            "finishLumberAxeTrees",
            cat,
            true,
            "When a Tinkers' Construct lumber axe stops felling a tree partway (it broke, or the player switched items), "
                + "fell the rest of the tree too, so no floating logs are left. The extra logs drop as if broken by hand "
                + "and don't wear the axe.");
        maxExtraLogs = c.getInt(
            "maxExtraLogs",
            cat,
            1024,
            0,
            100000,
            "At most this many extra logs are broken to finish one tree (a lumber axe also fells logs that touch the "
                + "tree, such as a log wall built against it).");
        logsPerTick = c.getInt("logsPerTick", cat, 128, 1, 4096, "Extra logs broken per tick, as Tinkers itself does.");
        peacefulDimensions = c.getBoolean(
            "peacefulDimensions",
            "pdim",
            true,
            "Let players make their own Personal Space dimension peaceful with /pdim peaceful on|off: no hostile mobs "
                + "spawn there, and ones already there are removed (no drops). Anyone who could break blocks where they "
                + "stand may switch it, so a claim keeps visitors from changing it; ops always may.");
        if (c.hasChanged()) c.save();
    }
}
