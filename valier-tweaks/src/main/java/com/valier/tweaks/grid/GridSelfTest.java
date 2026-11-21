package com.valier.tweaks.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.TweaksConfig;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.util.GTOreDictUnificator;

/**
 * {@code valiertweaks selftest grid} (2.3.0): the Valier Grid with real GregTech LV hulls as machines, over 45 ticks.
 * <ul>
 * <li>Ticks 1-20: a plug fed 64 EU a tick (as a cable would) powers a hull through an MV point in another chunk and one
 * in the Nether (at the hull's 32 V: nothing explodes); 128 V into an LV plug is refused; two equal points share; a
 * cell fills from a plug with no point; a point turns EU into RF at 3.6 (a test RF machine).</li>
 * <li>Ticks 22-41: with priority 10 the first point takes all; the cell, no longer fed, powers a new point; RF that came
 * in doesn't become EU (by default).</li>
 * <li>Then at once: a point in an unloaded chunk leaves the network and comes back with its chunk; another player's
 * network of the same name is another network; the menu's buttons; a cell picked up keeps its energy and network; items
 * charged (a GregTech battery); a network's colour (from its name, then picked, saved and sent to its blocks); 10% loss
 * (32 EU delivers 29); an IC2 machine fed in IC2 packets.</li>
 * </ul>
 */
public final class GridSelfTest { // public: Forge calls its tick handler from another class loader

    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000ba224");
    static final String SCOPE = "p:" + OWNER;

    final WorldServer world = DimensionManager.getWorld(0);
    WorldServer nether;
    final int bx = (SelfTest.CX + 5) << 4, bz = (SelfTest.CZ + 18) << 4;
    final int fx = (SelfTest.CX + 9) << 4; // the chunk that unloads
    int y, ticks;
    FakePlayer player;
    final List<Object[]> placed = new ArrayList<>();
    TileGridPlug plugA, plugB, plugC, plugD, plugE, plugF;
    TileGridPoint pointA, pointN, pointB1, pointB2, pointC, pointD, pointE, pointF;
    TileEntity cableF, hullF;
    TileGridCell cellC;
    TileEntity hullA, hullN, hullB1, hullB2, hullC, hullE;
    RfSink rfSink;
    long b1Before, b2Before, cellFull, hullCBefore, hullEBefore;

    public void start() {
        for (int cx = -1; cx <= 2; cx++) for (int cz = -1; cz <= 1; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        world.theChunkProviderServer.loadChunk(fx >> 4, bz >> 4);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        player = new FakePlayer(world, new GameProfile(OWNER, "GridTester"));
        player.setPosition(bx + 8.5, y, bz + 8.5);
        nether = DimensionManager.getWorld(-1);
        if (nether == null) {
            DimensionManager.initDimension(-1);
            nether = DimensionManager.getWorld(-1);
        }
        // A: plug, an MV point in the next chunk east, one in the Nether, each with an LV hull.
        plugA = (TileGridPlug) grid(world, bx + 2, y, bz + 2, ModBlocks.gridPlug, 0, "selftest-a");
        pointA = (TileGridPoint) grid(world, bx + 18, y, bz + 2, ModBlocks.gridPoint, 1, "selftest-a");
        hullA = hull(world, bx + 19, y, bz + 2);
        if (nether != null) {
            nether.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
            pointN = (TileGridPoint) grid(nether, bx + 2, 70, bz + 2, ModBlocks.gridPoint, 1, "selftest-a");
            hullN = hull(nether, bx + 3, 70, bz + 2);
        }
        // B: two equal LV points.
        plugB = (TileGridPlug) grid(world, bx + 2, y, bz + 6, ModBlocks.gridPlug, 0, "selftest-b");
        pointB1 = (TileGridPoint) grid(world, bx + 6, y, bz + 6, ModBlocks.gridPoint, 0, "selftest-b");
        hullB1 = hull(world, bx + 7, y, bz + 6);
        pointB2 = (TileGridPoint) grid(world, bx + 10, y, bz + 6, ModBlocks.gridPoint, 0, "selftest-b");
        hullB2 = hull(world, bx + 11, y, bz + 6);
        // C: a plug and a cell; the point comes later.
        plugC = (TileGridPlug) grid(world, bx + 2, y, bz + 10, ModBlocks.gridPlug, 0, "selftest-c");
        cellC = (TileGridCell) grid(world, bx + 4, y, bz + 10, ModBlocks.gridCell, 0, "selftest-c");
        pointC = (TileGridPoint) grid(world, bx + 6, y, bz + 10, ModBlocks.gridPoint, 0, "");
        hullC = hull(world, bx + 7, y, bz + 10);
        // D: EU to RF.
        plugD = (TileGridPlug) grid(world, bx + 2, y, bz + 14, ModBlocks.gridPlug, 0, "selftest-d");
        pointD = (TileGridPoint) grid(world, bx + 6, y, bz + 14, ModBlocks.gridPoint, 0, "selftest-d");
        rfSink = new RfSink(); // put next to it on the first tick, after the block has joined (that looks at its neighbours)
        // E: RF in, a hull out.
        plugE = (TileGridPlug) grid(world, bx + 10, y, bz + 14, ModBlocks.gridPlug, 0, "selftest-e");
        pointE = (TileGridPoint) grid(world, bx + 12, y, bz + 14, ModBlocks.gridPoint, 0, "selftest-e");
        hullE = hull(world, bx + 13, y, bz + 14);
        // F: a point feeding an LV hull through a GregTech tin cable (1 A, 32 V).
        plugF = (TileGridPlug) grid(world, bx + 2, y, bz + 18, ModBlocks.gridPlug, 0, "selftest-f");
        pointF = (TileGridPoint) grid(world, bx + 6, y, bz + 18, ModBlocks.gridPoint, 0, "selftest-f");
        cableF = cable(world, bx + 7, y, bz + 18);
        hullF = hull(world, bx + 8, y, bz + 18);
        if (cableF instanceof IGregTechTileEntity && ((IGregTechTileEntity) cableF).getMetaTileEntity() instanceof MetaPipeEntity) {
            MetaPipeEntity c = (MetaPipeEntity) ((IGregTechTileEntity) cableF).getMetaTileEntity();
            c.connect(ForgeDirection.WEST);
            c.connect(ForgeDirection.EAST);
        }
        long refused = plugA.injectEnergyUnits(ForgeDirection.UP, 128, 1);
        SelfTest.check(refused == 0 && world.getBlock(plugA.xCoord, plugA.yCoord, plugA.zCoord) == ModBlocks.gridPlug,
            "128 V into an LV plug is refused (0 amps taken), and nothing blows up");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        SelfTest.log("grid: built; running " + (WARM + 42) + " ticks");
    }

    /**
     * Ticks before anything is fed: a GregTech machine takes no energy until it has worked out its input sides, which it
     * does only after it has ticked a while (about 20 ticks).
     */
    static final int WARM = 30;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ticks++;
        SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 2, (bz >> 4) + 1);
        SelfTest.keepLoaded(world, fx >> 4, bz >> 4, fx >> 4, bz >> 4);
        SelfTest.keepLoaded(nether, bx >> 4, bz >> 4, bx >> 4, bz >> 4);
        int ticks = this.ticks - WARM;
        if (ticks < 1) return;
        try {
            if (ticks == 1) {
                pointD.refreshNear();
                pointD.near[5] = rfSink;
                pointD.nearKind[5] = 3;
            }
            if (ticks <= 20) {
                plugA.injectEnergyUnits(ForgeDirection.UP, 32, 2);
                plugB.injectEnergyUnits(ForgeDirection.UP, 32, 1);
                plugC.injectEnergyUnits(ForgeDirection.UP, 32, 2);
                plugD.injectEnergyUnits(ForgeDirection.UP, 32, 1);
                plugF.injectEnergyUnits(ForgeDirection.UP, 32, 2);
            } else if (ticks == 21) {
                checkFirst();
                pointB1.priority = 10;
                if (pointB1.network != null) pointB1.network.resort();
                b1Before = stored(hullB1);
                b2Before = stored(hullB2);
                cellFull = cellC.stored;
                hullCBefore = stored(hullC);
                hullEBefore = stored(hullE);
                GridNets.link(pointC, SCOPE, "selftest-c");
            } else if (ticks <= 41) {
                plugB.injectEnergyUnits(ForgeDirection.UP, 32, 1);
                plugE.receiveEnergy(ForgeDirection.UP, 1000, false);
            } else if (ticks == 42) {
                checkSecond();
                atOnce();
                finish();
            }
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] grid threw", t);
            SelfTest.check(false, "grid ran without an exception (" + t + ")");
            finish();
        }
    }

    void checkFirst() {
        long a = stored(hullA), n = stored(hullN);
        SelfTest.check(a > 0 && n > 0 && world.getTileEntity(hullA.xCoord, hullA.yCoord, hullA.zCoord) == hullA,
            "a plug's EU reaches hulls through an MV point in another chunk and one in the Nether, at the hulls' own 32 V "
                + "(" + a + " / " + n + " EU; nothing exploded)");
        long b1 = stored(hullB1), b2 = stored(hullB2);
        SelfTest.check(b1 > 0 && b2 > 0 && Math.abs(b1 - b2) <= 64, "two points of equal priority share (" + b1 + " / " + b2 + " EU)");
        SelfTest.check(cellC.stored >= 19 * 64 && cellC.stored <= 21 * 64, "a cell fills from a plug no point uses (" + cellC.stored
            + " EU after 20 ticks of 64)");
        long rfWant = (long) (19 * 32 * GridEnergy.rfPerEu());
        SelfTest.check(rfSink.got >= rfWant && rfSink.got <= rfWant + 32 * 4, "a point turns EU into RF at 3.6 RF per EU (" + rfSink.got
            + " RF for about 20 × 32 EU)");
        long f = stored(hullF);
        boolean connected = cableF instanceof IGregTechTileEntity && ((IGregTechTileEntity) cableF).getMetaTileEntity() instanceof MetaPipeEntity
            && ((MetaPipeEntity) ((IGregTechTileEntity) cableF).getMetaTileEntity()).isConnectedAtSide(ForgeDirection.WEST);
        SelfTest.check(connected && f > 0 && world.getTileEntity(cableF.xCoord, cableF.yCoord, cableF.zCoord) == cableF,
            "a GregTech tin cable connects to a point, and the point's EU reaches a hull through it, the cable unburnt (" + f + " EU)");
    }

    void checkSecond() {
        long b1 = stored(hullB1) - b1Before, b2 = stored(hullB2) - b2Before;
        SelfTest.check(b1 > 0 && b2 == 0, "with priority 10, the first point takes everything (" + b1 + " / " + b2 + " EU)");
        long c = stored(hullC) - hullCBefore;
        SelfTest.check(cellC.stored < cellFull && c > 0 && cellFull - cellC.stored >= c, "unfed, the cell powers the new point ("
            + (cellFull - cellC.stored) + " EU out, the hull got " + c + ")");
        long e = stored(hullE) - hullEBefore;
        SelfTest.check(e == 0 && plugE.rf > 0, "RF that came in doesn't become EU by default (the hull got " + e + "; the plug holds "
            + plugE.rf + " RF)");
    }

    void atOnce() {
        // A point in an unloaded chunk leaves the network, and comes back with its chunk.
        TileGridPoint far = (TileGridPoint) grid(world, fx + 2, y, bz + 2, ModBlocks.gridPoint, 0, "selftest-a");
        GridNet a = GridNets.find(SCOPE, "selftest-a");
        int before = a == null ? -1 : a.blocks();
        SelfTest.unloadChunk(world, fx >> 4, bz >> 4);
        int gone = a == null ? -1 : a.blocks();
        world.theChunkProviderServer.loadChunk(fx >> 4, bz >> 4);
        TileEntity back = world.getTileEntity(fx + 2, y, bz + 2);
        if (back instanceof TileGrid) GridNets.join((TileGrid) back);
        SelfTest.check(before >= 4 && gone == before - 1 && a != null && a.blocks() == before && far != back,
            "a point's chunk unloads: it leaves the network (" + before + " → " + gone + " blocks); loaded again, it is back");
        // Another player's network of the same name.
        TileGrid other = grid(world, bx + 14, y, bz + 2, ModBlocks.gridPoint, 0, "");
        other.setOwner(UUID.fromString("00000000-0000-4000-8000-0000000ba225"));
        GridNets.link(other, "p:00000000-0000-4000-8000-0000000ba225", "selftest-a");
        SelfTest.check(other.network != null && other.network != plugA.network, "another player's \"selftest-a\" is another network");
        // The menu.
        GridMenu m = (GridMenu) GridMenu.server(player, world, pointB2.xCoord, pointB2.yCoord, pointB2.zCoord);
        m.apply(player, GridMenu.PRIORITY, 5, 0, null, null);
        m.apply(player, GridMenu.SURGE, 0, 0, null, null);
        m.apply(player, GridMenu.LIMIT, -16, 0, null, null);
        m.apply(player, GridMenu.REDSTONE, 1, 0, null, null);
        m.apply(player, GridMenu.VOLTS, -1, 0, null, null);
        m.apply(player, GridMenu.NET, 0, 0, null, "selftest-menu");
        NBTTagCompound st = m.state(player);
        SelfTest.check(pointB2.priority == 5 && pointB2.surge && pointB2.limit == 112 && pointB2.redstone == 1 && "selftest-menu".equals(pointB2.net)
            && pointB2.network != null && st.getString("Net")
                .equals("selftest-menu"),
            "the menu's priority, Surge, limit, redstone and network buttons work (limit " + pointB2.limit + ")");
        // A cell picked up keeps its energy and network.
        ItemStack item = ModBlocks.gridCell.toItem(cellC);
        int cx = cellC.xCoord, cy = cellC.yCoord, cz = cellC.zCoord;
        long had = cellC.stored;
        cellC.pickedUp = true;
        world.setBlockToAir(cx, cy, cz);
        world.setBlock(cx, cy, cz, ModBlocks.gridCell, item.getItemDamage(), 3);
        ModBlocks.gridCell.onBlockPlacedBy(world, cx, cy, cz, player, item);
        TileEntity te = world.getTileEntity(cx, cy, cz);
        SelfTest.check(te instanceof TileGridCell && ((TileGridCell) te).stored == had && "selftest-c".equals(((TileGrid) te).net)
            && ((TileGrid) te).network != null, "a cell picked up and placed again keeps its " + had + " EU and its network");
        // Charging a GregTech battery.
        ItemStack battery = ItemList.Battery_RE_LV_Lithium.get(1);
        player.inventory.setInventorySlotContents(3, battery);
        long used = GridEnergy.charge(player, 5000);
        SelfTest.check(used > 0, "a controller's charging reaches a GregTech battery in a player's inventory (" + used + " EU)");
        player.inventory.setInventorySlotContents(3, null);
        // A placement Forge undoes (another mod cancelled it) drops nothing: the item kept the cell's energy.
        int ux = bx + 14, uz = bz + 10;
        TileGridCell spare = (TileGridCell) grid(world, ux, y, uz, ModBlocks.gridCell, 0, "");
        spare.stored = 5000;
        ItemStack ci = ModBlocks.gridCell.toItem(spare);
        spare.pickedUp = true;
        world.setBlockToAir(ux, y, uz);
        world.captureBlockSnapshots = true;
        world.setBlock(ux, y, uz, ModBlocks.gridCell, 0, 3);
        ModBlocks.gridCell.onBlockPlacedBy(world, ux, y, uz, player, ci);
        world.captureBlockSnapshots = false;
        world.restoringBlockSnapshots = true;
        world.setBlockToAir(ux, y, uz);
        world.restoringBlockSnapshots = false;
        world.capturedBlockSnapshots.clear();
        int dropped = world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(ux - 2, y - 2, uz - 2, ux + 3, y + 3, uz + 3))
            .size();
        SelfTest.check(dropped == 0, "a cell placement Forge undoes (a mod cancelled it) drops nothing: the item kept its 5,000 EU ("
            + dropped + " dropped)");
        // The configurator pastes a network's name in the pasted block's own team, never the copied block's team.
        ItemStack conf = new ItemStack(ModBlocks.pipeConfigurator);
        GridConfigure.use(conf, player, other, true);
        TileGrid mine = grid(world, bx + 14, y, bz + 6, ModBlocks.gridPoint, 0, "");
        GridConfigure.use(conf, player, mine, false);
        SelfTest.check(SCOPE.equals(mine.scope) && "selftest-a".equals(mine.net) && mine.network != null && mine.network == plugA.network
            && mine.network != other.network, "the configurator pastes another player's \"selftest-a\" as this player's own (" + mine.scope
                + ")");
        // Network colours: one from its name until someone picks one in the menu; picked, it is saved and sent to its blocks.
        GridColours.set(SCOPE, "selftest-b", -1); // a run stopped half-way may have left one picked
        int auto = GridColours.of(SCOPE, "selftest-b");
        GridMenu cm = (GridMenu) GridMenu.server(player, world, pointB1.xCoord, pointB1.yCoord, pointB1.zCoord);
        cm.apply(player, GridMenu.COLOUR, 1, 0, null, null);
        int picked = GridColours.of(SCOPE, "selftest-b");
        NBTTagCompound desc = plugB.description(); // (the packet's own getter is client-only)
        NBTTagCompound saved = new NBTTagCompound();
        GridColours.get()
            .writeToNBT(saved);
        GridColours reread = new GridColours("selftest");
        reread.readFromNBT(saved);
        Integer kept = reread.picked(SCOPE, "selftest-b");
        SelfTest.check(auto == GridColours.byName("selftest-b") && picked == (auto + 1) % 16 && kept != null && kept == picked
            && desc.getInteger("Colour") == GridColours.rgb(picked) && plugB.network != null && plugB.network.colour == picked
            && cm.state(player)
                .getInteger("Colour") == GridColours.rgb(picked),
            "a network's colour: " + GridColours.name(auto) + " from its name, " + GridColours.name(picked)
                + " picked in the menu, saved, and sent to its blocks");
        GridColours.set(SCOPE, "selftest-b", -1);
        // The menu's Blocks page: the network's blocks with place and status, this one marked.
        cm.apply(player, GridMenu.BLOCKS, 0, 0, null, null);
        NBTTagCompound page = cm.state(player);
        net.minecraft.nbt.NBTTagList listed = page.getTagList("Blocks", 10);
        boolean here = false;
        for (int i = 0; i < listed.tagCount(); i++) here |= listed.getCompoundTagAt(i)
            .getBoolean("Here")
            && listed.getCompoundTagAt(i)
                .getInteger("X") == pointB1.xCoord;
        SelfTest.check(page.getBoolean("Listing") && listed.tagCount() >= 2 && listed.tagCount() == page.getInteger("BlockCount") && here,
            "the menu's Blocks page lists the network's " + listed.tagCount() + " blocks with their places, this one marked");
        // Loss (gridLossPercent): at 10%, 32 EU in a plug delivers 29 and loses 3.
        int lossWas = TweaksConfig.gridLossPercent;
        TweaksConfig.gridLossPercent = 10;
        try {
            TileGridPlug plugG = (TileGridPlug) grid(world, bx + 2, y, bz + 22, ModBlocks.gridPlug, 0, "selftest-g");
            TileGridPoint pointG = (TileGridPoint) grid(world, bx + 6, y, bz + 22, ModBlocks.gridPoint, 0, "selftest-g");
            RfSink sinkG = new RfSink();
            pointG.refreshNear();
            pointG.near[5] = sinkG;
            pointG.nearKind[5] = 3;
            plugG.injectEnergyUnits(ForgeDirection.UP, 32, 1);
            GridNet g = plugG.network;
            long movedBefore = pointG.moved;
            if (g != null) g.tick(1);
            long got = pointG.moved - movedBefore, lost = g == null ? -1 : g.lostNow;
            SelfTest.check(g != null && got == 29 && lost == 3 && plugG.eu == 0 && sinkG.got > 0,
                "with 10% loss, 32 EU in a plug delivers " + got + " EU and loses " + lost + " (29 and 3)");
        } finally {
            TweaksConfig.gridLossPercent = lossWas;
        }
        // IC2: a sink fed in packets of its tier.
        Ic2Test.check(this);
    }

    void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        clear();
        SelfTest.summary();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    TileGrid grid(WorldServer w, int x, int yy, int z, BlockGrid b, int tier, String net) {
        w.setBlock(x, yy, z, b, tier, 3);
        TileGrid t = (TileGrid) w.getTileEntity(x, yy, z);
        t.setOwner(OWNER);
        if (!net.isEmpty()) GridNets.link(t, SCOPE, net);
        placed.add(new Object[] { w, x, yy, z });
        return t;
    }

    /** An LV hull at x, its front (output) facing east, away from the point west of it. */
    TileEntity hull(WorldServer w, int x, int yy, int z) {
        w.setBlock(x, yy - 1, z, Blocks.stone);
        w.setBlockToAir(x, yy, z); // (in the Nether: netherrack)
        ItemStack s = ItemList.Hull_LV.get(1);
        s.tryPlaceItemIntoWorld(player, w, x, yy - 1, z, 1, 0.5F, 1F, 0.5F);
        TileEntity te = w.getTileEntity(x, yy, z);
        if (te instanceof IGregTechTileEntity) ((IGregTechTileEntity) te).setFrontFacing(ForgeDirection.EAST);
        placed.add(new Object[] { w, x, yy, z });
        placed.add(new Object[] { w, x, yy - 1, z });
        return te;
    }

    /** A GregTech 1x tin cable at x (32 V, 1 A). */
    TileEntity cable(WorldServer w, int x, int yy, int z) {
        w.setBlock(x, yy - 1, z, Blocks.stone);
        w.setBlockToAir(x, yy, z);
        ItemStack s = GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.Tin, 1);
        if (s != null) s.tryPlaceItemIntoWorld(player, w, x, yy - 1, z, 1, 0.5F, 1F, 0.5F);
        placed.add(new Object[] { w, x, yy, z });
        placed.add(new Object[] { w, x, yy - 1, z });
        return w.getTileEntity(x, yy, z);
    }

    static long stored(TileEntity te) {
        return te instanceof IBasicEnergyContainer ? ((IBasicEnergyContainer) te).getStoredEU() : -1;
    }

    void clear() {
        for (Object[] p : placed) {
            WorldServer w = (WorldServer) p[0];
            int x = (Integer) p[1], yy = (Integer) p[2], z = (Integer) p[3];
            if (!w.blockExists(x, yy, z)) w.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
            TileEntity te = w.getTileEntity(x, yy, z);
            if (te instanceof TileGrid) ((TileGrid) te).pickedUp = true;
            Block b = w.getBlock(x, yy, z);
            if (b != Blocks.air) w.setBlockToAir(x, yy, z);
        }
        for (GridNet n : GridNets.NETS.values()
            .toArray(new GridNet[0])) if (n.name.startsWith("selftest-")) GridNets.NETS.remove(GridNets.key(n.scope, n.name));
    }

    /** A test RF machine: takes all it is given. */
    static final class RfSink extends TileEntity implements cofh.api.energy.IEnergyReceiver {

        long got;

        @Override
        public int receiveEnergy(ForgeDirection from, int max, boolean simulate) {
            if (!simulate) got += max;
            return max;
        }

        @Override
        public int getEnergyStored(ForgeDirection from) {
            return 0;
        }

        @Override
        public int getMaxEnergyStored(ForgeDirection from) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canConnectEnergy(ForgeDirection from) {
            return true;
        }
    }

    /** IC2's part (IC2 is on the test server). */
    static final class Ic2Test {

        static void check(GridSelfTest test) {
            TileGridPoint p = (TileGridPoint) test.grid(test.world, test.bx + 14, test.y, test.bz + 10, ModBlocks.gridPoint, 1, "");
            Sink s = new Sink();
            p.refreshNear();
            p.near[5] = s;
            p.nearKind[5] = 2;
            long want = p.demand();
            long used = p.deliver(10_000, 0);
            SelfTest.check(want > 0 && used == 128 && s.got == 128 && s.biggest <= 32,
                "an MV point feeds an LV IC2 machine 32 EU packets, as many as its amps (" + used + " EU, packets of up to " + s.biggest
                    + ")");
        }

        static final class Sink extends TileEntity implements ic2.api.energy.tile.IEnergySink {

            double got, biggest;

            @Override
            public double getDemandedEnergy() {
                return 10_000 - got;
            }

            @Override
            public int getSinkTier() {
                return 1;
            }

            @Override
            public double injectEnergy(ForgeDirection from, double amount, double voltage) {
                got += amount;
                biggest = Math.max(biggest, voltage);
                return 0;
            }

            @Override
            public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection from) {
                return true;
            }
        }
    }
}
