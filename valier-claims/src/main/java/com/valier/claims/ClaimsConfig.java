package com.valier.claims;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

import serverutils.lib.enums.EnumCreature;

/** Settings from config/valierclaims.cfg. */
public final class ClaimsConfig {

    public static boolean enabled = true;
    public static boolean applyToExistingTeams = true;
    /** The creature types a team blocks in its claims until it changes them. */
    public static Set<EnumCreature> defaultBlocked = EnumSet.of(EnumCreature.MOB);
    public static boolean logHostileMobs = true;
    /** What blockSpawnsFrom stops in claims that block hostile mobs: source keys, and code (class, method, package). */
    public static Set<String> blockSources = new HashSet<>();
    public static List<String> blockCode = new ArrayList<>();

    static final String[] DEFAULT_BLOCK = { "natural", "reinforcements", "siege", "thaumcraft.common.tiles.TileNode",
        "crazypants.enderzoo.entity.EntityWitherWitch.spawnCat" };

    private ClaimsConfig() {}

    static boolean stopsAnything() {
        return !blockSources.isEmpty() || !blockCode.isEmpty();
    }

    /** Splits blockSpawnsFrom into source keys and code names. */
    static void setBlockSpawnsFrom(String[] entries) {
        Set<String> sources = new HashSet<>();
        List<String> code = new ArrayList<>();
        for (String entry : entries) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.contains(".")) code.add(e);
            else if (SpawnLog.isSourceKey(e.toLowerCase(Locale.ROOT))) sources.add(e.toLowerCase(Locale.ROOT));
            else ValierClaims.LOG.warn("blockSpawnsFrom: unknown source '" + entry + "', skipped");
        }
        blockSources = sources;
        blockCode = code;
    }

    static void load(File file) {
        Configuration c = new Configuration(file);
        String cat = Configuration.CATEGORY_GENERAL;
        enabled = c.getBoolean(
            "enabled",
            cat,
            true,
            "Give every ServerUtilities team the default spawn settings below for its claims, once. Teams can change "
                + "them afterwards in their team settings, and keep what they choose.");
        applyToExistingTeams = c.getBoolean(
            "applyToExistingTeams",
            cat,
            true,
            "Also give them to teams that already existed when this mod was installed (once each, at server start).");
        String[] names = c.getStringList(
            "defaultBlocked",
            cat,
            new String[] { "mob" },
            "Creature types that don't spawn in a team's claims by default: mob (hostile), animal (passive), "
                + "ambient (bats), water_creature (squid). Empty: nothing is blocked by default.");
        Set<EnumCreature> blocked = EnumSet.noneOf(EnumCreature.class);
        for (String name : names) {
            EnumCreature type = EnumCreature.NAME_MAP.getNullable(
                name.trim()
                    .toLowerCase(Locale.ROOT));
            if (type != null) blocked.add(type);
            else if (!name.trim()
                .isEmpty()) ValierClaims.LOG.warn("defaultBlocked: unknown creature type '" + name + "', skipped");
        }
        defaultBlocked = blocked;
        logHostileMobs = c.getBoolean(
            "logHostileMobs",
            cat,
            true,
            "Log every hostile mob that turns up inside a claim that blocks hostile spawns, and what put it there (a "
                + "spawner, zombie reinforcements, a spawn egg, another mod, or walking in from outside), to "
                + "logs/valierclaims-spawns.log. /valierclaims spawns shows the latest.");
        setBlockSpawnsFrom(
            c.getStringList(
                "blockSpawnsFrom",
                cat,
                DEFAULT_BLOCK,
                "Stop hostile mobs from these sources spawning inside a claim that blocks hostile mobs (ServerUtilities "
                    + "only stops natural spawning; these skip its check). Each entry is a source: natural, "
                    + "reinforcements (a zombie that is hit calling for help), siege (village zombie sieges), spawner, "
                    + "spawn_egg, command, portal, villager_converted, slime_split, lightning, worldgen, or mod (any "
                    + "other mod's spawning; careful, that also stops mob farms such as EnderIO powered spawners, mobs "
                    + "let out of soul vials and summoned bosses). Or it is the class, method or package of the code "
                    + "that spawns them, as logs/valierclaims-spawns.log shows it after 'mod code:'. The defaults also "
                    + "stop Thaumcraft dark aura nodes' zombies and EnderZoo Wither Witches' cats. Empty: nothing is "
                    + "stopped, only logged."));
        if (c.hasChanged()) c.save();
    }
}
