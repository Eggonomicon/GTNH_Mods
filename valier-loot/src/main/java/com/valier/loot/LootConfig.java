package com.valier.loot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.registry.GameRegistry;

/** Settings from config/valierloot.cfg. {@code /lootchest reload} reads them again. */
public final class LootConfig {

    public static boolean enabled = true;
    public static int maxInhabitedTicks = 0;
    public static boolean protectFromBreaking = true;
    public static boolean breakWhileSneaking = true;
    public static boolean matterManipulatorBreaks = false;
    public static boolean blockAdjacentChests = true;
    public static boolean registerNewChunks = true;
    public static String title = "Loot Chest";
    public static String firstOpenMessage = "Loot chests are per-player: you get your own copy of what's inside, "
        + "and so does everyone else.";

    /** What a player's copy holds: the chest's own loot, a random roll from a pool, or both. */
    public enum RandomMode {
        OFF,
        BONUS,
        REPLACE
    }

    public static RandomMode randomMode = RandomMode.BONUS;
    public static List<PoolRule> poolRules = new ArrayList<>();

    private static final String[] DEFAULT_RULES = { "0;0;chest1", "0;1500;chest2", "0;4000;chest3", "0;7000;chest4",
        "-1;0;chest2", "*;0;chest3" };

    private static File file;
    /** Custom pool entries this mod added to Forge's tables, so a reload can take them out again. */
    private static final List<Object[]> addedPoolItems = new ArrayList<>();
    private static String[] customPools = {};

    /** Which table to roll from: for one dimension (or all, "*"), from a distance from spawn outwards. */
    public static final class PoolRule {

        public final Integer dimension; // null: any dimension without rules of its own
        public final int minDistance;
        public final String category;
        public final int minItems, maxItems; // -1: use the table's own count

        PoolRule(Integer dimension, int minDistance, String category, int minItems, int maxItems) {
            this.dimension = dimension;
            this.minDistance = minDistance;
            this.category = category;
            this.minItems = minItems;
            this.maxItems = maxItems;
        }

        public String describe() {
            return category + (minItems >= 0 ? " (" + minItems + "-" + maxItems + " items)" : "");
        }
    }

    private LootConfig() {}

    static void load(File configFile) {
        file = configFile;
        Configuration c = new Configuration(file);
        String cat = Configuration.CATEGORY_GENERAL;
        enabled = c.getBoolean("enabled", cat, true, "Turn per-player loot chests on or off.");
        maxInhabitedTicks = c.getInt(
            "maxInhabitedTicks",
            cat,
            0,
            0,
            Integer.MAX_VALUE,
            "A chest found when its chunk loads counts as worldgen loot only if players have spent at most this many "
                + "ticks near that chunk. 0 (the default) means no player has ever been near it, so it cannot be a "
                + "chest a player placed. Chunks saved while a player is near are always marked as visited.");
        protectFromBreaking = c.getBoolean(
            "protectFromBreaking",
            cat,
            true,
            "Stop players who are not in creative mode (and machines) from breaking loot chests, and explosions from "
                + "destroying them.");
        breakWhileSneaking = c.getBoolean(
            "breakWhileSneaking",
            cat,
            true,
            "With protectFromBreaking on, still let a player break a loot chest by sneaking while mining it (to "
                + "build in a village, say). Their own share of the loot drops; everyone else loses theirs.");
        matterManipulatorBreaks = c.getBoolean(
            "matterManipulatorBreaks",
            cat,
            false,
            "Let a player's Matter Manipulator remove loot chests, like breaking one by sneaking: the player's share "
                + "drops where the chest was. Off: a Manipulator skips loot chests, even while sneaking.");
        blockAdjacentChests = c.getBoolean(
            "blockAdjacentChests",
            cat,
            true,
            "Stop chests being placed next to a loot chest (that would turn it into a vanilla double chest).");
        registerNewChunks = c.getBoolean(
            "registerNewChunks",
            cat,
            true,
            "Also treat chests as loot when structures generate them in brand-new chunks.");
        title = c.getString("title", cat, "Loot Chest", "Title of the loot chest window.");
        firstOpenMessage = c.getString(
            "firstOpenMessage",
            cat,
            firstOpenMessage,
            "Chat message a player sees the first time they open a loot chest. Empty to turn it off.");

        String random = "randomloot";
        c.setCategoryComment(
            random,
            "Each player's own random roll from a weighted loot table (a Forge chest-loot category). Rolled once, the "
                + "first time that player opens that chest half, and kept in their copy.");
        String mode = c.getString(
            "mode",
            random,
            "bonus",
            "bonus: the chest's own loot plus the player's roll. replace: only the player's roll (each player "
                + "gets different loot, like Lootr). off: everyone gets the chest's own loot.",
            new String[] { "bonus", "replace", "off" });
        randomMode = "replace".equalsIgnoreCase(mode) ? RandomMode.REPLACE
            : "off".equalsIgnoreCase(mode) ? RandomMode.OFF : RandomMode.BONUS;
        String[] rules = c.getStringList(
            "pools",
            random,
            DEFAULT_RULES,
            "Which table a chest rolls from: dimension;distance;table[;min-max]. A chest uses the rule for its own "
                + "dimension (or *, for dimensions without rules) with the largest distance from spawn it has "
                + "reached. min-max (optional) sets how many items to roll; otherwise the table's own count is used. "
                + "Tables: chest1..chest4 (the LootGames reward tiers, rising in value), dungeonChest, "
                + "villageBlacksmith, strongholdLibrary and the rest in config/TooMuchLoot/loot, or a custom pool "
                + "below.");
        poolRules = parseRules(rules);
        customPools = c.getStringList(
            "customPools",
            random,
            new String[0],
            "Extra weighted entries to create or extend tables, one per line: table;modid:item[:damage];weight;min;"
                + "max. Example: valier_rare;minecraft:diamond;5;1;3 (weight 5, 1 to 3 diamonds). Give a custom "
                + "table an item count in its pool rule.");
        if (c.hasChanged()) c.save();
    }

    /** Reads the config file again and re-registers the custom pools. */
    public static void reload() {
        load(file);
        registerCustomPools();
    }

    /** Adds the custom pool entries to Forge's loot tables (after other mods, at server start). */
    static void registerCustomPools() {
        for (Object[] added : addedPoolItems) ChestGenHooks.removeItem((String) added[0], (ItemStack) added[1]);
        addedPoolItems.clear();
        for (String line : customPools) {
            String[] f = line.trim()
                .split(";");
            try {
                if (f.length != 5) throw new IllegalArgumentException("needs 5 parts");
                ItemStack stack = stack(f[1].trim());
                if (stack == null) throw new IllegalArgumentException("unknown item " + f[1]);
                int weight = Integer.parseInt(f[2].trim()), min = Integer.parseInt(f[3].trim()),
                    max = Integer.parseInt(f[4].trim());
                ChestGenHooks.addItem(f[0].trim(), new WeightedRandomChestContent(stack, min, max, weight));
                addedPoolItems.add(new Object[] { f[0].trim(), stack });
            } catch (RuntimeException e) {
                ValierLoot.LOG.warn("Ignoring custom pool line '{}': {}", line, e.getMessage());
            }
        }
    }

    private static ItemStack stack(String spec) {
        String[] p = spec.split(":");
        if (p.length < 2) return null;
        int damage = p.length > 2 ? Integer.parseInt(p[2]) : 0;
        Item item = GameRegistry.findItem(p[0], p[1]);
        if (item == null) {
            Block block = GameRegistry.findBlock(p[0], p[1]);
            if (block != null) item = Item.getItemFromBlock(block);
        }
        return item == null ? null : new ItemStack(item, 1, damage);
    }

    public static List<PoolRule> parseRules(String[] lines) {
        List<PoolRule> rules = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.trim()
                .split(";");
            try {
                if (f.length < 3) throw new IllegalArgumentException("needs dimension;distance;table");
                Integer dim = "*".equals(f[0].trim()) ? null : Integer.valueOf(f[0].trim());
                int min = -1, max = -1;
                if (f.length > 3) {
                    String[] range = f[3].trim()
                        .split("-");
                    min = Integer.parseInt(range[0].trim());
                    max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
                    if (min < 0 || max < min) throw new IllegalArgumentException("bad item count " + f[3]);
                }
                rules.add(new PoolRule(dim, Integer.parseInt(f[1].trim()), f[2].trim(), min, max));
            } catch (RuntimeException e) {
                ValierLoot.LOG.warn("Ignoring loot pool rule '{}': {}", line, e.getMessage());
            }
        }
        return rules;
    }

    /** The rule for a chest in this dimension at this distance from spawn, or null. */
    public static PoolRule poolFor(int dimension, double distance) {
        // A dimension with rules of its own only uses those; the "*" rules are for every other dimension.
        boolean own = false;
        for (PoolRule r : poolRules) own |= r.dimension != null && r.dimension == dimension;
        PoolRule best = null;
        for (PoolRule r : poolRules) {
            boolean applies = own ? r.dimension != null && r.dimension == dimension : r.dimension == null;
            if (applies && r.minDistance <= distance && (best == null || r.minDistance > best.minDistance)) best = r;
        }
        return best;
    }
}
