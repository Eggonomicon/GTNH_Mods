package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.pipe.PipeActionMessage;

import cpw.mods.fml.common.Loader;

/**
 * {@code valiertweaks selftest barrels} (2.3.0): Valier Barrels, alone and linked.
 * <ul>
 * <li>Clicks: the held stack in, twice quickly everything matching, a stack and one item out; an empty barrel takes
 * any item, a locked one keeps its item; keep (automation leaves it); void overflow.</li>
 * <li>Automation: a hopper's way in (vanilla's own insert) and out (a real hopper under it); Valier pipes move 320 at
 * once in and out; comparator levels.</li>
 * <li>Tiers: an upgrade in place keeps the contents, a wrong one is refused; picked up with its contents and placed
 * again.</li>
 * <li>Channels: two barrels (different chunks, and one in the Nether) share one count (640, take 1, 639 in both), the
 * capacity is the sum, one leaving takes away what the rest can't hold, a barrel holding another item can't join, a
 * barrel in an unloaded chunk still counts, another player's channel of the same name is another channel; saved and
 * loaded.</li>
 * <li>The menu (lock, void, keep, link, unlink, the screen's copy, refused where a claim protects it) and AE2 (a
 * storage bus's view: the whole count, in and out).</li>
 * </ul>
 */
public final class BarrelSelfTest {

    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000ba221");
    static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000ba222");

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 1) << 4, bz = (SelfTest.CZ + 10) << 4;
    final int fx = (SelfTest.CX + 6) << 4; // a chunk that is unloaded and loaded again
    int y;
    final String[] said = new String[1];
    FakePlayer player;
    final List<int[]> placed = new ArrayList<>();
    final List<Object[]> elsewhere = new ArrayList<>();

    public void start() {
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) {
            world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
            world.theChunkProviderServer.loadChunk((fx >> 4) + cx, (bz >> 4) + cz);
        }
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        player = new FakePlayer(world, new GameProfile(OWNER, "BarrelTester")) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said[0] = message.getUnformattedText();
            }
        };
        player.setPosition(bx + 8.5, y, bz + 8.5);
        try {
            clicks();
            automation();
            tiers();
            channels();
            menu();
            fixes();
            creative();
            ae2();
        } catch (Throwable t) {
            com.valier.tweaks.ValierTweaks.LOG.error("[ValierTweaks SelfTest] barrels threw", t);
            SelfTest.check(false, "barrels ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    // ---- clicks -------------------------------------------------------------------------------------------------------

    void clicks() {
        TileBarrel t = barrel(world, bx + 2, y, bz + 2, BarrelTier.BASIC, OWNER);
        hold(new ItemStack(Items.iron_ingot, 40));
        BarrelClicks.rightClick(player, t);
        SelfTest.check(t.store()
            .count() == 40 && player.getHeldItem() == null, "right-click puts the held 40 iron ingots in");
        player.inventory.setInventorySlotContents(5, new ItemStack(Items.iron_ingot, 64));
        player.inventory.setInventorySlotContents(6, new ItemStack(Items.iron_ingot, 64));
        player.inventory.setInventorySlotContents(7, new ItemStack(Items.gold_ingot, 64));
        hold(null);
        BarrelClicks.rightClick(player, t);
        BarrelClicks.rightClick(player, t); // a second right-click straight after
        SelfTest.check(t.store()
            .count() == 168 && player.inventory.getStackInSlot(5) == null && player.inventory.getStackInSlot(7) != null,
            "a quick second right-click puts in every iron ingot carried, not the gold (" + t.store()
                .count() + ")");
        clearInventory();
        BarrelClicks.take(player, t, false);
        BarrelClicks.take(player, t, true);
        SelfTest.check(t.store()
            .count() == 103 && countCarried(Items.iron_ingot) == 65, "left-click takes a stack, sneaking one (" + t.store()
                .count() + " left)");
        clearInventory();
        // Empty, it takes anything; locked, it keeps its item.
        TileBarrel e = barrel(world, bx + 4, y, bz + 2, BarrelTier.BASIC, OWNER);
        SelfTest.check(e.store()
            .takes(new ItemStack(Blocks.dirt)) && e.store()
                .takes(new ItemStack(Items.bone)), "an empty barrel takes any item");
        e.store()
            .insert(new ItemStack(Items.bone), 5, false);
        e.store()
            .setLocked(true);
        e.store()
            .extract(5, false, false);
        SelfTest.check(e.store()
            .item() != null && e.store()
                .item()
                .getItem() == Items.bone && !e.store()
                    .takes(new ItemStack(Blocks.dirt)),
            "locked, it keeps its item at 0 and takes nothing else");
        e.store()
            .setLocked(false);
        SelfTest.check(e.store()
            .item() == null, "unlocked and empty, it forgets its item");
        // Keep: automation leaves it.
        t.store().keep = 100;
        ItemStack out = t.decrStackSize(TileBarrel.OUT, 64);
        SelfTest.check(out != null && out.stackSize == 3 && t.store()
            .count() == 100, "keep 100: automation takes only what is over it (" + (out == null ? 0 : out.stackSize) + ")");
        t.store().keep = 0;
        // Void overflow.
        TileBarrel v = barrel(world, bx + 6, y, bz + 2, BarrelTier.BASIC, OWNER);
        v.store()
            .insert(new ItemStack(Blocks.cobblestone), 4096, false);
        long noRoom = v.store()
            .room(new ItemStack(Blocks.cobblestone), 64);
        v.store().voidOverflow = true;
        long took = v.store()
            .insert(new ItemStack(Blocks.cobblestone), 64, false);
        SelfTest.check(noRoom == 0 && took == 64 && v.store()
            .count() == 4096, "full, it takes no more; with void overflow it takes them and destroys them (count stays 4,096)");
    }

    // ---- automation ---------------------------------------------------------------------------------------------------

    void automation() {
        TileBarrel t = barrel(world, bx + 2, y, bz + 6, BarrelTier.BASIC, OWNER);
        ItemStack left = TileEntityHopper.func_145889_a(t, new ItemStack(Items.redstone, 20), 0);
        SelfTest.check(left == null && t.store()
            .count() == 20, "a hopper's insert puts 20 redstone in");
        world.setBlock(bx + 2, y - 1, bz + 6, Blocks.hopper);
        TileEntity h = world.getTileEntity(bx + 2, y - 1, bz + 6);
        boolean pulled = h instanceof TileEntityHopper && TileEntityHopper.func_145891_a((TileEntityHopper) h);
        SelfTest.check(pulled && t.store()
            .count() == 19, "a hopper under it pulls one out (" + t.store()
                .count() + " left)");
        world.setBlockToAir(bx + 2, y - 1, bz + 6);
        // Valier pipes: one transfer of 320.
        ItemStack big = new ItemStack(Items.redstone, 320);
        int room = com.valier.tweaks.pipe.PipeAccess.space(t, 1, big, 320);
        ItemStack rest = com.valier.tweaks.pipe.PipeAccess.insert(t, 1, big);
        SelfTest.check(room == 320 && rest == null && t.store()
            .count() == 339, "a Valier pipe puts 320 in at once (" + t.store()
                .count() + ")");
        ItemStack peek = com.valier.tweaks.pipe.PipeAccess.peek(t, BigStorage.OUT_SLOT);
        ItemStack got = com.valier.tweaks.pipe.PipeAccess.take(t, BigStorage.OUT_SLOT, 320);
        SelfTest.check(peek != null && peek.stackSize == 339 && got != null && got.stackSize == 320 && t.store()
            .count() == 19, "and takes 320 out at once (it sees all 339)");
        // Comparator.
        SelfTest.check(t.store()
            .comparator() == 1 && new BarrelStore().comparator() == 0, "comparator: 0 empty, 1 with a little in it");
        t.store()
            .insert(new ItemStack(Items.redstone), 4096, false);
        SelfTest.check(t.store()
            .comparator() == 15, "comparator: 15 full");
    }

    // ---- tiers ---------------------------------------------------------------------------------------------------------

    void tiers() {
        TileBarrel t = barrel(world, bx + 6, y, bz + 6, BarrelTier.BASIC, OWNER);
        t.store()
            .insert(new ItemStack(Items.iron_ingot), 4000, false);
        hold(new ItemStack(ModBlocks.barrelUpgrade, 1, BarrelTier.MV));
        said[0] = null;
        BarrelClicks.rightClick(player, t);
        SelfTest.check(t.tier == BarrelTier.BASIC && player.getHeldItem() != null && said[0] != null && said[0].contains("LV"),
            "an MV upgrade on a Basic barrel is refused (" + said[0] + ")");
        hold(new ItemStack(ModBlocks.barrelUpgrade, 2, BarrelTier.LV));
        BarrelClicks.rightClick(player, t);
        SelfTest.check(t.tier == BarrelTier.LV && world.getBlockMetadata(t.xCoord, t.yCoord, t.zCoord) == BarrelTier.LV
            && t.store()
                .count() == 4000
            && t.store()
                .capacity() == 512L * 64 && player.getHeldItem().stackSize == 1,
            "an LV upgrade makes it an LV barrel where it stands: 32,768 ingots, its 4,000 kept, one upgrade used");
        hold(null);
        ItemStack item = BarrelClicks.toItem(t);
        t.pickedUp = true;
        world.setBlockToAir(t.xCoord, t.yCoord, t.zCoord);
        TileBarrel again = placeItem(item, t.xCoord, t.yCoord, t.zCoord);
        SelfTest.check(item.getItemDamage() == BarrelTier.LV && again != null && again.tier == BarrelTier.LV && again.store()
            .count() == 4000, "picked up, the item holds its 4,000 ingots and tier; placed again, so does the barrel");
    }

    // ---- channels ------------------------------------------------------------------------------------------------------

    void channels() {
        BarrelChannels d = BarrelChannels.get();
        String scope = "p:" + OWNER;
        TileBarrel a = barrel(world, bx + 2, y, bz + 10, BarrelTier.BASIC, OWNER);
        TileBarrel b = barrel(world, bx + 18, y, bz + 10, BarrelTier.BASIC, OWNER); // the next chunk
        a.store()
            .insert(new ItemStack(Items.iron_ingot), 640, false);
        String why1 = d.join(a, scope, "selftest-iron"), why2 = d.join(b, scope, "selftest-iron");
        SelfTest.check(why1 == null && why2 == null && a.store() == b.store(), "two barrels link to one channel (" + why1 + ", "
            + why2 + ")");
        b.store()
            .extract(1, false, false);
        SelfTest.check(a.store()
            .count() == 639 && b.store()
                .count() == 639, "640 iron ingots, one taken from the second barrel: both show 639");
        SelfTest.check(a.store()
            .capacity() == 2 * 64 * 64, "the channel holds both barrels' capacity (8,192)");
        if (cpw.mods.fml.common.Loader.isModLoaded("holoinventory")) {
            net.minecraft.nbt.NBTTagList holo = net.dries007.holoInventory.compat.InventoryDecoderRegistry.toNBT(b);
            SelfTest.check(holo.tagCount() == 1 && holo.getCompoundTagAt(0)
                .getInteger("Count") == 639,
                "Holo Glasses see the linked barrel's whole count (639), not its output slot's stack (" + holo + ")");
        }
        // The Nether.
        WorldServer nether = DimensionManager.getWorld(-1);
        if (nether == null) {
            DimensionManager.initDimension(-1);
            nether = DimensionManager.getWorld(-1);
        }
        TileBarrel n = null;
        if (nether != null) {
            nether.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
            n = barrel(nether, bx + 2, 70, bz + 2, BarrelTier.BASIC, OWNER);
            elsewhere.add(new Object[] { nether, new int[] { bx + 2, 70, bz + 2 } });
            d.join(n, scope, "selftest-iron");
            n.store()
                .extract(9, false, false);
        }
        SelfTest.check(n != null && a.store()
            .count() == 630 && n.store() == a.store(), "a barrel in the Nether on the same channel shares it too (630 left)");
        // A barrel holding something else can't join.
        TileBarrel c = barrel(world, bx + 6, y, bz + 10, BarrelTier.BASIC, OWNER);
        c.store()
            .insert(new ItemStack(Blocks.cobblestone), 10, false);
        String why = d.join(c, scope, "selftest-iron");
        SelfTest.check(why != null && c.channel()
            .isEmpty() && c.store()
                .count() == 10, "a barrel holding cobblestone can't join a channel of iron (" + why + ")");
        // Another player's channel of the same name is another channel.
        TileBarrel o = barrel(world, bx + 10, y, bz + 10, BarrelTier.BASIC, OTHER);
        d.join(o, "p:" + OTHER, "selftest-iron");
        SelfTest.check(o.store() != a.store() && o.store()
            .count() == 0, "another player's \"selftest-iron\" is another channel");
        // Leaving takes what the rest can't hold.
        a.store()
            .insert(new ItemStack(Items.iron_ingot), 100000, false);
        long full = a.store()
            .count();
        int[] bp = { b.xCoord, b.yCoord, b.zCoord };
        killItems(bp);
        world.setBlockToAir(bp[0], bp[1], bp[2]); // broken: it drops as an item holding what the rest can't hold
        long carried = 0;
        for (Object e : items(bp)) {
            ItemStack s = ((EntityItem) e).getEntityItem();
            NBTTagCompound tag = ItemBlockBarrel.tag(s);
            if (tag == null) continue;
            BarrelStore st = new BarrelStore();
            st.read(tag.getCompoundTag("Store"));
            carried += st.count();
        }
        killItems(bp);
        SelfTest.check(full == 3 * 4096 && a.store()
            .capacity() == 2 * 4096 && a.store()
                .count() == 2 * 4096 && carried == 4096,
            "a full channel of 3 barrels loses one: the broken barrel's item carries the 4,096 the other two can't hold ("
                + carried + ")");
        // A barrel in an unloaded chunk still counts.
        TileBarrel f = barrel(world, fx + 2, y, bz + 2, BarrelTier.BASIC, OWNER);
        d.join(f, scope, "selftest-far");
        f.store()
            .insert(new ItemStack(Items.gold_ingot), 1000, false);
        boolean unloaded = SelfTest.unloadChunk(world, fx >> 4, bz >> 4);
        TileBarrel g = barrel(world, bx + 14, y, bz + 14, BarrelTier.BASIC, OWNER);
        d.join(g, scope, "selftest-far");
        SelfTest.check(unloaded && g.store()
            .count() == 1000 && g.store()
                .capacity() == 2 * 4096,
            "with one barrel's chunk unloaded, the channel still holds its 1,000 gold and both barrels' capacity");
        world.theChunkProviderServer.loadChunk(fx >> 4, bz >> 4);
        TileEntity back = world.getTileEntity(fx + 2, y, bz + 2);
        SelfTest.check(back instanceof TileBarrel && ((TileBarrel) back).store() == g.store(), "loaded again, it is on the channel");
        // Saved and loaded.
        NBTTagCompound tag = new NBTTagCompound();
        d.writeToNBT(tag);
        BarrelChannels copy = new BarrelChannels("x");
        copy.readFromNBT(tag);
        BarrelChannels.Channel cc = copy.find(scope, "selftest-iron");
        SelfTest.check(cc != null && cc.barrels() == 2 && cc.store.count() == 2 * 4096 && cc.store.item()
            .getItem() == Items.iron_ingot, "the channels save and load (2 barrels, 8,192 iron)");
        NBTTagCompound tt = new NBTTagCompound();
        a.writeToNBT(tt);
        TileBarrel ta = new TileBarrel();
        ta.readFromNBT(tt);
        SelfTest.check("selftest-iron".equals(ta.channel()) && ta.tier == BarrelTier.BASIC, "a linked barrel saves its channel");
    }

    // ---- the menu ------------------------------------------------------------------------------------------------------

    void menu() {
        TileBarrel t = barrel(world, bx + 10, y, bz + 2, BarrelTier.BASIC, OWNER);
        Object o = BarrelMenu.server(player, world, t.xCoord, t.yCoord, t.zCoord);
        SelfTest.check(o instanceof BarrelMenu, "the menu opens for its owner");
        if (!(o instanceof BarrelMenu)) return;
        BarrelMenu m = (BarrelMenu) o;
        m.apply(player, BarrelMenu.LOCK, 0, 0, null, null);
        boolean refusedEmpty = !t.store().locked && m.note.contains("first");
        t.store()
            .insert(new ItemStack(Items.diamond), 3, false);
        m.apply(player, BarrelMenu.LOCK, 0, 0, null, null);
        m.apply(player, BarrelMenu.VOID, 0, 0, null, null);
        m.apply(player, BarrelMenu.KEEP, 64, 0, null, null);
        SelfTest.check(refusedEmpty && t.store().locked && t.store().voidOverflow && t.store().keep == 64,
            "lock (refused while empty), void overflow and keep buttons work");
        m.apply(player, BarrelMenu.LINK, 0, 0, null, "selftest-gems");
        SelfTest.check("selftest-gems".equals(t.channel()) && t.store()
            .count() == 3 && t.store().locked, "Link: it joins a new channel with its 3 diamonds and settings");
        BarrelMenu screen = BarrelMenu.client(player, world, t.xCoord, t.yCoord, t.zCoord);
        screen.receive(m.state(player));
        boolean listed = false;
        for (String[] c : screen.channels) listed |= c[0].equals("selftest-gems");
        SelfTest.check(screen.count == 3 && screen.locked && "selftest-gems".equals(screen.channel) && listed,
            "the screen gets the count, settings, channel and the team's channel list");
        m.apply(player, BarrelMenu.UNLINK, 0, 0, null, null);
        SelfTest.check(t.channel()
            .isEmpty() && t.store()
                .count() == 3
            && BarrelChannels.get()
                .find("p:" + OWNER, "selftest-gems") == null,
            "Unlink: the last barrel takes everything back and the channel is gone");
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        Object refused;
        try {
            refused = BarrelMenu.server(player, world, t.xCoord, t.yCoord, t.zCoord);
            m.apply(player, BarrelMenu.KEEP, 64, 0, null, null);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(refused == null && t.store().keep == 64, "where a claim protects it, the menu doesn't open and buttons do "
            + "nothing");
        SelfTest.check(PipeActionMessage.SPREAD < BarrelMenu.LOCK, "barrel menu actions don't clash with pipe ones");
    }

    // ---- AE2 -----------------------------------------------------------------------------------------------------------

    void ae2() {
        if (!Loader.isModLoaded("appliedenergistics2")) {
            SelfTest.check(false, "AE2 is loaded on the test server");
            return;
        }
        TileBarrel t = barrel(world, bx + 14, y, bz + 2, BarrelTier.LV, OWNER);
        t.store()
            .insert(new ItemStack(Items.iron_ingot), 10000, false);
        SelfTest.check(Ae.check(t), "an AE2 storage bus sees all 10,000 ingots, puts 500 in and takes 2,000 out in one go each");
    }

    /** AE2's classes (only touched with AE2 loaded). */
    static final class Ae {

        static boolean check(TileBarrel t) {
            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> m = t.getMonitorable(null, null)
                .getItemInventory();
            appeng.api.storage.data.IItemList<appeng.api.storage.data.IAEItemStack> list = m.getStorageList();
            long seen = 0;
            for (appeng.api.storage.data.IAEItemStack s : list) seen += s.getStackSize();
            appeng.api.storage.data.IAEItemStack in = appeng.api.AEApi.instance()
                .storage()
                .createItemStack(new ItemStack(Items.iron_ingot));
            in.setStackSize(500);
            appeng.api.storage.data.IAEItemStack left = m.injectItems(in, appeng.api.config.Actionable.MODULATE, null);
            appeng.api.storage.data.IAEItemStack req = in.copy();
            req.setStackSize(2000);
            appeng.api.storage.data.IAEItemStack out = m.extractItems(req, appeng.api.config.Actionable.MODULATE, null);
            return seen == 10000 && left == null && out != null && out.getStackSize() == 2000 && t.store()
                .count() == 8500;
        }
    }

    // ---- 2.3.0-pre1 review fixes ---------------------------------------------------------------------------------------

    void fixes() {
        // A hopper that can't pass a pulled item on puts back the stack it first saw: it must not add a stack each try.
        TileBarrel t = barrel(world, bx + 2, y, bz + 12, BarrelTier.BASIC, OWNER);
        t.store()
            .insert(new ItemStack(Blocks.cobblestone), 1000, false);
        world.setBlock(bx + 2, y - 1, bz + 12, Blocks.hopper);
        TileEntityHopper h = (TileEntityHopper) world.getTileEntity(bx + 2, y - 1, bz + 12);
        for (int k = 0; k < h.getSizeInventory(); k++) h.setInventorySlotContents(k, new ItemStack(Blocks.dirt, 64));
        for (int k = 0; k < 5; k++) TileEntityHopper.func_145891_a(h);
        long afterHopper = t.store()
            .count();
        for (int k = 0; k < h.getSizeInventory(); k++) h.setInventorySlotContents(k, null);
        world.setBlockToAir(bx + 2, y - 1, bz + 12);
        ItemStack seen = t.getStackInSlot(TileBarrel.OUT);
        ItemStack took = t.decrStackSize(TileBarrel.OUT, 1);
        t.setInventorySlotContents(TileBarrel.OUT, new ItemStack(Blocks.cobblestone, seen == null ? 0 : seen.stackSize - 1));
        SelfTest.check(afterHopper == 1000 && took != null && t.store()
            .count() == 999,
            "a full hopper pulling 5 times adds nothing (" + afterHopper + " of 1,000), and taking one then setting the rest takes one ("
                + t.store()
                    .count()
                + ")");

        // A placement Forge undoes (another mod cancelled it) drops nothing: the player's item kept what it holds.
        int[] at = { bx + 5, y, bz + 12 };
        TileBarrel src = barrel(world, at[0], at[1], at[2], BarrelTier.BASIC, OWNER);
        src.store()
            .insert(new ItemStack(Items.gold_ingot), 300, false);
        ItemStack item = BarrelClicks.toItem(src);
        src.pickedUp = true;
        world.setBlockToAir(at[0], at[1], at[2]);
        world.captureBlockSnapshots = true;
        TileBarrel undone = placeItem(item, at[0], at[1], at[2]);
        world.captureBlockSnapshots = false;
        boolean emptyMeanwhile = undone != null && undone.store()
            .count() == 0;
        world.restoringBlockSnapshots = true;
        world.setBlockToAir(at[0], at[1], at[2]);
        world.restoringBlockSnapshots = false;
        world.capturedBlockSnapshots.clear();
        BarrelSync.flushPlaced();
        int dropped = items(at).size();
        SelfTest.check(emptyMeanwhile && dropped == 0, "a placement Forge undoes (a mod cancelled it) drops nothing and keeps nothing: "
            + "the item kept its 300 gold (" + dropped + " dropped)");
        world.captureBlockSnapshots = true;
        TileBarrel stood = placeItem(item, at[0], at[1], at[2]);
        world.captureBlockSnapshots = false;
        world.capturedBlockSnapshots.clear();
        BarrelSync.flushPlaced();
        SelfTest.check(stood != null && stood.store()
            .count() == 300, "the same placement standing: the 300 gold goes in at the end of the tick");

        // A barrel leaving a locked channel keeps the lock with the channel's item (else it would refuse everything).
        BarrelChannels d = BarrelChannels.get();
        TileBarrel l1 = barrel(world, bx + 8, y, bz + 12, BarrelTier.BASIC, OWNER);
        TileBarrel l2 = barrel(world, bx + 10, y, bz + 12, BarrelTier.BASIC, OWNER);
        l1.store()
            .insert(new ItemStack(Items.diamond), 10, false);
        d.join(l1, "p:" + OWNER, "selftest-lock");
        d.join(l2, "p:" + OWNER, "selftest-lock");
        l1.store()
            .setLocked(true);
        d.leave(l2);
        BarrelStore own = l2.store();
        SelfTest.check(own == l2.own && own.locked && own.item() != null && own.item()
            .getItem() == Items.diamond && own.count() == 0 && own.takes(new ItemStack(Items.diamond)),
            "a barrel leaving a locked channel stays locked to its diamonds at 0, and still takes diamonds");
        d.leave(l1);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    // ---- the Creative barrel (2.3.1) -----------------------------------------------------------------------------------

    void creative() {
        int[] at = { bx + 6, y, bz + 14 };
        TileBarrel t = barrel(world, at[0], at[1], at[2], BarrelTier.CREATIVE, OWNER);
        hold(new ItemStack(Items.diamond, 5));
        BarrelClicks.rightClick(player, t);
        BarrelStore st = t.store();
        SelfTest.check(st.creative() && st.item() != null && st.item()
            .getItem() == Items.diamond && st.count() == BarrelStore.ENDLESS && player.getHeldItem() != null
            && player.getHeldItem().stackSize == 5, "right-clicked with 5 diamonds, a Creative barrel gives diamonds without end, "
                + "and the 5 stay in the hand");
        clearInventory();
        BarrelClicks.take(player, t, false);
        BarrelClicks.take(player, t, true);
        SelfTest.check(countCarried(Items.diamond) == 65 && t.store()
            .count() == BarrelStore.ENDLESS, "left-click takes a stack of them, sneaking one, and it still has no end ("
                + countCarried(Items.diamond) + " carried)");
        clearInventory();
        world.setBlock(at[0], at[1] - 1, at[2], Blocks.hopper);
        TileEntity h = world.getTileEntity(at[0], at[1] - 1, at[2]);
        boolean pulled = h instanceof TileEntityHopper && TileEntityHopper.func_145891_a((TileEntityHopper) h)
            && TileEntityHopper.func_145891_a((TileEntityHopper) h);
        ItemStack inHopper = h instanceof TileEntityHopper ? ((TileEntityHopper) h).getStackInSlot(0) : null;
        if (h instanceof TileEntityHopper) ((TileEntityHopper) h).setInventorySlotContents(0, null);
        world.setBlockToAir(at[0], at[1] - 1, at[2]);
        ItemStack got = com.valier.tweaks.pipe.PipeAccess.take(t, BigStorage.OUT_SLOT, 320);
        int room = com.valier.tweaks.pipe.PipeAccess.space(t, 1, new ItemStack(Items.diamond, 64), 64);
        ItemStack refused = TileEntityHopper.func_145889_a(t, new ItemStack(Items.diamond, 10), 0);
        SelfTest.check(pulled && inHopper != null && inHopper.stackSize == 2 && got != null && got.stackSize == 320
            && room == 0 && refused != null && refused.stackSize == 10 && t.store()
                .count() == BarrelStore.ENDLESS,
            "a hopper under it pulls diamonds, a Valier pipe takes 320 at once, and nothing goes in (" + room + " room)");
        t.store().voidOverflow = true;
        ItemStack gone = TileEntityHopper.func_145889_a(t, new ItemStack(Items.diamond, 10), 0);
        ItemStack dirt = TileEntityHopper.func_145889_a(t, new ItemStack(Blocks.dirt, 10), 0);
        t.store().voidOverflow = false;
        SelfTest.check(gone == null && dirt != null && dirt.stackSize == 10 && t.store()
            .count() == BarrelStore.ENDLESS, "with void overflow on, diamonds put in are destroyed; dirt still isn't taken");
        hold(new ItemStack(Items.emerald, 1));
        BarrelClicks.rightClick(player, t);
        SelfTest.check(t.store()
            .item()
            .getItem() == Items.emerald && player.getHeldItem() != null, "right-clicked with an emerald, it gives emeralds now");
        hold(new ItemStack(ModBlocks.barrelUpgrade, 1, BarrelTier.LUV));
        BarrelClicks.rightClick(player, t);
        boolean noUpgrade = t.tier == BarrelTier.CREATIVE && player.getHeldItem() != null;
        hold(null);
        BarrelChannels d = BarrelChannels.get();
        String why = d == null ? "the channels aren't loaded" : d.join(t, t.scopeOf(OWNER), "creative-test");
        SelfTest.check(noUpgrade && why != null && t.channel()
            .isEmpty(), "a Creative barrel takes no upgrade and can't be linked (" + why + ")");
        ItemStack item = BarrelClicks.toItem(t);
        t.pickedUp = true;
        world.setBlockToAir(at[0], at[1], at[2]);
        TileBarrel again = placeItem(item, at[0], at[1], at[2]);
        SelfTest.check(item.getItemDamage() == BarrelTier.CREATIVE && again != null && again.tier == BarrelTier.CREATIVE
            && again.store()
                .item() != null
            && again.store()
                .item()
                .getItem() == Items.emerald
            && again.store()
                .count() == BarrelStore.ENDLESS
            && again.store()
                .comparator() == 15,
            "picked up and placed again, it is still a Creative barrel giving emeralds (comparator 15)");
        killItems(at);
    }

    TileBarrel barrel(WorldServer w, int x, int yy, int z, int tier, UUID owner) {
        w.setBlock(x, yy, z, ModBlocks.barrel, tier, 3);
        TileBarrel t = (TileBarrel) w.getTileEntity(x, yy, z);
        t.tier = tier;
        t.owner = owner;
        t.store();
        if (w == world) placed.add(new int[] { x, yy, z });
        return t;
    }

    TileBarrel placeItem(ItemStack item, int x, int yy, int z) {
        ((ItemBlockBarrel) item.getItem()).placeBlockAt(item, player, world, x, yy, z, 1, 0.5F, 1F, 0.5F, item.getItemDamage());
        TileEntity te = world.getTileEntity(x, yy, z);
        return te instanceof TileBarrel ? (TileBarrel) te : null;
    }

    void hold(ItemStack s) {
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, s);
    }

    void clearInventory() {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) player.inventory.setInventorySlotContents(i, null);
    }

    int countCarried(Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    List<?> items(int[] p) {
        return world.getEntitiesWithinAABB(EntityItem.class,
            AxisAlignedBB.getBoundingBox(p[0] - 2, p[1] - 2, p[2] - 2, p[0] + 3, p[1] + 3, p[2] + 3));
    }

    void killItems(int[] p) {
        for (Object o : items(p)) ((EntityItem) o).setDead();
    }

    /** Removes the test barrels without drops, their channels, and stray items. */
    void clear() {
        BarrelChannels d = BarrelChannels.get();
        List<Object[]> all = new ArrayList<>();
        for (int[] p : placed) all.add(new Object[] { world, p });
        all.addAll(elsewhere);
        for (Object[] e : all) {
            WorldServer w = (WorldServer) e[0];
            int[] p = (int[]) e[1];
            if (!w.blockExists(p[0], p[1], p[2])) w.theChunkProviderServer.loadChunk(p[0] >> 4, p[2] >> 4);
            TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileBarrel) {
                TileBarrel t = (TileBarrel) te;
                if (d != null && !t.channel()
                    .isEmpty()) d.leave(t);
                t.pickedUp = true;
            }
            w.setBlockToAir(p[0], p[1], p[2]);
            if (w == world) killItems(p);
        }
        if (d != null) {
            d.channels.values()
                .removeIf(c -> c.name.startsWith("selftest-"));
            d.markDirty();
        }
        clearInventory();
    }
}
