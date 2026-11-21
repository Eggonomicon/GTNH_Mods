package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest barrelperf}: what linked barrels cost the server. 1,024 barrels on one channel; every
 * tick each one gets an item put in the way a hopper does (its input slot) and every other one has one taken the way a
 * hopper does (its output slot): 1,536 automation calls a tick, every one changing the shared count, so every barrel's
 * label and comparator are due every 10 ticks. After 20 ticks to settle, 200 are measured: the barrel code's time (those
 * calls plus the label and comparator updates) per tick, against a limit of 1 ms.
 */
public final class BarrelPerfTest { // public: Forge calls its tick handler from another class loader

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 12) << 4, bz = (SelfTest.CZ + 12) << 4;
    final List<TileBarrel> barrels = new ArrayList<>();
    int y, ticks;
    long nanos;

    public void start() {
        for (int cx = -1; cx < 3; cx++) for (int cz = -1; cz < 3; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        BarrelChannels d = BarrelChannels.get();
        for (int i = 0; i < 1024; i++) {
            int x = bx + i % 32, z = bz + i / 32;
            world.setBlock(x, y, z, ModBlocks.barrel, BarrelTier.LUV, 2);
            TileBarrel t = (TileBarrel) world.getTileEntity(x, y, z);
            t.tier = BarrelTier.LUV;
            t.owner = BarrelSelfTest.OWNER;
            d.join(t, "p:" + BarrelSelfTest.OWNER, "selftest-perf");
            barrels.add(t);
        }
        barrels.get(0)
            .store()
            .insert(new ItemStack(Items.iron_ingot), 1_000_000, false);
        SelfTest.log("barrelperf: 1,024 linked LuV barrels built; settling 20 ticks, then measuring 200");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return; // the work; BarrelSync's share is timed around its own tick
        ticks++;
        com.valier.tweaks.SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 2, (bz >> 4) + 2);
        long t0 = System.nanoTime();
        ItemStack one = new ItemStack(Items.iron_ingot, 1);
        for (int i = 0; i < barrels.size(); i++) {
            TileBarrel t = barrels.get(i);
            if (t.isItemValidForSlot(TileBarrel.IN, one)) t.setInventorySlotContents(TileBarrel.IN, one.copy());
            if ((i & 1) == 0 && t.getStackInSlot(TileBarrel.OUT) != null) t.decrStackSize(TileBarrel.OUT, 1);
            t.markDirty();
        }
        long t1 = System.nanoTime();
        long sync = BarrelSync.lastTickNanos;
        if (ticks > 20) nanos += t1 - t0 + sync;
        if (ticks < 221) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            double perTick = nanos / 200.0 / 1e6;
            long count = barrels.get(0)
                .store()
                .count();
            SelfTest.log(String.format("barrelperf: %.3f ms a tick for 1,024 linked barrels, 1,536 hopper calls a tick", perTick));
            SelfTest.check(perTick < 1.0, String.format("1,024 linked barrels with 1,536 hopper calls a tick cost under 1 ms a tick "
                + "(%.3f ms)", perTick));
            SelfTest.check(count == 1_000_000 + 221L * 512, "the shared count is exact after it all (" + count + ")");
        } catch (Throwable e) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] barrelperf threw", e);
            SelfTest.check(false, "barrelperf ran without an exception (" + e + ")");
        } finally {
            BarrelChannels d = BarrelChannels.get();
            for (TileBarrel t : barrels) {
                d.leave(t);
                t.pickedUp = true;
                world.setBlockToAir(t.xCoord, t.yCoord, t.zCoord);
            }
            d.channels.values()
                .removeIf(c -> c.name.startsWith("selftest-"));
            SelfTest.summary();
        }
    }
}
