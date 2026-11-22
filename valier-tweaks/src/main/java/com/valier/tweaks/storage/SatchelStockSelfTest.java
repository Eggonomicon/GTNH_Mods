package com.valier.tweaks.storage;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.WorldServer;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;

import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Part of {@code valiertweaks selftest satchels} (2.3.3), on the step's player and chunk:
 * <ul>
 * <li>Sneak + right-click on a container, each mode: the selected item into a double chest (the full half passed
 * over), everything (keep amounts left), taking out what the Satchel holds (new items only when set, never destroyed by
 * void overflow), off, not sneaking; a furnace by its face (the top: its input slot, 64 at most); a barrel, thousands
 * at once, in and out.</li>
 * <li>Refill: topping the inventory up to the amount, counting what is there, leaving the keep amount, waiting while a
 * menu is open.</li>
 * <li>Hotbar restock: a used-up stack in the hand replaced on the next tick; a stack moved away isn't; off.</li>
 * <li>Send home: picked-up items over the keep amount into a barrel channel, a full slot emptying into it before
 * anything is destroyed, nothing for another team, the button going to off after the last channel.</li>
 * <li>The barrel ore dictionary setting (acacia logs into an oak log barrel; AE2 still offered only oak), saved.</li>
 * <li>The new settings saved and loaded; a Satchel never put into a Satchel; a barrel never taking a Satchel by hand.</li>
 * </ul>
 */
final class SatchelStockSelfTest {

    final SatchelSelfTest t;
    final WorldServer world;
    final int x0, z0, y;

    SatchelStockSelfTest(SatchelSelfTest t) {
        this.t = t;
        this.world = t.world;
        this.x0 = t.bx + 1;
        this.z0 = t.bz + 1;
        this.y = t.y;
    }

    void run() {
        try {
            boxes();
            refill();
            restock();
            home();
            barrelOre();
            saving();
        } finally {
            t.clearInventory();
            for (int x = x0 - 1; x <= x0 + 4; x++) for (int z = z0 - 1; z <= z0 + 4; z++) empty(x, y, z);
        }
    }

    static void check(boolean ok, String what) {
        SelfTest.check(ok, what);
    }

    /** Empties and removes a block (a chest or barrel spills nothing). */
    void empty(int x, int yy, int z) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, yy, z);
        if (te instanceof TileBarrel) {
            TileBarrel b = (TileBarrel) te;
            BarrelChannels d = BarrelChannels.get();
            if (d != null && !b.channel()
                .isEmpty()) d.leave(b);
            b.own.item = null;
            b.own.count = 0;
        } else if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            for (int k = 0; k < inv.getSizeInventory(); k++) inv.setInventorySlotContents(k, null);
        }
        if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
    }

    boolean click(ItemStack hand, int x, int z, int side, boolean sneak) {
        t.player.inventory.currentItem = 0;
        t.player.inventory.setInventorySlotContents(0, hand);
        t.player.setSneaking(sneak);
        try {
            return hand.getItem()
                .onItemUseFirst(hand, t.player, world, x, y, z, side, 0.5F, 0.5F, 0.5F);
        } finally {
            t.player.setSneaking(false);
        }
    }

    static int count(IInventory inv, net.minecraft.item.Item item) {
        int n = 0;
        for (int k = 0; k < inv.getSizeInventory(); k++) {
            ItemStack s = inv.getStackInSlot(k);
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    // ---- sneak + right-click on containers ----------------------------------------------------------------------------

    void boxes() {
        t.clearInventory();
        world.setBlock(x0, y, z0, Blocks.chest);
        world.setBlock(x0 + 1, y, z0, Blocks.chest);
        TileEntityChest a = (TileEntityChest) world.getTileEntity(x0, y, z0);
        TileEntityChest b = (TileEntityChest) world.getTileEntity(x0 + 1, y, z0);
        for (int k = 0; k < a.getSizeInventory(); k++) a.setInventorySlotContents(k, new ItemStack(Blocks.stone, 64));
        ItemStack bag = t.satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Blocks.cobblestone), 200, false, false);
        inv.insert(new ItemStack(Blocks.dirt), 100, false, false);
        inv.keep[0] = 10;
        inv.selected = 0;
        inv.save();
        boolean done = click(bag, x0, z0, 1, true);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        check(done && count(b, net.minecraft.item.Item.getItemFromBlock(Blocks.cobblestone)) == 190 && inv.counts[0] == 10,
            "sneak + right-click a full chest half: the selected cobblestone (190, keeping 10) goes into the double chest's other half ("
                + count(b, net.minecraft.item.Item.getItemFromBlock(Blocks.cobblestone)) + ", " + inv.counts[0] + " left)");
        inv.box = SatchelInventory.BOX_ALL;
        inv.save();
        click(bag, x0, z0, 1, true);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        int dirt = count(b, net.minecraft.item.Item.getItemFromBlock(Blocks.dirt));
        check(dirt == 100 && inv.counts[0] == 10 && inv.find(new ItemStack(Blocks.dirt)) < 0,
            "put everything in: the 100 dirt go in, the cobblestone keeps its 10 (" + dirt + " dirt in the chest)");
        // Take out: only what it holds; then new items too, never destroyed by a full voiding slot.
        inv.box = SatchelInventory.BOX_TAKE;
        inv.save();
        click(bag, x0, z0, 1, true);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        int stone = count(a, net.minecraft.item.Item.getItemFromBlock(Blocks.stone));
        check(inv.counts[0] == 200 && count(b, net.minecraft.item.Item.getItemFromBlock(Blocks.cobblestone)) == 0 && stone == 27 * 64
            && count(b, net.minecraft.item.Item.getItemFromBlock(Blocks.dirt)) == 100,
            "take out: the cobblestone it holds comes back (200); the stone and dirt it doesn't hold stay (" + stone + " stone)");
        inv.pickNew = true;
        inv.save();
        click(bag, x0, z0, 1, true);
        click(bag, x0, z0, 1, true);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        int st = inv.find(new ItemStack(Blocks.stone));
        stone = count(a, net.minecraft.item.Item.getItemFromBlock(Blocks.stone));
        check(st >= 0 && inv.counts[st] == inv.capacity && stone == 27 * 64 - inv.capacity && inv.voids[st],
            "with new items on, take out fills a stone slot to 1,024 and leaves the rest, twice clicked, though the slot voids pickups ("
                + stone + " stone left in the chest)");
        inv.box = SatchelInventory.BOX_OFF;
        inv.save();
        boolean off = click(bag, x0, z0, 1, true);
        inv.box = SatchelInventory.BOX_PUT;
        inv.save();
        boolean standing = click(bag, x0, z0, 1, false);
        check(!off && !standing, "container mode off, or not sneaking: the click isn't taken (the chest opens, or the item is placed)");
        empty(x0, y, z0);
        empty(x0 + 1, y, z0);

        // A furnace, by the face clicked: the top is its input slot, which holds 64.
        world.setBlock(x0 + 3, y, z0, Blocks.furnace);
        TileEntityFurnace f = (TileEntityFurnace) world.getTileEntity(x0 + 3, y, z0);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        inv.selected = 0; // cobblestone, 200
        inv.keep[0] = 0;
        inv.save();
        click(bag, x0 + 3, z0, 1, true);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        ItemStack in = f.getStackInSlot(0);
        check(in != null && in.stackSize == 64 && f.getStackInSlot(1) == null && inv.counts[0] == 136,
            "on a furnace's top: 64 cobblestone into its input slot, none into its fuel slot (" + (in == null ? 0 : in.stackSize) + ")");
        empty(x0 + 3, y, z0);

        // A barrel: thousands at once, in and out.
        t.clearInventory();
        world.setBlock(x0, y, z0 + 2, ModBlocks.barrel, 0, 3);
        TileBarrel barrel = (TileBarrel) world.getTileEntity(x0, y, z0 + 2);
        ItemStack big = t.satchel(SatchelTier.MV);
        SatchelInventory bi = SatchelInventory.of(big);
        bi.insert(new ItemStack(Blocks.cobblestone), 3000, false, false);
        bi.lock[0] = true; // emptied, it still holds cobblestone at 0, so taking out takes it back
        bi.save();
        click(big, x0, z0 + 2, 2, true);
        bi = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        long held = barrel.store()
            .count();
        bi.box = SatchelInventory.BOX_TAKE;
        bi.save();
        click(big, x0, z0 + 2, 2, true);
        bi = SatchelInventory.of(t.player.inventory.getStackInSlot(0));
        check(held == 3000 && barrel.store()
            .count() == 0 && bi.counts[0] == 3000, "a barrel: 3,000 cobblestone in with one click, and out again with one (" + held + ")");
        // A barrel never takes a Satchel by hand.
        ItemStack hand = t.satchel(SatchelTier.LV);
        t.player.inventory.setInventorySlotContents(0, hand);
        BarrelClicks.rightClick(t.player, barrel);
        check(barrel.store()
            .isEmpty() && t.player.inventory.getStackInSlot(0) == hand, "an empty barrel right-clicked with a Satchel doesn't take it");
        empty(x0, y, z0 + 2);
        // A Satchel never goes into a Satchel.
        check(SatchelInventory.of(t.satchel(SatchelTier.LV))
            .insert(t.satchel(SatchelTier.LV), 1, false, false) == 0, "a Satchel isn't put into a Satchel");
        t.clearInventory();
    }

    // ---- refill ------------------------------------------------------------------------------------------------------

    void refill() {
        t.clearInventory();
        ItemStack bag = t.satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Blocks.cobblestone), 500, false, false);
        inv.refill[0] = 64;
        inv.save();
        t.player.inventory.setInventorySlotContents(9, bag);
        int moved = SatchelRestock.refill(t.player);
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(9));
        int have = SatchelRestock.count(t.player.inventory.mainInventory, new ItemStack(Blocks.cobblestone));
        check(moved == 64 && have == 64 && inv.counts[0] == 436, "refill 64: a stack of cobblestone comes out into the inventory ("
            + have + ", " + inv.counts[0] + " left)");
        t.player.inventory.mainInventory[0].stackSize = 20;
        moved = SatchelRestock.refill(t.player);
        have = SatchelRestock.count(t.player.inventory.mainInventory, new ItemStack(Blocks.cobblestone));
        check(moved == 44 && have == 64, "20 left in the inventory: refill brings it back to 64 (" + moved + " more)");
        t.player.inventory.mainInventory[0] = null;
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(9));
        int had = inv.counts[0];
        inv.keep[0] = had - 16;
        inv.save();
        moved = SatchelRestock.refill(t.player);
        check(moved == 16, "refill leaves the slot's keep amount: 16 of " + had + " over a keep of " + (had - 16) + " (" + moved + ")");
        t.player.inventory.mainInventory[0] = null;
        Container was = t.player.openContainer;
        t.player.openContainer = new SatchelMenu(t.player, 9);
        moved = SatchelRestock.refill(t.player);
        t.player.openContainer = was;
        check(moved == 0, "refill waits while a menu is open");
        t.clearInventory();
    }

    // ---- hotbar restock ----------------------------------------------------------------------------------------------

    void restock() {
        t.clearInventory();
        ItemStack bag = t.satchel(SatchelTier.LV);
        SatchelInventory.of(bag)
            .insert(new ItemStack(Blocks.cobblestone), 300, false, false);
        t.player.inventory.setInventorySlotContents(9, bag);
        t.player.inventory.currentItem = 0;
        SatchelRestock r = new SatchelRestock();
        // Used up: the last block placed.
        ItemStack last = new ItemStack(Blocks.cobblestone, 1);
        t.player.inventory.setInventorySlotContents(0, last);
        last.stackSize = 0;
        SatchelRestock.used(t.player, last);
        t.player.inventory.setInventorySlotContents(0, null);
        r.onTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, t.player));
        ItemStack now = t.player.inventory.getStackInSlot(0);
        int left = SatchelInventory.of(t.player.inventory.getStackInSlot(9)).counts[0];
        check(now != null && now.getItem() == last.getItem() && now.stackSize == 64 && left == 236,
            "hotbar restock: the last cobblestone placed, a stack of 64 comes into the hand the next tick (" + left + " left)");
        // Moved away (no "used up" event): not replaced.
        t.player.inventory.setInventorySlotContents(0, null);
        r.onTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, t.player));
        check(t.player.inventory.getStackInSlot(0) == null, "a stack moved out of the hand isn't replaced");
        // Off.
        SatchelInventory inv = SatchelInventory.of(t.player.inventory.getStackInSlot(9));
        inv.restock = false;
        inv.save();
        last = new ItemStack(Blocks.cobblestone, 1);
        t.player.inventory.setInventorySlotContents(0, last);
        last.stackSize = 0;
        SatchelRestock.used(t.player, last);
        t.player.inventory.setInventorySlotContents(0, null);
        r.onTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, t.player));
        check(t.player.inventory.getStackInSlot(0) == null, "with hotbar restock off, nothing comes");
        t.clearInventory();
    }

    // ---- send home ---------------------------------------------------------------------------------------------------

    void home() {
        t.clearInventory();
        BarrelChannels d = BarrelChannels.get();
        if (d == null) {
            check(false, "the barrel channels are loaded");
            return;
        }
        world.setBlock(x0 + 2, y, z0 + 3, ModBlocks.barrel, 0, 3);
        TileBarrel barrel = (TileBarrel) world.getTileEntity(x0 + 2, y, z0 + 3);
        String scope = SatchelHome.scopeOf(t.player);
        String why = d.join(barrel, scope, "selftest-home");
        BarrelStore ch = barrel.store();
        ItemStack bag = t.satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Blocks.cobblestone), 10, false, false);
        inv.keep[0] = 16;
        inv.save();
        t.player.inventory.setInventorySlotContents(5, bag);
        SatchelHome.next(t.player, inv, 0);
        boolean set = "selftest-home".equals(inv.home[0]);
        int took = t.pick(new ItemStack(Blocks.cobblestone, 100));
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(5));
        check(why == null && set && took == 100 && inv.counts[0] == 16 && ch.count() == 94,
            "send home: 100 cobblestone picked up, the slot fills to its keep (16), 94 go into the barrel channel ("
                + inv.counts[0] + ", " + ch.count() + ")");
        // Full (and voiding): it empties into the channel first, nothing destroyed.
        inv.counts[0] = inv.capacity;
        inv.save();
        took = t.pick(new ItemStack(Blocks.cobblestone, 64));
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(5));
        long expect = 94 + (inv.capacity - 16) + 64;
        check(took == 64 && inv.counts[0] == 16 && ch.count() == expect,
            "a full slot with void overflow on: it empties into the channel and the 64 follow, none destroyed (" + ch.count() + " of "
                + expect + ")");
        // Another team's: nothing is sent.
        inv.homeScope[0] = "p:00000000-0000-4000-8000-00000000dead";
        inv.save();
        long before = ch.count();
        t.pick(new ItemStack(Blocks.cobblestone, 20));
        inv = SatchelInventory.of(t.player.inventory.getStackInSlot(5));
        check(ch.count() == before && inv.counts[0] == 36, "a Satchel linked by another team sends nothing (" + inv.counts[0] + ")");
        inv.homeScope[0] = scope;
        inv.save();
        SatchelHome.next(t.player, inv, 0);
        check(inv.home[0] == null, "the Send home button goes to off after the last channel");
        empty(x0 + 2, y, z0 + 3);
        t.clearInventory();
    }

    // ---- barrel ore dictionary ---------------------------------------------------------------------------------------

    void barrelOre() {
        BarrelStore st = new BarrelStore();
        st.insert(new ItemStack(Blocks.log, 1, 0), 10, false);
        ItemStack acacia = new ItemStack(Blocks.log2, 5, 0);
        boolean before = st.takes(acacia);
        st.ore = true;
        boolean after = st.takes(acacia);
        long took = st.insert(acacia, 5, false);
        BarrelStore back = new BarrelStore();
        back.read(st.write(new NBTTagCompound()));
        check(!before && after && took == 5 && st.count() == 15 && st.item()
            .getItem() == Item(Blocks.log) && !st.takesExact(acacia) && back.ore,
            "a barrel's ore dictionary setting: off, acacia logs stay out of an oak log barrel; on, they go in as oak (15), AE2 is still "
                + "offered only oak, and it is saved");
    }

    static net.minecraft.item.Item Item(net.minecraft.block.Block b) {
        return net.minecraft.item.Item.getItemFromBlock(b);
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    void saving() {
        ItemStack bag = t.satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Items.arrow), 5, false, false);
        inv.refill[0] = 128;
        inv.home[0] = "Arrows";
        inv.homeScope[0] = "t:archers";
        inv.restock = false;
        inv.box = SatchelInventory.BOX_TAKE;
        inv.save();
        ItemStack copy = ItemStack.loadItemStackFromNBT(bag.writeToNBT(new NBTTagCompound()));
        SatchelInventory c = SatchelInventory.of(copy);
        check(c != inv && c.refill[0] == 128 && "Arrows".equals(c.home[0]) && "t:archers".equals(c.homeScope[0]) && !c.restock
            && c.box == SatchelInventory.BOX_TAKE && c.refills, "refill, send home, hotbar restock and container mode are saved with the Satchel");
    }
}
