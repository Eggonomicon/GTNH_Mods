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
    public static String lootSpawnerXpItem = "minecraft:gold_block";
    public static int lootSpawnerXpItemCount = 1;
    public static int lootSpawnerXpPerBottle = 7;
    public static String lootSpawnerLootingItem = "minecraft:lapis_block";
    public static int[] lootSpawnerLootingBlocks = { 1, 3, 6 };
    public static String lootSpawnerSpeedItem = "ExtraUtilities:cursedearthside:0";
    public static int[] lootSpawnerSpeedBlocks = { 1, 3, 6 };
    public static boolean lootSpawnerSpeedBelow = true;
    public static boolean lootSpawnerInfernalMobs = true;
    public static boolean lootSpawnerSpecialMobs = true;
    public static boolean lootSpawnerWrench = true;
    public static boolean lootSpawnerHoverName = true;
    public static boolean lootSpawnerSpawnEggs = false;
    public static boolean xpCrafter = true;
    public static String xpCrafterTank = "gregtech:gt.blockmachines:817";
    public static String[] xpCrafterFluids = { "xpjuice" };
    public static String xpCrafterFullItem = "minecraft:emerald_block";
    public static String[] xpCrafterSolidXp = { "TConstruct:oreBerries:5=250" };
    public static int xpCrafterTinyTimes = 9;
    public static int xpCrafterSmallTimes = 4;
    public static boolean chainArmourFullDust = true;

    private TweaksConfig() {}

    /** Levels' block counts: 1 to 10 rising numbers above 0, else 1, 3, 6 (with a warning). */
    private static int[] tiers(int[] tiers, String key) {
        boolean rising = tiers.length > 0 && tiers.length <= 10;
        for (int i = 0; rising && i < tiers.length; i++) rising = tiers[i] > (i == 0 ? 0 : tiers[i - 1]);
        if (!rising) ValierTweaks.LOG.warn(key + " must be 1 to 10 rising numbers above 0; using 1, 3, 6");
        return rising ? tiers : new int[] { 1, 3, 6 };
    }

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
        lootSpawnerXpItem = c.getString("lootSpawnerXpItem", cat, "minecraft:gold_block",
            "Sneak-right-click a loot spawner with this and the XP its mobs would have dropped (as a player kill) comes "
                + "out as bottles o' enchanting, one per lootSpawnerXpPerBottle XP, with the loot. The item is kept in the "
                + "spawner (not in creative): doing it again switches that off and gives it back, and it drops if the "
                + "spawner is broken. An item name, optionally with :meta. Empty, or a name that isn't an item: it can't "
                + "be switched on or off (spawners already set keep making bottles).");
        lootSpawnerXpItemCount = c.getInt("lootSpawnerXpItemCount", cat, 1, 1, 64, "How many of that item it takes.");
        lootSpawnerXpPerBottle = c.getInt("lootSpawnerXpPerBottle", cat, 7, 1, 1000,
            "XP per bottle o' enchanting. 7 is what a thrown bottle gives back on average (3 to 11). What is left over "
                + "waits for the next batch.");
        lootSpawnerLootingItem = c.getString("lootSpawnerLootingItem", cat, "minecraft:lapis_block",
            "Sneak-right-click a loot spawner with this to put one in: its mobs' loot is then rolled with Looting, as if "
                + "killed with a Looting sword. How many it holds sets the level (lootSpawnerLootingBlocks). They stay in "
                + "it and drop when it is broken (a wrench keeps them in the item). An item name, optionally with :meta. "
                + "Empty, or a name that isn't an item: none can be put in (spawners that have some keep their Looting).");
        lootSpawnerLootingBlocks = tiers(c.get(cat, "lootSpawnerLootingBlocks", new int[] { 1, 3, 6 },
            "How many of that item a loot spawner needs in it for each Looting level, in order: by default 1 for Looting "
                + "I, 3 for Looting II, 6 for Looting III. More numbers allow higher levels (each larger than the last).")
            .getIntList(), "lootSpawnerLootingBlocks");
        lootSpawnerSpeedItem = c.getString("lootSpawnerSpeedItem", cat, "ExtraUtilities:cursedearthside:0",
            "Sneak-right-click a loot spawner with this to put one in: its batches come faster. How many it holds sets "
                + "the Speed level (lootSpawnerSpeedBlocks), and each level adds its normal speed once more: Speed I is "
                + "twice as fast, II three times, III four times. They stay in it and drop when it is broken (a wrench "
                + "keeps them in the item). By default Extra Utilities' cursed earth. An item name, optionally with :meta. "
                + "Empty, or a name that isn't an item: none can be put in (spawners that have some keep their Speed).");
        lootSpawnerSpeedBlocks = tiers(c.get(cat, "lootSpawnerSpeedBlocks", new int[] { 1, 3, 6 },
            "How many of that item a loot spawner needs in it for each Speed level, in order: by default 1 for Speed I, "
                + "3 for Speed II, 6 for Speed III. More numbers allow higher levels (each larger than the last). However "
                + "fast, a loot spawner makes at most one batch a second.")
            .getIntList(), "lootSpawnerSpeedBlocks");
        lootSpawnerSpeedBelow = c.getBoolean("lootSpawnerSpeedBelow", cat, true,
            "That item placed as a block right under a loot spawner counts as one put in, the way Extra Utilities' cursed "
                + "earth under a spawner speeds it up.");
        lootSpawnerInfernalMobs = c.getBoolean("lootSpawnerInfernalMobs", cat, true,
            "With Infernal Mobs installed, a loot spawner's mobs can be elites as real spawned mobs are (Infernal Mobs' own "
                + "rarity settings), and the elites' extra loot (enchanted armour, tools, books and so on) and XP come out "
                + "too.");
        lootSpawnerSpecialMobs = c.getBoolean("lootSpawnerSpecialMobs", cat, true,
            "With Special Mobs installed, a loot spawner's mobs are swapped for its variants as real spawned mobs are "
                + "(Special Mobs' own weights: on a GTNH server every blaze, and some zombies, creepers, skeletons and "
                + "others), and the variants' own loot comes out.");
        lootSpawnerWrench = c.getBoolean("lootSpawnerWrench", cat, true,
            "Sneak-right-click a loot spawner with a wrench (GregTech, EnderIO Yeta, AE2 quartz, BuildCraft and the like) "
                + "and it comes off as a Valier Loot Spawner item: same mob, settings and emerald block; the loot it was "
                + "keeping drops. Placing the item makes it a loot spawner again. Claims are respected. Breaking one "
                + "with a pickaxe is unchanged.");
        lootSpawnerHoverName = c.getBoolean("lootSpawnerHoverName", cat, true,
            "Players' WAILA hover on a loot spawner says \"Monster Spawner (Valier Loot Spawner: Zombie)\" instead of "
                + "\"Monster Spawner (Zombie)\". The price: the little mob inside the cage isn't drawn, because clients "
                + "draw it from that same name. Off: the mob spins in the cage and the hover names only the mob.");
        lootSpawnerSpawnEggs = c.getBoolean("lootSpawnerSpawnEggs", cat, false,
            "Sneak-right-click a loot spawner with a spawn egg and it makes that mob's loot from now on, the mobs dressed "
                + "as a vanilla spawner dresses them. The egg is used up (not in creative). Claims apply, blocked mobs are "
                + "refused, and what it was keeping and its emerald block stay. Plain spawners are left alone.");
        cat = "xpcrafter";
        c.setCategoryComment(cat, "XP crafters: three of the tank below, two LV pumps, an LV conveyor, an LV robot arm, a "
            + "crafting table and an LV circuit craft a Valier XP Crafter. Placed, it fills with liquid XP like the tank it "
            + "is, makes one of the things GTNH's XP bucket crafting recipes make (a tiny pile of gallium dust from 8 "
            + "buckets' worth, and so on) whenever it has the XP for one, into its store, and hands its store to the "
            + "chests, inventories and pipes touching it. Right-click it (not holding a GregTech tool or cover) for its menu (a chest window: "
            + "its liquid XP, what it can make, its store); sneak-right-click it with what it should make to choose, with "
            + "an empty hand for its status. Clients need nothing.");
        xpCrafter = c.getBoolean("xpCrafter", cat, true,
            "XP crafters can be made and work. Off: none can be made, and existing ones stop (they keep what they know).");
        xpCrafterTank = c.getString("xpCrafterTank", cat, "gregtech:gt.blockmachines:817",
            "The GregTech tank that becomes an XP crafter: an item name with :meta. By default GT++'s Ultra Low Voltage "
                + "Fluid Tank (32,000 mB). Any GregTech tank works.");
        c.getCategory(cat)
            .remove("xpCrafterItem"); // 1.10.0 and before: the tank and this crafted one
        xpCrafterFluids = c.getStringList("xpCrafterFluids", cat, new String[] { "xpjuice" },
            "Fluid names that count as liquid XP (EnderIO's and OpenBlocks' are both xpjuice). Crafting recipes made only "
                + "of containers of these (the XP buckets) are what XP crafters can make; a recipe's cost is the liquid XP "
                + "in its containers.");
        xpCrafterSolidXp = c.getStringList("xpCrafterSolidXp", cat, new String[] { "TConstruct:oreBerries:5=250" },
            "Items an XP crafter takes as liquid XP, as item=mB (mod:item:meta=mB). It takes them, as it needs them, "
                + "from the chests and inventories touching it, and players can put them in through its menu. By "
                + "default Tinkers' Concentrated Essence Berry (what CropsNH's essence berry crop drops), 250 mB: eating "
                + "one gives 6 to 19 XP, 12.5 on average, and EnderIO and OpenBlocks count 20 mB of liquid XP a point.");
        xpCrafterFullItem = c.getString("xpCrafterFullItem", cat, "minecraft:emerald_block",
            "Sneak-right-click an XP crafter with this (or click full dust in its menu holding it) and it makes the whole "
                + "dust instead of a tiny or small pile (a "
                + "gallium dust instead of a tiny pile), for xpCrafterTinyTimes or xpCrafterSmallTimes the liquid XP. The "
                + "item is kept in it (not in creative): doing it again, or a sneaking hit with an empty hand, switches that "
                + "off and gives it back; breaking the crafter keeps it in the item. Empty, or a name that isn't an item: it "
                + "can't be switched on or off.");
        xpCrafterTinyTimes = c.getInt("xpCrafterTinyTimes", cat, 9, 1, 64,
            "How many times the liquid XP the whole dust takes instead of a tiny pile. 9: GregTech makes a dust from 9 "
                + "tiny piles.");
        xpCrafterSmallTimes = c.getInt("xpCrafterSmallTimes", cat, 4, 1, 64,
            "How many times the liquid XP the whole dust takes instead of a small pile. 4: GregTech makes a dust from 4 "
                + "small piles.");
        chainArmourFullDust = c.getBoolean("chainArmourFullDust", "macerator", true,
            "Chain armour macerates into whole steel dust: a chain helmet gives 1 steel dust instead of 5 small piles, "
                + "chain leggings 2 instead of 7 small piles (boots 1 and chestplates 2, as before), so a full set still "
                + "gives 6. All GregTech macerators. Clients' NEI still shows the small piles.");
        if (c.hasChanged()) c.save();
    }
}
