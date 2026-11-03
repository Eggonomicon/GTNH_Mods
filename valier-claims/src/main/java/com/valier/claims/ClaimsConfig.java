package com.valier.claims;

import java.io.File;
import java.util.EnumSet;
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

    private ClaimsConfig() {}

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
        if (c.hasChanged()) c.save();
    }
}
