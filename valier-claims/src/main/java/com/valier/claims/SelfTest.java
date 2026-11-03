package com.valier.claims;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.ForgeEventFactory;

import cpw.mods.fml.common.eventhandler.Event;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.config.ConfigGroup;
import serverutils.lib.config.ConfigValueInstance;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.TeamType;
import serverutils.lib.data.Universe;
import serverutils.lib.enums.EnumCreature;
import serverutils.lib.math.ChunkDimPos;

/**
 * Test-server-only checks, enabled with {@code -Dvalierclaims.selftest=true}. Two boots:
 * <ol>
 * <li>{@code team create_server_team} for vctest, vclegacy and vcown, then {@code valierclaims selftest prepare}: new
 * teams get the defaults, a claim of vctest's blocks hostile spawns only, and changing the team's settings the way
 * {@code /team settings} and the settings screen do changes what spawns. vclegacy and vcown are made to look like
 * teams from before this mod, vcown one that already blocks bats. Then stop the server (it saves).</li>
 * <li>{@code valierclaims selftest check}: vctest kept its own choice (creature list included) through the restart,
 * vclegacy got the defaults as it loaded, vcown kept its own settings, and {@code reapply} works; then the test teams
 * and claim are removed again.</li>
 * </ol>
 * Each check logs one {@code [ValierClaims SelfTest] PASS/FAIL} line, then a SUMMARY line.
 */
final class SelfTest {

    static final boolean ENABLED = Boolean.getBoolean("valierclaims.selftest");

    private static final String TEAM = "vctest", LEGACY = "vclegacy", OWN = "vcown";
    private static final int CX = 200, CZ = 200; // claimed by vctest
    private static final int FREE_CX = 260, FREE_CZ = 200; // nobody's

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {}

    static void run(ICommandSender sender, String step) {
        passed = failed = 0;
        failures.clear();
        try {
            switch (step) {
                case "prepare":
                    prepare(sender);
                    break;
                case "check":
                    check(sender);
                    break;
                default:
                    ClaimsCommand.say(sender, "Steps: prepare, check");
                    return;
            }
        } catch (Throwable t) {
            ValierClaims.LOG.error("[ValierClaims SelfTest] " + step + " threw", t);
            check(false, step + " ran without an exception (" + t + ")");
        }
        String summary = "SUMMARY " + step
            + ": "
            + passed
            + " passed, "
            + failed
            + " failed"
            + (failures.isEmpty() ? "" : " " + failures);
        log(summary);
        ClaimsCommand.say(sender, summary);
    }

    private static void prepare(ICommandSender sender) throws Exception {
        check(ClaimedChunks.isActive(), "ServerUtilities chunk claiming is on");
        Universe universe = Universe.get();
        ForgeTeam team = team(universe, TEAM), legacy = team(universe, LEGACY), own = team(universe, OWN);
        check(team != null && team.type == TeamType.SERVER, "team create_server_team made " + TEAM);
        check(legacy != null && own != null, "team create_server_team made " + LEGACY + " and " + OWN);
        if (team == null || legacy == null || own == null) return;

        ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
        checkDefaults(team, "new team");

        ChunkDimPos claim = new ChunkDimPos(CX, CZ, 0);
        ClaimedChunks.instance.addChunk(new ClaimedChunk(claim, data));
        ClaimedChunks.instance.processQueue(); // new claims wait for the next tick otherwise
        team.markDirty();
        check(team.equalsTeam(ClaimedChunks.instance.getChunkTeam(claim)), "chunk " + claim + " is claimed by " + TEAM);
        check(ClaimedChunks.instance.getChunkTeam(new ChunkDimPos(FREE_CX, FREE_CZ, 0)) == null, "chunk " + FREE_CX + "," + FREE_CZ + " is nobody's");

        // The defaults: hostile mobs blocked, everything else spawns.
        expectSpawns(claim, false, EntityZombie.class, EntitySkeleton.class, EntityCreeper.class, EntitySpider.class);
        expectSpawns(claim, true, EntityCow.class, EntityPig.class, EntityChicken.class, EntityBat.class, EntitySquid.class);
        expectSpawns(new ChunkDimPos(FREE_CX, FREE_CZ, 0), true, EntityZombie.class, EntityCreeper.class);

        // The team blocks passive animals and bats too, in its settings screen.
        check(
            saveFromScreen(sender, team, "blocked_creatures", creatures("mob", "animal", "ambient")),
            "settings screen: blocked_creatures set to mob, animal, ambient");
        check(
            data.blockedCreatures.equals(EnumSet.of(EnumCreature.MOB, EnumCreature.ANIMAL, EnumCreature.AMBIENT)),
            "team data now blocks " + data.blockedCreatures);
        expectSpawns(claim, false, EntityZombie.class, EntityCow.class, EntityChicken.class, EntityBat.class);
        expectSpawns(claim, true, EntitySquid.class);

        // ...then turns claim spawn blocking off altogether: everything spawns again.
        check(
            saveFromScreen(sender, team, "mob_spawning", new NBTTagByte((byte) 0)),
            "settings screen: \"Disable Mob Spawns Within Claimed Chunks\" set to false");
        check(data.allowsMobSpawning(), "team data now allows mob spawning");
        expectSpawns(claim, true, EntityZombie.class, EntityCow.class, EntityBat.class);
        check(ClaimDefaults.marker(team).version() == ClaimDefaults.VERSION, "the marker stays after the team's change");

        // vclegacy: as if the team existed before this mod (no marker, ServerUtilities' own defaults).
        ServerUtilitiesTeamData legacyData = ServerUtilitiesTeamData.get(legacy);
        ClaimDefaults.marker(legacy).version = 0;
        check(setting(sender, legacy, "serverutilities.mob_spawning", "false"), "/team settings: vclegacy spawn blocking off");
        legacyData.blockedCreatures.clear();
        legacy.markDirty();
        check(legacyData.allowsMobSpawning() && legacyData.blockedCreatures.isEmpty(), "vclegacy looks like a pre-mod team");

        // vcown: a pre-mod team that already blocks spawns in its claims, bats only.
        ServerUtilitiesTeamData ownData = ServerUtilitiesTeamData.get(own);
        ClaimDefaults.marker(own).version = 0;
        check(saveFromScreen(sender, own, "blocked_creatures", creatures("ambient")), "settings screen: vcown blocks bats");
        check(
            !ownData.allowsMobSpawning() && ownData.blockedCreatures.equals(EnumSet.of(EnumCreature.AMBIENT)),
            "vcown looks like a pre-mod team that blocks bats only");
        log("now stop the server, start it again and run: valierclaims selftest check");
    }

    private static void check(ICommandSender sender) throws Exception {
        Universe universe = Universe.get();
        ForgeTeam team = team(universe, TEAM), legacy = team(universe, LEGACY), own = team(universe, OWN);
        check(team != null && legacy != null && own != null, "the test teams loaded");
        if (team == null || legacy == null || own == null) return;

        // vctest keeps its own choice: the defaults aren't given twice.
        ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
        check(ClaimDefaults.marker(team).version() == ClaimDefaults.VERSION, "vctest: marker saved and loaded");
        check(data.allowsMobSpawning(), "vctest: still allows spawning (its own choice, not reset)");
        check(
            data.blockedCreatures.equals(EnumSet.of(EnumCreature.MOB, EnumCreature.ANIMAL, EnumCreature.AMBIENT)),
            "vctest: its creature list was saved (" + data.blockedCreatures + ")");
        ChunkDimPos claim = new ChunkDimPos(CX, CZ, 0);
        check(team.equalsTeam(ClaimedChunks.instance.getChunkTeam(claim)), "vctest: its claim was saved");
        expectSpawns(claim, true, EntityZombie.class, EntityCow.class);

        // vclegacy had no marker: it gets the defaults as it loads.
        checkDefaults(legacy, "vclegacy after restart");

        // vcown had no marker either, but already blocked spawns: it keeps its own settings, bats only.
        ServerUtilitiesTeamData ownData = ServerUtilitiesTeamData.get(own);
        check(ClaimDefaults.marker(own).version() == ClaimDefaults.VERSION, "vcown: marked as done");
        check(
            !ownData.allowsMobSpawning() && ownData.blockedCreatures.equals(EnumSet.of(EnumCreature.AMBIENT)),
            "vcown: kept its own settings, bats only (" + ownData.blockedCreatures + ")");

        // reapply gives a team the defaults again.
        check(ClaimDefaults.apply(team), "reapply vctest");
        checkDefaults(team, "vctest after reapply");
        expectSpawns(claim, false, EntityZombie.class, EntitySkeleton.class);
        expectSpawns(claim, true, EntityCow.class, EntityBat.class, EntitySquid.class);

        // Clean up, so the test can run again.
        ClaimedChunks.instance.removeChunk(claim);
        team.markDirty();
        check(ClaimedChunks.instance.getChunkTeam(claim) == null, "test claim removed");
        team.delete();
        legacy.delete();
        own.delete();
        check(
            team(universe, TEAM) == null && team(universe, LEGACY) == null && team(universe, OWN) == null,
            "test teams deleted");
    }

    private static void checkDefaults(ForgeTeam team, String what) {
        ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
        ClaimDefaults.Marker marker = ClaimDefaults.marker(team);
        check(marker != null && marker.version() == ClaimDefaults.VERSION, what + ": defaults marked as given");
        check(!data.allowsMobSpawning(), what + ": mob spawning in claims is disabled");
        check(
            data.blockedCreatures.equals(EnumSet.of(EnumCreature.MOB)),
            what + ": only hostile mobs are blocked (" + data.blockedCreatures + ")");
    }

    /** Changes one team setting the way {@code /team settings <key> <value>} does. */
    private static boolean setting(ICommandSender sender, ForgeTeam team, String key, String value) {
        ConfigGroup group = team.getSettings();
        ConfigValueInstance instance = group.getValueInstance(key);
        if (instance == null) {
            log("no team setting " + key + "; there are: " + group.getValueKeyTree());
            return false;
        }
        if (!instance.getValue()
            .setValueFromString(sender, value, true)) return false;
        instance.getValue()
            .setValueFromString(sender, value, false);
        team.onConfigSaved(group, sender);
        return true;
    }

    /**
     * Changes one of the team's ServerUtilities settings the way its settings screen does: the screen sends back the
     * whole settings group as NBT, edited, and the server reads it in and saves.
     */
    private static boolean saveFromScreen(ICommandSender sender, ForgeTeam team, String key, NBTBase value) {
        ConfigGroup group = team.getSettings();
        if (group.getValueInstance("serverutilities." + key) == null) {
            log("no team setting serverutilities." + key + "; there are: " + group.getValueKeyTree());
            return false;
        }
        NBTTagCompound sent = group.serializeNBT();
        NBTTagCompound ours = sent.getCompoundTag("serverutilities");
        ours.setTag(key, value);
        sent.setTag("serverutilities", ours);
        group.deserializeNBT(sent);
        team.onConfigSaved(group, sender);
        return true;
    }

    /** A creature-type list as the settings screen sends it. */
    private static NBTTagList creatures(String... names) {
        NBTTagList list = new NBTTagList();
        for (String name : names) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("value", name);
            list.appendTag(entry);
        }
        return list;
    }

    @SafeVarargs
    private static void expectSpawns(ChunkDimPos where, boolean spawns, Class<? extends EntityLiving>... types)
        throws Exception {
        for (Class<? extends EntityLiving> type : types) check(
            spawns(type, where) == spawns,
            type.getSimpleName() + (spawns ? " spawns" : " is blocked") + " in chunk " + where);
    }

    /** What Forge's spawn check (the one natural spawning asks) says about this mob in the middle of the chunk. */
    private static boolean spawns(Class<? extends EntityLiving> type, ChunkDimPos where) throws Exception {
        WorldServer world = DimensionManager.getWorld(0);
        EntityLiving mob = type.getConstructor(World.class)
            .newInstance(world);
        float x = (where.posX << 4) + 8.5f, y = 70, z = (where.posZ << 4) + 8.5f;
        mob.setLocationAndAngles(x, y, z, 0, 0);
        return ForgeEventFactory.canEntitySpawn(mob, world, x, y, z) != Event.Result.DENY;
    }

    private static ForgeTeam team(Universe universe, String id) {
        ForgeTeam team = universe.getTeam(id);
        return team != null && team.isValid() ? team : null;
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            failures.add(what);
        }
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void log(String text) {
        ValierClaims.LOG.info("[ValierClaims SelfTest] " + text);
    }
}
