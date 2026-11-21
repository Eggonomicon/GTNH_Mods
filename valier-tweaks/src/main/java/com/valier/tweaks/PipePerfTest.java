package com.valier.tweaks;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeNetworks;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest pipeperf}: how much server time pipes cost. 512 rigs (chest, pipe, pipe, chest) in an
 * empty 4 by 4 chunk corner: 256 moving all the time (Basic, a chest of cobblestone each), 128 with nothing to move
 * (Elite, empty: these should sleep), 128 Request faces already stocked. That is 1,024 faces working on timers. After
 * 100 ticks to settle, 200 ticks are measured; the time spent in the pipe code per tick is logged and checked against
 * a limit (1 ms a tick).
 */
public final class PipePerfTest { // public: Forge calls its tick handler from another class loader

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 12) << 4, bz = (SelfTest.CZ + 6) << 4;
    int y, ticks;
    long startNanos, startMoves;

    void start() {
        // One chunk more on every side: a chunk is decorated (dungeons, trees) once its neighbours exist, and that must
        // happen now, not over the rigs while they are built.
        for (int cx = -1; cx < 5; cx++) for (int cz = -1; cz < 5; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8) + 1;
        clear();
        int rigs = 0;
        for (int row = 0; row < 32; row++) {
            for (int col = 0; col < 16; col++) {
                int x = bx + col * 4, z = bz + row * 2;
                int kind = rigs < 256 ? 0 : rigs < 384 ? 1 : 2;
                rig(x, z, kind);
                rigs++;
            }
        }
        SelfTest.log("pipeperf: built " + rigs + " rigs; settling 100 ticks, then measuring 200");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /** kind 0: moving (Basic, a full chest); 1: nothing to move (Elite, empty); 2: a Request face already stocked. */
    void rig(int x, int z, int kind) {
        world.setBlock(x, y, z, Blocks.chest);
        world.setBlock(x + 3, y, z, Blocks.chest);
        TileEntity ta = world.getTileEntity(x, y, z), tb = world.getTileEntity(x + 3, y, z);
        if (!(ta instanceof IInventory) || !(tb instanceof IInventory)) {
            SelfTest.log("pipeperf: no chest at " + x + " " + y + " " + z + "; that rig is skipped");
            return;
        }
        IInventory a = (IInventory) ta, b = (IInventory) tb;
        int tier = kind == 0 ? PipeTier.BASIC : PipeTier.ELITE;
        TilePipe p1 = place(x + 1, z, tier), p2 = place(x + 2, z, tier);
        if (p1 == null || p2 == null) return;
        if (kind == 0) {
            for (int i = 0; i < a.getSizeInventory(); i++) a.setInventorySlotContents(i, new ItemStack(Blocks.cobblestone, 64));
            set(p1, ForgeDirection.WEST, FaceMode.EXTRACT);
            set(p2, ForgeDirection.EAST, FaceMode.INSERT);
        } else if (kind == 1) {
            set(p1, ForgeDirection.WEST, FaceMode.EXTRACT);
            set(p2, ForgeDirection.EAST, FaceMode.INSERT);
        } else {
            a.setInventorySlotContents(0, new ItemStack(Blocks.dirt, 64));
            b.setInventorySlotContents(0, new ItemStack(Blocks.dirt, 16));
            FaceConfig f = p2.face(ForgeDirection.EAST.ordinal());
            f.filter[0] = new ItemStack(Blocks.dirt);
            f.count[0] = 16;
            set(p2, ForgeDirection.EAST, FaceMode.REQUEST);
            p1.faceChanged();
        }
    }

    TilePipe place(int x, int z, int tier) {
        world.setBlock(x, y, z, ModBlocks.itemPipe, tier, 2);
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return null;
        BlockPipe.setOwner((TilePipe) te, PipeSelfTest.OWNER, "PipeTester");
        ((TilePipe) te).updateConnections();
        return (TilePipe) te;
    }

    void set(TilePipe p, ForgeDirection side, FaceMode m) {
        p.updateConnections();
        FaceConfig f = p.face(side.ordinal());
        if (f == null) return;
        f.mode = m;
        p.faceChanged();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks == 100) {
            PipeNetworks.resetStats();
            return;
        }
        if (ticks < 300) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            java.util.List<String> stats = PipeNetworks.stats();
            for (String s : stats) SelfTest.log("pipeperf: " + s);
            double msTotal = Double.parseDouble(stats.get(2)
                .replaceAll("[^0-9.]", ""));
            double perTick = msTotal / 200.0;
            SelfTest.log(String.format("pipeperf: %.3f ms a tick on pipes for 1,024 working faces (%.1f µs a face a tick)",
                perTick, perTick * 1000 / 1024));
            SelfTest.check(perTick < 1.0, String.format("1,024 working pipe faces cost under 1 ms a tick (%.3f ms)", perTick));
            SelfTest.check(stats.get(0)
                .contains("waiting"), "the idle faces are asleep: " + stats.get(0));
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] pipeperf threw", t);
            SelfTest.check(false, "pipeperf ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    void clear() {
        for (int x = bx; x < bx + 64; x++) for (int z = bz; z < bz + 64; z++) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                IInventory i = (IInventory) te;
                for (int s = 0; s < i.getSizeInventory(); s++) i.setInventorySlotContents(s, null);
            }
            if (!world.isAirBlock(x, y, z)) world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
    }
}
