package com.valier.tweaks;

import static com.valier.tweaks.SelfTest.check;
import static com.valier.tweaks.SelfTest.log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.block.PortalBlock;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.world.DimensionConfig;
import rwg2.world.ChunkGeneratorRealistic;

/**
 * {@code valiertweaks selftest pdimterrain} (2.3.7): a void personal dimension is made through Personal Space's portal
 * code, then:
 * <ul>
 * <li>{@code /pdim terrain} reports flat layers; real terrain is refused while it's generated, and for a biome RWG2 has
 * no terrain for (Plains);</li>
 * <li>after {@code /pdim reset}, Jungle Canyon in the settings and {@code /pdim terrain realistic}, it loads with RWG2's
 * biome picker and chunk generator; the trip lands in the air (y 250) and, at the end of the tick, on dry ground near
 * the portal's spot, with the return portal there and a player who arrived moved down;</li>
 * <li>the terrain: Jungle Canyon in every chunk around, heights far from flat, trees, not mostly water; the saved data
 * round-trips; flat is refused once it has generated.</li>
 * </ul>
 */
public final class PdimTerrainSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int cx = SelfTest.CX - 34, cz = SelfTest.CZ - 30;
    final int x = (cx << 4) + 8, z = (cz << 4) + 8;
    int y, dim, ticks, stage;
    PortalTileEntity portal;
    final List<String> said = new ArrayList<>();
    FakePlayer owner, arriving;

    void start() {
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) world.theChunkProviderServer.loadChunk(cx + i, cz + k);
        SelfTest.keepLoaded(world, cx - 1, cz - 1, cx + 1, cz + 1);
        y = world.getHeightValue(x, z);
        owner = fake("TerrainOwner", "00000000-0000-4000-8000-000000000d04", world);
        owner.setPosition(x + 2.5, y, z + 0.5);
        world.setBlock(x, y, z, PersonalSpaceMod.BLOCK_PORTAL, 0, 3);
        portal = world.getTileEntity(x, y, z) instanceof PortalTileEntity ? (PortalTileEntity) world.getTileEntity(x, y, z) : null;
        check(portal != null, "a Personal Space portal block is placed at " + x + "," + y + "," + z);
        if (portal == null) {
            SelfTest.summary();
            return;
        }
        DimensionConfig cfg = new DimensionConfig();
        cfg.setLayers("");
        cfg.setBiomeId("Plains");
        portal.updateSettings(owner, cfg);
        dim = portal.targetDimId;
        check(portal.active && dim > 0 && DimensionManager.getWorld(dim) != null, "Personal Space made void personal dimension " + dim);

        String r = run("terrain");
        check(r.contains("flat layers") && r.contains("Plains"), "/pdim terrain says it has flat layers (" + r + ")");
        r = run("terrain", "realistic");
        check(r.contains("generated already") && !PdimTerrain.isRealistic(dim), "real terrain is refused once it has generated (" + r + ")");

        run("reset");
        r = run("reset", "confirm");
        check(r.contains("Resetting personal dimension " + dim), "the owner resets it (" + r + ")");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        log("waiting for personal dimension " + dim + " to reset");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            if (stage == 0) {
                if (!PdimReset.lastResult.containsKey(dim)) {
                    if (ticks < 300) return;
                    check(false, "the reset finished within 15 seconds");
                    finish();
                    return;
                }
                afterReset();
                stage = 1;
            } else {
                afterLoad();
                finish();
            }
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] pdimterrain threw", t);
            check(false, "pdimterrain checks ran without an exception (" + t + ")");
            finish();
        }
    }

    private void afterReset() {
        check(PdimReset.lastResult.get(dim).startsWith("reset: "), "it was reset (" + PdimReset.lastResult.get(dim) + ")");
        String r = run("terrain", "realistic");
        check(r.contains("has no RWG2 terrain") && !PdimTerrain.isRealistic(dim), "real terrain is refused for Plains (" + r + ")");

        DimensionConfig next = new DimensionConfig();
        next.setLayers("minecraft:bedrock;minecraft:dirt*3;minecraft:grass");
        next.setBiomeId("Jungle Canyon");
        portal.updateSettings(owner, next);
        DimensionConfig cfg = DimensionConfig.getForDimension(dim, false);
        check(cfg != null && "Jungle Canyon".equals(cfg.getBiomeId()) && !cfg.getAllowGenerationChanges(),
            "Jungle Canyon took in the portal's settings (" + (cfg == null ? "?" : cfg.getBiomeId()) + ")");
        r = run("terrain", "realistic");
        check(r.contains("real Jungle Canyon terrain") && PdimTerrain.isRealistic(dim), "/pdim terrain realistic takes (" + r + ")");
        r = run("terrain");
        check(r.contains("real (RWG2) terrain"), "/pdim terrain now says real terrain (" + r + ")");

        DimensionManager.initDimension(dim); // the trip in
        WorldServer d = DimensionManager.getWorld(dim);
        check(d != null && PdimTerrain.isOneBiome(d.getWorldChunkManager()), "it loads with RWG2's biome picker, one biome");
        check(d != null && d.theChunkProviderServer.currentChunkProvider instanceof ChunkGeneratorRealistic,
            "and RWG2's chunk generator (" + (d == null ? "?" : d.theChunkProviderServer.currentChunkProvider.getClass().getSimpleName()) + ")");
        check(portal.targetPosY == PdimReset.AIR_ARRIVAL && PdimReset.groundPending.contains(dim) && PdimReset.relink.contains(portal),
            "until the ground is known, the portal lands in the air (y " + portal.targetPosY + ")");
        if (d != null) { // a player who just came through, up in the air
            arriving = fake("TerrainArrival", "00000000-0000-4000-8000-000000000d05", d);
            arriving.setPosition(portal.targetPosX + 0.5, PdimReset.AIR_ARRIVAL, portal.targetPosZ + 0.5);
            arriving.fallDistance = 5F;
            d.playerEntities.add(arriving);
        }
    }

    private void afterLoad() {
        WorldServer d = DimensionManager.getWorld(dim);
        if (d != null) d.playerEntities.remove(arriving);
        int tx = portal.targetPosX, ty = portal.targetPosY, tz = portal.targetPosZ;
        check(d != null && ty > 2 && ty < PdimReset.AIR_ARRIVAL, "the arrival moved down to the ground (" + tx + "," + ty + "," + tz + ")");
        if (d == null) return;
        check(d.isAirBlock(tx, ty, tz) || d.getBlock(tx, ty, tz) instanceof PortalBlock, "nothing solid at the arrival spot");
        check(d.getBlock(tx, ty - 1, tz).getMaterial().isSolid() && !d.getBlock(tx, ty - 1, tz).getMaterial().isLiquid(),
            "solid, dry ground under it (" + d.getBlock(tx, ty - 1, tz).getUnlocalizedName() + ")");
        check(d.getBlock(tx, ty, tz) instanceof PortalBlock, "the return portal was built there");
        check(arriving != null && Math.abs(arriving.posY - portal.getTargetTeleportY()) < 0.01 && arriving.fallDistance == 0,
            "a player who arrived in the air was moved down to it (y " + (arriving == null ? -1 : arriving.posY) + ")");
        PdimReset.Data data = PdimReset.Data.get();
        int[] xz = data.arrivalXZ.get(dim);
        check(xz != null && xz[0] == tx && xz[1] == tz && Integer.valueOf(ty).equals(data.arrival.get(dim)), "the arrival spot is saved");

        // The terrain around the arrival: 64 x 64 blocks, every 4th column (their chunks loaded, with a ring around so
        // they're decorated).
        for (int i = (tx >> 4) - 3; i <= (tx >> 4) + 3; i++) for (int k = (tz >> 4) - 3; k <= (tz >> 4) + 3; k++) d.theChunkProviderServer.loadChunk(i, k);
        BiomeGenBase canyon = PdimTerrain.biomeOf(DimensionConfig.getForDimension(dim, false));
        int min = 256, max = 0, columns = 0, water = 0, canyonCols = 0, trees = 0;
        java.util.Set<String> others = new java.util.TreeSet<>();
        for (int bx = tx - 32; bx < tx + 32; bx += 4) for (int bz = tz - 32; bz < tz + 32; bz += 4) {
            int top = d.getTopSolidOrLiquidBlock(bx, bz);
            if (top <= 0) continue;
            columns++;
            min = Math.min(min, top);
            max = Math.max(max, top);
            if (d.getBlock(bx, top, bz).getMaterial().isLiquid()) water++;
            BiomeGenBase b = d.getChunkFromBlockCoords(bx, bz).getBiomeGenForWorldCoords(bx & 15, bz & 15, d.getWorldChunkManager());
            if (b == canyon) canyonCols++;
            else others.add(b == null ? "null" : b.biomeName);
            int over = d.getHeightValue(bx, bz);
            for (int k = top; k < over; k++) if (d.getBlock(bx, k, bz).getMaterial() == Material.wood || d.getBlock(bx, k, bz).getMaterial() == Material.leaves) {
                trees++;
                break;
            }
        }
        check(columns > 200 && canyonCols * 10 >= columns * 9, "the land around is Jungle Canyon (" + canyonCols + " of " + columns + " columns; others: " + others + ")");
        check(max - min >= 10, "the ground is far from flat: heights " + min + " to " + max);
        check(trees > 0, "there are trees (" + trees + " of " + columns + " columns)");
        check(water * 2 < columns, "it isn't mostly water (" + water + " of " + columns + " columns)");
        ValierTweaks.LOG.info("[ValierTweaks SelfTest] pdimterrain: heights {}..{}, {} tree columns, {} water columns of {}", min, max, trees, water, columns);

        // Information for the owner (logged, not checked): ores in the 5 x 5 chunks around, and RWG2's rivers and river
        // junctions in this dimension's noise.
        java.util.Map<String, Integer> ores = new java.util.TreeMap<>();
        for (int i = (tx >> 4) - 2; i <= (tx >> 4) + 2; i++) for (int k = (tz >> 4) - 2; k <= (tz >> 4) + 2; k++) {
            for (net.minecraft.world.chunk.storage.ExtendedBlockStorage st : d.getChunkFromChunkCoords(i, k).getBlockStorageArray()) {
                if (st == null) continue;
                for (int bx = 0; bx < 16; bx++) for (int by = 0; by < 16; by++) for (int bz = 0; bz < 16; bz++) {
                    String n = st.getBlockByExtId(bx, by, bz).getUnlocalizedName();
                    if (n.toLowerCase(java.util.Locale.ROOT).contains("ore")) ores.merge(n, 1, Integer::sum);
                }
            }
        }
        ValierTweaks.LOG.info("[ValierTweaks SelfTest] pdimterrain: ore blocks in the 25 chunks around the arrival: {}", ores);
        rwg2.world.ChunkManagerRealistic m = (rwg2.world.ChunkManagerRealistic) d.getWorldChunkManager();
        float rMin = 0, jMax = 0;
        int n = 0, r25 = 0, r75 = 0, j50 = 0;
        for (int bx = -2048; bx <= 2048; bx += 16) for (int bz = -2048; bz <= 2048; bz += 16) {
            float rs = m.getRiverStrength(bx, bz), js = m.getRiverJunctionStrength(bx, bz);
            n++;
            rMin = Math.min(rMin, rs); // RWG2's river strength: 0 away from rivers, below 0 where one carves
            jMax = Math.max(jMax, js);
            if (rs < -0.25F) r25++;
            if (rs < -0.75F) r75++;
            if (js > 0.5F) j50++;
        }
        ValierTweaks.LOG.info("[ValierTweaks SelfTest] pdimterrain: river noise over +-2048 blocks ({} points): strength min {}, <-0.25 at {}, <-0.75 at {}; junction max {}, >0.5 at {}",
            n, rMin, r25, r75, jMax, j50);

        String r = run("terrain", "flat");
        check(r.contains("generated already") && PdimTerrain.isRealistic(dim), "flat is refused now that it has generated (" + r + ")");
        NBTTagCompound tag = new NBTTagCompound();
        PdimTerrain.Data.get().writeToNBT(tag);
        PdimTerrain.Data copy = new PdimTerrain.Data("copy");
        copy.readFromNBT(tag);
        NBTTagCompound tag2 = new NBTTagCompound();
        data.writeToNBT(tag2);
        PdimReset.Data copy2 = new PdimReset.Data("copy");
        copy2.readFromNBT(tag2);
        check(copy.realistic.contains(dim) && copy2.arrivalXZ.get(dim) != null && copy2.arrivalXZ.get(dim)[0] == tx,
            "the terrain and arrival data save and load");
    }

    private void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        world.setBlockToAir(x, y, z);
        PdimReset.pending.clear();
        SelfTest.summary();
    }

    private String run(String... args) {
        said.clear();
        try {
            new PdimCommand().processCommand(owner, args);
        } catch (RuntimeException e) {
            said.add("threw " + e);
        }
        return String.join(" | ", said);
    }

    private FakePlayer fake(String name, String uuid, WorldServer in) {
        return new FakePlayer(in, new GameProfile(UUID.fromString(uuid), name)) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said.add(message.getUnformattedText());
            }
        };
    }
}
