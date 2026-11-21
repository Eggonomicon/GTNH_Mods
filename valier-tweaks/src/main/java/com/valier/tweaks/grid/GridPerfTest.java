package com.valier.tweaks.grid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest gridperf}: what a busy Grid costs the server. One HV network of 200 blocks: 50 plugs fed
 * 4 amps every tick (as cables would), 100 points each feeding a machine that takes all it can (a test RF machine), 50
 * cells. After 20 ticks to settle, 200 are measured: the Grid's own time per tick (the network loop, not the feeding),
 * against a limit of 1 ms.
 */
public final class GridPerfTest { // public: Forge calls its tick handler from another class loader

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 14) << 4, bz = (SelfTest.CZ + 18) << 4;
    final List<TileGridPlug> plugs = new ArrayList<>();
    final List<TileGridPoint> points = new ArrayList<>();
    final List<TileEntity> placed = new ArrayList<>();
    final List<GridSelfTest.RfSink> sinks = new ArrayList<>();
    int y, ticks;

    public void start() {
        for (int cx = -1; cx < 3; cx++) for (int cz = -1; cz < 3; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        for (int i = 0; i < 200; i++) {
            int x = bx + (i % 20) * 2, z = bz + (i / 20) * 2;
            BlockGrid b = i < 50 ? ModBlocks.gridPlug : i < 150 ? ModBlocks.gridPoint : ModBlocks.gridCell;
            world.setBlock(x, y, z, b, 2, 2);
            TileGrid t = (TileGrid) world.getTileEntity(x, y, z);
            t.setOwner(GridSelfTest.OWNER);
            GridNets.link(t, GridSelfTest.SCOPE, "selftest-perf");
            placed.add(t);
            if (t instanceof TileGridPlug) plugs.add((TileGridPlug) t);
            if (t instanceof TileGridPoint) points.add((TileGridPoint) t);
        }
        SelfTest.log("gridperf: 200 HV Grid blocks built; settling 20 ticks, then measuring 200");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ticks++;
        com.valier.tweaks.SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 2, (bz >> 4) + 2);
        if (ticks == 1) for (TileGridPoint p : points) { // each point gets its machine (after joining looked at its neighbours)
            GridSelfTest.RfSink s = new GridSelfTest.RfSink();
            p.refreshNear();
            p.near[5] = s;
            p.nearKind[5] = 3;
            sinks.add(s);
        }
        for (TileGridPlug p : plugs) p.injectEnergyUnits(ForgeDirection.UP, 512, 4);
        if (ticks == 20) {
            GridNets.timed = 0;
            GridNets.timedTicks = 0;
        }
        if (ticks < 221) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            double perTick = GridNets.timed / (double) Math.max(1, GridNets.timedTicks) / 1e6;
            long rf = 0;
            for (GridSelfTest.RfSink s : sinks) rf += s.got;
            SelfTest.log(String.format("gridperf: %.3f ms a tick for a 200-block network (%d ticks timed; %,d RF delivered)", perTick,
                GridNets.timedTicks, rf));
            SelfTest.check(perTick < 1.0, String.format("a 200-block Grid network moving power every tick costs under 1 ms a tick "
                + "(%.3f ms)", perTick));
            SelfTest.check(rf > 0, "the points delivered power the whole time (" + rf + " RF)");
        } catch (Throwable e) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] gridperf threw", e);
            SelfTest.check(false, "gridperf ran without an exception (" + e + ")");
        } finally {
            for (TileEntity t : placed) {
                ((TileGrid) t).pickedUp = true;
                world.setBlockToAir(t.xCoord, t.yCoord, t.zCoord);
            }
            GridNets.NETS.remove(GridNets.key(GridSelfTest.SCOPE, "selftest-perf"));
            SelfTest.summary();
        }
    }
}
