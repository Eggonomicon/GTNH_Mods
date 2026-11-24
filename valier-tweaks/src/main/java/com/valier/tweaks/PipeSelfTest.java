package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.Fluids;
import com.valier.tweaks.pipe.ItemPipeConfigurator;
import com.valier.tweaks.pipe.PipeClicks;
import com.valier.tweaks.pipe.PipeInfo;
import com.valier.tweaks.pipe.PipeNetwork;
import com.valier.tweaks.pipe.PipeNetworks;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest pipes}: real pipes, chests and crafters in an empty corner of the test world, checked
 * over the next ticks:
 * <ul>
 * <li>joins (pipe to pipe, pipe to chest, not to a GregTech-like slotless block) and a wrench shutting a side;</li>
 * <li>Extract to Insert moves everything, nothing lost or made;</li>
 * <li>a whitelist filter; a Request face keeping 10 iron stocked from a Provide face;</li>
 * <li>a double chest as one inventory (a face never feeds the chest it takes from);</li>
 * <li>a fluid pipe moving liquid XP between two XP crafters;</li>
 * <li>Wireless pipes with the same tag linked (same owner), another owner's left out;</li>
 * <li>a loot spawner with only a pipe pulling from it works; an idle face sleeps;</li>
 * <li>the {@code pipes} command answers.</li>
 * <li>2.1 (two layers higher): priority strictly first, round robin, even split, in order; Deny over Allow and Void;
 * Void on a pulling face (with an amount kept) and on a pushing face (overflow), for fluids too; creative pipes (Pull,
 * Provide, wireless, fluid; only ops and creative players); the wrench picking a pipe up; the Pipe Configurator; 2.0
 * filters read in; the new tiers; what WAILA is sent.</li>
 * <li>2.3.4 (seven blocks up): tagged faces on Basic or Elite pipes joined to a Wireless pipe link like Wireless faces,
 * both ways, items and fluids; the same tag with no Wireless pipe joined in stays unlinked.</li>
 * </ul>
 */
public final class PipeSelfTest { // public: Forge calls its tick handler from another class loader

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 8) << 4, bz = (SelfTest.CZ + 2) << 4;
    int y, ticks;
    final List<int[]> placed = new ArrayList<>();
    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    // The rigs, checked later.
    int[] a1, b1, a2, b2, c3, d3, dc, out4, e5, f5, wa, wb, wc, ls, lsOut, idle, lsPush, lsPushOut;
    int[] idlePipe;

    void start() {
        // One chunk more on every side: a chunk is decorated (dungeons, villages) once its neighbours exist, and that
        // must happen now, not over the rigs while they are built.
        for (int cx = -1; cx < 3; cx++) for (int cz = -1; cz < 3; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8);
        clear();
        joins();
        extractInsert();
        filterAndRequest();
        doubleChest();
        fluids();
        wireless();
        lootSpawner();
        filters();
        v21();
        wirelessJoined();
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        SelfTest.log("waiting 80 ticks for the pipes to work");
    }

    // ---- building ------------------------------------------------------------------------------------------------

    TilePipe pipe(int[] p, Block block, int tier, UUID owner) {
        world.setBlock(p[0], p[1], p[2], block, tier, 3);
        placed.add(p);
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        if (!(te instanceof TilePipe)) return null;
        BlockPipe.setOwner((TilePipe) te, owner, "PipeTester");
        ((TilePipe) te).updateConnections();
        return (TilePipe) te;
    }

    IInventory chest(int[] p) {
        world.setBlock(p[0], p[1], p[2], Blocks.chest);
        placed.add(p);
        return (IInventory) world.getTileEntity(p[0], p[1], p[2]);
    }

    void mode(TilePipe p, ForgeDirection side, FaceMode m) {
        FaceConfig f = p.face(side.ordinal());
        if (f == null) {
            SelfTest.check(false, "pipe at " + p.xCoord + " " + p.yCoord + " " + p.zCoord + " touches a block on " + side);
            return;
        }
        f.mode = m;
        p.faceChanged();
    }

    int[] at(int dx, int dz) {
        return new int[] { bx + dx, y, bz + dz };
    }

    static int count(IInventory inv, Item item) {
        int n = 0;
        if (inv == null) return 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    IInventory inv(int[] p) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        return te instanceof IInventory ? (IInventory) te : null;
    }

    TilePipe pipeAt(int[] p) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        return te instanceof TilePipe ? (TilePipe) te : null;
    }

    // ---- the rigs ----------------------------------------------------------------------------------------------------

    void joins() {
        int[] c = at(1, 1), p1 = at(2, 1), p2 = at(3, 1), slotless = at(2, 2);
        chest(c);
        TilePipe t1 = pipe(p1, ModBlocks.itemPipe, PipeTier.BASIC, OWNER), t2 = pipe(p2, ModBlocks.itemPipe, PipeTier.BASIC, OWNER);
        world.setBlock(slotless[0], y, slotless[2], Blocks.stone);
        placed.add(slotless);
        t1.updateConnections();
        SelfTest.check(t1.blockOn(ForgeDirection.WEST.ordinal()) && t1.pipeOn(ForgeDirection.EAST.ordinal())
            && t2.pipeOn(ForgeDirection.WEST.ordinal()) && !t1.blockOn(ForgeDirection.SOUTH.ordinal()),
            "joins: a pipe joins the chest beside it and the pipe beside it, not plain stone");
        t1.toggleSide(ForgeDirection.EAST.ordinal());
        boolean shut = !t1.pipeOn(ForgeDirection.EAST.ordinal()) && !t2.pipeOn(ForgeDirection.WEST.ordinal());
        t1.toggleSide(ForgeDirection.EAST.ordinal());
        SelfTest.check(shut && t1.pipeOn(ForgeDirection.EAST.ordinal()) && t2.pipeOn(ForgeDirection.WEST.ordinal()),
            "a wrench shuts a side (both pipes let go) and opens it again");
        FaceConfig f = t1.face(ForgeDirection.WEST.ordinal());
        SelfTest.check(f != null && f.mode == FaceMode.PROVIDE, "a new face touching a block is Provide: nothing moves by itself");
    }

    void extractInsert() {
        a1 = at(1, 4);
        int[] p1 = at(2, 4), p2 = at(3, 4);
        b1 = at(4, 4);
        IInventory a = chest(a1);
        chest(b1);
        a.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 20));
        a.setInventorySlotContents(5, new ItemStack(Items.iron_ingot, 5));
        TilePipe t1 = pipe(p1, ModBlocks.itemPipe, PipeTier.ELITE, OWNER), t2 = pipe(p2, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        mode(t1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(t2, ForgeDirection.EAST, FaceMode.INSERT);
    }

    void filterAndRequest() {
        a2 = at(1, 6);
        int[] p1 = at(2, 6), p2 = at(3, 6);
        b2 = at(4, 6);
        IInventory a = chest(a2);
        chest(b2);
        a.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 20));
        a.setInventorySlotContents(1, new ItemStack(Items.iron_ingot, 5));
        TilePipe t1 = pipe(p1, ModBlocks.itemPipe, PipeTier.ELITE, OWNER), t2 = pipe(p2, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        t1.face(ForgeDirection.WEST.ordinal()).allow.filter[0] = new ItemStack(Items.iron_ingot);
        mode(t1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(t2, ForgeDirection.EAST, FaceMode.INSERT);

        c3 = at(1, 8);
        int[] q1 = at(2, 8), q2 = at(3, 8);
        d3 = at(4, 8);
        IInventory c = chest(c3);
        chest(d3);
        c.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 64));
        TilePipe u1 = pipe(q1, ModBlocks.itemPipe, PipeTier.ELITE, OWNER), u2 = pipe(q2, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        FaceConfig r = u2.face(ForgeDirection.EAST.ordinal());
        r.allow.filter[0] = new ItemStack(Items.iron_ingot);
        r.allow.count[0] = 10;
        mode(u2, ForgeDirection.EAST, FaceMode.REQUEST);
        u1.faceChanged(); // its west face stays Provide
    }

    void doubleChest() {
        // A double chest (two chests side by side) with a pipe on each half: one Extract, one Insert. It is one
        // inventory, so nothing moves between them; the extract face sends to the single chest at the end instead.
        dc = at(1, 10);
        int[] dc2 = at(1, 11), p1 = at(2, 10), p2 = at(2, 11), p3 = at(3, 11);
        out4 = at(4, 11);
        IInventory a = chest(dc);
        chest(dc2);
        chest(out4);
        a.setInventorySlotContents(0, new ItemStack(Items.flint, 12));
        TilePipe t1 = pipe(p1, ModBlocks.itemPipe, PipeTier.ELITE, OWNER), t2 = pipe(p2, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        TilePipe t3 = pipe(p3, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        mode(t1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(t2, ForgeDirection.WEST, FaceMode.INSERT);
        mode(t3, ForgeDirection.EAST, FaceMode.INSERT);
        t1.updateConnections();
    }

    void fluids() {
        e5 = at(1, 13);
        int[] p1 = at(2, 13), p2 = at(3, 13);
        f5 = at(4, 13);
        world.setBlock(e5[0], y, e5[2], ModBlocks.xpCrafter);
        world.setBlock(f5[0], y, f5[2], ModBlocks.xpCrafter);
        placed.add(e5);
        placed.add(f5);
        TileXpCrafter e = (TileXpCrafter) world.getTileEntity(e5[0], y, e5[2]);
        e.fill(new FluidStack(FluidRegistry.getFluid("xpjuice"), 10000), true);
        TilePipe t1 = pipe(p1, ModBlocks.fluidPipe, PipeTier.BASIC, OWNER), t2 = pipe(p2, ModBlocks.fluidPipe, PipeTier.BASIC, OWNER);
        mode(t1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(t2, ForgeDirection.EAST, FaceMode.INSERT);
    }

    void wireless() {
        wa = at(8, 1);
        wb = at(12, 1);
        wc = at(12, 4);
        int[] pa = at(9, 1), pb = at(11, 1), pc = at(11, 4);
        IInventory a = chest(wa);
        chest(wb);
        chest(wc);
        a.setInventorySlotContents(0, new ItemStack(Items.coal, 30));
        TilePipe ta = pipe(pa, ModBlocks.itemPipe, PipeTier.WIRELESS, OWNER), tb = pipe(pb, ModBlocks.itemPipe, PipeTier.WIRELESS, OWNER);
        TilePipe tc = pipe(pc, ModBlocks.itemPipe, PipeTier.WIRELESS, STRANGER);
        ta.face(ForgeDirection.WEST.ordinal()).tag = "001";
        tb.face(ForgeDirection.EAST.ordinal()).tag = "001";
        tc.face(ForgeDirection.EAST.ordinal()).tag = "001";
        tc.face(ForgeDirection.EAST.ordinal()).priority = 50; // would win if it were linked
        mode(ta, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(tb, ForgeDirection.EAST, FaceMode.INSERT);
        mode(tc, ForgeDirection.EAST, FaceMode.INSERT);
    }

    void lootSpawner() {
        ls = at(8, 8);
        int[] p1 = at(9, 8);
        lsOut = at(10, 8);
        world.setBlock(ls[0], y, ls[2], Blocks.mob_spawner);
        placed.add(ls);
        net.minecraft.tileentity.TileEntityMobSpawner v = (net.minecraft.tileentity.TileEntityMobSpawner) world.getTileEntity(ls[0], y,
            ls[2]);
        v.func_145881_a()
            .setEntityName("Cow");
        LootSpawnerTile.mark(v);
        LootSpawnerTile t = LootSpawners.swap(world, v);
        t.timer = 1;
        chest(lsOut);
        TilePipe p = pipe(p1, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        mode(p, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(p, ForgeDirection.EAST, FaceMode.INSERT);

        // 2.3.1: a spawner pushing into a pipe whose face on it is left on Provide (no Pull face anywhere); that face's
        // Deny list keeps leather back (it stays in the spawner).
        lsPush = at(6, 2);
        lsPushOut = at(6, 4);
        world.setBlock(lsPush[0], y, lsPush[2], Blocks.mob_spawner);
        placed.add(lsPush);
        net.minecraft.tileentity.TileEntityMobSpawner v2 = (net.minecraft.tileentity.TileEntityMobSpawner) world.getTileEntity(lsPush[0],
            y, lsPush[2]);
        v2.func_145881_a()
            .setEntityName("Cow");
        LootSpawnerTile.mark(v2);
        LootSpawners.swap(world, v2).timer = 1;
        chest(lsPushOut);
        TilePipe pp = pipe(at(6, 3), ModBlocks.itemPipe, PipeTier.BASIC, OWNER);
        mode(pp, ForgeDirection.NORTH, FaceMode.PROVIDE);
        if (pp.face(ForgeDirection.NORTH.ordinal()) != null) pp.face(ForgeDirection.NORTH.ordinal()).deny.filter[0] = new ItemStack(Items.leather);
        mode(pp, ForgeDirection.SOUTH, FaceMode.INSERT);

        idle = at(8, 12);
        idlePipe = at(9, 12);
        int[] dst = at(10, 12);
        chest(idle);
        chest(dst);
        TilePipe q = pipe(idlePipe, ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        mode(q, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(q, ForgeDirection.EAST, FaceMode.INSERT);
    }

    int[] stockSrc, stockDst, limSrc, limDst, exSrc, exDst, ppP, ppQ, ppR;

    /** Filter amounts, the existing-items kind and Push/Pull. */
    void filters() {
        // Pull keeping 5 iron in its chest.
        stockSrc = at(1, 15);
        stockDst = at(4, 15);
        chest(stockSrc).setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 20));
        chest(stockDst);
        TilePipe a = pipe(at(2, 15), ModBlocks.itemPipe, PipeTier.ELITE, OWNER), b = pipe(at(3, 15), ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        a.face(ForgeDirection.WEST.ordinal()).allow.filter[0] = new ItemStack(Items.iron_ingot);
        a.face(ForgeDirection.WEST.ordinal()).allow.count[0] = 5;
        mode(a, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(b, ForgeDirection.EAST, FaceMode.INSERT);
        // Push filling its chest up to 8 cobblestone.
        limSrc = at(12, 6);
        limDst = at(15, 6);
        chest(limSrc).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 30));
        chest(limDst);
        TilePipe c = pipe(at(13, 6), ModBlocks.itemPipe, PipeTier.ELITE, OWNER), d = pipe(at(14, 6), ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        d.face(ForgeDirection.EAST.ordinal()).allow.filter[0] = new ItemStack(Blocks.cobblestone);
        d.face(ForgeDirection.EAST.ordinal()).allow.count[0] = 8;
        mode(c, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(d, ForgeDirection.EAST, FaceMode.INSERT);
        // Push taking only what its chest already holds (existing items).
        exSrc = at(12, 10);
        exDst = at(15, 10);
        IInventory es = chest(exSrc);
        es.setInventorySlotContents(0, new ItemStack(Items.coal, 10));
        es.setInventorySlotContents(1, new ItemStack(Blocks.dirt, 10));
        chest(exDst).setInventorySlotContents(0, new ItemStack(Items.coal, 1));
        TilePipe e = pipe(at(13, 10), ModBlocks.itemPipe, PipeTier.ELITE, OWNER), g = pipe(at(14, 10), ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        g.face(ForgeDirection.EAST.ordinal()).allow.kind = FaceConfig.Kind.EXISTING;
        mode(e, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(g, ForgeDirection.EAST, FaceMode.INSERT);
        // Push/Pull: P's sticks go to Q (which takes only sticks); R's flint can only go into P, and stays there.
        ppP = at(12, 14);
        ppQ = at(15, 14);
        ppR = at(13, 15);
        chest(ppP).setInventorySlotContents(0, new ItemStack(Items.stick, 12));
        chest(ppQ);
        chest(ppR).setInventorySlotContents(0, new ItemStack(Items.flint, 6));
        TilePipe p = pipe(at(13, 14), ModBlocks.itemPipe, PipeTier.ELITE, OWNER), q = pipe(at(14, 14), ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        q.face(ForgeDirection.EAST.ordinal()).allow.filter[0] = new ItemStack(Items.stick);
        p.updateConnections();
        mode(p, ForgeDirection.WEST, FaceMode.PUSH_PULL);
        mode(p, ForgeDirection.SOUTH, FaceMode.EXTRACT);
        mode(q, ForgeDirection.EAST, FaceMode.INSERT);
    }

    void checkFilters() {
        SelfTest.check(count(inv(stockSrc), Items.iron_ingot) == 5 && count(inv(stockDst), Items.iron_ingot) == 15,
            "a Pull face with an amount of 5 leaves 5 iron in its chest (" + count(inv(stockSrc), Items.iron_ingot) + " left, "
                + count(inv(stockDst), Items.iron_ingot) + " moved)");
        Item cobble = Item.getItemFromBlock(Blocks.cobblestone);
        SelfTest.check(count(inv(limDst), cobble) == 8 && count(inv(limSrc), cobble) == 22,
            "a Push face with an amount of 8 fills its chest up to 8 cobblestone (" + count(inv(limDst), cobble) + ")");
        Item dirt = Item.getItemFromBlock(Blocks.dirt);
        SelfTest.check(count(inv(exDst), Items.coal) == 11 && count(inv(exDst), dirt) == 0 && count(inv(exSrc), dirt) == 10,
            "a Push face on existing items takes coal (its chest has some) but no dirt (" + count(inv(exDst), Items.coal) + " coal, "
                + count(inv(exDst), dirt) + " dirt)");
        SelfTest.check(count(inv(ppQ), Items.stick) == 12 && count(inv(ppP), Items.flint) == 6 && count(inv(ppR), Items.flint) == 0
            && count(inv(ppP), Items.stick) == 0, "Push/Pull: it pulled its sticks out to the Push face, and took the flint "
                + "another face pulled (nowhere else would take it) (" + count(inv(ppQ), Items.stick) + " sticks there, "
                + count(inv(ppP), Items.flint) + " flint in it)");
        FaceConfig f = new FaceConfig();
        f.allow.damage = FaceConfig.Damage.D50;
        ItemStack worn = new ItemStack(Items.iron_pickaxe), fresh = new ItemStack(Items.iron_pickaxe);
        worn.setItemDamage(200);
        f.allow.kind = FaceConfig.Kind.MODS;
        f.allow.filter[0] = new ItemStack(Blocks.dirt);
        boolean mods = f.passes(worn, null) && !f.passes(fresh, null);
        SelfTest.check(mods, "damaged 50%+ passes a worn pickaxe, not a fresh one; a mod list of dirt passes any vanilla item");
    }

    // ---- 2.1 ---------------------------------------------------------------------------------------------------------

    int y2, y4;
    int[] prSrc, prHi, prLo, prPull, rrA, rrB, ev1, ev2, ev3, evSrc, io1, io2, lsSrc, lsDst, kvSrc, kvPipe, ovSrc, ovDst,
        ovPipe, fvTank, fdTank, crChest, rqChest, wlChest, cfTank;
    FakePlayer tester;

    int[] at2(int dx, int dz) {
        return new int[] { bx + dx, y2, bz + dz };
    }

    int[] at4(int dx, int dz) {
        return new int[] { bx + dx, y4, bz + dz };
    }

    /** Pipes in a row from x0 to x1 (inclusive), on layer ly, row dz. */
    TilePipe[] row(int ly, int x0, int x1, int dz, Block block, int tier) {
        TilePipe[] out = new TilePipe[x1 - x0 + 1];
        for (int x = x0; x <= x1; x++) out[x - x0] = pipe(new int[] { bx + x, ly, bz + dz }, block, tier, OWNER);
        for (TilePipe t : out) t.updateConnections();
        return out;
    }

    /** An item holding a fluid, as a player would put in a filter (a filled bucket, or GregTech's display item). */
    static ItemStack fluidItem(String name) {
        net.minecraftforge.fluids.Fluid f = FluidRegistry.getFluid(name);
        if (f == null) return null;
        FluidStack fs = new FluidStack(f, 1000);
        ItemStack bucket = net.minecraftforge.fluids.FluidContainerRegistry.fillFluidContainer(fs, new ItemStack(Items.bucket));
        if (bucket != null) return bucket;
        try {
            return (ItemStack) Class.forName("gregtech.api.util.GTUtility")
                .getMethod("getFluidDisplay", FluidStack.class, boolean.class)
                .invoke(null, fs, false);
        } catch (Throwable e) {
            return null;
        }
    }

    // ---- 2.3.4: a Wireless pipe counts for its whole network ---------------------------------------------------------

    int y7;
    int[] jiSrc, jiDst, jiCtl, joSrc, joDst, jfSrc, jfDst, jgSrc, jgDst;

    int[] at7(int dx, int dz) {
        return new int[] { bx + dx, y7, bz + dz };
    }

    /**
     * Tagged faces on Basic or Elite pipes joined to a Wireless pipe link like Wireless faces: both ways round, for items
     * and for fluids (Adair77's steam line: a Wireless pipe under Elite pipes, the Elite face on Push with the tag). The
     * same tag on pipes with no Wireless pipe still only trades within its own pipes.
     */
    void wirelessJoined() {
        y7 = y + 7;
        Block ip = ModBlocks.itemPipe, fp = ModBlocks.fluidPipe;
        ForgeDirection w = ForgeDirection.WEST, e = ForgeDirection.EAST;
        List<TilePipe> rig = new ArrayList<>();
        // Items: a Wireless Pull face x1 -> a Basic pipe's Push face x1, joined to a Wireless pipe with no face of its own.
        jiSrc = at7(1, 1);
        jiDst = at7(6, 1);
        chest(jiSrc).setInventorySlotContents(0, new ItemStack(Items.coal, 20));
        chest(jiDst);
        TilePipe a = pipe(at7(2, 1), ip, PipeTier.WIRELESS, OWNER), b = pipe(at7(5, 1), ip, PipeTier.BASIC, OWNER);
        rig.add(a);
        rig.add(b);
        rig.add(pipe(at7(5, 2), ip, PipeTier.WIRELESS, OWNER));
        // The control: x1 on a Basic pipe with no Wireless pipe, first in line if it were linked. It gets nothing.
        jiCtl = at7(6, 7);
        chest(jiCtl);
        TilePipe c = pipe(at7(5, 7), ip, PipeTier.BASIC, OWNER);
        rig.add(c);
        // Items the other way: a Basic pipe's Pull face x2, joined to a Wireless pipe -> a far Wireless Push face x2.
        joSrc = at7(1, 4);
        joDst = at7(6, 4);
        chest(joSrc).setInventorySlotContents(0, new ItemStack(Items.flint, 15));
        chest(joDst);
        TilePipe d = pipe(at7(2, 4), ip, PipeTier.BASIC, OWNER), g = pipe(at7(5, 4), ip, PipeTier.WIRELESS, OWNER);
        rig.add(d);
        rig.add(g);
        rig.add(pipe(at7(2, 5), ip, PipeTier.WIRELESS, OWNER));
        // Fluids: a Wireless Pull face f1 -> an Elite pipe's Push face f1, Elite pipes down to a Wireless pipe.
        jfSrc = at7(1, 10);
        jfDst = at7(6, 10);
        crafter(jfSrc, 4000);
        crafter(jfDst, 0);
        TilePipe h = pipe(at7(2, 10), fp, PipeTier.WIRELESS, OWNER), k = pipe(at7(5, 10), fp, PipeTier.ELITE, OWNER);
        rig.add(h);
        rig.add(k);
        rig.add(pipe(at7(5, 11), fp, PipeTier.ELITE, OWNER));
        rig.add(pipe(at7(5, 12), fp, PipeTier.WIRELESS, OWNER));
        // Fluids the other way: an Elite pipe's Pull face f2, joined to a Wireless pipe -> a far Wireless Push face f2.
        jgSrc = at7(1, 14);
        jgDst = at7(6, 14);
        crafter(jgSrc, 3000);
        crafter(jgDst, 0);
        TilePipe m = pipe(at7(2, 14), fp, PipeTier.ELITE, OWNER), n = pipe(at7(5, 14), fp, PipeTier.WIRELESS, OWNER);
        rig.add(m);
        rig.add(n);
        rig.add(pipe(at7(2, 15), fp, PipeTier.WIRELESS, OWNER));
        for (TilePipe p : rig) if (p != null) p.updateConnections(); // every pipe is placed now: join them all
        Object[][] faces = { { a, w, "x1", FaceMode.EXTRACT }, { b, e, "x1", FaceMode.INSERT }, { c, e, "x1", FaceMode.INSERT },
            { d, w, "x2", FaceMode.EXTRACT }, { g, e, "x2", FaceMode.INSERT }, { h, w, "f1", FaceMode.EXTRACT },
            { k, e, "f1", FaceMode.INSERT }, { m, w, "f2", FaceMode.EXTRACT }, { n, e, "f2", FaceMode.INSERT } };
        for (Object[] f : faces) {
            TilePipe p = (TilePipe) f[0];
            ForgeDirection side = (ForgeDirection) f[1];
            if (p == null || p.face(side.ordinal()) == null) {
                SelfTest.check(false, "the joined-Wireless rig's pipe touches its block on " + side);
                continue;
            }
            p.face(side.ordinal()).tag = (String) f[2];
            mode(p, side, (FaceMode) f[3]);
        }
        if (c != null && c.face(e.ordinal()) != null) c.face(e.ordinal()).priority = 50; // would win if it were linked
    }

    void checkWirelessJoined() {
        int coal = count(inv(jiDst), Items.coal), ctl = count(inv(jiCtl), Items.coal);
        SelfTest.check(coal == 20 && ctl == 0 && count(inv(jiSrc), Items.coal) == 0,
            "items: a Wireless Pull face x1 feeds a Basic pipe's Push face x1 joined to a Wireless pipe; x1 on pipes with no "
                + "Wireless pipe gets nothing, though it is first in line (" + coal + " / " + ctl + ")");
        int flint = count(inv(joDst), Items.flint);
        SelfTest.check(flint == 15 && count(inv(joSrc), Items.flint) == 0,
            "items the other way: a Basic pipe's Pull face x2 joined to a Wireless pipe feeds a far Wireless Push face (" + flint
                + " flint)");
        int f1s = mB(world.getTileEntity(jfSrc[0], y7, jfSrc[2])), f1d = mB(world.getTileEntity(jfDst[0], y7, jfDst[2]));
        SelfTest.check(f1d == 4000 && f1s == 0,
            "fluids: a Wireless Pull face f1 feeds an Elite pipe's Push face f1, Elite pipes joined to a Wireless pipe (" + f1s
                + " left, " + f1d + " mB there)");
        int f2s = mB(world.getTileEntity(jgSrc[0], y7, jgSrc[2])), f2d = mB(world.getTileEntity(jgDst[0], y7, jgDst[2]));
        SelfTest.check(f2d == 3000 && f2s == 0,
            "fluids the other way: an Elite pipe's Pull face f2 joined to a Wireless pipe feeds a far Wireless Push face (" + f2s
                + " left, " + f2d + " mB there)");
    }

    TileXpCrafter crafter(int[] p, int mB) {
        world.setBlock(p[0], p[1], p[2], ModBlocks.xpCrafter);
        placed.add(p);
        TileXpCrafter t = (TileXpCrafter) world.getTileEntity(p[0], p[1], p[2]);
        if (mB > 0) t.fill(new FluidStack(FluidRegistry.getFluid("xpjuice"), mB), true);
        return t;
    }

    static int mB(TileEntity te) {
        return te instanceof TileXpCrafter && ((TileXpCrafter) te).getFluid() != null ? ((TileXpCrafter) te).getFluid().amount : 0;
    }

    void v21() {
        y2 = y + 2;
        y4 = y + 4;
        tester = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000c3"), "PipeTester21")) {

            @Override
            public void addChatMessage(net.minecraft.util.IChatComponent message) {}
        };
        tester.setPosition(bx + 8.5, y4, bz + 0.5);
        Block ip = ModBlocks.itemPipe, fp = ModBlocks.fluidPipe;
        int n = ForgeDirection.NORTH.ordinal(), w = ForgeDirection.WEST.ordinal(), e = ForgeDirection.EAST.ordinal();
        // Priority: all 40 go to the priority-10 chest, none to the priority-0 one (2.0 sent some to it).
        prSrc = at2(1, 2);
        prHi = at2(3, 1);
        prLo = at2(5, 1);
        chest(prSrc).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 40));
        chest(prHi);
        chest(prLo);
        TilePipe[] pr = row(y2, 2, 5, 2, ip, PipeTier.ELITE);
        prPull = at2(2, 2);
        pr[0].face(w).amount = 4; // ten transfers: 2.0's rotation would have sent every other one to priority 0
        mode(pr[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        pr[1].face(n).priority = 10;
        mode(pr[1], ForgeDirection.NORTH, FaceMode.INSERT);
        mode(pr[3], ForgeDirection.NORTH, FaceMode.INSERT);
        // Round robin, 4 a transfer: two equal chests take turns.
        chest(at2(1, 6)).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 20));
        rrA = at2(3, 5);
        rrB = at2(5, 5);
        chest(rrA);
        chest(rrB);
        TilePipe[] rr = row(y2, 2, 5, 6, ip, PipeTier.ELITE);
        rr[0].face(w).amount = 4;
        mode(rr[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(rr[1], ForgeDirection.NORTH, FaceMode.INSERT);
        mode(rr[3], ForgeDirection.NORTH, FaceMode.INSERT);
        // Even split: one transfer of 64 over three chests is 22, 21, 21.
        evSrc = at2(1, 10);
        chest(evSrc).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
        ev1 = at2(3, 9);
        ev2 = at2(5, 9);
        ev3 = at2(7, 9);
        chest(ev1);
        chest(ev2);
        chest(ev3);
        TilePipe[] ev = row(y2, 2, 7, 10, ip, PipeTier.ELITE);
        ev[0].face(w).spread = FaceConfig.Spread.EVEN;
        mode(ev[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(ev[1], ForgeDirection.NORTH, FaceMode.INSERT);
        mode(ev[3], ForgeDirection.NORTH, FaceMode.INSERT);
        mode(ev[5], ForgeDirection.NORTH, FaceMode.INSERT);
        // In order, 5 a transfer: the first chest takes everything.
        chest(at2(1, 14)).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 30));
        io1 = at2(3, 13);
        io2 = at2(5, 13);
        chest(io1);
        chest(io2);
        TilePipe[] io = row(y2, 2, 5, 14, ip, PipeTier.ELITE);
        io[0].face(w).amount = 5;
        io[0].face(w).spread = FaceConfig.Spread.IN_ORDER;
        mode(io[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(io[1], ForgeDirection.NORTH, FaceMode.INSERT);
        mode(io[3], ForgeDirection.NORTH, FaceMode.INSERT);
        // Three lists at once: allow cobble and dirt, deny dirt, void sticks and dirt.
        lsSrc = at2(9, 2);
        IInventory ls = chest(lsSrc);
        ls.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 10));
        ls.setInventorySlotContents(1, new ItemStack(Blocks.dirt, 10));
        ls.setInventorySlotContents(2, new ItemStack(Items.stick, 10));
        lsDst = at2(12, 1);
        chest(lsDst);
        TilePipe[] lp = row(y2, 10, 13, 2, ip, PipeTier.ELITE);
        FaceConfig lf = lp[0].face(w);
        lf.allow.filter[0] = new ItemStack(Blocks.cobblestone);
        lf.allow.filter[1] = new ItemStack(Blocks.dirt);
        lf.deny.filter[0] = new ItemStack(Blocks.dirt);
        lf.voids.filter[0] = new ItemStack(Items.stick);
        lf.voids.filter[1] = new ItemStack(Blocks.dirt);
        mode(lp[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(lp[2], ForgeDirection.NORTH, FaceMode.INSERT);
        // Void on a Pull face, keeping 40: no destination at all.
        kvSrc = at2(9, 6);
        IInventory kv = chest(kvSrc);
        kv.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
        kv.setInventorySlotContents(1, new ItemStack(Blocks.cobblestone, 36));
        kvPipe = at2(10, 6);
        TilePipe kp = pipe(kvPipe, ip, PipeTier.ELITE, OWNER);
        kp.face(w).voids.filter[0] = new ItemStack(Blocks.cobblestone);
        kp.face(w).voids.count[0] = 40;
        mode(kp, ForgeDirection.WEST, FaceMode.EXTRACT);
        // Void on a Push face: its chest has room for 10 cobblestone; the other 54 sent there are destroyed.
        ovSrc = at2(9, 10);
        chest(ovSrc).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
        ovDst = at2(12, 9);
        IInventory od = chest(ovDst);
        for (int i = 0; i < 26; i++) od.setInventorySlotContents(i, new ItemStack(Blocks.dirt, 64));
        od.setInventorySlotContents(26, new ItemStack(Blocks.cobblestone, 54));
        TilePipe[] op = row(y2, 10, 12, 10, ip, PipeTier.ELITE);
        ovPipe = at2(12, 10);
        mode(op[0], ForgeDirection.WEST, FaceMode.EXTRACT);
        op[2].face(n).voids.filter[0] = new ItemStack(Blocks.cobblestone);
        mode(op[2], ForgeDirection.NORTH, FaceMode.INSERT);
        // Fluids: void liquid XP keeping 4,000 mB; Deny wins over Void.
        ItemStack xp = fluidItem("xpjuice");
        SelfTest.check(xp != null && Fluids.of(xp) != null, "an item naming liquid XP for the fluid filters (" + xp + ")");
        fvTank = at2(9, 14);
        fdTank = at2(12, 14);
        crafter(fvTank, 10000);
        crafter(fdTank, 10000);
        TilePipe fv = pipe(at2(10, 14), fp, PipeTier.ELITE, OWNER), fd = pipe(at2(13, 14), fp, PipeTier.ELITE, OWNER);
        if (xp != null) {
            fv.face(w).voids.filter[0] = xp.copy();
            fv.face(w).voids.count[0] = 4000;
            fd.face(w).deny.filter[0] = xp.copy();
            fd.face(w).voids.filter[0] = xp.copy();
        }
        mode(fv, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(fd, ForgeDirection.WEST, FaceMode.EXTRACT);
        // Creative pipes: Pull fills a chest; Provide stocks a Request face; tagged, a far Wireless face; fluid.
        crChest = at4(3, 2);
        chest(crChest);
        TilePipe cr = pipe(at4(2, 2), ip, PipeTier.CREATIVE, OWNER);
        FaceConfig src = cr.face(TilePipe.SOURCE);
        src.allow.filter[0] = new ItemStack(Items.iron_ingot);
        src.mode = FaceMode.EXTRACT;
        mode(cr, ForgeDirection.EAST, FaceMode.INSERT);
        rqChest = at4(4, 6);
        chest(rqChest);
        TilePipe cq = pipe(at4(2, 6), ip, PipeTier.CREATIVE, OWNER), rq1 = pipe(at4(3, 6), ip, PipeTier.ELITE, OWNER);
        cq.updateConnections();
        cq.face(TilePipe.SOURCE).allow.filter[0] = new ItemStack(Items.gold_ingot);
        cq.face(TilePipe.SOURCE).mode = FaceMode.PROVIDE;
        cq.faceChanged();
        rq1.face(e).allow.filter[0] = new ItemStack(Items.gold_ingot);
        rq1.face(e).allow.count[0] = 100;
        mode(rq1, ForgeDirection.EAST, FaceMode.REQUEST);
        wlChest = at4(7, 10);
        chest(wlChest);
        TilePipe cw = pipe(at4(2, 10), ip, PipeTier.CREATIVE, OWNER), ww = pipe(at4(6, 10), ip, PipeTier.WIRELESS, OWNER);
        cw.face(TilePipe.SOURCE).allow.filter[0] = new ItemStack(Items.diamond);
        cw.face(TilePipe.SOURCE).tag = "c21";
        cw.face(TilePipe.SOURCE).mode = FaceMode.EXTRACT;
        cw.faceChanged();
        ww.face(e).tag = "c21";
        mode(ww, ForgeDirection.EAST, FaceMode.INSERT);
        cfTank = at4(3, 14);
        crafter(cfTank, 0);
        TilePipe cf = pipe(at4(2, 14), fp, PipeTier.CREATIVE, OWNER);
        if (xp != null) cf.face(TilePipe.SOURCE).allow.filter[0] = xp.copy();
        cf.face(TilePipe.SOURCE).mode = FaceMode.EXTRACT;
        mode(cf, ForgeDirection.EAST, FaceMode.INSERT);
        // Who may touch a creative pipe.
        SelfTest.check(!PipeClicks.may(cr, tester), "a survival player who isn't an op can't change a creative pipe");
        tester.capabilities.isCreativeMode = true;
        SelfTest.check(PipeClicks.may(cr, tester), "a player in creative mode can");
        tester.capabilities.isCreativeMode = false;
        SelfTest.check(ip.getBlockHardness(world, cr.xCoord, cr.yCoord, cr.zCoord) < 0
            && ip.getBlockHardness(world, rq1.xCoord, rq1.yCoord, rq1.zCoord) > 0,
            "a creative pipe can't be broken in survival; other pipes can");
        // The wrench on a pipe's centre: the pipe and what its face held go to the player.
        int[] wp = at4(10, 2);
        chest(at4(11, 2));
        TilePipe wt = pipe(wp, ip, PipeTier.SUPERIOR, OWNER);
        wt.face(e).heldItems.add(new ItemStack(Items.flint, 5));
        PipeClicks.pickUp(wt, tester, null);
        int pipes = 0, flint = 0;
        for (ItemStack s : tester.inventory.mainInventory) {
            if (s == null) continue;
            if (s.getItem() == Item.getItemFromBlock(ip) && s.getItemDamage() == PipeTier.SUPERIOR) pipes += s.stackSize;
            if (s.getItem() == Items.flint) flint += s.stackSize;
        }
        SelfTest.check(world.isAirBlock(wp[0], wp[1], wp[2]) && pipes == 1 && flint == 5,
            "the wrench on a pipe's centre picks it up (" + pipes + " Superior pipe) with what it held back (" + flint + " flint)");
        // The Pipe Configurator: copy a face, paste it onto another pipe's face; the other keeps what it holds.
        chest(at4(11, 6));
        chest(at4(14, 6));
        TilePipe k1 = pipe(at4(10, 6), ip, PipeTier.ELITE, OWNER), k2 = pipe(at4(13, 6), ip, PipeTier.ULTIMATE, OWNER);
        FaceConfig a = k1.face(e);
        a.mode = FaceMode.EXTRACT;
        a.priority = 3;
        a.tag = "cf";
        a.spread = FaceConfig.Spread.EVEN;
        a.allow.filter[0] = new ItemStack(Items.iron_ingot);
        a.deny.filter[0] = new ItemStack(Blocks.dirt);
        a.voids.filter[0] = new ItemStack(Items.stick);
        a.voids.count[0] = 7;
        k1.faceChanged();
        k2.face(e).heldItems.add(new ItemStack(Items.flint, 3));
        ItemStack tool = new ItemStack(ModBlocks.pipeConfigurator);
        ItemPipeConfigurator.use(tool, tester, k1, e, true);
        ItemPipeConfigurator.use(tool, tester, k2, e, false);
        FaceConfig b = k2.face(e);
        boolean same = b.mode == FaceMode.EXTRACT && b.priority == 3 && "cf".equals(b.tag) && b.spread == FaceConfig.Spread.EVEN
            && b.allow.filter[0] != null && b.allow.filter[0].getItem() == Items.iron_ingot && b.deny.filter[0] != null
            && b.voids.filter[0] != null && b.voids.count[0] == 7 && b.heldItems.size() == 1 && b.heldItems.get(0).stackSize == 3;
        SelfTest.check(same, "the Pipe Configurator copies a face (mode, priority, tag, spread, all three lists) onto another; "
            + "that one keeps what it holds");
        mode(k1, ForgeDirection.EAST, FaceMode.OFF);
        mode(k2, ForgeDirection.EAST, FaceMode.OFF);
        // 2.0 filters read in: a whitelist is the Allow list, a blacklist the Deny list (the wear setting on both).
        net.minecraft.nbt.NBTTagCompound old = new net.minecraft.nbt.NBTTagCompound();
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        net.minecraft.nbt.NBTTagCompound entry = new ItemStack(Blocks.dirt).writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        entry.setByte("Slot", (byte) 0);
        list.appendTag(entry);
        old.setTag("Filter", list);
        old.setBoolean("Blacklist", true);
        old.setByte("Damage", (byte) FaceConfig.Damage.ANY.ordinal());
        FaceConfig black = FaceConfig.read(old);
        old.removeTag("Blacklist");
        FaceConfig white = FaceConfig.read(old);
        ItemStack dirt = new ItemStack(Blocks.dirt), cobble = new ItemStack(Blocks.cobblestone);
        SelfTest.check(!black.passes(dirt, null) && black.passes(cobble, null) && black.deny.hasEntries() && !black.allow.hasEntries()
            && white.passes(dirt, null) && !white.passes(cobble, null) && white.allow.hasEntries() && !white.deny.hasEntries(),
            "a 2.0 blacklist reads as the Deny list, a whitelist as the Allow list");
        // The new tiers.
        SelfTest.check(PipeTier.maxItems(PipeTier.SUPERIOR) == 160 && PipeTier.maxItems(PipeTier.ULTIMATE) == 320
            && PipeTier.maxFluid(PipeTier.SUPERIOR) == 40000 && PipeTier.maxFluid(PipeTier.ULTIMATE) == 160000
            && PipeTier.interval(PipeTier.ULTIMATE) == 5 && PipeTier.maxItems(PipeTier.WIRELESS) == 64,
            "Superior moves 160 items / 40,000 mB and Ultimate 320 / 160,000 every 5 ticks; Wireless stays at Elite's 64");
        int found = 0;
        for (Object r : net.minecraft.item.crafting.CraftingManager.getInstance()
            .getRecipeList()) {
            ItemStack out = ((net.minecraft.item.crafting.IRecipe) r).getRecipeOutput();
            if (out == null) continue;
            if (out.getItem() == Item.getItemFromBlock(ip) && (out.getItemDamage() == PipeTier.SUPERIOR || out.getItemDamage() == PipeTier.ULTIMATE)) found++;
            if (out.getItem() == ModBlocks.pipeConfigurator) found++;
            if (out.getItemDamage() == PipeTier.CREATIVE && out.getItem() == Item.getItemFromBlock(ip)) found -= 100;
        }
        SelfTest.check(found == 3, "recipes for Superior and Ultimate item pipes and the Pipe Configurator, none for creative ("
            + found + ")");
    }

    void checkV21() {
        Item cobble = Item.getItemFromBlock(Blocks.cobblestone), dirt = Item.getItemFromBlock(Blocks.dirt);
        SelfTest.check(count(inv(prHi), cobble) == 40 && count(inv(prLo), cobble) == 0,
            "priority: all 40 go to the priority-10 chest, none to priority 0 (" + count(inv(prHi), cobble) + " / "
                + count(inv(prLo), cobble) + ")");
        int ra = count(inv(rrA), cobble), rb = count(inv(rrB), cobble);
        SelfTest.check(ra + rb == 20 && ra > 0 && rb > 0 && Math.abs(ra - rb) <= 4,
            "round robin: two equal chests take turns (" + ra + " / " + rb + ")");
        int[] even = { count(inv(ev1), cobble), count(inv(ev2), cobble), count(inv(ev3), cobble) };
        java.util.Arrays.sort(even);
        SelfTest.check(even[0] == 21 && even[1] == 21 && even[2] == 22 && count(inv(evSrc), cobble) == 0,
            "even split: one transfer of 64 shares 22/21/21 (" + even[0] + " " + even[1] + " " + even[2] + ")");
        SelfTest.check(count(inv(io1), cobble) == 30 && count(inv(io2), cobble) == 0,
            "in order: the first chest takes all 30 (" + count(inv(io1), cobble) + " / " + count(inv(io2), cobble) + ")");
        IInventory ls = inv(lsSrc), ld = inv(lsDst);
        SelfTest.check(count(ld, cobble) == 10 && count(ls, cobble) == 0 && count(ls, dirt) == 10 && count(ld, dirt) == 0
            && count(ls, Items.stick) == 0 && count(ld, Items.stick) == 0,
            "three lists: allowed cobblestone moved, denied dirt neither moved nor voided, sticks voided (" + count(ld, cobble)
                + " cobble there, " + count(ls, dirt) + " dirt left, " + (count(ls, Items.stick) + count(ld, Items.stick)) + " sticks)");
        long voided = PipeInfo.snapshot(world, kvPipe[0], kvPipe[1], kvPipe[2]) == null ? -1
            : pipeAt(kvPipe).face(ForgeDirection.WEST.ordinal()).flow.voidedPerMinute(world.getTotalWorldTime());
        SelfTest.check(count(inv(kvSrc), cobble) == 40 && voided == 60,
            "a Pull face's Void list with an amount of 40 destroys the other 60 cobblestone, with nowhere to send anything ("
                + count(inv(kvSrc), cobble) + " left, " + voided + " voided)");
        long over = pipeAt(ovPipe).face(ForgeDirection.NORTH.ordinal()).flow.voidedPerMinute(world.getTotalWorldTime());
        SelfTest.check(count(inv(ovDst), cobble) == 64 && count(inv(ovSrc), cobble) == 0 && over == 54,
            "a Push face's Void list: its chest is filled, the 54 that don't fit are destroyed (" + count(inv(ovDst), cobble)
                + " there, " + over + " voided)");
        SelfTest.check(mB(world.getTileEntity(fvTank[0], fvTank[1], fvTank[2])) == 4000,
            "a fluid Pull face voids liquid XP down to its 4,000 mB (" + mB(world.getTileEntity(fvTank[0], fvTank[1], fvTank[2])) + " mB)");
        SelfTest.check(mB(world.getTileEntity(fdTank[0], fdTank[1], fdTank[2])) == 10000,
            "Deny wins over Void for fluids too (" + mB(world.getTileEntity(fdTank[0], fdTank[1], fdTank[2])) + " mB kept)");
        SelfTest.check(count(inv(crChest), Items.iron_ingot) == 27 * 64,
            "a creative pipe (Pull) fills a chest with iron (" + count(inv(crChest), Items.iron_ingot) + ")");
        SelfTest.check(count(inv(rqChest), Items.gold_ingot) == 100,
            "a creative pipe (Provide) keeps a Request face's chest at 100 gold (" + count(inv(rqChest), Items.gold_ingot) + ")");
        SelfTest.check(count(inv(wlChest), Items.diamond) == 27 * 64,
            "a creative pipe tagged c21 fills a far Wireless pipe's chest with the same tag (" + count(inv(wlChest), Items.diamond) + ")");
        SelfTest.check(mB(world.getTileEntity(cfTank[0], cfTank[1], cfTank[2])) > 0,
            "a creative fluid pipe fills an XP crafter (" + mB(world.getTileEntity(cfTank[0], cfTank[1], cfTank[2])) + " mB)");
        net.minecraft.nbt.NBTTagCompound info = PipeInfo.snapshot(world, prPull[0], prPull[1], prPull[2]);
        List<String> atFace = info == null ? new ArrayList<>() : com.valier.tweaks.client.PipeHud.lines(info, ForgeDirection.WEST.ordinal());
        List<String> atCore = info == null ? new ArrayList<>() : com.valier.tweaks.client.PipeHud.lines(info, 6);
        String face = String.join(" | ", atFace).replaceAll("\u00a7.", ""), core = String.join(" | ", atCore).replaceAll("\u00a7.", "");
        SelfTest.check(face.contains("Pull") && face.contains("Moved: 40/min") && face.contains("Cobblestone"),
            "WAILA on a Pull face: its mode, 40 moved this minute, the last thing (" + face + ")");
        SelfTest.check(core.contains("Network: 4 pipes") && core.contains("Flowing") && core.contains("Cobblestone"),
            "WAILA on the centre: the network and what flows through it (" + core + ")");
    }

    // ---- later -------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 2, (bz >> 4) + 2);
        if (++ticks < 80) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            check();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] pipes threw", t);
            SelfTest.check(false, "the pipe checks ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    void check() {
        IInventory a = inv(a1), b = inv(b1);
        SelfTest.check(count(a, Item.getItemFromBlock(Blocks.cobblestone)) == 0 && count(a, Items.iron_ingot) == 0
            && count(b, Item.getItemFromBlock(Blocks.cobblestone)) == 20 && count(b, Items.iron_ingot) == 5,
            "Extract to Insert: all 20 cobblestone and 5 iron went across, none lost or made (left " + count(a, Item.getItemFromBlock(Blocks.cobblestone))
                + "+" + count(a, Items.iron_ingot) + ", there " + count(b, Item.getItemFromBlock(Blocks.cobblestone)) + "+"
                + count(b, Items.iron_ingot) + ")");
        IInventory a2i = inv(a2), b2i = inv(b2);
        SelfTest.check(count(a2i, Item.getItemFromBlock(Blocks.cobblestone)) == 20 && count(b2i, Items.iron_ingot) == 5
            && count(b2i, Item.getItemFromBlock(Blocks.cobblestone)) == 0, "a whitelist of iron moves only the iron ("
                + count(b2i, Items.iron_ingot) + " iron, " + count(b2i, Item.getItemFromBlock(Blocks.cobblestone)) + " cobblestone)");
        IInventory c = inv(c3), d = inv(d3);
        SelfTest.check(count(d, Items.iron_ingot) == 10 && count(c, Items.iron_ingot) == 54,
            "a Request face keeps 10 iron in its chest, taken from a Provide face, and no more (" + count(d, Items.iron_ingot)
                + " there, " + count(c, Items.iron_ingot) + " left)");
        IInventory big = inv(dc), out = inv(out4);
        SelfTest.check(count(out, Items.flint) == 12 && count(big, Items.flint) == 0,
            "a double chest is one inventory: its flint goes to the other chest, never round to its own other half ("
                + count(out, Items.flint) + " there)");
        TileXpCrafter e = (TileXpCrafter) world.getTileEntity(e5[0], y, e5[2]), f = (TileXpCrafter) world.getTileEntity(f5[0], y, f5[2]);
        int ea = e == null || e.getFluid() == null ? 0 : e.getFluid().amount, fa = f == null || f.getFluid() == null ? 0 : f.getFluid().amount;
        SelfTest.check(ea + fa == 10000 && fa >= 3000, "a fluid pipe moves liquid XP from one crafter to the other (1,000 mB a "
            + "second on Basic), none lost (" + ea + " + " + fa + " mB)");
        IInventory wbi = inv(wb), wci = inv(wc);
        SelfTest.check(count(wbi, Items.coal) == 30 && count(wci, Items.coal) == 0 && count(inv(wa), Items.coal) == 0,
            "Wireless pipes tagged 001 are linked (same owner); another owner's 001 gets nothing, though it is first in line ("
                + count(wbi, Items.coal) + " / " + count(wci, Items.coal) + ")");
        IInventory lo = inv(lsOut);
        int loot = 0;
        for (int i = 0; lo != null && i < lo.getSizeInventory(); i++) if (lo.getStackInSlot(i) != null) loot += lo.getStackInSlot(i).stackSize;
        SelfTest.check(loot > 0, "a loot spawner with only a Valier pipe pulling from it makes loot, and the pipe takes it to the "
            + "chest (" + loot + " items)");
        IInventory po = inv(lsPushOut);
        TileEntity pt = world.getTileEntity(lsPush[0], y, lsPush[2]);
        int beef = count(po, Items.beef) + count(po, Items.cooked_beef), leather = count(po, Items.leather);
        SelfTest.check(beef > 0 && leather == 0 && pt instanceof LootSpawnerTile && count((IInventory) pt, Items.beef) == 0,
            "a loot spawner pushes its loot into a Valier pipe whose face on it is left on Provide, on to the chest on its Push "
                + "face; leather, on that face's Deny list, stays in the spawner (" + beef + " beef, " + leather + " leather in the chest)");
        int sleeping = PipeNetworks.idleOf(world, idlePipe[0], y, idlePipe[2], ForgeDirection.WEST.ordinal());
        SelfTest.check(sleeping >= 3, "an Extract face with nothing to take sleeps longer each time (" + sleeping + " empty turns)");
        PipeNetwork net = PipeNetworks.networkAt(world, idlePipe[0], y, idlePipe[2]);
        SelfTest.check(net != null && net.pipeCount() == 1 && net.faceCount() == 2 && !net.isTooBig(),
            "the network knows its pipe and two faces");
        checkFilters();
        checkV21();
        checkWirelessJoined();
        List<String> stats = PipeNetworks.stats();
        SelfTest.check(stats.size() >= 2 && stats.get(0)
            .contains("pipe networks"), "/valiertweaks pipes answers (" + stats.get(0) + ")");
        for (String s : stats) SelfTest.log(s);
    }

    void clear() {
        for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) for (int yy = y; yy < y + 10; yy++) {
            TileEntity te = world.getTileEntity(x, yy, z);
            if (te instanceof IInventory && !(te instanceof TileXpCrafter)) {
                IInventory i = (IInventory) te;
                for (int s = 0; s < i.getSizeInventory(); s++) i.setInventorySlotContents(s, null);
            }
            if (te instanceof LootSpawnerTile) LootSpawners.empty((LootSpawnerTile) te);
            if (te instanceof TileXpCrafter) ((TileXpCrafter) te).moving = true;
            if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
        }
        placed.clear();
        for (Object o : world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(bx - 2, y - 2, bz - 2, bx + 18, y + 6, bz + 18)))
            ((net.minecraft.entity.Entity) o).setDead();
    }
}
