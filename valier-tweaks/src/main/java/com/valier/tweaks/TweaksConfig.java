package com.valier.tweaks;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Settings from config/valiertweaks.cfg. */
public final class TweaksConfig {

    public static boolean finishLumberAxeTrees = true;
    public static int maxExtraLogs = 1024;
    public static int logsPerTick = 128;
    public static boolean peacefulDimensions = true;
    public static boolean noBoilerAsh = true;
    public static boolean lootSpawners = true;
    public static String lootSpawnerItem = "minecraft:diamond_block";
    public static int lootSpawnerItemCount = 1;
    public static double lootSpawnerSpeed = 1.0;
    public static boolean lootSpawnerNeedsPlayer = false;
    public static String[] lootSpawnerBlockedMobs = { "WitherBoss", "EnderDragon" };
    public static String lootSpawnerFilterItem = "minecraft:emerald_block";
    public static int lootSpawnerFilterItemCount = 1;

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
        noBoilerAsh = c.getBoolean(
            "noBoilerAsh",
            "boilers",
            true,
            "GregTech's small coal and high pressure coal boilers, and GT++'s advanced solid fuel boilers, make no ash or "
                + "dark ash from the fuel they burn. The fuel burns and makes steam as before. Ash already in a boiler "
                + "stays there until taken out.");
        cat = "lootspawner";
        c.setCategoryComment(cat, "Loot spawners: sneak-right-click a mob spawner with the item below and it stops spawning "
            + "mobs. Each spawner cycle it makes the loot of the mobs it would have spawned (as if a player killed them, no "
            + "Looting, no XP) and puts it into the chests, inventories and pipes touching it. Clients need nothing.");
        lootSpawners = c.getBoolean("lootSpawners", cat, true,
            "Loot spawners work. Off: no new ones can be made, and existing ones become ordinary spawners of the same "
                + "mob again within 2 seconds of their chunk loading, dropping what they kept.");
        lootSpawnerItem = c.getString("lootSpawnerItem", cat, "minecraft:diamond_block",
            "What it takes to make one, used up (not in creative): an item name, optionally with :meta. Empty: free, "
                + "with an empty hand. A name that isn't an item: none can be made.");
        lootSpawnerItemCount = c.getInt("lootSpawnerItemCount", cat, 1, 1, 64, "How many of that item it takes.");
        lootSpawnerSpeed = c.getFloat("lootSpawnerSpeed", cat, 1.0F, 0.05F, 20.0F,
            "1 = as fast as the spawner itself would spawn mobs (a vanilla spawner: up to 4 mobs every 10 to 40 "
                + "seconds). 2 = twice as often.");
        lootSpawnerNeedsPlayer = c.getBoolean("lootSpawnerNeedsPlayer", cat, false,
            "Only make loot while a player is within 16 blocks, like a vanilla spawner. Off: whenever its chunk is "
                + "loaded.");
        lootSpawnerBlockedMobs = c.getStringList("lootSpawnerBlockedMobs", cat, new String[] { "WitherBoss", "EnderDragon" },
            "Mobs (entity names, as in a spawner's EntityId) whose spawners can't become loot spawners and make "
                + "nothing if one already is.");
        lootSpawnerFilterItem = c.getString("lootSpawnerFilterItem", cat, "minecraft:emerald_block",
            "Sneak-right-click a loot spawner with this and it throws damaged items away (the worn tools, weapons and "
                + "armour mobs drop), so only undamaged loot comes out. The item is kept in the spawner (not in creative): "
                + "doing it again switches that off and gives it back, and it drops if the spawner is broken. An item "
                + "name, optionally with :meta. Empty, or a name that isn't an item: it can't be switched on or off "
                + "(spawners already set keep throwing damaged items away).");
        lootSpawnerFilterItemCount = c.getInt("lootSpawnerFilterItemCount", cat, 1, 1, 64, "How many of that item it takes.");
        if (c.hasChanged()) c.save();
    }
}
