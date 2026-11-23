package com.valier.tweaks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * {@code valiertweaks selftest pipebench}: Valier pipes against GregTech pipes and EnderIO conduits, on the same rigs.
 * Each case is built in the same empty corner, left 100 ticks to settle, then timed for 600 ticks: the overworld's
 * tick and the server's tick handlers (other dimensions and the network only add noise). The same corner empty is timed
 * for 300 ticks before and after; the case's cost is its time less their mean. Autosave ticks are left out, and the
 * slowest 2% of each run (garbage collection) too. The server warms up first: 600 ticks, or
 * {@code -Dvaliertweaks.pipebench.warmup=<ticks>}. {@code -Dvaliertweaks.pipebench=GT_ITEMS,VALIER_ITEMS} runs only those
 * cases, in that order.
 * <ul>
 * <li>Items: 256 rigs of chest, pipe, pipe, chest, the first chest kept full of cobblestone and the last kept empty.
 * Valier: Basic item pipes, a Pull face on the chest. GregTech: tin item pipes, an LV conveyor cover set to import on
 * the chest. EnderIO: basic item conduits, the chest's connection set to extract, always (EnderIO's default needs a
 * redstone signal). Rigs are 5 blocks apart, so no two chests make a double chest.</li>
 * <li>Fluids: 128 rigs of EnderIO tank, pipe, pipe, tank, the first kept full of water. Valier: Basic fluid pipes, a Pull
 * face. GregTech: bronze fluid pipes, an LV pump cover set to import. EnderIO: basic fluid conduits set to extract.</li>
 * <li>Idle: 64 runs of 32 item pipes each (2,048 blocks) with nothing to move.</li>
 * <li>AE2: the item and fluid rigs as small networks, an import bus to a storage bus (fluids: ae2fc's fluid buses), a
 * creative energy cell each, no cards.</li>
 * <li>Valier fluid pipes at Advanced and Elite too (bigger batches, more often). Fluid rigs are tended every 5 ticks (an
 * Elite face moves a whole 16,000 mB tank every 5 ticks), item rigs every 20.</li>
 * <li>{@code -Dvaliertweaks.pipebench.passes=2} runs the cases again in reverse order and gives each case's mean: slow drift
 * in the server's own tick cancels out, and the two runs show how far a number can be trusted.</li>
 * <li>The item rigs' chests and the fluid rigs' tanks alone, with no pipes: what the cases above pay besides their pipes,
 * so a pipe's own cost is its case's less these.</li>
 * </ul>
 * Blocks in a world keep ticking all through (an empty server would stop them after a minute). Items and mB moved are
 * counted so each case is seen to work. Results go to the log as {@code pipebench:} lines.
 */
public final class PipeBench { // public: Forge calls its tick handlers from another class loader

    enum Case {
        VALIER_ITEMS("Valier item pipes", "items"), GT_ITEMS("GregTech item pipes", "items"), EIO_ITEMS("EnderIO item conduits", "items"),
        VALIER_FLUIDS("Valier fluid pipes", "mB"), GT_FLUIDS("GregTech fluid pipes", "mB"), EIO_FLUIDS("EnderIO fluid conduits", "mB"),
        VALIER_IDLE("Valier item pipes, idle", ""), GT_IDLE("GregTech item pipes, idle", ""), EIO_IDLE("EnderIO item conduits, idle", ""),
        AE2_ITEMS("AE2 import bus to storage bus", "items"), AE2_FLUIDS("AE2 fluid import bus to fluid storage bus", "mB"),
        CHESTS_ONLY("The item rigs' chests alone", "items"), TANKS_ONLY("The fluid rigs' tanks alone", "mB"),
        VALIER_FLUIDS_ADV("Valier fluid pipes, Advanced", "mB"), VALIER_FLUIDS_ELITE("Valier fluid pipes, Elite", "mB");

        final String label, unit;

        Case(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        boolean fluid() {
            return this == VALIER_FLUIDS || this == GT_FLUIDS || this == EIO_FLUIDS || this == AE2_FLUIDS || this == TANKS_ONLY
                || this == VALIER_FLUIDS_ADV || this == VALIER_FLUIDS_ELITE;
        }

        /** A Valier pipe case, and the pipes' tier. */
        boolean valier() {
            return this == VALIER_ITEMS || this == VALIER_FLUIDS || this == VALIER_IDLE || this == VALIER_FLUIDS_ADV
                || this == VALIER_FLUIDS_ELITE;
        }

        int tier() {
            return this == VALIER_FLUIDS_ADV ? PipeTier.ADVANCED : this == VALIER_FLUIDS_ELITE ? PipeTier.ELITE : PipeTier.BASIC;
        }

        /** The rigs' chests or tanks with nothing between them: what every item or fluid case pays besides its pipes. */
        boolean bare() {
            return this == CHESTS_ONLY || this == TANKS_ONLY;
        }

        boolean idle() {
            return this == VALIER_IDLE || this == GT_IDLE || this == EIO_IDLE;
        }
    }

    /** Sides to try filling and draining a tank from (UNKNOWN first: the tank itself). */
    static final ForgeDirection[] SIDES = { ForgeDirection.UNKNOWN, ForgeDirection.UP, ForgeDirection.DOWN, ForgeDirection.NORTH };
    static final int RIGS = 256, FLUID_RIGS = 128, LINES = 64, LINE = 32;
    static final int CLEAR = 40, BASE = 300, SETTLE = 100, MEASURE = 600;

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 30) << 4, bz = (SelfTest.CZ + 30) << 4;
    final FakePlayer player;
    final List<int[]> sources = new ArrayList<>(), sinks = new ArrayList<>();
    /** The cases to run: all, or those named in -Dvaliertweaks.pipebench=GT_ITEMS,EIO_ITEMS (for finding faults). */
    final Case[] cases;
    /** Ticks to let the server settle first (-Dvaliertweaks.pipebench.warmup, default 600). */
    final int warmup = Integer.getInteger("valiertweaks.pipebench.warmup", 600);
    int y, at = -1, phase, ticks;
    long t0;
    final long[] base = new long[BASE], run = new long[MEASURE];
    int nBase, nRun;
    final double[] baseMs, runMs;
    final long[] moved;

    PipeBench() {
        player = FakePlayerFactory.get(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-00000000be4c"), "PipeBench"));
        String only = System.getProperty("valiertweaks.pipebench", "");
        List<Case> list = new ArrayList<>();
        if (only.isEmpty()) list.addAll(Arrays.asList(Case.values()));
        else for (String name : only.split(",")) list.add(Case.valueOf(name.trim())); // in the order given
        if (Integer.getInteger("valiertweaks.pipebench.passes", 1) >= 2) { // and back again: slow drift cancels out
            List<Case> back = new ArrayList<>(list);
            java.util.Collections.reverse(back);
            list.addAll(back);
        }
        cases = list.toArray(new Case[0]);
        baseMs = new double[cases.length + 1];
        runMs = new double[cases.length];
        moved = new long[cases.length];
    }

    void start() {
        load();
        y = world.getHeightValue(bx + 8, bz + 8) + 1;
        player.setPosition(bx, y + 1, bz);
        clear();
        SelfTest.log("pipebench: " + cases.length + " cases, each timed against the empty corner before and after it; "
            + "warming up for " + warmup + " ticks first");
        ticks = CLEAR - warmup;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    void load() {
        for (int cx = -1; cx <= 6; cx++) for (int cz = -1; cz <= 9; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
    }

    /*
     * What is timed: the server's tick handlers (start and end: EnderIO's conduit networks run there, Valier's meter too)
     * and the overworld's whole tick (its blocks, tiles and world tick handlers: Valier pipes, GregTech pipes, conduit
     * bundles). Other dimensions and the network are left out: they only add noise.
     */
    long acc;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerFirst(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) acc = 0;
        t0 = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStartLast(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) acc += System.nanoTime() - t0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldFirst(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.world == world) t0 = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldLast(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world == world) acc += System.nanoTime() - t0;
    }

    /** Last thing each tick: the tick's time is kept, then the next step (outside the time measured). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long took = acc + System.nanoTime() - t0;
        boolean autosave = MinecraftServer.getServer()
            .getTickCounter() % 900 == 0;
        if (!autosave) {
            if (phase == 1 && nBase < BASE) base[nBase++] = took;
            if (phase == 3 && nRun < MEASURE) run[nRun++] = took;
        }
        try {
            step();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] pipebench threw", t);
            SelfTest.check(false, "pipebench ran without an exception (" + t + ")");
            finish();
        }
    }

    /**
     * Phases: 0 clearing (and, the first time, warming up), 1 timing the empty corner, 2 built and settling, 3 timing
     * the case. After the last case the empty corner is timed once more, so every case has a before and an after.
     */
    void step() {
        load();
        world.resetUpdateEntityTick(); // keep blocks ticking with nobody on
        ticks++;
        Case c = at >= 0 && at < cases.length ? cases[at] : null;
        if (phase >= 2 && ticks % (c != null && c.fluid() ? 5 : 20) == 0) { // tanks hold 16,000 mB: an Elite face's batch
            long n = tend(c);
            if (phase == 3) moved[at] += n;
        }
        if (phase == 0 && ticks >= CLEAR) {
            at++;
            phase = 1;
            nBase = 0;
            ticks = 0;
        } else if (phase == 1 && nBase >= BASE) {
            baseMs[at] = mean(base, nBase);
            if (at >= cases.length) {
                finish();
                return;
            }
            build(c);
            diag(c, "built");
            phase = 2;
            ticks = 0;
        } else if (phase == 2 && ticks >= SETTLE) {
            diag(c, "after settling");
            phase = 3;
            nRun = 0;
            ticks = 0;
        } else if (phase == 3 && nRun >= MEASURE) {
            moved[at] += tend(c);
            runMs[at] = mean(run, nRun);
            SelfTest.log(String.format("pipebench: %s: %.3f ms a tick with it (empty before: %.3f)", c.label, runMs[at], baseMs[at]));
            clear();
            phase = 0;
            ticks = 0;
        }
    }

    void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        clear();
        SelfTest.log("pipebench: results (cost = the tick with it, less the mean of the empty corner before and after)");
        java.util.Map<Case, List<double[]>> byCase = new java.util.LinkedHashMap<>();
        for (int i = 0; i < cases.length && i < at; i++) {
            Case c = cases[i];
            double without = (baseMs[i] + baseMs[i + 1]) / 2, cost = runMs[i] - without;
            double perSec = moved[i] / (MEASURE / 20.0);
            SelfTest.log(String.format("pipebench:   %s: %.3f ms a tick (%.3f with it, %.3f / %.3f without)%s", c.label, cost,
                runMs[i], baseMs[i], baseMs[i + 1], c.idle() ? "" : String.format(", %,.0f %s a second", perSec, c.unit)));
            if (c.bare()) SelfTest.check(moved[i] == 0, c.label + " moved nothing (" + moved[i] + " " + c.unit + ")");
            else SelfTest.check(c.idle() || moved[i] > 0, c.label + " moved something (" + moved[i] + " " + c.unit + ")");
            byCase.computeIfAbsent(c, k -> new ArrayList<>())
                .add(new double[] { cost, perSec });
        }
        SelfTest.log("pipebench: mean of each case's runs");
        for (java.util.Map.Entry<Case, List<double[]>> e : byCase.entrySet()) {
            double cost = 0, perSec = 0;
            StringBuilder each = new StringBuilder();
            for (double[] r : e.getValue()) {
                cost += r[0];
                perSec += r[1];
                each.append(each.length() == 0 ? "" : ", ").append(String.format("%.3f", r[0]));
            }
            int n = e.getValue().size();
            Case c = e.getKey();
            SelfTest.log(String.format("pipebench:   %s: %.3f ms a tick (runs: %s)%s", c.label, cost / n, each,
                c.idle() || c.bare() ? "" : String.format(", %,.0f %s a second", perSec / n, c.unit)));
        }
        SelfTest.summary();
    }

    /** The first rig's state, to see a case is set up as meant. */
    void diag(Case c, String when) {
        int x = bx + 1, z = bz;
        StringBuilder b = new StringBuilder("pipebench: " + c.label + ", " + when + ": ");
        for (int i = -1; i <= 2; i++) {
            TileEntity te = world.getTileEntity(x + i, y, z);
            b.append("[").append(i + 1).append("] ").append(te == null ? world.getBlock(x + i, y, z).getUnlocalizedName() : describe(te)).append("; ");
        }
        SelfTest.log(b.toString());
    }

    static String describe(TileEntity te) {
        StringBuilder b = new StringBuilder(te.getClass().getSimpleName());
        if (te instanceof IInventory) {
            int n = 0;
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i) != null) n += inv.getStackInSlot(i).stackSize;
            b.append(" items ").append(n);
        }
        if (te instanceof IFluidHandler && !(te instanceof TilePipe)) {
            try {
                net.minecraftforge.fluids.FluidTankInfo[] info = ((IFluidHandler) te).getTankInfo(ForgeDirection.UNKNOWN);
                if (info != null) for (net.minecraftforge.fluids.FluidTankInfo i : info)
                    b.append(" fluid ").append(i.fluid == null ? 0 : i.fluid.amount);
            } catch (Throwable ignored) {}
        }
        try {
            if (te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity) b.append(Gt.describe(te));
        } catch (Throwable ignored) {}
        b.append(Eio.describe(te));
        if (te instanceof appeng.api.parts.IPartHost) {
            for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.EAST }) {
                appeng.api.parts.IPart part = ((appeng.api.parts.IPartHost) te).getPart(d);
                if (part != null) b.append(" ").append(d).append(" ").append(part.getClass().getSimpleName());
            }
        }
        return b.toString();
    }

    /** Mean of the times without the slowest 2% (garbage collection), in ms. */
    static double mean(long[] t, int n) {
        long[] s = Arrays.copyOf(t, n);
        Arrays.sort(s);
        int keep = Math.max(1, n - n / 50);
        long sum = 0;
        for (int i = 0; i < keep; i++) sum += s[i];
        return sum / 1e6 / keep;
    }

    // ---- building ------------------------------------------------------------------------------------------------

    void build(Case c) {
        sources.clear();
        sinks.clear();
        if (c.idle()) {
            for (int row = 0; row < LINES; row++) for (int i = 0; i < LINE; i++) {
                int x = bx + i, z = bz + row * 2;
                if (c == Case.VALIER_IDLE) valierPipe(x, z, ModBlocks.itemPipe);
                else if (c == Case.GT_IDLE) Gt.pipe(this, x, z, false);
                else Eio.conduit(this, x, z, false);
            }
            for (int row = 0; row < LINES; row++) for (int i = 0; i < LINE; i++) connect(c, bx + i, bz + row * 2, i > 0, i < LINE - 1);
            return;
        }
        int n = c.fluid() ? FLUID_RIGS : RIGS;
        for (int k = 0; k < n; k++) {
            int x = bx + (k % 16) * 5, z = bz + (k / 16) * 2; // a gap between rigs: no double chests
            if (c.fluid()) {
                Block tank = GameRegistry.findBlock("EnderIO", "blockTank");
                world.setBlock(x, y, z, tank, 0, 3);
                world.setBlock(x + 3, y, z, tank, 0, 3);
            } else {
                world.setBlock(x, y, z, Blocks.chest);
                world.setBlock(x + 3, y, z, Blocks.chest);
            }
            sources.add(new int[] { x, z });
            sinks.add(new int[] { x + 3, z });
            if (c.bare()) continue;
            if (c == Case.AE2_ITEMS || c == Case.AE2_FLUIDS) {
                Ae2.rig(this, x, z, c.fluid());
                continue;
            }
            for (int i = 1; i <= 2; i++) {
                if (c == Case.VALIER_ITEMS) valierPipe(x + i, z, ModBlocks.itemPipe, c.tier());
                else if (c.valier()) valierPipe(x + i, z, ModBlocks.fluidPipe, c.tier());
                else if (c == Case.GT_ITEMS || c == Case.GT_FLUIDS) Gt.pipe(this, x + i, z, c.fluid());
                else Eio.conduit(this, x + i, z, c.fluid());
            }
            connect(c, x + 1, z, true, true);
            connect(c, x + 2, z, true, true);
            if (c.valier()) {
                valierFace(x + 1, z, ForgeDirection.WEST, FaceMode.EXTRACT);
                valierFace(x + 2, z, ForgeDirection.EAST, FaceMode.INSERT);
            } else if (c == Case.GT_ITEMS || c == Case.GT_FLUIDS) Gt.importCover(this, x + 1, z, c.fluid());
            else {
                Eio.mode(this, x + 1, z, ForgeDirection.WEST, "INPUT", c.fluid());
                Eio.mode(this, x + 2, z, ForgeDirection.EAST, "OUTPUT", c.fluid());
            }
        }
        tend(c);
    }

    /** Joins a pipe to its west and east neighbours (GregTech needs telling; the others join by themselves). */
    void connect(Case c, int x, int z, boolean west, boolean east) {
        if (c == Case.GT_ITEMS || c == Case.GT_FLUIDS || c == Case.GT_IDLE) {
            if (west) Gt.connect(this, x, z, ForgeDirection.WEST);
            if (east) Gt.connect(this, x, z, ForgeDirection.EAST);
        } else if (c.valier()) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TilePipe) ((TilePipe) te).updateConnections();
        }
    }

    void valierPipe(int x, int z, BlockPipe block) {
        valierPipe(x, z, block, PipeTier.BASIC);
    }

    void valierPipe(int x, int z, BlockPipe block, int tier) {
        world.setBlock(x, y, z, block, tier, 2);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TilePipe) {
            BlockPipe.setOwner((TilePipe) te, PipeSelfTest.OWNER, "PipeTester");
            ((TilePipe) te).updateConnections();
        }
    }

    void valierFace(int x, int z, ForgeDirection side, FaceMode m) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return;
        TilePipe p = (TilePipe) te;
        p.updateConnections();
        FaceConfig f = p.face(side.ordinal());
        if (f == null) return;
        f.mode = m;
        p.faceChanged();
    }

    /** Keeps the sources full and the sinks empty; returns what reached the sinks. */
    long tend(Case c) {
        if (c == null || c.idle()) return 0;
        long got = 0;
        boolean fluid = c.fluid(); // EnderIO tanks are inventories too (their bucket slots)
        for (int[] s : sinks) {
            TileEntity te = world.getTileEntity(s[0], y, s[1]);
            if (!fluid && te instanceof IInventory) {
                IInventory inv = (IInventory) te;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack st = inv.getStackInSlot(i);
                    if (st == null) continue;
                    got += st.stackSize;
                    inv.setInventorySlotContents(i, null);
                }
            } else if (fluid && te instanceof IFluidHandler) {
                for (ForgeDirection d : SIDES) {
                    FluidStack out = ((IFluidHandler) te).drain(d, Integer.MAX_VALUE, true);
                    if (out != null && out.amount > 0) {
                        got += out.amount;
                        break;
                    }
                }
            }
        }
        for (int[] s : sources) {
            TileEntity te = world.getTileEntity(s[0], y, s[1]);
            if (!fluid && te instanceof IInventory) {
                IInventory inv = (IInventory) te;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack st = inv.getStackInSlot(i);
                    if (st == null || st.stackSize < 64) inv.setInventorySlotContents(i, new ItemStack(Blocks.cobblestone, 64));
                }
            } else if (fluid && te instanceof IFluidHandler) {
                for (ForgeDirection d : SIDES) {
                    if (((IFluidHandler) te).fill(d, new FluidStack(FluidRegistry.WATER, 1_000_000), true) > 0) break;
                }
            }
        }
        return got;
    }

    void clear() {
        for (int x = bx - 1; x < bx + 82; x++) for (int z = bz - 1; z < bz + 130; z++) for (int y = this.y; y <= this.y + 1; y++) {
            if (world.isAirBlock(x, y, z)) continue;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                IInventory inv = (IInventory) te;
                for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, null);
            }
            if (te instanceof IFluidHandler && !(te instanceof TilePipe)) {
                try {
                    ((IFluidHandler) te).drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, true);
                } catch (Throwable ignored) {}
            }
            world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(bx - 2, y - 2, bz - 2, bx + 84, y + 3, bz + 132);
        for (Object o : world.getEntitiesWithinAABB(EntityItem.class, box)) ((EntityItem) o).setDead();
    }

    // ---- GregTech -----------------------------------------------------------------------------------------------

    /** GregTech, only touched while it is loaded (it always is on GTNH). */
    static final class Gt {

        static void pipe(PipeBench b, int x, int z, boolean fluid) {
            ItemStack s = gregtech.api.util.GTOreDictUnificator.get(gregtech.api.enums.OrePrefixes.pipeMedium,
                fluid ? gregtech.api.enums.Materials.Bronze : gregtech.api.enums.Materials.Tin, 1L);
            if (s == null) throw new IllegalStateException("no GregTech " + (fluid ? "bronze fluid" : "tin item") + " pipe");
            s.tryPlaceItemIntoWorld(b.player, b.world, x, b.y - 1, z, 1, 0.5F, 1F, 0.5F);
        }

        static gregtech.api.metatileentity.MetaPipeEntity meta(PipeBench b, int x, int z) {
            TileEntity te = b.world.getTileEntity(x, b.y, z);
            if (!(te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity)) return null;
            Object m = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te).getMetaTileEntity();
            return m instanceof gregtech.api.metatileentity.MetaPipeEntity ? (gregtech.api.metatileentity.MetaPipeEntity) m : null;
        }

        static String describe(TileEntity te) {
            Object m = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te).getMetaTileEntity();
            StringBuilder b = new StringBuilder(" ").append(m == null ? "no meta" : m.getClass().getSimpleName());
            if (m instanceof gregtech.api.metatileentity.MetaPipeEntity)
                b.append(" connections ").append(Integer.toBinaryString(((gregtech.api.metatileentity.MetaPipeEntity) m).mConnections & 0xFF));
            gregtech.common.covers.Cover w = ((gregtech.api.interfaces.tileentity.ICoverable) te).getCoverAtSide(ForgeDirection.WEST);
            if (w != null && w.getCoverID() != 0) b.append(" west cover ").append(w.getClass().getSimpleName())
                .append(w instanceof gregtech.common.covers.CoverIOBase ? " " + ((gregtech.common.covers.CoverIOBase) w).getIOMode() : "")
                .append(" every ").append(w.getTickRate()).append(" ticks");
            return b.toString();
        }

        static void connect(PipeBench b, int x, int z, ForgeDirection d) {
            gregtech.api.metatileentity.MetaPipeEntity m = meta(b, x, z);
            if (m != null) m.connect(d);
        }

        /** An LV conveyor (items) or pump (fluids) on the first pipe's west face, importing from the block there. */
        static void importCover(PipeBench b, int x, int z, boolean fluid) {
            TileEntity te = b.world.getTileEntity(x, b.y, z);
            if (!(te instanceof gregtech.api.interfaces.tileentity.ICoverable)) return;
            gregtech.api.interfaces.tileentity.ICoverable c = (gregtech.api.interfaces.tileentity.ICoverable) te;
            ItemStack item = (fluid ? gregtech.api.enums.ItemList.Electric_Pump_LV : gregtech.api.enums.ItemList.Conveyor_Module_LV).get(1L);
            gregtech.common.covers.Cover cover = gregtech.api.covers.CoverRegistry.buildCover(item, ForgeDirection.WEST, c);
            c.attachCover(cover);
            gregtech.common.covers.Cover on = c.getCoverAtSide(ForgeDirection.WEST);
            if (on instanceof gregtech.common.covers.CoverIOBase)
                ((gregtech.common.covers.CoverIOBase) on).setIOMode(gregtech.common.covers.modes.TransferMode.IMPORT);
        }
    }

    // ---- AE2 -----------------------------------------------------------------------------------------------------

    /**
     * AE2 (a compile-time dependency already): each rig its own small network, as a player would build it to move things
     * from one block to another. Glass cable on both pipe spots, an import bus (items) or ae2fc fluid import bus on the
     * source, a storage bus or fluid storage bus on the sink (no filters: everything), and a creative energy cell on the
     * first cable for power. Two channels: no controller needed. No upgrade cards.
     */
    static final class Ae2 {

        static void rig(PipeBench b, int x, int z, boolean fluid) {
            appeng.api.definitions.IDefinitions d = appeng.api.AEApi.instance()
                .definitions();
            ItemStack cable = d.parts()
                .cableGlass()
                .stack(appeng.api.util.AEColor.Transparent, 1);
            ItemStack in = fluid ? new ItemStack(GameRegistry.findItem("ae2fc", "part_fluid_import"))
                : d.parts()
                    .importBus()
                    .maybeStack(1)
                    .get();
            ItemStack out = fluid ? new ItemStack(GameRegistry.findItem("ae2fc", "part_fluid_storage_bus"))
                : d.parts()
                    .storageBus()
                    .maybeStack(1)
                    .get();
            Block bus = d.blocks()
                .multiPart()
                .maybeBlock()
                .get();
            for (int i = 1; i <= 2; i++) {
                b.world.setBlock(x + i, b.y, z, bus, 0, 3);
                TileEntity te = b.world.getTileEntity(x + i, b.y, z);
                if (!(te instanceof appeng.api.parts.IPartHost)) throw new IllegalStateException("no AE2 cable bus at " + (x + i) + " " + b.y + " " + z);
                appeng.api.parts.IPartHost host = (appeng.api.parts.IPartHost) te;
                host.addPart(cable.copy(), ForgeDirection.UNKNOWN, b.player);
                host.addPart(i == 1 ? in : out, i == 1 ? ForgeDirection.WEST : ForgeDirection.EAST, b.player);
                host.markForUpdate();
            }
            Block cell = d.blocks()
                .energyCellCreative()
                .maybeBlock()
                .get();
            b.world.setBlock(x + 1, b.y + 1, z, cell, 0, 3);
        }
    }

    // ---- EnderIO (by reflection: its conduit classes reach into half a dozen other mods) ---------------------------

    static final class Eio {

        static void conduit(PipeBench b, int x, int z, boolean fluid) {
            try {
                Block bundle = GameRegistry.findBlock("EnderIO", "blockConduitBundle");
                b.world.setBlock(x, b.y, z, bundle, 0, 3);
                TileEntity te = b.world.getTileEntity(x, b.y, z);
                Object conduit = fluid ? Class.forName("crazypants.enderio.conduit.liquid.LiquidConduit")
                    .getConstructor()
                    .newInstance()
                    : Class.forName("crazypants.enderio.conduit.item.ItemConduit")
                        .getConstructor(int.class)
                        .newInstance(0);
                method(te.getClass(), "addConduit", 1).invoke(te, conduit);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("EnderIO conduit", e);
            }
        }

        static void mode(PipeBench b, int x, int z, ForgeDirection d, String mode, boolean fluid) {
            try {
                TileEntity te = b.world.getTileEntity(x, b.y, z);
                Class<?> type = Class.forName(fluid ? "crazypants.enderio.conduit.liquid.ILiquidConduit"
                    : "crazypants.enderio.conduit.item.IItemConduit");
                Object conduit = method(te.getClass(), "getConduit", 1).invoke(te, type);
                Class<?> modes = Class.forName("crazypants.enderio.conduit.ConnectionMode");
                Object m = modes.getMethod("valueOf", String.class)
                    .invoke(null, mode);
                method(conduit.getClass(), "setConnectionMode", 2).invoke(conduit, d, m);
                if ("INPUT".equals(mode)) { // extracting: always, not only with a redstone signal (EnderIO's default)
                    Object always = Class.forName("crazypants.enderio.machine.RedstoneControlMode")
                        .getMethod("valueOf", String.class)
                        .invoke(null, "IGNORE");
                    for (Method setter : conduit.getClass().getMethods()) {
                        if (!setter.getName().startsWith("setExtractionRed") || setter.getParameterTypes().length != 2) continue;
                        if (setter.getParameterTypes()[0] == ForgeDirection.class) setter.invoke(conduit, d, always);
                        else setter.invoke(conduit, always, d);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("EnderIO connection mode", e);
            }
        }

        /** A conduit bundle's conduits: their connections and the west side's modes (empty for anything else). */
        static String describe(TileEntity te) {
            StringBuilder b = new StringBuilder();
            try {
                Method all = method(te.getClass(), "getConduits", 0);
                for (Object conduit : (java.util.Collection<?>) all.invoke(te)) {
                    b.append(" ").append(conduit.getClass().getSimpleName());
                    for (String name : new String[] { "getConduitConnections", "getExternalConnections" }) {
                        try {
                            b.append(" ").append(name.substring(3)).append(" ").append(method(conduit.getClass(), name, 0).invoke(conduit));
                        } catch (NoSuchMethodException ignored) {}
                    }
                    for (Method m : conduit.getClass().getMethods()) {
                        String n = m.getName();
                        if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == ForgeDirection.class
                            && (n.equals("getConnectionMode") || n.startsWith("getExtractionRed") || n.startsWith("getExtractioRed")))
                            b.append(" ").append(n.substring(3)).append("(W) ").append(m.invoke(conduit, ForgeDirection.WEST));
                    }
                }
            } catch (NoSuchMethodException ignored) {
                return "";
            } catch (Throwable t) {
                b.append(" (").append(t).append(")");
            }
            return b.toString();
        }

        static Method method(Class<?> c, String name, int params) throws NoSuchMethodException {
            for (Method m : c.getMethods()) if (m.getName()
                .equals(name) && m.getParameterTypes().length == params) return m;
            throw new NoSuchMethodException(c.getName() + "." + name);
        }
    }
}
