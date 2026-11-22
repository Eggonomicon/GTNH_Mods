package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.valier.tweaks.storage.BarrelTier;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest cost}: {@code /valier cost} (2.3.3).
 * <ul>
 * <li>The meter's own cost: a million timings, each well under a microsecond.</li>
 * <li>A known load: 2 ms of work timed as tree felling on each of 50 ticks. The last 10 seconds show 50 x 2 ms spread
 * over the ticks measured (0.5 ms a tick over 200), the minute likewise, and the worst tick is at least 2 ms.</li>
 * <li>Real work lands on the right part: 4 pipe rigs moving cobblestone between chests, and a barrel. Over 100 ticks the
 * pipes cost more than nothing, the cobblestone moved, and the pipes and the barrel are counted.</li>
 * <li>The command, run as {@code /valier cost} (the alias): a header, a line for every part, and the total against the
 * server's own tick time.</li>
 * </ul>
 */
public final class CostSelfTest { // public: Forge calls its tick handler from another class loader

    static final int BUSY_TICKS = 50;
    static final long BUSY_NANOS = 2_000_000;

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 20) << 4, bz = (SelfTest.CZ + 20) << 4;
    final PipePerfTest rigs = new PipePerfTest();
    int y, ticks;

    void start() {
        // The meter's own cost, with what this tick had put back after (the million timings aren't real work).
        int p = ValierCost.Part.PEACEFUL.ordinal();
        long had = ValierCost.now[p];
        for (int i = 0; i < 100_000; i++) ValierCost.add(ValierCost.Part.PEACEFUL, ValierCost.start()); // warm up
        long t0 = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) ValierCost.add(ValierCost.Part.PEACEFUL, ValierCost.start());
        double each = (System.nanoTime() - t0) / 1e6;
        ValierCost.now[p] = had;
        SelfTest.check(each < 200, String.format("timing a piece of work costs %.0f ns (under 200)", each));

        for (int cx = -1; cx < 2; cx++) for (int cz = -1; cz < 2; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8) + 1;
        rigs.y = y;
        for (int i = 0; i < 4; i++) rigs.rig(bx + 1, bz + 1 + i * 2, 0);
        world.setBlock(bx + 8, y, bz + 1, ModBlocks.barrel, BarrelTier.BASIC, 3);
        SelfTest.log("cost: 4 pipe rigs and a barrel built; " + BUSY_TICKS + " ticks of 2 ms timed as tree felling, then 50 more");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /** START: after the meter has closed the last tick (it goes first), so each tick's 2 ms are that tick's. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ticks++;
        SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 1, (bz >> 4) + 1);
        if (ticks <= BUSY_TICKS) {
            long t0 = ValierCost.start();
            while (System.nanoTime() - t0 < BUSY_NANOS) {} // busy, as real work would be
            ValierCost.add(ValierCost.Part.TREES, t0);
            return;
        }
        if (ticks < 101) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            check();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] cost threw", t);
            SelfTest.check(false, "cost ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    void check() {
        // The known load. The ticks before the test may have felled no trees: the test server has nobody chopping.
        int n10 = ValierCost.have(ValierCost.SHORT), n60 = ValierCost.have(ValierCost.MINUTE);
        double want10 = BUSY_TICKS * BUSY_NANOS / 1e6 / n10, want60 = BUSY_TICKS * BUSY_NANOS / 1e6 / n60;
        double got10 = ValierCost.sum(ValierCost.Part.TREES, n10) / 1e6 / n10;
        double got60 = ValierCost.sum(ValierCost.Part.TREES, n60) / 1e6 / n60;
        double worst = ValierCost.worst(ValierCost.Part.TREES, n60) / 1e6;
        SelfTest.check(n10 >= 100 && got10 >= want10 && got10 < want10 * 1.2, String.format(
            "50 ticks of 2 ms show as %.3f ms a tick over the last %d ticks (%.3f wanted)", got10, n10, want10));
        SelfTest.check(got60 >= want60 && got60 < want60 * 1.2, String.format("and as %.3f ms over the last %d (%.3f wanted)",
            got60, n60, want60));
        SelfTest.check(worst >= 2.0 && ValierCost.worstAll(n60) / 1e6 >= worst, String.format(
            "the worst tick shows at least 2 ms (%.3f), and the worst tick in all at least that", worst));

        // Real work on the right part.
        long pipes = ValierCost.sum(ValierCost.Part.PIPES, 100);
        int moved = 0;
        for (int i = 0; i < 4; i++) moved += cobble(bx + 4, bz + 1 + i * 2);
        SelfTest.check(pipes > 0 && moved > 0, String.format("pipes moving cobblestone show as pipe time (%.3f ms over 100 ticks, "
            + "%d moved)", pipes / 1e6, moved));
        String[] what = ValierCost.loaded();
        SelfTest.check(lead(what[ValierCost.Part.PIPES.ordinal()]) >= 8, "the 8 pipes are counted: " + what[ValierCost.Part.PIPES.ordinal()]);
        SelfTest.check(lead(what[ValierCost.Part.BARRELS.ordinal()]) >= 1, "the barrel is counted: " + what[ValierCost.Part.BARRELS.ordinal()]);

        // The command, by its alias.
        ICommand alias = (ICommand) MinecraftServer.getServer()
            .getCommandManager()
            .getCommands()
            .get("valier");
        SelfTest.check(alias instanceof TweaksCommand, "/valier is /valiertweaks (" + alias + ")");
        final List<String> said = new ArrayList<>();
        ICommandSender op = new ICommandSender() {

            @Override
            public String getCommandSenderName() {
                return "CostTester";
            }

            @Override
            public IChatComponent func_145748_c_() {
                return new ChatComponentText(getCommandSenderName());
            }

            @Override
            public void addChatMessage(IChatComponent c) {
                said.add(c.getUnformattedText());
            }

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return true;
            }

            @Override
            public ChunkCoordinates getPlayerCoordinates() {
                return new ChunkCoordinates(bx, y, bz);
            }

            @Override
            public World getEntityWorld() {
                return world;
            }
        };
        MinecraftServer.getServer()
            .getCommandManager()
            .executeCommand(op, "valier cost");
        for (String s : said) SelfTest.log("cost: " + s);
        int parts = ValierCost.Part.values().length;
        boolean everyPart = true;
        for (ValierCost.Part p : ValierCost.Part.values()) {
            boolean found = false;
            for (String s : said) if (s.startsWith(" " + p.label + ": ")) found = true;
            everyPart &= found;
        }
        String last = said.isEmpty() ? "" : said.get(said.size() - 1);
        SelfTest.check(said.size() == parts + 2 && said.get(0)
            .startsWith("Valier cost") && everyPart, "/valier cost lists every part (" + said.size() + " lines)");
        SelfTest.check(last.startsWith("In all: ") && last.contains("TPS"), "and ends with the total against the server's tick: " + last);
        SelfTest.check(said.size() > 1 && said.get(1)
            .startsWith(" " + ValierCost.Part.TREES.label), "the costliest part comes first: " + (said.size() > 1 ? said.get(1) : ""));
    }

    int cobble(int x, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IInventory)) return 0;
        IInventory inv = (IInventory) te;
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i) != null) n += inv.getStackInSlot(i).stackSize;
        return n;
    }

    /** The number a count starts with. */
    static int lead(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        return i == 0 ? -1 : Integer.parseInt(s.substring(0, i));
    }

    void clear() {
        com.valier.tweaks.storage.BarrelPerfTest.remove(world, bx + 8, y, bz + 1);
        for (int x = bx; x < bx + 6; x++) for (int z = bz; z < bz + 10; z++) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                IInventory i = (IInventory) te;
                for (int s = 0; s < i.getSizeInventory(); s++) i.setInventorySlotContents(s, null);
            }
            if (!world.isAirBlock(x, y, z)) world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
    }
}
