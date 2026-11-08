package gtnhserver.mmfreepower;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

/**
 * Settings from config/mmfreepower.cfg: which blocks the Valier MM Mk IV places for free, and how every manipulator
 * mines (ore drops, Fortune, ore veins on the map).
 */
public final class MkIVConfig {

    public static boolean enabled = true;
    public static boolean dropOres = true;
    public static boolean fortuneBooks = true;
    public static int baseFortune = 0;
    public static boolean markVeins = true;
    public static String[] excludedMods = {};
    public static String[] excludedBlocks = {};
    public static String[] excludedOrePrefixes = {};
    public static String[] allowedOreNames = {};
    public static String[] alwaysFree = {};

    private static final String[] DEFAULT_EXCLUDED_MODS = {
        // GregTech and its add-ons: casings, coils, glass, ores and metal blocks are machine parts or resources.
        "gregtech", "bartworks", "bartworkscrossmod", "tectech", "miscutils", "gtnhlanth", "kekztech", "GoodGenerator",
        "ggfab", "gtnhintergalactic", "kubatech", "galacticgreg", "detravscannermod", "dreamcraft",
        // Blocks that are compressed resources or endgame materials.
        "Avaritia", "avaritiaddons", "DraconicEvolution", "eternalsingularity", "universalsingularities",
        "ExtraUtilities", "IC2", "appliedenergistics2" };

    private static final String[] DEFAULT_EXCLUDED_BLOCKS = {
        // Vanilla blocks that break or recycle (GregTech macerator, arc furnace) into valuable materials.
        "minecraft:anvil", "minecraft:glowstone", "minecraft:redstone_lamp", "minecraft:lit_redstone_lamp",
        "minecraft:tnt", "minecraft:quartz_block", "minecraft:iron_bars", "minecraft:golden_rail",
        "minecraft:detector_rail", "minecraft:activator_rail", "minecraft:rail", "minecraft:melon_block",
        "minecraft:hay_block", "minecraft:sponge",
        // Chisel's carved metal and gem blocks chisel back into the real thing.
        "chisel:*diamond*", "chisel:*emerald*", "chisel:*gold*", "chisel:*iron*", "chisel:*lapis*",
        "chisel:*redstone*", "chisel:*coal*", "chisel:*glowstone*", "chisel:*quartz*", "chisel:*copper*",
        "chisel:tinblock*", "chisel:*bronze*", "chisel:silverblock*", "chisel:*lead*", "chisel:*steel*", "chisel:*aluminum*",
        "chisel:*uranium*", "chisel:*platinum*", "chisel:*nickel*", "chisel:*electrum*", "chisel:*invar*",
        "chisel:*cobalt*", "chisel:*ardite*", "chisel:*thaumium*", "chisel:*voidmetal*", "chisel:*amber*",
        // Et Futurum's valuable blocks, and anything named like a storage or compressed block.
        "etfuturum:*netherite*", "etfuturum:copper_block", "etfuturum:*copper_ore*", "etfuturum:*amethyst*",
        "etfuturum:*ancient_debris*",
        "*:*storage*", "*:*compressed*" };

    private static final String[] DEFAULT_ORE_PREFIXES = { "block", "ore", "cluster", "crystal", "gem", "ingot",
        "nugget", "dust" };

    /**
     * Ore dictionary names (with * wildcards) that start with an excluded prefix but are plain building blocks. GTNH
     * tags glass and wool with names like blockGlassHV, blockGlassWhite and blockWoolWhite.
     */
    private static final String[] DEFAULT_ALLOWED_ORE_NAMES = { "blockGlass*", "blockWool*", "blockCloth*",
        "blockCobblestone*", "blockStone*", "blockSandstone*", "blockClay", "blockHardenedClay*",
        "blockStainedHardenedClay*", "blockCarpet*", "blockSnow",
        "blockIce", "blockBrick*", "blockNetherBrick*", "blockMarble*", "blockBasalt*", "blockLimestone*",
        "blockObsidian", "blockAndesite*", "blockDiorite*", "blockGranite*", "blockMossCarpet", "blockWaterstone",
        "blockTorch", "blockCactus", "blockLivingrock*", "blockLivingwood*", "blockDreamwood*", "blockPrismarine*" };

    /** Decorative blocks whose names happen to match an excluded pattern (Modern Markings' "storage" decal). */
    private static final String[] DEFAULT_ALWAYS_FREE = { "modernmarkings:*" };

    private MkIVConfig() {}

    static void load(File file) {
        Configuration c = new Configuration(file);
        String cat = "mk4";
        c.setCategoryComment(
            cat,
            "The Valier MM Mk IV (craft a Matter Manipulator MK III on its own in a crafting table) places blocks "
                + "for free: blocks with no tile entity (machines, chests and other tile entities always cost), except "
                + "the ones excluded below. Patterns are modid:name, lower case, with * as a wildcard.");
        enabled = c.getBoolean("enabled", cat, true, "Turn free placement (and the Mk IV recipe) on or off.");
        excludedMods = c.getStringList(
            "excludedMods",
            cat,
            DEFAULT_EXCLUDED_MODS,
            "Mods whose blocks always cost items (mod ids, any case).");
        excludedBlocks = c.getStringList(
            "excludedBlocks",
            cat,
            DEFAULT_EXCLUDED_BLOCKS,
            "Blocks that always cost items (modid:name patterns).");
        excludedOrePrefixes = c.getStringList(
            "excludedOrePrefixes",
            cat,
            DEFAULT_ORE_PREFIXES,
            "Blocks with an ore dictionary name starting with one of these words (followed by a capital letter, as "
                + "in blockDiamond or oreIron) always cost items: storage blocks, ores and the like.");
        allowedOreNames = c.getStringList(
            "allowedOreNames",
            cat,
            DEFAULT_ALLOWED_ORE_NAMES,
            "Ore dictionary names (* wildcards allowed) that are plain building blocks even though they start with an "
                + "excluded word.");
        alwaysFree = c.getStringList(
            "alwaysFree",
            cat,
            DEFAULT_ALWAYS_FREE,
            "Blocks that are always free (modid:name patterns), even if excluded above. Tile entities still cost.");
        dropOres = c.getBoolean(
            "dropOres",
            "mining",
            true,
            "Every Matter Manipulator (MK0 to MK III and the Mk IV) hands back what the ores it removes drop, as a "
                + "pickaxe without Fortune would: raw ores (GregTech's oredropbehavior setting decides), small ores' "
                + "dusts and gems, diamonds, coal and so on. The manipulator on its own deletes ores with no drops; "
                + "false keeps that.");
        fortuneBooks = c.getBoolean(
            "fortuneBooks",
            "mining",
            true,
            "A Matter Manipulator crafted together with a Fortune enchanted book gets that Fortune level (it is "
                + "otherwise kept: charge, settings, links, the Mk IV marker). Every manipulator mines with the Fortune "
                + "level on it.");
        baseFortune = c.getInt(
            "baseFortune",
            "mining",
            0,
            0,
            10,
            "Every Matter Manipulator mines with at least this Fortune level, enchanted or not. 0 = only what's "
                + "enchanted on it; 3 = Fortune III for everyone.");
        markVeins = c.getBoolean(
            "markVeins",
            "mining",
            true,
            "Ores a Matter Manipulator removes count as mined with a pickaxe for VisualProspecting: the ore vein they "
                + "belong to shows on the player's map (and their VisualProspecting team's). Natural, non-small "
                + "GregTech ores only, as with a pickaxe.");
        if (c.hasChanged()) c.save();
    }

    /** Whether "modid:name" (any case) matches a pattern with * wildcards. */
    static boolean matches(String name, String[] patterns) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            String regex = "\\Q" + p.trim()
                .toLowerCase(Locale.ROOT)
                .replace("*", "\\E.*\\Q") + "\\E";
            if (n.matches(regex)) return true;
        }
        return false;
    }
}
