package com.valier.worldfix;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

/**
 * Pregenerates and populates the overworld out to a maximum radius, only while nobody is online.
 * <p>
 * It walks square rings outward from a centre chunk, keeping to the circle of the given radius. For every chunk it
 * generates it if it is missing, and finishes populating it (trees, ores, structures) if a pregen left it unpopulated:
 * the server would otherwise do that the moment a player comes near, all at once on the main thread. Population
 * writes into the +x/+z neighbours, so those are generated too (one chunk past the radius at the edge).
 * <ul>
 * <li>Works only while the server is empty, within a time budget per tick; pauses the moment someone logs in.</li>
 * <li>Chunks it loaded are let go again afterwards (unless a player is watching them), so memory stays flat.</li>
 * <li>Pauses while the server's drive has less than a minimum of free space (150 GB by default).</li>
 * <li>Its place is saved in valierworldfix-task.properties, so it carries on by itself after a restart.</li>
 * </ul>
 */
final class PregenTask {

    static final File STATE = new File("valierworldfix-task.properties");
    private static final long LOG_EVERY_NS = 60_000_000_000L;

    final int centerX, centerZ, radius, budgetMs;
    /** Pauses while the server's drive has less than this many GB free. */
    final int minFreeGB;
    private boolean lowDisk;
    private long lastDiskCheckNs;
    /** Current ring (square, chunks from the centre) and position along it. */
    int ring, index;
    long checked, generated, populated, alreadyFine;
    boolean paused;
    private final long startedNs = System.nanoTime();
    private long lastLogNs = System.nanoTime();
    private long workNs;
    private final long checkedAtStart;

    PregenTask(int centerX, int centerZ, int radius, int budgetMs, int minFreeGB, int ring, int index, long checked,
        long generated, long populated, long alreadyFine) {
        this.minFreeGB = minFreeGB;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.budgetMs = budgetMs;
        this.ring = ring;
        this.index = index;
        this.checked = checked;
        this.generated = generated;
        this.populated = populated;
        this.alreadyFine = alreadyFine;
        this.checkedAtStart = checked;
    }

    boolean finished() {
        return ring > radius;
    }

    /** About how many chunks lie inside the radius. */
    long totalChunks() {
        return Math.round(Math.PI * radius * radius);
    }

    /** One server tick of work. */
    void tick(MinecraftServer server) {
        if (finished()) return;
        if (!server.getConfigurationManager().playerEntityList.isEmpty()) {
            if (!paused) {
                paused = true;
                ValierWorldFix.LOG.info("Pregen paused while players are online. " + progress());
                save(true);
            }
            return;
        }
        if (System.nanoTime() - lastDiskCheckNs > 30_000_000_000L) {
            lastDiskCheckNs = System.nanoTime();
            boolean low = freeGB() < minFreeGB;
            if (low != lowDisk) ValierWorldFix.LOG.warn(
                low ? "Pregen paused: only " + freeGB() + " GB free on the server's drive (minimum " + minFreeGB + " GB). "
                    + progress() : "Pregen resumed: " + freeGB() + " GB free.");
            lowDisk = low;
        }
        if (lowDisk) return;
        if (paused) {
            paused = false;
            ValierWorldFix.LOG.info("Pregen resumed. " + progress());
        }
        WorldServer world = DimensionManager.getWorld(0);
        if (world == null) return;
        ChunkProviderServer provider = world.theChunkProviderServer;
        long start = System.nanoTime(), budget = budgetMs * 1_000_000L;
        while (!finished() && System.nanoTime() - start < budget) {
            int[] d = nextOffset();
            if (d == null) continue; // outside the circle, or moved to the next ring
            process(world, provider, centerX + d[0], centerZ + d[1]);
        }
        workNs += System.nanoTime() - start;
        if (finished()) {
            ValierWorldFix.LOG.info("Pregen finished. " + progress());
            save(false);
        } else if (System.nanoTime() - lastLogNs > LOG_EVERY_NS) {
            lastLogNs = System.nanoTime();
            ValierWorldFix.LOG.info("Pregen: " + progress());
            save(true);
        }
    }

    /**
     * The next position on the current ring as an offset from the centre, advancing ring and index; null if it lies
     * outside the circle or the ring just ended. Ring r has 8r positions (1 for r = 0): its top and bottom rows, then
     * its left and right columns between them, so consecutive positions are neighbours.
     */
    int[] nextOffset() {
        int r = ring, i = index;
        int size = r == 0 ? 1 : 8 * r;
        if (i >= size) {
            ring++;
            index = 0;
            return null;
        }
        index++;
        int dx, dz;
        if (r == 0) {
            dx = 0;
            dz = 0;
        } else if (i < 2 * r + 1) {
            dx = -r + i;
            dz = -r;
        } else if (i < 4 * r + 2) {
            dx = -r + (i - (2 * r + 1));
            dz = r;
        } else if (i < 6 * r + 1) {
            dx = -r;
            dz = -r + 1 + (i - (4 * r + 2));
        } else {
            dx = r;
            dz = -r + 1 + (i - (6 * r + 1));
        }
        if ((long) dx * dx + (long) dz * dz > (long) radius * radius) return null;
        return new int[] { dx, dz };
    }

    private void process(WorldServer world, ChunkProviderServer provider, int cx, int cz) {
        checked++;
        boolean existed = onDisk(world, provider, cx, cz);
        Chunk chunk = provider.loadChunk(cx, cz); // generates it if it is missing
        if (!existed) generated++;
        boolean wasPopulated = chunk.isTerrainPopulated;
        if (!wasPopulated) {
            provider.loadChunk(cx + 1, cz);
            provider.loadChunk(cx, cz + 1);
            provider.loadChunk(cx + 1, cz + 1); // with these loaded, the game usually populates it by itself
            if (!chunk.isTerrainPopulated) provider.populate(provider, cx, cz);
            if (chunk.isTerrainPopulated) populated++;
            letGo(world, provider, cx + 1, cz);
            letGo(world, provider, cx, cz + 1);
            letGo(world, provider, cx + 1, cz + 1);
        } else if (existed) alreadyFine++;
        letGo(world, provider, cx, cz);
    }

    private static boolean onDisk(WorldServer world, ChunkProviderServer provider, int cx, int cz) {
        return provider.chunkExists(cx, cz) || provider.currentChunkLoader instanceof AnvilChunkLoader
            && ((AnvilChunkLoader) provider.currentChunkLoader).chunkExists(world, cx, cz);
    }

    /** Queues a chunk for the normal unload (and save), unless a player is watching it. */
    private static void letGo(WorldServer world, ChunkProviderServer provider, int cx, int cz) {
        if (!world.getPlayerManager()
            .func_152621_a(cx, cz)) provider.unloadChunksIfNotNearSpawn(cx, cz);
    }

    static long freeGB() {
        return new File(".").getUsableSpace() / (1L << 30);
    }

    String progress() {
        double workMin = workNs / 6e10;
        long thisRun = checked - checkedAtStart;
        double perMin = workMin > 0 ? thisRun / workMin : 0;
        long left = Math.max(0, totalChunks() - checked);
        String eta = perMin > 0 ? String.format(" About %.1f hours of empty-server time left.", left / perMin / 60) : "";
        return String.format(
            "ring %d of %d (%.1f%% of ~%d chunks): %d checked, %d generated, %d populated, %d already fine; "
                + "%.0f chunks per minute of work.%s %d GB free on disk%s.",
            Math.min(ring, radius),
            radius,
            100.0 * Math.min(checked, totalChunks()) / Math.max(1, totalChunks()),
            totalChunks(),
            checked,
            generated,
            populated,
            alreadyFine,
            perMin,
            eta,
            freeGB(),
            lowDisk ? " (paused: below the " + minFreeGB + " GB minimum)" : "");
    }

    /** Remembers where it is, so a restart carries on (active=false once finished or stopped). */
    void save(boolean active) {
        Properties p = new Properties();
        p.setProperty("centerX", Integer.toString(centerX));
        p.setProperty("centerZ", Integer.toString(centerZ));
        p.setProperty("radius", Integer.toString(radius));
        p.setProperty("budgetMs", Integer.toString(budgetMs));
        p.setProperty("minFreeGB", Integer.toString(minFreeGB));
        p.setProperty("ring", Integer.toString(ring));
        p.setProperty("index", Integer.toString(index));
        p.setProperty("checked", Long.toString(checked));
        p.setProperty("generated", Long.toString(generated));
        p.setProperty("populated", Long.toString(populated));
        p.setProperty("alreadyFine", Long.toString(alreadyFine));
        p.setProperty("active", Boolean.toString(active && !finished()));
        try (OutputStream out = Files.newOutputStream(STATE.toPath())) {
            p.store(out, "Valier World Fix pregen task");
        } catch (IOException e) {
            ValierWorldFix.LOG.warn("Could not save pregen progress: " + e);
        }
    }

    /** The saved task, if any (active or not). */
    static PregenTask loadSaved() {
        if (!STATE.isFile()) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(STATE.toPath())) {
            p.load(in);
            if (p.getProperty("radius") == null) return null; // an older format
            return new PregenTask(
                Integer.parseInt(p.getProperty("centerX")),
                Integer.parseInt(p.getProperty("centerZ")),
                Integer.parseInt(p.getProperty("radius")),
                Integer.parseInt(p.getProperty("budgetMs", "40")),
                Integer.parseInt(p.getProperty("minFreeGB", "150")),
                Integer.parseInt(p.getProperty("ring", "0")),
                Integer.parseInt(p.getProperty("index", "0")),
                Long.parseLong(p.getProperty("checked", "0")),
                Long.parseLong(p.getProperty("generated", "0")),
                Long.parseLong(p.getProperty("populated", "0")),
                Long.parseLong(p.getProperty("alreadyFine", "0")));
        } catch (IOException | RuntimeException e) {
            ValierWorldFix.LOG.warn("Could not read the saved pregen task: " + e);
            return null;
        }
    }

    static boolean savedActive() {
        if (!STATE.isFile()) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(STATE.toPath())) {
            p.load(in);
            return Boolean.parseBoolean(p.getProperty("active"));
        } catch (IOException e) {
            return false;
        }
    }
}
