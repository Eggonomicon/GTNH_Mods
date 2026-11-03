package com.valier.claims;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.events.team.ForgeTeamCreatedEvent;
import serverutils.events.team.ForgeTeamDataEvent;
import serverutils.events.team.ForgeTeamLoadedEvent;
import serverutils.events.universe.UniverseLoadedEvent;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.NBTDataStorage;
import serverutils.lib.data.TeamData;
import serverutils.lib.enums.EnumCreature;

/**
 * Gives every ServerUtilities team its default spawn settings for its claims, once: by default hostile mobs are
 * blocked. ServerUtilities does the blocking itself, from two team settings ("Disable Mob Spawns Within Claimed Chunks"
 * and "Creature Types To Block Within Claimed Chunks"), as long as {@code blockMobSpawningInClaims} in its own config
 * is DEFAULT. A team that changes them keeps its choice: a {@link Marker} in the team's saved data records that it had
 * its defaults.
 * <p>
 * The marker also keeps a copy of the team's creature list. ServerUtilities 2.4.22 saves that list as "MOB",
 * "ANIMAL", ... but looks the names up in lower case when loading, so every entry comes back as "mob" and a team's
 * choice of passive animals or bats is lost at each restart. The copy is put back once the team has loaded.
 */
public final class ClaimDefaults {

    static final String DATA_ID = "valierclaims";
    /** Raise this to give every team the (new) defaults again. */
    static final int VERSION = 1;

    private static Field mobSpawningDisabled;

    @SubscribeEvent
    public void onTeamData(ForgeTeamDataEvent event) {
        event.register(new Marker(event.getTeam()));
    }

    @SubscribeEvent
    public void onTeamCreated(ForgeTeamCreatedEvent event) {
        applyOnce(event.getTeam(), false);
    }

    @SubscribeEvent
    public void onTeamLoaded(ForgeTeamLoadedEvent event) {
        loaded(event.getTeam());
    }

    /** Server teams load without a ForgeTeamLoadedEvent, so look over every team once loading is done. */
    @SubscribeEvent
    public void onUniverseLoaded(UniverseLoadedEvent.Finished event) {
        for (ForgeTeam team : event.getUniverse()
            .getTeams()) loaded(team);
    }

    private static void loaded(ForgeTeam team) {
        restoreBlocked(team);
        if (ClaimsConfig.applyToExistingTeams) applyOnce(team, true);
    }

    /**
     * Gives the team its defaults if it never had them. A team from before this mod that already blocks spawns in its
     * claims ({@code existing}) keeps its own settings.
     */
    public static boolean applyOnce(ForgeTeam team, boolean existing) {
        if (!ClaimsConfig.enabled || !team.type.save) return false;
        Marker marker = marker(team);
        ServerUtilitiesTeamData data = data(team);
        if (marker == null || data == null || marker.version >= VERSION) return false;
        if (existing && !data.allowsMobSpawning()) {
            marker.version = VERSION;
            team.markDirty();
            ValierClaims.LOG.info("Team " + team.getId() + " already blocks " + describe(data) + " in its claims: kept");
            return false;
        }
        return apply(team);
    }

    /** Gives the team the defaults now, replacing its own settings; false if this ServerUtilities can't take them. */
    public static boolean apply(ForgeTeam team) {
        ServerUtilitiesTeamData data = data(team);
        Marker marker = marker(team);
        if (data == null || marker == null) return false;
        Set<EnumCreature> blocked = ClaimsConfig.defaultBlocked;
        // ServerUtilities reads "disabled, with no types picked" as "block everything", so nothing picked means allow.
        if (!setSpawningDisabled(data, !blocked.isEmpty())) return false;
        data.blockedCreatures.clear();
        data.blockedCreatures.addAll(blocked);
        marker.version = VERSION;
        team.markDirty();
        ValierClaims.LOG.info("Team " + team.getId() + ": claims now block " + describe(data));
        return true;
    }

    /** Puts back the team's creature list as it was saved (see the class comment); once, right after loading. */
    static void restoreBlocked(ForgeTeam team) {
        Marker marker = marker(team);
        ServerUtilitiesTeamData data = data(team);
        if (marker == null || data == null || marker.savedBlocked == null) return;
        data.blockedCreatures.clear();
        data.blockedCreatures.addAll(marker.savedBlocked);
        marker.savedBlocked = null;
    }

    /** This mod's part of the team's saved data; null only if the team was made before this mod loaded. */
    public static Marker marker(ForgeTeam team) {
        NBTDataStorage.Data data = team.getData()
            .getRaw(DATA_ID);
        return data instanceof Marker ? (Marker) data : null;
    }

    /** ServerUtilities' own data for the team (its settings, its claims). */
    public static ServerUtilitiesTeamData data(ForgeTeam team) {
        NBTDataStorage.Data data = team.getData()
            .getRaw("serverutilities");
        return data instanceof ServerUtilitiesTeamData ? (ServerUtilitiesTeamData) data : null;
    }

    /** What the team's claims block, in words: "hostile mobs", "everything", "nothing", ... */
    public static String describe(ServerUtilitiesTeamData data) {
        if (data.allowsMobSpawning()) return "nothing";
        return data.blockedCreatures.isEmpty() ? "everything" : describe(data.blockedCreatures);
    }

    /** These creature types in words. */
    public static String describe(Set<EnumCreature> blocked) {
        if (blocked.isEmpty()) return "nothing";
        if (blocked.size() >= EnumCreature.VALUES.length) return "everything";
        StringJoiner words = new StringJoiner(", ");
        for (EnumCreature type : EnumCreature.VALUES) if (blocked.contains(type)) words.add(word(type));
        return words.toString();
    }

    private static String word(EnumCreature type) {
        switch (type) {
            case MOB:
                return "hostile mobs";
            case ANIMAL:
                return "passive animals";
            case AMBIENT:
                return "bats";
            case WATER_CREATURE:
                return "water mobs";
            default:
                return type.name();
        }
    }

    /** ServerUtilities has no public setter for this one (its team settings screen sets it). */
    private static boolean setSpawningDisabled(ServerUtilitiesTeamData data, boolean disabled) {
        try {
            if (mobSpawningDisabled == null) {
                Field f = ServerUtilitiesTeamData.class.getDeclaredField("mobSpawningDisabled");
                f.setAccessible(true);
                mobSpawningDisabled = f;
            }
            mobSpawningDisabled.setBoolean(data, disabled);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ValierClaims.LOG.error("Can't change a team's mob spawning setting: unsupported ServerUtilities version?", e);
            return false;
        }
    }

    /**
     * Whether the team has had its defaults, and a copy of its creature list (saved with the team, as "valierclaims"
     * in its data).
     */
    public static final class Marker extends TeamData {

        int version;
        /** The creature list as last saved, until {@link #restoreBlocked} puts it back; null if there was none. */
        Set<EnumCreature> savedBlocked;

        Marker(ForgeTeam team) {
            super(team);
        }

        public int version() {
            return version;
        }

        @Override
        public String getId() {
            return DATA_ID;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (version > 0) tag.setInteger("Defaults", version);
            ServerUtilitiesTeamData data = data(team);
            if (data != null) {
                NBTTagList blocked = new NBTTagList();
                for (EnumCreature type : data.blockedCreatures)
                    blocked.appendTag(new NBTTagString(EnumCreature.NAME_MAP.getName(type)));
                tag.setTag("Blocked", blocked);
            }
            return tag;
        }

        @Override
        public void deserializeNBT(NBTTagCompound tag) {
            version = tag.getInteger("Defaults");
            savedBlocked = null;
            if (tag.hasKey("Blocked", Constants.NBT.TAG_LIST)) {
                savedBlocked = EnumSet.noneOf(EnumCreature.class);
                NBTTagList blocked = tag.getTagList("Blocked", Constants.NBT.TAG_STRING);
                for (int i = 0; i < blocked.tagCount(); i++) {
                    EnumCreature type = EnumCreature.NAME_MAP.getNullable(blocked.getStringTagAt(i));
                    if (type != null) savedBlocked.add(type);
                }
            }
        }
    }
}
