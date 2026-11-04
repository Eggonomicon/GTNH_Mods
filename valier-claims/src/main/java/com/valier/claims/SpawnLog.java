package com.valier.claims;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.config.EnumTristate;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.enums.EnumCreature;
import serverutils.lib.math.ChunkDimPos;

/**
 * Logs every hostile mob that turns up inside a claim whose team blocks hostile spawns, and what put it there, to
 * {@code logs/valierclaims-spawns.log}. ServerUtilities only stops natural spawning: spawner blocks, zombie
 * reinforcements, spawn eggs and other mods' own spawning skip its check, and mobs can walk in from unclaimed land.
 * {@code /valierclaims spawns} shows the latest entries.
 */
public final class SpawnLog {

    static final File FILE = new File("logs", "valierclaims-spawns.log");
    /** The same mob, from the same source, in the same chunk, is logged at most once in this long. */
    private static final long QUIET_MS = 10 * 60 * 1000L;
    private static final int KEEP = 50;

    /**
     * What spawned a mob, recognised from the code that called: {label, class (or class prefix ending in "."), method
     * names (deobfuscated, then runtime)...}; no method names means any method. The first rule that matches any frame
     * wins, so a mob that Special Mobs swaps for a variant still shows where the original came from.
     */
    private static final String[][] SOURCES = {
        { "a mob spawner block", "net.minecraft.tileentity.MobSpawnerBaseLogic" },
        { "zombie reinforcements (a zombie was hit and called for help)", "net.minecraft.entity.monster.EntityZombie",
            "attackEntityFrom", "func_70097_a" },
        { "a zombie killed a villager", "net.minecraft.entity.monster.EntityZombie", "onKillEntity", "func_70074_a" },
        { "a slime split", "net.minecraft.entity.monster.EntitySlime", "setDead", "func_70106_y" },
        { "lightning", "net.minecraft.entity.", "onStruckByLightning", "func_70077_a" },
        { "a village zombie siege", "net.minecraft.village.VillageSiege" },
        { "a spawn egg", "net.minecraft.item.ItemMonsterPlacer" },
        { "a portal (it came from another dimension)", "net.minecraft.entity.Entity", "travelToDimension",
            "func_71027_c" },
        { "natural spawning, which ServerUtilities should have blocked", "net.minecraft.world.SpawnerAnimals",
            "findChunksForSpawning", "func_77192_a" },
        { "world generation", "net.minecraft.world.SpawnerAnimals", "performWorldGenSpawning", "func_77191_a" },
        { "a command", "net.minecraft.command." }, };

    private static final Deque<String> recent = new ArrayDeque<>();
    /** Per mob, source and chunk: when it was last logged, and how many were left out since. */
    private static final Map<String, long[]> lastLogged = new HashMap<>();
    private static final Set<Entity> walkedIn = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinWorldEvent event) {
        Entity mob = event.entity;
        if (!ClaimsConfig.logHostileMobs || event.world.isRemote || !hostile(mob)) return;
        ChunkDimPos pos = new ChunkDimPos(
            MathHelper.floor_double(mob.posX) >> 4,
            MathHelper.floor_double(mob.posZ) >> 4,
            event.world.provider.dimensionId);
        ForgeTeam team = blockingTeam(mob, pos);
        if (team == null) return;
        StackTraceElement[] stack = new Throwable().getStackTrace();
        if (loadedWithChunk(stack)) return; // it was saved there, not spawned
        record(team, mob, source(stack), stack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnteringChunk(EntityEvent.EnteringChunk event) {
        Entity mob = event.entity;
        // A mob that spawned or loaded this tick enters its first chunk too (ticksExisted 0): onJoin covers those.
        if (!ClaimsConfig.logHostileMobs || mob.ticksExisted == 0
            || mob.worldObj == null
            || mob.worldObj.isRemote
            || !hostile(mob)) return;
        int dim = mob.worldObj.provider.dimensionId;
        ForgeTeam team = blockingTeam(mob, new ChunkDimPos(event.newChunkX, event.newChunkZ, dim));
        if (team == null) return;
        ForgeTeam from = blockingTeam(mob, new ChunkDimPos(event.oldChunkX, event.oldChunkZ, dim));
        if (from != null && team.equalsTeam(from)) return; // moving around inside the claim
        if (!walkedIn.add(mob)) return; // once per mob
        record(
            team,
            mob,
            "came in from chunk " + event.oldChunkX + "," + event.oldChunkZ + " (walked or teleported)",
            null);
    }

    private static boolean hostile(Entity entity) {
        return entity instanceof IMob && entity instanceof EntityLiving;
    }

    /**
     * The team that claimed this chunk, if ServerUtilities would stop this mob spawning there (the same rules as its
     * own spawn check); null otherwise.
     */
    static ForgeTeam blockingTeam(Entity mob, ChunkDimPos pos) {
        EnumTristate setting = ServerUtilitiesConfig.world.blockMobSpawningInClaims;
        if (setting.isFalse() || !ClaimedChunks.isActive()) return null;
        ForgeTeam team = ClaimedChunks.instance.getChunkTeam(pos);
        if (team == null) return null;
        if (setting.isTrue()) {
            String[] names = ServerUtilitiesConfig.world.mobTypesToBlock;
            if (names.length == 0) return team;
            for (String name : names) {
                EnumCreature type = EnumCreature.NAME_MAP.getNullable(name.toLowerCase(Locale.ROOT));
                if (type != null && is(type, mob)) return team;
            }
            return null;
        }
        ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
        if (data.allowsMobSpawning()) return null;
        Set<EnumCreature> blocked = data.blockedCreatures;
        if (blocked.isEmpty() || blocked.size() >= EnumCreature.VALUES.length) return team;
        for (EnumCreature type : blocked) if (is(type, mob)) return team;
        return null;
    }

    private static boolean is(EnumCreature type, Entity mob) {
        return type.creatureType.getCreatureClass()
            .isAssignableFrom(mob.getClass());
    }

    /** Entities saved in a chunk join the world again each time the chunk loads. */
    static boolean loadedWithChunk(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if (matches(frame, "net.minecraft.world.World", "addLoadedEntities", "func_72868_a")
                || matches(frame, "net.minecraft.world.chunk.Chunk", "onChunkLoad", "func_76631_c")
                || frame.getClassName()
                    .equals("net.minecraft.world.chunk.storage.AnvilChunkLoader"))
                return true;
        }
        return false;
    }

    /** What spawned the mob, in words: a known source, else the mod (or Minecraft) code that did. */
    static String source(StackTraceElement[] stack) {
        for (String[] rule : SOURCES) {
            for (StackTraceElement frame : stack) {
                if (matches(frame, rule[1], Arrays.copyOfRange(rule, 2, rule.length))) return rule[0];
            }
        }
        List<StackTraceElement> callers = callers(stack);
        for (StackTraceElement frame : callers) if (!isMinecraftOrForge(frame)) return "mod code: " + name(frame);
        return callers.isEmpty() ? "unknown" : "Minecraft code: " + name(callers.get(0));
    }

    private static boolean matches(StackTraceElement frame, String type, String... methods) {
        String cls = frame.getClassName();
        if (type.endsWith(".") ? !cls.startsWith(type) : !cls.equals(type)) return false;
        if (methods.length == 0) return true;
        for (String method : methods) if (method.equals(frame.getMethodName())) return true;
        return false;
    }

    /** The frames below World.spawnEntityInWorld: the code that spawned the mob, innermost first. */
    private static List<StackTraceElement> callers(StackTraceElement[] stack) {
        List<StackTraceElement> out = new ArrayList<>();
        boolean found = false;
        for (StackTraceElement frame : stack) {
            if (found) out.add(frame);
            else if (frame.getClassName()
                .startsWith("net.minecraft.world.World")
                && ("spawnEntityInWorld".equals(frame.getMethodName())
                    || "func_72838_d".equals(frame.getMethodName())))
                found = true;
        }
        return out;
    }

    private static boolean isMinecraftOrForge(StackTraceElement frame) {
        String cls = frame.getClassName();
        return cls.startsWith("net.minecraft.") || cls.startsWith("net.minecraftforge.")
            || cls.startsWith("cpw.mods.fml.")
            || cls.startsWith("java.")
            || cls.startsWith("jdk.")
            || cls.startsWith("sun.")
            || cls.startsWith("com.valier.claims.");
    }

    private static String name(StackTraceElement frame) {
        return frame.getClassName() + "." + frame.getMethodName();
    }

    private static synchronized void record(ForgeTeam team, Entity mob, String how, StackTraceElement[] stack) {
        String name = EntityList.getEntityString(mob);
        if (name == null) name = mob.getClass()
            .getSimpleName();
        int x = MathHelper.floor_double(mob.posX), y = MathHelper.floor_double(mob.posY),
            z = MathHelper.floor_double(mob.posZ);
        int dim = mob.worldObj.provider.dimensionId;
        long now = System.currentTimeMillis();
        String key = how + "|" + name + "|" + dim + "|" + (x >> 4) + "," + (z >> 4);
        long[] last = lastLogged.get(key);
        if (last != null && now - last[0] < QUIET_MS) {
            last[1]++;
            return;
        }
        if (lastLogged.size() > 2000) {
            Iterator<long[]> it = lastLogged.values()
                .iterator();
            while (it.hasNext()) if (now - it.next()[0] >= QUIET_MS) it.remove();
        }
        lastLogged.put(key, new long[] { now, 0 });

        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(now)) + " "
            + name
            + " in "
            + team.getId()
            + "'s claim at "
            + x
            + " "
            + y
            + " "
            + z
            + (dim != 0 ? " (dim " + dim + ")" : "")
            + ": "
            + how
            + (last != null && last[1] > 0 ? " [" + last[1] + " more like this were left out before]" : "");
        recent.addLast(line);
        while (recent.size() > KEEP) recent.removeFirst();
        ValierClaims.LOG.info("Hostile mob in a claim: " + line);
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            try (PrintWriter out = new PrintWriter(new FileWriter(FILE, true))) {
                out.println(line);
                if (stack != null) out.println("    called from: " + trace(stack));
            }
        } catch (IOException e) {
            ValierClaims.LOG.warn("Can't write " + FILE + ": " + e);
        }
    }

    /** The first few frames below World.spawnEntityInWorld, for working out an unknown source. */
    private static String trace(StackTraceElement[] stack) {
        StringJoiner out = new StringJoiner(" < ");
        int n = 0;
        for (StackTraceElement frame : callers(stack)) {
            if (n++ == 8) break;
            out.add(name(frame) + ":" + frame.getLineNumber());
        }
        return out.toString();
    }

    /** The latest entries, oldest first. */
    static synchronized List<String> recent() {
        return new ArrayList<>(recent);
    }

    /** Forgets what was logged, so the self-test starts clean. */
    static synchronized void reset() {
        recent.clear();
        lastLogged.clear();
        walkedIn.clear();
    }
}
