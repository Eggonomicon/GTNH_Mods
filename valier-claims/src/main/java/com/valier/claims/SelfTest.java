package com.valier.claims;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
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
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
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
 * The spawn log has a one-boot test: {@code team create_server_team vcspawn}, then {@code valierclaims selftest
 * spawnlog}. It puts hostile mobs into a claim of vcspawn's in different ways (a command, a spawn egg, real zombie
 * reinforcements, code that blockSpawnsFrom lists, walking in) and checks what {@link SpawnLog} makes of each: logged,
 * or stopped and logged. It also checks the default blockSpawnsFrom against the code that spawns Thaumcraft dark node
 * zombies, Wither Witch cats, EnderIO powered spawner mobs and others. Then it removes the team and claim.
 * <p>
 * Each check logs one {@code [ValierClaims SelfTest] PASS/FAIL} line, then a SUMMARY line.
 */
final class SelfTest {

    static final boolean ENABLED = Boolean.getBoolean("valierclaims.selftest");

    private static final String TEAM = "vctest", LEGACY = "vclegacy", OWN = "vcown", SPAWN_TEAM = "vcspawn";
    private static final int CX = 200, CZ = 200; // claimed by vctest
    private static final int FREE_CX = 260, FREE_CZ = 200; // nobody's
    /** vcspawn claims a square of chunks this far around SCX,SCZ: room for reinforcements, which land 7-40 away. */
    private static final int SCX = 300, SCZ = 200, SR = 3;

    private static int passed, failed;
    /** How many of SpawnLog's entries the spawn-log checks have looked at. */
    private static int seen;
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
                case "spawnlog":
                    spawnLog();
                    break;
                default:
                    ClaimsCommand.say(sender, "Steps: prepare, check, spawnlog");
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

    private static void spawnLog() throws Exception {
        Universe universe = Universe.get();
        ForgeTeam team = team(universe, SPAWN_TEAM);
        check(team != null, "team create_server_team made " + SPAWN_TEAM);
        if (team == null) return;
        checkDefaults(team, SPAWN_TEAM);
        check(ClaimsConfig.logHostileMobs, "logHostileMobs is on");

        WorldServer world = DimensionManager.getWorld(0);
        ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
        List<ChunkDimPos> claims = new ArrayList<>();
        for (int cx = SCX - SR; cx <= SCX + SR; cx++) for (int cz = SCZ - SR; cz <= SCZ + SR; cz++) {
            world.theChunkProviderServer.loadChunk(cx, cz);
            ChunkDimPos pos = new ChunkDimPos(cx, cz, 0);
            ClaimedChunks.instance.addChunk(new ClaimedChunk(pos, data));
            claims.add(pos);
        }
        int freeCX = SCX + SR + 1; // the chunk east of the claim: nobody's
        world.theChunkProviderServer.loadChunk(freeCX, SCZ);
        ClaimedChunks.instance.processQueue();
        boolean claimed = true;
        for (ChunkDimPos pos : claims) claimed &= team.equalsTeam(ClaimedChunks.instance.getChunkTeam(pos));
        check(claimed, SPAWN_TEAM + " claims the " + claims.size() + " chunks around " + SCX + "," + SCZ);
        check(ClaimedChunks.instance.getChunkTeam(new ChunkDimPos(freeCX, SCZ, 0)) == null, "chunk " + freeCX + "," + SCZ + " is nobody's");

        long time = world.getWorldTime();
        EnumDifficulty difficulty = world.difficultySetting;
        List<Entity> made = new ArrayList<>();
        SpawnLog.reset();
        seen = 0;
        try {
            int x = (SCX << 4) + 8, z = (SCZ << 4) + 8;

            check(
                ClaimsConfig.blockSources.containsAll(Arrays.asList("natural", "reinforcements", "siege"))
                    && ClaimsConfig.blockCode.containsAll(
                        Arrays.asList("thaumcraft.common.tiles.TileNode", "crazypants.enderzoo.entity.EntityWitherWitch.spawnCat")),
                "blockSpawnsFrom has the defaults (" + ClaimsConfig.blockSources + " " + ClaimsConfig.blockCode + ")");
            // The defaults against the code that spawns these: the mod frames are as the live server logged them.
            expectStop(
                true,
                "a Thaumcraft dark node's zombie",
                "thaumcraft.common.tiles.TileNode.handleDarkNode",
                "thaumcraft.common.tiles.TileNode.func_145845_h",
                "makeo.gadomancy.common.blocks.tiles.TileExtendedNode.func_145845_h");
            expectStop(
                true,
                "an EnderZoo Wither Witch's cat",
                "crazypants.enderzoo.entity.EntityWitherWitch.spawnCat",
                "crazypants.enderzoo.entity.EntityWitherWitch.spawnCats",
                "crazypants.enderzoo.entity.EntityWitherWitch.func_70636_d");
            expectStop(true, "a naturally spawned mob", "net.minecraft.world.SpawnerAnimals.func_77192_a");
            expectStop(true, "a village siege zombie", "net.minecraft.village.VillageSiege.spawnZombie");
            expectStop(false, "an EnderIO powered spawner's mob", "crazypants.enderio.machine.spawner.TilePoweredSpawner.trySpawnEntity");
            expectStop(
                false,
                "a mob let out of an EnderIO soul vial",
                "crazypants.enderio.item.ItemSoulVessel.func_77648_a",
                "net.minecraft.item.ItemStack.func_77943_a");
            expectStop(
                false,
                "a spawner block's mob",
                "net.minecraft.tileentity.MobSpawnerBaseLogic.func_98278_g",
                "net.minecraft.tileentity.TileEntityMobSpawner.func_145845_h");

            EntityZombie byCommand = spawn(world, new EntityZombie(world), x, z, made);
            expectLog("zombie spawned by a command", "Zombie in vcspawn's claim at " + x + " ", "a command");
            check(world.loadedEntityList.contains(byCommand), "the zombie from a command is there (not stopped)");

            Entity fromEgg = ItemMonsterPlacer.spawnCreature(world, 54, x + 2.5, world.getHeightValue(x + 2, z), z + 0.5);
            made.add(fromEgg);
            expectLog("zombie from a spawn egg", "Zombie", "a spawn egg");
            check(world.loadedEntityList.contains(fromEgg), "the zombie from a spawn egg is there (not stopped)");

            spawn(world, new EntityCow(world), x + 4, z, made);
            expectLog("cow in the claim");

            EntityZombie saved = new EntityZombie(world);
            saved.setLocationAndAngles(x + 5.5, world.getHeightValue(x + 5, z), z + 0.5, 0, 0);
            world.addLoadedEntities(Collections.singletonList(saved));
            made.add(saved);
            expectLog("zombie loaded with its chunk");

            for (int i = 0; i < 3; i++) spawn(world, new EntityZombie(world), x - 2 - i, z, made);
            expectLog("3 more zombies from a command in the same chunk, within 10 minutes");

            // Zombie reinforcements: on Hard, a zombie that is hit can call another zombie, 7-40 blocks away, in the dark.
            world.setWorldTime(18000);
            world.skylightSubtracted = world.calculateSkylightSubtracted(1.0F);
            world.difficultySetting = EnumDifficulty.HARD;
            EntityZombie hit = spawn(world, new EntityZombie(world), x - 6, z - 6, made);
            hit.getAttributeMap()
                .getAttributeInstanceByName("zombie.spawnReinforcements")
                .setBaseValue(1.0);
            FakePlayer player = FakePlayerFactory.getMinecraft(world);
            AxisAlignedBB claimArea = AxisAlignedBB
                .getBoundingBox((SCX - SR) << 4, 0, (SCZ - SR) << 4, (SCX + SR + 1) << 4, 256, (SCZ + SR + 1) << 4);
            List<?> zombiesBefore = world.getEntitiesWithinAABB(EntityZombie.class, claimArea);
            seen = SpawnLog.recent()
                .size();
            int hits = 0;
            while (hits < 40 && SpawnLog.recent()
                .size() == seen) {
                hits++;
                hit.hurtResistantTime = 0;
                hit.setHealth(hit.getMaxHealth());
                hit.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
            }
            log("zombie hit " + hits + " time(s)");
            expectLog(
                "a hit zombie's reinforcements",
                "Zombie stopped from spawning in vcspawn's claim",
                "zombie reinforcements");
            int extra = 0;
            for (Object o : world.getEntitiesWithinAABB(EntityZombie.class, claimArea))
                if (!zombiesBefore.contains(o)) extra++;
            check(extra == 0, "the reinforcements were stopped: no new zombie in the claim (" + extra + ")");

            // A mod's spawning that blockSpawnsFrom names by its method is stopped; once unlisted, it isn't.
            List<String> code = ClaimsConfig.blockCode;
            try {
                ClaimsConfig.blockCode = new ArrayList<>(code);
                ClaimsConfig.blockCode.add("com.valier.claims.SelfTest.likeAMod");
                EntityZombie listed = likeAMod(world, new EntityZombie(world), x - 16, z, made);
                expectLog("a zombie from code that blockSpawnsFrom lists", "Zombie stopped from spawning in vcspawn's claim");
                check(!world.loadedEntityList.contains(listed), "the zombie from listed code isn't in the world");
            } finally {
                ClaimsConfig.blockCode = code;
            }
            EntityZombie unlisted = likeAMod(world, new EntityZombie(world), x - 16, z - 16, made);
            expectLog("the same code once unlisted", "Zombie in vcspawn's claim");
            check(world.loadedEntityList.contains(unlisted), "the zombie from unlisted code is there");

            EntityZombie walker = spawn(world, new EntityZombie(world), (freeCX << 4) + 8, z, made);
            expectLog("zombie spawned outside the claim");
            walker.ticksExisted = 40;
            moveTo(world, walker, ((SCX + SR) << 4) + 8, z);
            expectLog("zombie walking in", "Zombie", "came in from chunk " + freeCX + "," + SCZ);
            moveTo(world, walker, (freeCX << 4) + 8, z);
            moveTo(world, walker, ((SCX + SR) << 4) + 8, z);
            expectLog("the same zombie walking in again");

            String file = new String(Files.readAllBytes(SpawnLog.FILE.toPath()), StandardCharsets.UTF_8);
            check(
                file.contains("zombie reinforcements") && file.contains("called from: "),
                SpawnLog.FILE + " has the entries and where they were called from");
        } finally {
            for (Entity e : made) if (e != null) e.setDead();
            AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
                (SCX - SR) << 4,
                0,
                (SCZ - SR) << 4,
                (freeCX + 1) << 4,
                256,
                (SCZ + SR + 1) << 4);
            for (Object e : world.getEntitiesWithinAABB(EntityLiving.class, area)) ((Entity) e).setDead();
            world.setWorldTime(time);
            world.skylightSubtracted = world.calculateSkylightSubtracted(1.0F);
            world.difficultySetting = difficulty;
            for (ChunkDimPos pos : claims) ClaimedChunks.instance.removeChunk(pos);
            team.markDirty();
            team.delete();
        }
        check(ClaimedChunks.instance.getChunkTeam(new ChunkDimPos(SCX, SCZ, 0)) == null, "test claims removed");
        check(team(universe, SPAWN_TEAM) == null, "test team deleted");
    }

    private static <T extends EntityLiving> T spawn(WorldServer world, T mob, int x, int z, List<Entity> made) {
        mob.setLocationAndAngles(x + 0.5, world.getHeightValue(x, z), z + 0.5, 0, 0);
        world.spawnEntityInWorld(mob);
        made.add(mob);
        return mob;
    }

    /** Spawns a mob the way another mod's code would; blockSpawnsFrom can name this method. */
    private static <T extends EntityLiving> T likeAMod(WorldServer world, T mob, int x, int z, List<Entity> made) {
        return spawn(world, mob, x, z, made);
    }

    /** What blockSpawnsFrom does with a spawn by this code (innermost first), called from a world tick. */
    private static void expectStop(boolean stopped, String what, String... code) {
        List<StackTraceElement> frames = new ArrayList<>();
        frames.add(frame("com.valier.claims.SpawnLog.stopSpawn"));
        frames.add(frame("cpw.mods.fml.common.eventhandler.EventBus.post"));
        frames.add(frame("net.minecraft.world.World.func_72838_d"));
        for (String method : code) frames.add(frame(method));
        frames.add(frame("net.minecraft.world.World.func_72939_s"));
        frames.add(frame("net.minecraft.server.MinecraftServer.func_71217_p"));
        StackTraceElement[] stack = frames.toArray(new StackTraceElement[0]);
        SpawnLog.Source source = SpawnLog.source(stack);
        check(
            SpawnLog.stops(source, stack) == stopped,
            what + (stopped ? " is stopped" : " isn't stopped") + " (" + source.key + ": " + source.words + ")");
    }

    private static StackTraceElement frame(String method) {
        int dot = method.lastIndexOf('.');
        return new StackTraceElement(method.substring(0, dot), method.substring(dot + 1), null, -1);
    }

    /** Moves a mob the way a tick does, so it changes chunk. */
    private static void moveTo(WorldServer world, Entity mob, int x, int z) {
        mob.setPosition(x + 0.5, world.getHeightValue(x, z), z + 0.5);
        world.updateEntityWithOptionalForce(mob, true);
    }

    /** One new SpawnLog entry containing all of {@code parts} since the last call; none if no parts are given. */
    private static void expectLog(String what, String... parts) {
        List<String> lines = SpawnLog.recent();
        List<String> fresh = lines.subList(Math.min(seen, lines.size()), lines.size());
        seen = lines.size();
        if (parts.length == 0) {
            check(fresh.isEmpty(), what + ": not logged" + (fresh.isEmpty() ? "" : " (but got " + fresh + ")"));
            return;
        }
        boolean ok = fresh.size() == 1;
        if (ok) for (String part : parts) ok &= fresh.get(0)
            .contains(part);
        check(ok, what + ": logged " + (fresh.isEmpty() ? "nothing" : fresh));
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
