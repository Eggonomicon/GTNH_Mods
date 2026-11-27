package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest barrelcontroller} (2.4.0): the Valier Barrel Controller.
 * <ul>
 * <li>Routing: it reaches the barrels chained to it (not one a block apart); items go to the barrel holding them; an
 * item no barrel holds is refused, or with "fill empty barrels" starts an empty barrel; a barrel given a new item by
 * hand is found; NBT tells enchanted books apart; linked barrels count as one store.</li>
 * <li>Changes: a barrel placed onto the chain joins, a broken one leaves; one placed 80 blocks away costs nothing.</li>
 * <li>Slots: one output per store, a hopper under it pulls, a stack changed in place is followed.</li>
 * <li>Clicks and the menu; AE2's view (everything, in and out, and changes told to a listening storage bus).</li>
 * <li>Valier pipes (checked 100 ticks later): a Request for charcoal from a controller's Provide face (and not coal),
 * the same over Wireless pipes, a filtered Pull, a Push into it (what no barrel holds stays out), a Pull of everything,
 * a Request ignoring damage.</li>
 * <li>Cost, with 1,024 barrels each holding another item: finding the network, putting in, the pipes' lookup, an item
 * no barrel holds, the pipes' list of everything, and a hopper's look over every slot.</li>
 * </ul>
 */
public final class BarrelControllerSelfTest { // public: Forge calls its tick handler from another class loader

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 20) << 4, bz = (SelfTest.CZ + 20) << 4;
    final int px = (SelfTest.CX + 24) << 4; // the perf check's floor of barrels
    int y, ticks;
    final String[] said = new String[1];
    FakePlayer player;
    final List<int[]> placed = new ArrayList<>();

    // Routing (reused by the later steps).
    TileBarrelController r;
    TileBarrel rIron, rGold, rEmpty, rApart, rDia;
    int x0, z0;
    // AE2, finished in the tick handler.
    TileBarrelController aeCtrl;
    TileBarrel aeIron;
    Object aeProbe;
    // The pipe rigs.
    TileBarrelController reqC, wlC, pullC, pushC, allC, dmgC;
    IInventory reqChest, wlChest, pullChest, pushChest, allChest, dmgChest;
    TileBarrel pushIron, pushGold, pushEmpty;

    public void start() {
        for (int cx = -1; cx <= 2; cx++) for (int cz = -1; cz <= 2; cz++) {
            world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
            world.theChunkProviderServer.loadChunk((px >> 4) + cx, (bz >> 4) + cz);
        }
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        player = new FakePlayer(world, new GameProfile(BarrelSelfTest.OWNER, "ControllerTester")) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said[0] = message.getUnformattedText();
            }
        };
        player.setPosition(bx + 8.5, y, bz + 8.5);
        try {
            routing();
            changes();
            slots();
            nbt();
            linked();
            clicks();
            menu();
            ae2();
            perf();
            pipes();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] barrelcontroller threw", t);
            SelfTest.check(false, "barrelcontroller ran without an exception (" + t + ")");
            clear();
            SelfTest.summary();
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        SelfTest.log("barrelcontroller: waiting 100 ticks for the pipes and AE2");
    }

    // ---- routing ------------------------------------------------------------------------------------------------------

    void routing() {
        x0 = bx + 2;
        z0 = bz + 2;
        r = ctrl(x0, y, z0);
        rIron = barrel(x0 + 1, y, z0, BarrelTier.BASIC); // east of it
        rGold = barrel(x0, y, z0 + 1, BarrelTier.BASIC); // south of it
        rEmpty = barrel(x0, y + 1, z0 + 1, BarrelTier.BASIC); // on the gold barrel
        rApart = barrel(x0 + 3, y, z0, BarrelTier.BASIC); // a block apart from the iron barrel
        rIron.store()
            .insert(new ItemStack(Items.iron_ingot), 100, false);
        rGold.store()
            .insert(new ItemStack(Items.gold_ingot), 50, false);
        rApart.store()
            .insert(new ItemStack(Items.iron_ingot), 5, false);
        int[] n = r.counts();
        SelfTest.check(n[0] == 3 && n[1] == 3 && n[2] == 2 && n[3] == 1,
            "a controller reaches the 3 barrels chained to it, not one a block apart (" + Arrays.toString(n) + ")");
        ItemStack left = TileEntityHopper.func_145889_a(r, new ItemStack(Items.iron_ingot, 64), 1);
        SelfTest.check(left == null && rIron.store()
            .count() == 164 && rApart.store()
                .count() == 5,
            "64 iron ingots put in a hopper's way go to the iron barrel (" + rIron.store()
                .count() + ")");
        r.setInventorySlotContents(TileBarrelController.IN, new ItemStack(Items.gold_ingot, 10));
        SelfTest.check(rGold.store()
            .count() == 60, "10 gold ingots go to the gold barrel");
        ItemStack dirt = TileEntityHopper.func_145889_a(r, new ItemStack(Blocks.dirt, 16), 1);
        SelfTest.check(dirt != null && dirt.stackSize == 16 && rEmpty.store()
            .item() == null && !r.isItemValidForSlot(TileBarrelController.IN, new ItemStack(Blocks.dirt)),
            "with fill empty barrels off, dirt (no barrel holds it) is refused and stays where it was");
        r.fillEmpty = true;
        dirt = TileEntityHopper.func_145889_a(r, new ItemStack(Blocks.dirt, 16), 1);
        SelfTest.check(dirt == null && rEmpty.store()
            .count() == 16 && rEmpty.store()
                .item()
                .getItem() == Item.getItemFromBlock(Blocks.dirt),
            "with it on, the dirt starts the empty barrel");
        r.setInventorySlotContents(TileBarrelController.IN, new ItemStack(Blocks.dirt, 4));
        SelfTest.check(rEmpty.store()
            .count() == 20, "more dirt follows it there");
        r.fillEmpty = false;
    }

    void changes() {
        int built = r.rebuilds;
        rDia = barrel(x0, y + 2, z0 + 1, BarrelTier.BASIC); // on the dirt barrel
        rDia.store()
            .insert(new ItemStack(Items.diamond), 7, false);
        r.setInventorySlotContents(TileBarrelController.IN, new ItemStack(Items.diamond, 3));
        SelfTest.check(rDia.store()
            .count() == 10 && r.rebuilds == built + 1,
            "a barrel placed onto the chain joins: 3 diamonds go to it, the network found again once (" + (r.rebuilds - built) + ")");
        world.theChunkProviderServer.loadChunk((x0 + 80) >> 4, z0 >> 4);
        built = r.rebuilds;
        barrel(x0 + 80, y, z0, BarrelTier.BASIC);
        r.counts();
        SelfTest.check(r.rebuilds == built, "a barrel placed 80 blocks away doesn't make it look again");
        remove(x0 + 80, y, z0);
        remove(x0 + 1, y, z0); // the iron barrel
        long iron = r.insert(new ItemStack(Items.iron_ingot), 10, false, false);
        SelfTest.check(iron == 0 && rApart.store()
            .count() == 5 && r.counts()[0] == 3,
            "a broken barrel leaves the network: iron is refused now, the barrel a block apart isn't reached");
        // A barrel given another item by hand behind its back.
        rGold.store()
            .extract(60, false, false);
        rGold.store()
            .insert(new ItemStack(Items.emerald), 5, false);
        long gold = r.insert(new ItemStack(Items.gold_ingot), 1, false, false);
        long emeralds = r.insert(new ItemStack(Items.emerald), 5, false, false);
        SelfTest.check(gold == 0 && emeralds == 5 && rGold.store()
            .count() == 10,
            "a barrel emptied and given emeralds by hand: gold is refused, emeralds are found and go to it");
    }

    void slots() {
        int size = r.getSizeInventory();
        SelfTest.check(size == 4, "one input slot and an output slot per barrel (" + size + ")");
        world.setBlock(x0, y - 1, z0, Blocks.hopper);
        placed.add(new int[] { x0, y - 1, z0 });
        long before = total(r);
        TileEntity h = world.getTileEntity(x0, y - 1, z0);
        boolean pulled = h instanceof TileEntityHopper && TileEntityHopper.func_145891_a((TileEntityHopper) h);
        SelfTest.check(pulled && total(r) == before - 1, "a hopper under it pulls an item out of a barrel");
        remove(x0, y - 1, z0); // before it pulls more on its own
        int dia = -1;
        for (int i = 1; i < size; i++) {
            ItemStack s = r.getStackInSlot(i);
            if (s != null && s.getItem() == Items.diamond) dia = i;
        }
        long was = rDia.store()
            .count();
        ItemStack s = dia < 0 ? null : r.getStackInSlot(dia);
        if (s != null) s.stackSize -= 3;
        r.markDirty();
        SelfTest.check(s != null && rDia.store()
            .count() == was - 3, "another mod taking 3 diamonds from an output slot's stack in place: the barrel follows");
        ItemStack got = dia < 0 ? null : r.decrStackSize(dia, 2);
        SelfTest.check(got != null && got.stackSize == 2 && rDia.store()
            .count() == was - 5, "2 diamonds taken out of the diamond barrel's slot");
    }

    void nbt() {
        int x1 = bx + 8, z1 = bz + 2;
        TileBarrelController c = ctrl(x1, y, z1);
        ItemStack sharp = Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.sharpness, 1));
        ItemStack eff = Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.efficiency, 2));
        TileBarrel t1 = barrel(x1 + 1, y, z1, BarrelTier.BASIC), t2 = barrel(x1 - 1, y, z1, BarrelTier.BASIC);
        t1.store()
            .insert(sharp, 3, false);
        t2.store()
            .insert(eff, 4, false);
        long put = c.insert(sharp.copy(), 2, false, false);
        SelfTest.check(put == 2 && t1.store()
            .count() == 5 && t2.store()
                .count() == 4,
            "an enchanted book goes to the barrel with the same enchantment, not the other book barrel");
        List<ItemStack> exact = c.manyPeek(eff, false), any = c.manyPeek(new ItemStack(Items.enchanted_book), true);
        SelfTest.check(exact.size() == 1 && exact.get(0).stackSize == 4 && any.size() == 2,
            "the pipes' lookup: one book by its NBT, both ignoring NBT (" + exact.size() + ", " + any.size() + ")");
    }

    void linked() {
        int x2 = bx + 12, z2 = bz + 2;
        TileBarrelController c = ctrl(x2, y, z2);
        TileBarrel a = barrel(x2 + 1, y, z2, BarrelTier.BASIC), b = barrel(x2 - 1, y, z2, BarrelTier.BASIC);
        BarrelChannels d = BarrelChannels.get();
        d.join(a, "p:" + BarrelSelfTest.OWNER, "selftest-ctrl");
        d.join(b, "p:" + BarrelSelfTest.OWNER, "selftest-ctrl");
        a.store()
            .insert(new ItemStack(Items.redstone), 100, false);
        int[] n = c.counts();
        long put = c.insert(new ItemStack(Items.redstone), 50, false, false);
        SelfTest.check(n[0] == 2 && n[1] == 1 && put == 50 && b.store()
            .count() == 150, "two linked barrels count as one store: 50 redstone in, 150 in both (" + Arrays.toString(n) + ")");
    }

    void clicks() {
        long was = rDia.store()
            .count();
        hold(new ItemStack(Items.diamond, 5));
        BlockBarrelController.rightClick(player, r);
        SelfTest.check(rDia.store()
            .count() == was + 5 && player.getHeldItem() == null, "right-click with 5 diamonds puts them in the diamond barrel");
        player.inventory.setInventorySlotContents(5, new ItemStack(Blocks.dirt, 30));
        player.inventory.setInventorySlotContents(6, new ItemStack(Items.diamond, 10));
        player.inventory.setInventorySlotContents(7, new ItemStack(Blocks.cobblestone, 32));
        long dirt = rEmpty.store()
            .count();
        r.fillEmpty = true; // a double-click still never starts an empty barrel
        TileBarrel spare = barrel(x0 - 1, y, z0, BarrelTier.BASIC);
        BlockBarrelController.rightClick(player, r); // straight after: everything carried that a barrel holds
        r.fillEmpty = false;
        SelfTest.check(rEmpty.store()
            .count() == dirt + 30 && rDia.store()
                .count() == was + 15 && player.inventory.getStackInSlot(7) != null && spare.store()
                    .item() == null,
            "a quick second right-click puts in the dirt and diamonds carried, not the cobblestone (nor into an empty barrel)");
        remove(x0 - 1, y, z0);
        clearInventory();
    }

    void menu() {
        Object o = BarrelControllerMenu.server(player, world, r.xCoord, r.yCoord, r.zCoord);
        BarrelControllerMenu m = o instanceof BarrelControllerMenu ? (BarrelControllerMenu) o : null;
        if (m == null) {
            SelfTest.check(false, "the controller's menu opens for its owner");
            return;
        }
        boolean was = r.fillEmpty;
        m.apply(player, BarrelControllerMenu.FILL, 0, 0, null, null);
        BarrelControllerMenu shown = BarrelControllerMenu.client(player, world, r.xCoord, r.yCoord, r.zCoord);
        shown.receive(m.state());
        SelfTest.check(r.fillEmpty != was && shown.fillEmpty == r.fillEmpty && shown.barrels == r.counts()[0] && !shown.top.isEmpty(),
            "the menu switches fill empty barrels and shows the barrels and what they hold most of (" + shown.barrels + " barrels, "
                + shown.top.size() + " shown)");
        m.apply(player, BarrelControllerMenu.FILL, 0, 0, null, null);
        SelfTest.check(r.fillEmpty == was, "and switches it back");
    }

    // ---- AE2 ----------------------------------------------------------------------------------------------------------

    void ae2() {
        if (!Loader.isModLoaded("appliedenergistics2")) {
            SelfTest.check(false, "AE2 is loaded on the test server");
            return;
        }
        int x3 = bx + 2, z3 = bz + 8;
        aeCtrl = ctrl(x3, y, z3);
        aeIron = barrel(x3 + 1, y, z3, BarrelTier.LV);
        TileBarrel gold = barrel(x3 - 1, y, z3, BarrelTier.LV);
        aeIron.store()
            .insert(new ItemStack(Items.iron_ingot), 1000, false);
        gold.store()
            .insert(new ItemStack(Items.gold_ingot), 300, false);
        aeProbe = Ae.start(aeCtrl, aeIron, gold);
    }

    /** AE2's classes (only touched with AE2 loaded). */
    static final class Ae {

        static final class Probe implements appeng.api.storage.IMEMonitorHandlerReceiver<appeng.api.storage.data.IAEItemStack> {

            long seen;
            int calls;

            @Override
            public boolean isValid(Object verification) {
                return true;
            }

            @Override
            public void postChange(appeng.api.networking.storage.IBaseMonitor<appeng.api.storage.data.IAEItemStack> m,
                Iterable<appeng.api.storage.data.IAEItemStack> changes, appeng.api.networking.security.BaseActionSource src) {
                for (appeng.api.storage.data.IAEItemStack s : changes) seen += s.getStackSize();
                calls++;
            }

            @Override
            public void onListUpdate() {}
        }

        static appeng.api.storage.data.IAEItemStack ae(ItemStack s, long n) {
            appeng.api.storage.data.IAEItemStack a = appeng.api.AEApi.instance()
                .storage()
                .createItemStack(s);
            a.setStackSize(n);
            return a;
        }

        static Object start(TileBarrelController c, TileBarrel iron, TileBarrel gold) {
            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> m = c.getMonitorable(null, null)
                .getItemInventory();
            long seen = 0;
            for (appeng.api.storage.data.IAEItemStack s : m.getStorageList()) seen += s.getStackSize();
            appeng.api.storage.data.IAEItemStack left = m.injectItems(ae(new ItemStack(Items.iron_ingot), 500),
                appeng.api.config.Actionable.MODULATE, null);
            appeng.api.storage.data.IAEItemStack refused = m.injectItems(ae(new ItemStack(Items.diamond), 10),
                appeng.api.config.Actionable.MODULATE, null);
            appeng.api.storage.data.IAEItemStack out = m.extractItems(ae(new ItemStack(Items.gold_ingot), 200),
                appeng.api.config.Actionable.MODULATE, null);
            appeng.api.storage.data.IAEItemStack one = ((ControllerAe) m).getAvailableItem(ae(new ItemStack(Items.iron_ingot), 1), 0);
            SelfTest.check(seen == 1300, "an AE2 storage bus on the controller sees both barrels: 1,300 items (" + seen + ")");
            SelfTest.check(left == null && iron.store()
                .count() == 1500 && refused != null && refused.getStackSize() == 10,
                "AE2 puts 500 iron in (to the iron barrel), and 10 diamonds no barrel holds are refused");
            SelfTest.check(out != null && out.getStackSize() == 200 && gold.store()
                .count() == 100, "AE2 takes 200 gold out in one go");
            SelfTest.check(one != null && one.getStackSize() == 1500, "AE2 asking for one item gets its whole amount from the lookup");
            Probe p = new Probe();
            m.addListener(p, null);
            return p;
        }

        /** Tick 5: a barrel joins with 77 emeralds and 5 iron go into the iron barrel by hand. */
        static void change(BarrelControllerSelfTest t) {
            TileBarrel e = t.barrel(t.aeCtrl.xCoord, t.y, t.aeCtrl.zCoord + 1, BarrelTier.BASIC);
            e.store()
                .insert(new ItemStack(Items.emerald), 77, false);
            t.aeIron.store()
                .insert(new ItemStack(Items.iron_ingot), 5, false);
        }

        static void check(BarrelControllerSelfTest t) {
            Probe p = (Probe) t.aeProbe;
            SelfTest.check(p.seen == 82 && p.calls >= 1, "a listening storage bus is told of a barrel joining (77 emeralds) and of 5 iron "
                + "put in by hand (" + p.seen + " in " + p.calls + " changes)");
            ControllerAe a = ControllerAe.of(t.aeCtrl);
            a.removeListener(p);
            int[] w = a.watching();
            SelfTest.check(w[0] == 0 && w[1] == 0 && !TileBarrelController.aeWatched.contains(t.aeCtrl),
                "with nobody listening, it stops watching the barrels (" + Arrays.toString(w) + ")");
        }
    }

    // ---- cost ---------------------------------------------------------------------------------------------------------

    void perf() {
        TileBarrelController c = ctrl(px - 1, y, bz);
        ItemStack[] kinds = new ItemStack[1024];
        for (int i = 0; i < 1024; i++) {
            int x = px + i % 32, z = bz + i / 32;
            TileBarrel t = barrel(x, y, z, BarrelTier.LUV);
            ItemStack k = new ItemStack(Items.paper);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("ValierTest", i);
            k.setTagCompound(tag);
            kinds[i] = k;
            t.store()
                .insert(k, 1000, false);
        }
        long t0 = System.nanoTime();
        int[] n = c.counts();
        long first = System.nanoTime() - t0;
        SelfTest.check(n[0] == 1024 && n[1] == 1024 && n[2] == 1024,
            "a controller reaches 1,024 barrels holding 1,024 kinds of item (" + Arrays.toString(n) + ")");
        long rebuild = 0;
        for (int i = 0; i < 5; i++) {
            c.lookAgain();
            t0 = System.nanoTime();
            c.counts();
            rebuild += System.nanoTime() - t0;
        }
        double rebuildMs = rebuild / 5.0 / 1e6;
        t0 = System.nanoTime();
        long in = 0;
        for (int i = 0; i < 20000; i++) in += c.insert(kinds[(i * 7919) & 1023], 1, false, false);
        double insertUs = (System.nanoTime() - t0) / 20000.0 / 1e3;
        t0 = System.nanoTime();
        long found = 0;
        for (int i = 0; i < 20000; i++) found += c.manyPeek(kinds[(i * 104729) & 1023], false)
            .size();
        double lookupUs = (System.nanoTime() - t0) / 20000.0 / 1e3;
        ItemStack stranger = new ItemStack(Items.book);
        t0 = System.nanoTime();
        long strangers = 0;
        for (int i = 0; i < 20000; i++) strangers += c.insert(stranger, 1, false, false);
        double unknownUs = (System.nanoTime() - t0) / 20000.0 / 1e3;
        t0 = System.nanoTime();
        int listed = c.manyPeek()
            .size();
        double peekMs = (System.nanoTime() - t0) / 1e6;
        t0 = System.nanoTime();
        int seen = 0;
        for (int slot : c.getAccessibleSlotsFromSide(1)) if (c.getStackInSlot(slot) != null) seen++;
        c.markDirty();
        double slotsMs = (System.nanoTime() - t0) / 1e6;
        SelfTest.log(String.format("barrelcontroller perf: 1,024 barrels: found first in %.2f ms, again in %.2f ms; put in %.2f us, "
            + "pipes' lookup %.2f us, unknown item %.2f us, pipes' list %.2f ms, hopper over 1,025 slots %.2f ms", first / 1e6, rebuildMs,
            insertUs, lookupUs, unknownUs, peekMs, slotsMs));
        SelfTest.check(in == 20000 && found == 20000 && strangers == 0 && listed == 1024 && seen == 1024,
            "every put-in, lookup, refusal and listing over the 1,024 barrels came out right");
        SelfTest.check(rebuildMs < 25, String.format("finding 1,024 barrels again takes under 25 ms (%.2f ms)", rebuildMs));
        SelfTest.check(insertUs < 10, String.format("putting an item in takes under 10 us (%.2f us)", insertUs));
        SelfTest.check(lookupUs < 10, String.format("a pipe's lookup takes under 10 us (%.2f us)", lookupUs));
        SelfTest.check(unknownUs < 5, String.format("refusing an item no barrel holds takes under 5 us (%.2f us)", unknownUs));
        SelfTest.check(peekMs < 10, String.format("listing 1,024 kinds for the pipes takes under 10 ms (%.2f ms)", peekMs));
        for (int i = 0; i < 1024; i++) remove(px + i % 32, y, bz + i / 32);
        remove(px - 1, y, bz);
    }

    // ---- Valier pipes -------------------------------------------------------------------------------------------------

    void pipes() {
        // A: a Request face on a chest keeps 32 charcoal stocked from a controller (which also holds coal).
        int z = bz + 14;
        reqC = ctrl(bx + 2, y, z);
        barrel(bx + 2, y, z - 1, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.coal, 1, 1), 1000, false);
        barrel(bx + 2, y + 1, z, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.coal, 1, 0), 500, false);
        reqChest = chest(bx + 5, y, z);
        TilePipe a1 = pipe(bx + 3, y, z, PipeTier.ELITE), a2 = pipe(bx + 4, y, z, PipeTier.ELITE);
        mode(a1, ForgeDirection.WEST, FaceMode.PROVIDE);
        FaceConfig req = a2.face(ForgeDirection.EAST.ordinal());
        if (req != null) {
            req.allow.filter[0] = new ItemStack(Items.coal, 1, 1);
            req.allow.count[0] = 32;
        }
        mode(a2, ForgeDirection.EAST, FaceMode.REQUEST);

        // B: the same over Wireless pipes: 10 diamonds.
        wlC = ctrl(bx + 8, y, z);
        barrel(bx + 8, y, z - 1, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.diamond), 200, false);
        wlChest = chest(bx + 14, y, z);
        TilePipe w1 = pipe(bx + 9, y, z, PipeTier.WIRELESS), w2 = pipe(bx + 13, y, z, PipeTier.WIRELESS);
        if (w1.face(ForgeDirection.WEST.ordinal()) != null) w1.face(ForgeDirection.WEST.ordinal()).tag = "c40";
        mode(w1, ForgeDirection.WEST, FaceMode.PROVIDE);
        FaceConfig wr = w2.face(ForgeDirection.EAST.ordinal());
        if (wr != null) {
            wr.tag = "c40";
            wr.allow.filter[0] = new ItemStack(Items.diamond);
            wr.allow.count[0] = 10;
        }
        mode(w2, ForgeDirection.EAST, FaceMode.REQUEST);

        // C: a Pull face with iron on its Allow list takes the iron out, not the gold.
        z = bz + 18;
        pullC = ctrl(bx + 2, y, z);
        barrel(bx + 2, y, z - 1, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.iron_ingot), 100, false);
        barrel(bx + 2, y + 1, z, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.gold_ingot), 100, false);
        pullChest = chest(bx + 5, y, z);
        TilePipe c1 = pipe(bx + 3, y, z, PipeTier.ELITE), c2 = pipe(bx + 4, y, z, PipeTier.ELITE);
        if (c1.face(ForgeDirection.WEST.ordinal()) != null) c1.face(ForgeDirection.WEST.ordinal()).allow.filter[0] = new ItemStack(Items.iron_ingot);
        mode(c1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(c2, ForgeDirection.EAST, FaceMode.INSERT);

        // D: a Push face into a controller: iron and gold go to their barrels, dirt (no barrel holds it) stays in the chest.
        pushChest = chest(bx + 8, y, z);
        pushChest.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 20));
        pushChest.setInventorySlotContents(1, new ItemStack(Items.gold_ingot, 15));
        pushChest.setInventorySlotContents(2, new ItemStack(Blocks.dirt, 12));
        pushC = ctrl(bx + 11, y, z);
        pushIron = barrel(bx + 12, y, z, BarrelTier.BASIC);
        pushIron.store()
            .insert(new ItemStack(Items.iron_ingot), 1, false);
        pushGold = barrel(bx + 11, y, z + 1, BarrelTier.BASIC);
        pushGold.store()
            .insert(new ItemStack(Items.gold_ingot), 1, false);
        pushEmpty = barrel(bx + 11, y + 1, z, BarrelTier.BASIC);
        TilePipe d1 = pipe(bx + 9, y, z, PipeTier.ELITE), d2 = pipe(bx + 10, y, z, PipeTier.ELITE);
        mode(d1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(d2, ForgeDirection.EAST, FaceMode.INSERT);

        // E: a Pull face with no list takes everything out.
        z = bz + 22;
        allC = ctrl(bx + 2, y, z);
        barrel(bx + 2, y, z - 1, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.redstone), 40, false);
        barrel(bx + 2, y + 1, z, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.glowstone_dust), 30, false);
        barrel(bx + 1, y, z, BarrelTier.BASIC).store()
            .insert(new ItemStack(Items.bone), 20, false);
        allChest = chest(bx + 5, y, z);
        TilePipe e1 = pipe(bx + 3, y, z, PipeTier.ELITE), e2 = pipe(bx + 4, y, z, PipeTier.ELITE);
        mode(e1, ForgeDirection.WEST, FaceMode.EXTRACT);
        mode(e2, ForgeDirection.EAST, FaceMode.INSERT);

        // F: a Request for white wool ignoring damage gets the orange wool the controller has.
        dmgC = ctrl(bx + 8, y, z);
        barrel(bx + 8, y, z - 1, BarrelTier.BASIC).store()
            .insert(new ItemStack(Blocks.wool, 1, 1), 100, false);
        dmgChest = chest(bx + 11, y, z);
        TilePipe f1 = pipe(bx + 9, y, z, PipeTier.ELITE), f2 = pipe(bx + 10, y, z, PipeTier.ELITE);
        mode(f1, ForgeDirection.WEST, FaceMode.PROVIDE);
        FaceConfig fr = f2.face(ForgeDirection.EAST.ordinal());
        if (fr != null) {
            fr.allow.filter[0] = new ItemStack(Blocks.wool, 1, 0);
            fr.allow.count[0] = 16;
            fr.allow.damage = FaceConfig.Damage.ANY;
        }
        mode(f2, ForgeDirection.EAST, FaceMode.REQUEST);
    }

    void checkPipes() {
        int charcoal = count(reqChest, Items.coal, 1), coal = count(reqChest, Items.coal, 0);
        SelfTest.check(charcoal == 32 && coal == 0 && reqC.available(new ItemStack(Items.coal, 1, 1)) == 968,
            "a Request face keeps 32 charcoal stocked from a controller's Provide face, and takes no coal (" + charcoal + " charcoal, " + coal
                + " coal)");
        int dia = count(wlChest, Items.diamond, -1);
        SelfTest.check(dia == 10 && wlC.available(new ItemStack(Items.diamond)) == 190,
            "the same over Wireless pipes: 10 diamonds requested from the controller (" + dia + ")");
        int iron = count(pullChest, Items.iron_ingot, -1), gold = count(pullChest, Items.gold_ingot, -1);
        SelfTest.check(iron == 100 && gold == 0, "a Pull face with iron on its list takes all 100 iron out of the controller, no gold ("
            + iron + ", " + gold + ")");
        int left = count(pushChest, Items.iron_ingot, -1) + count(pushChest, Items.gold_ingot, -1);
        int dirt = count(pushChest, Item.getItemFromBlock(Blocks.dirt), -1);
        SelfTest.check(left == 0 && dirt == 12 && pushIron.store()
            .count() == 21 && pushGold.store()
                .count() == 16 && pushEmpty.store()
                    .item() == null,
            "a Push face into a controller: iron and gold go to their barrels, the dirt stays in the chest (" + left + " left, " + dirt
                + " dirt)");
        int red = count(allChest, Items.redstone, -1), glow = count(allChest, Items.glowstone_dust, -1),
            bone = count(allChest, Items.bone, -1);
        SelfTest.check(red == 40 && glow == 30 && bone == 20 && total(allC) == 0,
            "a Pull face with no list takes everything out of the controller (" + red + ", " + glow + ", " + bone + ")");
        int wool = count(dmgChest, Item.getItemFromBlock(Blocks.wool), -1), orange = count(dmgChest, Item.getItemFromBlock(Blocks.wool), 1);
        SelfTest.check(wool == 16 && orange == 16, "a Request for white wool ignoring damage gets 16 orange wool (" + wool + ")");
    }

    // ---- ticks --------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SelfTest.keepLoaded(world, (bx >> 4) - 1, (bz >> 4) - 1, (bx >> 4) + 2, (bz >> 4) + 2);
        ticks++;
        try {
            if (ticks == 5 && aeProbe != null) Ae.change(this);
            if (ticks < 100) return;
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] barrelcontroller threw", t);
            SelfTest.check(false, "barrelcontroller's tick ran without an exception (" + t + ")");
        }
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            if (aeProbe != null) Ae.check(this);
            checkPipes();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] barrelcontroller threw", t);
            SelfTest.check(false, "the barrelcontroller checks ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    TileBarrel barrel(int x, int yy, int z, int tier) {
        world.setBlock(x, yy, z, ModBlocks.barrel, tier, 3);
        TileBarrel t = (TileBarrel) world.getTileEntity(x, yy, z);
        t.tier = tier;
        t.owner = BarrelSelfTest.OWNER;
        t.store();
        placed.add(new int[] { x, yy, z });
        return t;
    }

    TileBarrelController ctrl(int x, int yy, int z) {
        world.setBlock(x, yy, z, ModBlocks.barrelController, 0, 3);
        placed.add(new int[] { x, yy, z });
        return (TileBarrelController) world.getTileEntity(x, yy, z);
    }

    IInventory chest(int x, int yy, int z) {
        world.setBlock(x, yy, z, Blocks.chest);
        placed.add(new int[] { x, yy, z });
        return (IInventory) world.getTileEntity(x, yy, z);
    }

    TilePipe pipe(int x, int yy, int z, int tier) {
        world.setBlock(x, yy, z, ModBlocks.itemPipe, tier, 3);
        placed.add(new int[] { x, yy, z });
        TilePipe t = (TilePipe) world.getTileEntity(x, yy, z);
        BlockPipe.setOwner(t, BarrelSelfTest.OWNER, "ControllerTester");
        t.updateConnections();
        return t;
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

    void remove(int x, int yy, int z) {
        TileEntity te = world.getTileEntity(x, yy, z);
        if (te instanceof TileBarrel) ((TileBarrel) te).pickedUp = true;
        if (te instanceof IInventory && !(te instanceof TileBarrel) && !(te instanceof TileBarrelController)) {
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, null);
        }
        world.setBlockToAir(x, yy, z);
    }

    /** Everything in the controller's barrels. */
    static long total(TileBarrelController c) {
        c.network();
        long n = 0;
        for (BarrelStore st : c.stores) n += st.count();
        return n;
    }

    /** Items of that item (and damage, unless -1) in an inventory. */
    static int count(IInventory inv, Item item, int damage) {
        int n = 0;
        if (inv == null) return 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() == item && (damage < 0 || s.getItemDamage() == damage)) n += s.stackSize;
        }
        return n;
    }

    void hold(ItemStack s) {
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, s);
    }

    void clearInventory() {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) player.inventory.setInventorySlotContents(i, null);
    }

    /** Removes everything it placed (barrels without drops), its channel and stray items. */
    void clear() {
        for (int i = placed.size() - 1; i >= 0; i--) {
            int[] p = placed.get(i);
            if (!world.blockExists(p[0], p[1], p[2])) world.theChunkProviderServer.loadChunk(p[0] >> 4, p[2] >> 4);
            TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileBarrel) {
                TileBarrel t = (TileBarrel) te;
                BarrelChannels d = BarrelChannels.get();
                if (d != null && !t.channel()
                    .isEmpty()) d.leave(t);
            }
            remove(p[0], p[1], p[2]);
        }
        BarrelChannels d = BarrelChannels.get();
        if (d != null) {
            d.channels.values()
                .removeIf(c -> c.name.startsWith("selftest-"));
            d.markDirty();
        }
        for (Object o : world.getEntitiesWithinAABB(EntityItem.class,
            AxisAlignedBB.getBoundingBox(bx - 4, y - 6, bz - 4, bx + 36, y + 6, bz + 36))) ((EntityItem) o).setDead();
        clearInventory();
    }
}
