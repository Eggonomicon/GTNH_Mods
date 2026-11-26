package com.valier.tweaks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.Loader;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.world.DimensionConfig;
import rwg2.biomes.realistic.RealisticBiomeBase;
import rwg2.world.ChunkGeneratorRealistic;
import rwg2.world.ChunkManagerRealistic;

/**
 * Real terrain for a personal dimension (2.3.7, config {@code pdim/pdimRealisticTerrain}): {@code /pdim terrain realistic}
 * makes a Personal Space dimension generate with Realistic World Gen 2's own terrain for its biome, everywhere and
 * without end, instead of Personal Space's flat layers. Jungle Canyon is the one RWG2 biome in Valier's Personal Space
 * list: its canyons, cliffs, rivers and jungle, like the overworld's, but nothing else (no oceans, other biomes,
 * volcanoes or lava caves).
 * <p>
 * <b>How:</b> a server-side hook in Personal Space's world provider ({@code MixinPersonalWorldProvider}) gives such a
 * dimension RWG2's chunk generator and a copy of RWG2's biome picker ({@link Rwg.OneBiome}) that answers with that one
 * biome and "far inland" everywhere. Its seed is the world's mixed with the dimension number, so it isn't a copy of the
 * overworld. Players' game clients are unchanged: they only get the chunks. Portals, {@code /pdim reset} and
 * {@code /pdim peaceful} work as for any personal dimension.
 * <p>
 * <b>When:</b> only while the dimension has nothing generated yet (right after {@code /pdim reset}), so old and new
 * terrain never meet. Who: the same rule as reset (could break the portal block), ops for any dimension. Saved in
 * {@code data/valiertweaks_pdimterrain.dat}.
 */
public final class PdimTerrain {

    /** Whether real terrain can work here (setting on, Personal Space and RWG2 loaded). */
    static boolean available() {
        return TweaksConfig.pdimRealisticTerrain && Loader.isModLoaded("personalspace") && Loader.isModLoaded("rwg2");
    }

    /** Whether the dimension generates with real terrain; false on a client and for every other world. */
    public static boolean isRealistic(int dim) {
        if (!available() || DimensionManager.getWorld(0) == null) return false;
        Data data = Data.peek();
        return data != null && data.realistic.contains(dim);
    }

    // ---- the command ------------------------------------------------------------------------------------------------

    /** {@code /pdim terrain [realistic|flat] [dimension]}. */
    static void command(ICommandSender sender, String mode, Integer named, boolean op) {
        if (!available()) {
            PdimReset.say(sender, "Real terrain for personal dimensions is turned off on this server (or RWG2 isn't installed).");
            return;
        }
        int dim;
        if (named != null) {
            if (!op) {
                PdimReset.say(sender, "Only ops can name a dimension. Stand next to your personal dimension's portal and leave the number off.");
                return;
            }
            dim = named;
            if (!PdimReset.isPersonalDim(dim)) {
                PdimReset.say(sender, "Dimension " + dim + " isn't a personal dimension (made with a Personal Space portal).");
                return;
            }
        } else if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PortalTileEntity portal = PdimReset.nearestPortal(player);
            if (portal == null) {
                PdimReset.say(sender, "Stand next to the portal of the personal dimension (within " + PdimReset.RADIUS
                    + " blocks), outside that dimension.");
                return;
            }
            if (mode != null && !op && !PdimReset.mayChange(player, portal)) {
                PdimReset.say(sender, "You can't change this personal dimension: its portal is claimed by someone else.");
                return;
            }
            dim = portal.targetDimId;
        } else {
            PdimReset.say(sender, "From the console, name the dimension: /pdim terrain <realistic|flat> <dimension>");
            return;
        }
        DimensionConfig cfg = DimensionConfig.getForDimension(dim, false);
        String biome = cfg == null ? "?" : cfg.getBiomeId();
        Data data = Data.get();
        boolean now = data.realistic.contains(dim);
        if (mode == null) {
            PdimReset.say(sender, "Personal dimension " + dim + " has " + (now ? "real (RWG2) terrain" : "Personal Space's flat layers")
                + ", biome " + biome + ". Change it right after /pdim reset with /pdim terrain realistic or /pdim terrain flat.");
            return;
        }
        boolean want = "realistic".equals(mode);
        if (want == now) {
            PdimReset.say(sender, "Personal dimension " + dim + " already has " + (now ? "real terrain." : "flat layers."));
            return;
        }
        if (DimensionManager.getWorld(dim) != null || hasChunks(dim)) {
            PdimReset.say(sender, "Personal dimension " + dim + " has been generated already. Reset it first (/pdim reset), then"
                + " change its terrain before anyone goes in.");
            return;
        }
        if (want && Rwg.realisticFor(biomeOf(cfg)) == null) {
            PdimReset.say(sender, "Its biome, " + biome + ", has no RWG2 terrain. Pick Jungle Canyon in the portal's settings first"
                + " (sneak + right-click, then Done), then run this again.");
            return;
        }
        if (want) data.realistic.add(dim);
        else data.realistic.remove(dim);
        data.markDirty();
        ValierTweaks.LOG.info("[pdim terrain] {} set personal dimension {} to {} terrain ({})", sender.getCommandSenderName(), dim,
            want ? "real" : "flat", biome);
        PdimReset.say(sender, want
            ? "Personal dimension " + dim + " will generate with real " + biome + " terrain everywhere, without end, like the"
                + " overworld's. Its layers are only used for the sky's horizon now. Go in when ready: you land on the ground"
                + " near the portal's spot."
            : "Personal dimension " + dim + " will generate from its flat layers again.");
    }

    /** Whether the dimension's folder holds any chunks. */
    static boolean hasChunks(int dim) {
        File region = new File(new File(DimensionManager.getCurrentSaveRootDirectory(), PdimReset.saveFolder(dim)), "region");
        String[] files = region.list();
        return files != null && files.length > 0;
    }

    /** The biome Personal Space has for the dimension, or null. */
    static BiomeGenBase biomeOf(DimensionConfig cfg) {
        if (cfg == null) return null;
        int id = cfg.getRawBiomeId();
        BiomeGenBase[] all = BiomeGenBase.getBiomeGenArray();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    // ---- the provider hook's calls ----------------------------------------------------------------------------------

    /** The biome picker for a real-terrain dimension, or null to keep Personal Space's (no RWG2 version of its biome). */
    public static WorldChunkManager manager(World world, DimensionConfig cfg) {
        RealisticBiomeBase biome = Rwg.realisticFor(biomeOf(cfg));
        if (biome == null) {
            ValierTweaks.LOG.warn("[pdim terrain] personal dimension {}'s biome {} has no RWG2 terrain; it stays flat",
                world.provider.dimensionId, cfg == null ? "?" : cfg.getBiomeId());
            return null;
        }
        return new Rwg.OneBiome(seed(world), biome);
    }

    /** Whether this biome picker is a real-terrain one ({@link #manager}). */
    public static boolean isOneBiome(WorldChunkManager manager) {
        return manager instanceof Rwg.OneBiome;
    }

    /** RWG2's chunk generator for a dimension whose biome picker {@link #manager} made. */
    public static IChunkProvider generator(World world) {
        return new ChunkGeneratorRealistic(world, seed(world), true);
    }

    static long seed(World world) {
        return world.getSeed() ^ (world.provider.dimensionId * 0x9E3779B97F4A7C15L);
    }

    /** RWG2's classes, only touched when RWG2 is loaded. */
    static final class Rwg {

        /** RWG2's realistic biome whose base biome this is, or null. */
        static RealisticBiomeBase realisticFor(BiomeGenBase base) {
            if (base == null) return null;
            for (int i = 0; i < 1024; i++) {
                RealisticBiomeBase r;
                try {
                    r = RealisticBiomeBase.getBiome(i);
                } catch (RuntimeException e) {
                    break; // past the end of RWG2's list
                }
                if (r != null && r.baseBiome == base) return r;
            }
            return null;
        }

        /**
         * RWG2's biome picker with one biome everywhere and continents everywhere: every lookup the generator makes
         * (biome, ocean, volcano, lava cave) answers "this biome, far inland, none here". Rivers are RWG2's own.
         */
        static final class OneBiome extends ChunkManagerRealistic {

            static final float INLAND = 300F; // well past the coast (RWG2's terrain ocean value tops out at 2 from 100)
            final RealisticBiomeBase biome;
            final BiomeGenBase base;
            final List<BiomeGenBase> spawn;

            OneBiome(long seed, RealisticBiomeBase biome) {
                super(seed, true);
                this.biome = biome;
                this.base = biome.baseBiome;
                this.spawn = new ArrayList<>(Arrays.asList(base));
            }

            @Override
            public RealisticBiomeBase getBiomeDataAt(int x, int z) {
                return biome;
            }

            @Override
            public RealisticBiomeBase getBiomeDataAt(int x, int z, float ocean) {
                return biome;
            }

            @Override
            public RealisticBiomeBase getCoreBiomeAt(int x, int z) {
                return biome;
            }

            @Override
            public RealisticBiomeBase getVolcanoUnderlyingBiome(int x, int z) {
                return biome;
            }

            @Override
            public RealisticBiomeBase[] getBiomesGensData(int x, int z, int w, int h) {
                RealisticBiomeBase[] out = new RealisticBiomeBase[w * h];
                Arrays.fill(out, biome);
                return out;
            }

            @Override
            public float getContinentValue(int x, int z) {
                return INLAND;
            }

            @Override
            public boolean isOceanBiomeAt(int x, int z) {
                return false;
            }

            @Override
            public long getVolcanoCoordinates(int x, int z) {
                return Long.MIN_VALUE;
            }

            @Override
            public long getVolcanoVicinityCoordinates(int x, int z) {
                return Long.MIN_VALUE;
            }

            @Override
            public long getLavaCaveCoordinates(int x, int z) {
                return Long.MIN_VALUE;
            }

            @Override
            public long getLavaCaveCenterCoordinates(int x, int z) {
                return Long.MIN_VALUE;
            }

            @Override
            public BiomeGenBase getBiomeGenAt(int x, int z) {
                return base;
            }

            @Override
            public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] arr, int x, int z, int w, int h) {
                return fill(arr, w * h);
            }

            @Override
            public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] arr, int x, int z, int w, int h) {
                return fill(arr, w * h);
            }

            @Override
            public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] arr, int x, int z, int w, int h, boolean cache) {
                return fill(arr, w * h);
            }

            private BiomeGenBase[] fill(BiomeGenBase[] arr, int n) {
                if (arr == null || arr.length < n) arr = new BiomeGenBase[n];
                Arrays.fill(arr, 0, n, base);
                return arr;
            }

            @Override
            public List getBiomesToSpawnIn() {
                return spawn;
            }

            @Override
            public boolean areBiomesViable(int x, int z, int radius, List allowed) {
                return allowed.contains(base);
            }

            @Override
            public ChunkPosition findBiomePosition(int x, int z, int range, List allowed, java.util.Random random) {
                return allowed.contains(base) ? new ChunkPosition(x - range + random.nextInt(range * 2 + 1), 0,
                    z - range + random.nextInt(range * 2 + 1)) : null;
            }
        }
    }

    /** The personal dimensions with real terrain, kept in the overworld's shared map storage. */
    public static final class Data extends WorldSavedData {

        static final String NAME = "valiertweaks_pdimterrain";
        final Set<Integer> realistic = new TreeSet<>();
        private static Data cached;
        private static Object cachedFor;

        public Data(String name) {
            super(name);
        }

        static Data get() {
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld == null) return cached != null ? cached : new Data(NAME);
            if (cached != null && cachedFor == overworld.mapStorage) return cached;
            Data data = (Data) overworld.mapStorage.loadData(Data.class, NAME);
            if (data == null) {
                data = new Data(NAME);
                overworld.mapStorage.setData(NAME, data);
            }
            cached = data;
            cachedFor = overworld.mapStorage;
            return data;
        }

        static Data peek() {
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld == null) return null;
            return cached != null && cachedFor == overworld.mapStorage ? cached : get();
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            realistic.clear();
            for (int d : tag.getIntArray("Realistic")) realistic.add(d);
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            int[] a = new int[realistic.size()];
            int i = 0;
            for (int d : realistic) a[i++] = d;
            tag.setIntArray("Realistic", a);
        }
    }
}
