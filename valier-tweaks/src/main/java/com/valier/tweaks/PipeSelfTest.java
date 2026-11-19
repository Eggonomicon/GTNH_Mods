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
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
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
 * </ul>
 */
final class PipeSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 8) << 4, bz = (SelfTest.CZ + 2) << 4;
    int y, ticks;
    final List<int[]> placed = new ArrayList<>();
    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    // The rigs, checked later.
    int[] a1, b1, a2, b2, c3, d3, dc, out4, e5, f5, wa, wb, wc, ls, lsOut, idle;
    int[] idlePipe;

    void start() {
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
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
        t1.face(ForgeDirection.WEST.ordinal()).filter[0] = new ItemStack(Items.iron_ingot);
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
        r.filter[0] = new ItemStack(Items.iron_ingot);
        r.count[0] = 10;
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
        a.face(ForgeDirection.WEST.ordinal()).filter[0] = new ItemStack(Items.iron_ingot);
        a.face(ForgeDirection.WEST.ordinal()).count[0] = 5;
        mode(a, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(b, ForgeDirection.EAST, FaceMode.INSERT);
        // Push filling its chest up to 8 cobblestone.
        limSrc = at(12, 6);
        limDst = at(15, 6);
        chest(limSrc).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 30));
        chest(limDst);
        TilePipe c = pipe(at(13, 6), ModBlocks.itemPipe, PipeTier.ELITE, OWNER), d = pipe(at(14, 6), ModBlocks.itemPipe, PipeTier.ELITE, OWNER);
        d.face(ForgeDirection.EAST.ordinal()).filter[0] = new ItemStack(Blocks.cobblestone);
        d.face(ForgeDirection.EAST.ordinal()).count[0] = 8;
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
        g.face(ForgeDirection.EAST.ordinal()).kind = FaceConfig.Kind.EXISTING;
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
        q.face(ForgeDirection.EAST.ordinal()).filter[0] = new ItemStack(Items.stick);
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
        f.damage = FaceConfig.Damage.D50;
        ItemStack worn = new ItemStack(Items.iron_pickaxe), fresh = new ItemStack(Items.iron_pickaxe);
        worn.setItemDamage(200);
        f.kind = FaceConfig.Kind.MODS;
        f.filter[0] = new ItemStack(Blocks.dirt);
        boolean mods = f.passes(worn, null) && !f.passes(fresh, null);
        SelfTest.check(mods, "damaged 50%+ passes a worn pickaxe, not a fresh one; a mod list of dirt passes any vanilla item");
    }

    // ---- later -------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        int sleeping = PipeNetworks.idleOf(world, idlePipe[0], y, idlePipe[2], ForgeDirection.WEST.ordinal());
        SelfTest.check(sleeping >= 3, "an Extract face with nothing to take sleeps longer each time (" + sleeping + " empty turns)");
        PipeNetwork net = PipeNetworks.networkAt(world, idlePipe[0], y, idlePipe[2]);
        SelfTest.check(net != null && net.pipeCount() == 1 && net.faceCount() == 2 && !net.isTooBig(),
            "the network knows its pipe and two faces");
        checkFilters();
        List<String> stats = PipeNetworks.stats();
        SelfTest.check(stats.size() >= 2 && stats.get(0)
            .contains("pipe networks"), "/valiertweaks pipes answers (" + stats.get(0) + ")");
        for (String s : stats) SelfTest.log(s);
    }

    void clear() {
        for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) for (int yy = y; yy < y + 3; yy++) {
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
