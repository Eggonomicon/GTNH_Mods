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
    public static boolean noRailcraftBoilerExplosions = true;
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
    public static boolean lootSpawnerSpawnEggs = false;
    public static int[] pipeItems = com.valier.tweaks.pipe.PipeTier.ITEMS.clone();
    public static int[] pipeFluids = com.valier.tweaks.pipe.PipeTier.FLUIDS.clone();
    public static int[] pipeTicks = com.valier.tweaks.pipe.PipeTier.TICKS.clone();
    public static int pipeMaxSleepTicks = 100;
    public static int pipeBudgetMicros = 2000;
    public static int pipeNetworkMax = 4096;
    public static boolean pipeWirelessCrossDimension = true;
    public static int pipeHeldStacks = 4;
    public static int[] barrelStacks = com.valier.tweaks.storage.BarrelTier.STACKS.clone();
    public static int[] satchelSlots = com.valier.tweaks.storage.SatchelTier.SLOTS.clone();
    public static int[] satchelPerSlot = com.valier.tweaks.storage.SatchelTier.PER_SLOT.clone();
    public static int[] flaskTanks = com.valier.tweaks.storage.SatchelTier.TANKS.clone();
    public static int[] flaskPerTank = com.valier.tweaks.storage.SatchelTier.PER_TANK.clone();
    public static int gridAmps = 4;
    public static long[] gridCellEu = com.valier.tweaks.grid.GridTier.CELL_EU.clone();
    public static boolean gridRfToEu = false;
    public static double gridRfPerEu = 3.6;
    public static boolean gridControllerCrossDimension = true;
    public static int gridLossPercent = 0;
    public static boolean xpCrafter = true;
    public static String xpCrafterTank = "gregtech:gt.blockmachines:817";
    public static String[] xpCrafterFluids = { "xpjuice" };
    public static String xpCrafterFullItem = "minecraft:emerald_block";
    public static String[] xpCrafterSolidXp = { "TConstruct:oreBerries:5=250" };
    public static int xpCrafterTinyTimes = 9;
    public static int xpCrafterCostPercent = 100;
    public static String[] xpCrafterCosts = {};
    public static int xpCrafterSmallTimes = 4;
    public static boolean chainArmourFullDust = true;
    public static boolean opLock = false;
    public static boolean opQuiet = false;
    public static boolean worldEditRanks = true;
    public static boolean endlessWands = true;

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
        noRailcraftBoilerExplosions = c.getBoolean(
            "noRailcraftBoilerExplosions",
            "boilers",
            true,
            "Railcraft boilers don't explode. Railcraft blows a boiler up when water reaches it while it is superheated "
                + "(300 or more) with an empty water tank, and that explosion breaks blocks even with mobGriefing off and "
                + "GregTech's machine explosions off. On: it hisses and smokes instead, takes the water, cools to 299 as "
                + "Railcraft has it and keeps working. Other explosions are untouched. Off: Railcraft's explosion.");
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
                + "keeping drops. Placing the item makes it a loot spawner again. Claims are respected. Without it, a "
                + "loot spawner can't be moved except with a dolly (players can't break one).");
        c.getCategory(cat).remove("lootSpawnerHoverName"); // 2.0.0: players see the mob and the block's own name
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
        xpCrafterCostPercent = c.getInt("xpCrafterCostPercent", cat, 100, 1, 1000,
            "Every XP recipe's liquid XP, in percent of what GTNH's XP bucket recipe takes. 100: the same (a tiny pile of "
                + "gallium dust takes 8,000 mB); 25: a quarter (2,000 mB). Items set in xpCrafterCosts take what is set "
                + "there instead. The whole dust (full dust on) takes xpCrafterTinyTimes or xpCrafterSmallTimes times "
                + "this.");
        xpCrafterCosts = c.getStringList("xpCrafterCosts", cat, new String[0],
            "The liquid XP one craft of an item takes, as item=mB, instead of GTNH's cost and xpCrafterCostPercent. The "
                + "item is what a liquid XP recipe makes: an ore dictionary name, e.g. dustTinyGallium=2000, or an item name "
                + "(mod:item or mod:item:meta). The whole dust (full dust on) still takes xpCrafterTinyTimes or "
                + "xpCrafterSmallTimes times it. An entry that sets nothing is named in the server log.");
        chainArmourFullDust = c.getBoolean("chainArmourFullDust", "macerator", true,
            "Chain armour macerates into whole steel dust: a chain helmet gives 1 steel dust instead of 5 small piles, "
                + "chain leggings 2 instead of 7 small piles (boots 1 and chestplates 2, as before), so a full set still "
                + "gives 6. All GregTech macerators. Clients' NEI still shows the small piles.");
        cat = "pipes";
        c.setCategoryComment(cat, "Valier item and fluid pipes. Seven tiers each; the lists give each tier's value in this "
            + "order: Basic, Advanced, Elite, Wireless, Superior, Ultimate, Creative (the block metadata). Players need the "
            + "same values for their menus to show them right; the server's are what count.");
        String order = " One line per tier: Basic, Advanced, Elite, Wireless, Superior, Ultimate, Creative (the 4th line is "
            + "Wireless). A face moves at its own pipe's tier, also when it links through a Wireless pipe.";
        pipeItems = tiers(c.get(cat, "pipeItems", com.valier.tweaks.pipe.PipeTier.ITEMS, "Items one Extract or Request face "
            + "moves per transfer, at most (a face's own amount can be lower)." + order), com.valier.tweaks.pipe.PipeTier.ITEMS);
        pipeFluids = tiers(c.get(cat, "pipeFluids", com.valier.tweaks.pipe.PipeTier.FLUIDS, "Fluid (mB) one Extract or "
            + "Request face moves per transfer, at most." + order), com.valier.tweaks.pipe.PipeTier.FLUIDS);
        pipeTicks = tiers(c.get(cat, "pipeTicks", com.valier.tweaks.pipe.PipeTier.TICKS, "Ticks between an Extract or "
            + "Request face's transfers (20 = once a second)." + order), com.valier.tweaks.pipe.PipeTier.TICKS);
        pipeMaxSleepTicks = c.getInt("pipeMaxSleepTicks", cat, 100, 20, 1200, "A face that finds nothing to do waits twice as "
            + "long each time, up to this many ticks (a change in the block it touches wakes it sooner).");
        pipeBudgetMicros = c.getInt("pipeBudgetMicros", cat, 2000, 100, 50000, "At most this many microseconds a tick on "
            + "pipes; faces still due wait for the next tick.");
        pipeNetworkMax = c.getInt("pipeNetworkMax", cat, 4096, 1, 1000000, "A network of more pipes than this does nothing "
            + "(a safety limit against huge accidental networks).");
        pipeWirelessCrossDimension = c.getBoolean("pipeWirelessCrossDimension", cat, true, "Wireless pipes link across "
            + "dimensions (both ends' chunks must be loaded either way).");
        pipeHeldStacks = c.getInt("pipeHeldStacks", cat, 4, 1, 64, "Stacks a face may hold back (what a block took less of than "
            + "it said and that couldn't go back); past that the oldest drops.");
        cat = "barrels";
        c.setCategoryComment(cat, "Valier Barrels: one item each, lots of it. Seven tiers (Basic, LV, MV, HV, EV, IV, LuV); "
            + "linked barrels share one store, holding the sum of their tiers.");
        barrelStacks = tiers(c.get(cat, "barrelStacks", com.valier.tweaks.storage.BarrelTier.STACKS, "Stacks of its item each "
            + "tier holds (64 stacks of iron ingots: 4,096)."), com.valier.tweaks.storage.BarrelTier.STACKS);
        cat = "satchels";
        c.setCategoryComment(cat, "Valier Satchels (items) and Flasks (fluids). Seven tiers each: LV, MV, HV, EV, IV, LuV, "
            + "Creative (Creative is endless whatever these say). Slots and tanks: 1 to 54.");
        satchelSlots = tiers(c.get(cat, "satchelSlots", com.valier.tweaks.storage.SatchelTier.SLOTS, "Slots of each Satchel "
            + "tier."), com.valier.tweaks.storage.SatchelTier.SLOTS);
        satchelPerSlot = tiers(c.get(cat, "satchelPerSlot", com.valier.tweaks.storage.SatchelTier.PER_SLOT, "Items one Satchel "
            + "slot holds, per tier."), com.valier.tweaks.storage.SatchelTier.PER_SLOT);
        flaskTanks = tiers(c.get(cat, "flaskTanks", com.valier.tweaks.storage.SatchelTier.TANKS, "Tanks of each Flask tier."),
            com.valier.tweaks.storage.SatchelTier.TANKS);
        flaskPerTank = tiers(c.get(cat, "flaskPerTank", com.valier.tweaks.storage.SatchelTier.PER_TANK, "mB one Flask tank "
            + "holds, per tier."), com.valier.tweaks.storage.SatchelTier.PER_TANK);
        cat = "grid";
        c.setCategoryComment(cat, "The Valier Grid: wireless EU and RF. Plugs take power in, points send it out, cells store "
            + "it, controllers charge players' items. Eight tiers, LV to UV, at GregTech's voltages.");
        gridAmps = c.getInt("gridAmps", cat, 4, 1, 64, "Amps a plug takes and a point sends a tick, at its tier's voltage.");
        double[] cells = c.get(cat, "gridCellEu", toDoubles(com.valier.tweaks.grid.GridTier.CELL_EU), "EU each Grid Cell tier holds.")
            .getDoubleList();
        gridCellEu = new long[com.valier.tweaks.grid.GridTier.CELL_EU.length];
        for (int i = 0; i < gridCellEu.length; i++) gridCellEu[i] = i < cells.length ? (long) Math.max(1, cells[i])
            : com.valier.tweaks.grid.GridTier.CELL_EU[i];
        gridRfToEu = c.getBoolean("gridRfToEu", cat, false, "RF that comes in may go out as EU (GregTech machines). Off, as "
            + "GregTech itself has it in this pack: RF only goes out as RF.");
        gridRfPerEu = c.getFloat("gridRfPerEu", cat, 3.6F, 0.1F, 100F, "RF per EU, both ways (GregTech's own rate is 3.6).");
        gridControllerCrossDimension = c.getBoolean("gridControllerCrossDimension", cat, true, "Controllers charge players in "
            + "any dimension (else only in their own).");
        gridLossPercent = c.getInt("gridLossPercent", cat, 0, 0, 90, "EU (and RF) lost on the way, as a percentage of what arrives: "
            + "at 10, 100 EU delivered costs the network 110. 0: nothing is lost.");
        cat = "permissions";
        c.setCategoryComment(cat, "Who may op, and WorldEdit by ServerUtilities rank.");
        opLock = c.getBoolean("opLock", cat, false, "Only the server console can op anyone: /op from a player (whatever their "
            + "op level or rank), a command block or RCON is refused and logged. /deop and every other command work as before.");
        opQuiet = c.getBoolean("opQuiet", cat, false, "The console's /op and /deop tell nobody in game (vanilla shows every op "
            + "online, the newly opped player too, a grey \"[Server: Opped X]\" line). Only the console sees it.");
        worldEditRanks = c.getBoolean("worldEditRanks", cat, true, "WorldEdit also asks the player's ServerUtilities rank, so a "
            + "rank with worldedit: true (or single worldedit.* nodes) gets WorldEdit without op. Ops keep it either way.");
        cat = "thaumcraft";
        c.setCategoryComment(cat, "Endless wands (2.3.5).");
        endlessWands = c.getBoolean("endlessWands", cat, true, "Wands made with the server console's /valier wand <player> "
            + "keep their vis full while their owner carries them (or has them in an open menu, such as an Arcane Worktable). "
            + "In anyone else's hands they are plain wands. Off: every endless wand is a plain wand and /valier wand refuses.");
        if (c.hasChanged()) c.save();
    }

    /** A per-tier list; one saved before a tier existed (2.0 had four) gets that tier's default added. */
    private static double[] toDoubles(long[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) d[i] = v[i];
        return d;
    }

    private static int[] tiers(net.minecraftforge.common.config.Property p, int[] defaults) {
        int[] v = p.getIntList();
        if (v.length >= defaults.length) return v;
        int[] out = java.util.Arrays.copyOf(v, defaults.length);
        for (int i = v.length; i < defaults.length; i++) out[i] = defaults[i];
        p.set(out);
        return out;
    }
}
