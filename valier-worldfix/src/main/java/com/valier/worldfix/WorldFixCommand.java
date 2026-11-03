package com.valier.worldfix;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

/**
 * {@code /worldfix pregen <radius> [blocks] [budgetMs]}: pregenerate and populate the overworld out to a radius
 * (chunks, or blocks with "blocks") around world spawn, only while nobody is online. Starting again with the same or a
 * larger radius carries on from where it got to. {@code /worldfix status} shows progress, {@code /worldfix stop}
 * stops it.
 */
public class WorldFixCommand extends CommandBase {

    private static final String USAGE = "/worldfix <pregen <radius> [blocks] [budgetMs] [minfree=GB]|status|stop>";
    static final boolean SELFTEST = Boolean.getBoolean("valierworldfix.selftest");

    @Override
    public String getCommandName() {
        return "worldfix";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(USAGE);
        switch (args[0]) {
            case "pregen": {
                if (args.length < 2) throw new WrongUsageException(USAGE);
                int radius = parseIntBounded(sender, args[1], 1, 30000 * 16);
                int budget = 40, minFree = 150;
                for (int i = 2; i < args.length; i++) {
                    if ("blocks".equals(args[i])) radius = (radius + 15) / 16;
                    else if (args[i].startsWith("minfree=")) minFree = parseIntBounded(sender, args[i].substring(8), 0, 100000);
                    else budget = parseIntBounded(sender, args[i], 1, 45);
                }
                ChunkCoordinates spawn = DimensionManager.getWorld(0)
                    .getSpawnPoint();
                int cx = spawn.posX >> 4, cz = spawn.posZ >> 4;
                PregenTask saved = PregenTask.loadSaved();
                boolean carryOn = saved != null && saved.centerX == cx && saved.centerZ == cz && radius >= saved.radius;
                ValierWorldFix.task = carryOn
                    ? new PregenTask(
                        cx,
                        cz,
                        radius,
                        budget,
                        minFree,
                        saved.ring,
                        saved.index,
                        saved.checked,
                        saved.generated,
                        saved.populated,
                        saved.alreadyFine)
                    : new PregenTask(cx, cz, radius, budget, minFree, 0, 0, 0, 0, 0, 0);
                ValierWorldFix.task.save(true);
                reply(
                    sender,
                    "Pregen to " + radius
                        + " chunks ("
                        + radius * 16
                        + " blocks) around spawn, "
                        + budget
                        + " ms per tick, only while nobody is online and at least "
                        + minFree
                        + " GB stay free on disk"
                        + (carryOn ? ", carrying on from ring " + saved.ring : "")
                        + ". /worldfix status shows progress.");
                return;
            }
            case "status":
                reply(
                    sender,
                    ValierWorldFix.task == null ? "No pregen running."
                        : (ValierWorldFix.task.paused ? "Paused (players online). " : "Running when the server is empty. ")
                            + ValierWorldFix.task.progress());
                return;
            case "stop":
                if (ValierWorldFix.task != null) {
                    ValierWorldFix.task.save(false);
                    reply(sender, "Stopped (pregen again with the same radius to carry on). " + ValierWorldFix.task.progress());
                    ValierWorldFix.task = null;
                } else reply(sender, "No pregen running.");
                return;
            case "selftest":
                if (SELFTEST && args.length >= 2) {
                    selfTest(args[1], args.length > 2 ? Integer.parseInt(args[2]) : 6);
                    return;
                }
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    // ---- Test-server-only check (-Dvalierworldfix.selftest=true) ----

    /**
     * "prepare": on the flat test world, generates some chunks near spawn and marks a few of them as never populated,
     * leaving the rest of a radius-6 circle missing. "check": after a radius-6 pregen ran, every chunk in the circle
     * exists and is populated, and the task let them go from memory.
     */
    private static void selfTest(String step, int r) {
        WorldServer world = DimensionManager.getWorld(0);
        ChunkProviderServer provider = world.theChunkProviderServer;
        ChunkCoordinates spawn = world.getSpawnPoint();
        int cx = spawn.posX >> 4, cz = spawn.posZ >> 4;
        if ("prepare".equals(step)) {
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
                Chunk chunk = provider.loadChunk(x, z);
                chunk.isTerrainPopulated = false;
                chunk.setChunkModified();
            }
            log("prepared: 25 never-populated chunks around spawn chunk " + cx + "," + cz);
            return;
        }
        int inCircle = 0, ok = 0, loaded = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            inCircle++;
            if (provider.chunkExists(cx + dx, cz + dz)) loaded++;
            if (provider.loadChunk(cx + dx, cz + dz).isTerrainPopulated) ok++;
        }
        log((ok == inCircle ? "PASS" : "FAIL") + " every chunk within radius " + r + " exists and is populated (" + ok + " of " + inCircle + ")");
        log("info: " + loaded + " of " + inCircle + " were still loaded (spawn chunks stay loaded on purpose)");
        log("SUMMARY " + (ok == inCircle ? "PASSED" : "FAILED"));
    }

    private static void log(String text) {
        ValierWorldFix.LOG.info("[WorldFix SelfTest] " + text);
    }

    private static void reply(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
