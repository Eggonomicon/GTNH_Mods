package com.valier.tweaks.storage;

import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.authlib.GameProfile;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.pipe.PipeAccess;

/**
 * {@code valiertweaks selftest satchels} (2.3.0): Valier Satchels, Flasks and the Satchel Dock.
 * <ul>
 * <li>Pickup: into the slot holding the item, before the inventory; new items only when set; void overflow; the ore
 * dictionary setting (acacia logs into an oak log slot); items the player threw are left alone.</li>
 * <li>In hand: placing from the selected slot (keep respected), sneak + wheel picking a slot.</li>
 * <li>Counts far past 64 saved and loaded; the upgrade recipe keeping everything; the menu (take, put, shift-click,
 * settings, the Satchel's own slot locked).</li>
 * <li>Flask: picking up a water source, placing it, a bucket emptied into it and filled from it, filling and draining a
 * tank (a docked Flask).</li>
 * <li>The dock: a Satchel as an inventory (pipes and hoppers in, slots out, keep respected) and a Flask as a tank.</li>
 * </ul>
 */
public final class SatchelSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 3) << 4, bz = (SelfTest.CZ + 14) << 4;
    int y;
    FakePlayer player;

    public void start() {
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.theChunkProviderServer.loadChunk((bx >> 4) + cx, (bz >> 4) + cz);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        player = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000ba223"), "SatchelTester"));
        player.setPosition(bx + 8.5, y, bz + 8.5);
        try {
            pickup();
            inHand();
            saving();
            menu();
            flask();
            dock();
            fixes();
        } catch (Throwable t) {
            com.valier.tweaks.ValierTweaks.LOG.error("[ValierTweaks SelfTest] satchels threw", t);
            SelfTest.check(false, "satchels ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    ItemStack satchel(int tier) {
        return new ItemStack(ModBlocks.satchel, 1, tier);
    }

    // ---- pickup -------------------------------------------------------------------------------------------------------

    void pickup() {
        clearInventory();
        ItemStack bag = satchel(SatchelTier.LV);
        player.inventory.setInventorySlotContents(5, bag);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Blocks.cobblestone), 10, false, false);
        int ate = pick(new ItemStack(Blocks.cobblestone, 40));
        int dirt = pick(new ItemStack(Blocks.dirt, 5));
        SatchelInventory now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        SatchelSelfTest.check(ate == 40 && now.counts[0] == 50 && dirt == 0,
            "pickup: 40 cobblestone go into the Satchel's cobblestone slot (50 now); dirt it doesn't hold is left");
        now.pickNew = true;
        now.save();
        dirt = pick(new ItemStack(Blocks.dirt, 5));
        now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        SatchelSelfTest.check(dirt == 5 && now.find(new ItemStack(Blocks.dirt)) >= 0, "with new items on, dirt gets a slot");
        // Void overflow: the cobblestone slot full, more is still eaten.
        now.counts[0] = now.capacity;
        now.save();
        ate = pick(new ItemStack(Blocks.cobblestone, 64));
        now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        SatchelSelfTest.check(ate == 64 && now.counts[0] == now.capacity, "full, void overflow (on by default) destroys picked-up cobblestone");
        now.voids[0] = false;
        now.save();
        ate = pick(new ItemStack(Blocks.cobblestone, 64));
        SatchelSelfTest.check(ate == 0, "with void overflow off, a full slot leaves it on the ground");
        // Ore dictionary: an oak log slot takes acacia logs.
        now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        now.pickNew = false;
        now.insert(new ItemStack(Blocks.log, 1, 0), 1, false, false);
        int logs = pick(new ItemStack(Blocks.log2, 3, 0));
        now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        int oak = now.find(new ItemStack(Blocks.log, 1, 0));
        now.ore[oak] = true;
        now.save();
        int logs2 = pick(new ItemStack(Blocks.log2, 3, 0));
        now = SatchelInventory.of(player.inventory.getStackInSlot(5));
        SatchelSelfTest.check(logs == 0 && logs2 == 3 && now.counts[oak] == 4, "with the ore dictionary on, acacia logs go into the oak "
            + "log slot (stored as oak)");
        // Thrown by the player: left alone.
        EntityItem thrown = new EntityItem(world, player.posX, player.posY, player.posZ, new ItemStack(Blocks.cobblestone, 5));
        thrown.func_145799_b(player.getCommandSenderName());
        now.voids[0] = true;
        now.save();
        EntityItemPickupEvent ev = new EntityItemPickupEvent(player, thrown);
        new SatchelEvents().onPickup(ev);
        SatchelSelfTest.check(!ev.isCanceled() && thrown.getEntityItem().stackSize == 5, "items the player just threw aren't picked "
            + "back up");
        now.pickup = false;
        now.save();
        SatchelSelfTest.check(pick(new ItemStack(Blocks.cobblestone, 5)) == 0, "with pickup off, nothing goes in");
        clearInventory();
    }

    static void check(boolean ok, String what) {
        SelfTest.check(ok, what);
    }

    /** An item entity picked up by the player: how many the Satchels took. */
    int pick(ItemStack s) {
        EntityItem e = new EntityItem(world, player.posX, player.posY, player.posZ, s);
        int before = s.stackSize;
        new SatchelEvents().onPickup(new EntityItemPickupEvent(player, e));
        return before - (e.isDead ? 0 : e.getEntityItem().stackSize);
    }

    // ---- in hand ------------------------------------------------------------------------------------------------------

    void inHand() {
        clearInventory();
        ItemStack bag = satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Blocks.cobblestone), 100, false, false);
        inv.insert(new ItemStack(Blocks.glass), 3, false, false);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, bag);
        int px = bx + 2, pz = bz + 2;
        world.setBlock(px, y - 1, pz, Blocks.stone);
        world.setBlockToAir(px, y, pz);
        boolean used = bag.getItem()
            .onItemUse(bag, player, world, px, y - 1, pz, 1, 0.5F, 1F, 0.5F);
        SatchelInventory now = SatchelInventory.of(player.inventory.getStackInSlot(0));
        SatchelSelfTest.check(used && world.getBlock(px, y, pz) == Blocks.cobblestone && now.counts[0] == 99,
            "right-click on a block places the selected cobblestone (99 left)");
        now.keep[0] = 99;
        now.save();
        world.setBlockToAir(px, y, pz);
        bag = player.inventory.getStackInSlot(0);
        used = bag.getItem()
            .onItemUse(bag, player, world, px, y - 1, pz, 1, 0.5F, 1F, 0.5F);
        SatchelSelfTest.check(!used && world.isAirBlock(px, y, pz), "with keep 99, placing leaves the last 99");
        SatchelEvents.scroll(player, 1);
        now = SatchelInventory.of(player.inventory.getStackInSlot(0));
        SatchelSelfTest.check(now.selected == 1, "sneak + wheel picks the next slot holding something (glass)");
        world.setBlockToAir(px, y - 1, pz);
        clearInventory();
    }

    // ---- saving, upgrade ----------------------------------------------------------------------------------------------

    void saving() {
        ItemStack bag = satchel(SatchelTier.MV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Items.diamond), 4000, false, false);
        inv.keep[0] = 7;
        inv.lock[3] = true;
        inv.save();
        NBTTagCompound tag = bag.writeToNBT(new NBTTagCompound());
        ItemStack back = ItemStack.loadItemStackFromNBT(tag);
        SatchelInventory r = SatchelInventory.of(back);
        SatchelSelfTest.check(r.counts[0] == 4000 && r.items[0].getItem() == Items.diamond && r.keep[0] == 7 && r.lock[3] && r.voids[1],
            "4,000 diamonds in one slot, keep and lock survive saving and loading");
        InventoryCrafting grid = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer p) {
                return true;
            }
        }, 3, 3);
        grid.setInventorySlotContents(0, back);
        grid.setInventorySlotContents(4, new ItemStack(ModBlocks.satchelUpgrade, 1, SatchelTier.HV));
        ItemSatchelUpgrade.Recipe recipe = new ItemSatchelUpgrade.Recipe();
        ItemStack up = recipe.matches(grid, world) ? recipe.getCraftingResult(grid) : null;
        SatchelInventory u = SatchelInventory.of(up);
        SatchelSelfTest.check(up != null && up.getItemDamage() == SatchelTier.HV && u.counts[0] == 4000 && u.size == 27,
            "an HV upgrade on an MV Satchel makes an HV Satchel (27 slots) with its 4,000 diamonds");
        grid.setInventorySlotContents(4, new ItemStack(ModBlocks.satchelUpgrade, 1, SatchelTier.EV));
        SatchelSelfTest.check(!recipe.matches(grid, world), "an EV upgrade doesn't fit an MV Satchel");
        grid.setInventorySlotContents(4, new ItemStack(ModBlocks.satchelUpgrade, 1, SatchelTier.HV + ItemSatchelUpgrade.FLUID));
        SatchelSelfTest.check(!recipe.matches(grid, world), "a Flask upgrade doesn't fit a Satchel");
    }

    // ---- menu ---------------------------------------------------------------------------------------------------------

    void menu() {
        clearInventory();
        ItemStack bag = satchel(SatchelTier.LV);
        SatchelInventory.of(bag)
            .insert(new ItemStack(Items.iron_ingot), 500, false, false);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, bag);
        SatchelMenu m = new SatchelMenu(player, 0);
        m.apply(player, SatchelMenu.TAKE, 0, 0, null, null);
        ItemStack cursor = player.inventory.getItemStack();
        SatchelSelfTest.check(cursor != null && cursor.stackSize == 64 && m.inv().counts[0] == 436, "clicking a slot takes a stack");
        m.apply(player, SatchelMenu.PUT, 0, 0, null, null);
        SatchelSelfTest.check(player.inventory.getItemStack() == null && m.inv().counts[0] == 500, "clicking with it puts it back");
        m.apply(player, SatchelMenu.SELECT, 0, 0, null, null);
        m.apply(player, SatchelMenu.KEEP, 450, 0, null, null);
        m.apply(player, SatchelMenu.SHIFT, 0, 0, null, null);
        SatchelSelfTest.check(m.inv().counts[0] == 450 && countCarried(Items.iron_ingot) == 50,
            "keep 450 then shift-click: only the 50 over it go to the inventory");
        m.apply(player, SatchelMenu.ORE, 0, 0, null, null);
        m.apply(player, SatchelMenu.VOID, 0, 0, null, null);
        m.apply(player, SatchelMenu.LOCK, 0, 0, null, null);
        m.apply(player, SatchelMenu.PICKNEW, 0, 0, null, null);
        SatchelInventory i = m.inv();
        SatchelSelfTest.check(i.ore[0] && !i.voids[0] && i.lock[0] && i.pickNew, "the settings buttons work on the selected slot");
        int heldSlot = 27; // the hotbar's first slot in the menu
        ItemStack r = m.slotClick(heldSlot, 0, 0, player);
        ItemStack swap = m.slotClick(5, 0, 2, player);
        SatchelSelfTest.check(r == null && swap == null && player.inventory.getStackInSlot(0) == bag && player.inventory.getItemStack() == null,
            "the open Satchel's own slot can't be clicked or swapped away");
        // The game leaves a copy in the hand after the right-click that opened the menu (NetHandlerPlayServer).
        ItemStack copy = ItemStack.copyItemStack(bag);
        player.inventory.setInventorySlotContents(0, copy);
        boolean stays = m.canInteractWith(player);
        m.apply(player, SatchelMenu.TAKE, 0, 0, null, null);
        boolean onCopy = SatchelInventory.of(copy).counts[0] == 386 && player.inventory.getItemStack() != null;
        m.apply(player, SatchelMenu.PUT, 0, 0, null, null);
        SatchelSelfTest.check(stays && onCopy && SatchelInventory.of(copy).counts[0] == 450,
            "with the hand's Satchel swapped for an equal copy (as after the right-click that opened it), the menu stays open and works on it");
        player.inventory.setInventorySlotContents(0, satchel(SatchelTier.LV));
        SatchelSelfTest.check(!m.canInteractWith(player), "a different Satchel put in its slot closes the menu");
        player.inventory.setInventorySlotContents(0, null);
        SatchelSelfTest.check(!m.canInteractWith(player), "with the Satchel gone from the hand, the menu closes");
        clearInventory();
    }

    int countCarried(net.minecraft.item.Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    // ---- flask --------------------------------------------------------------------------------------------------------

    void flask() {
        clearInventory();
        ItemStack fl = new ItemStack(ModBlocks.flask, 1, SatchelTier.LV);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, fl);
        int px = bx + 6, pz = bz + 2;
        world.setBlock(px, y - 1, pz, Blocks.stone);
        world.setBlock(px, y, pz, Blocks.water);
        ItemSatchel item = (ItemSatchel) fl.getItem();
        item.fluidClick(fl, world, player, mop(px, y, pz, 1));
        SatchelInventory inv = SatchelInventory.of(player.inventory.getStackInSlot(0));
        SatchelSelfTest.check(world.isAirBlock(px, y, pz) && inv.fluids[0] != null && inv.fluids[0].amount == 1000,
            "right-clicking a water source picks it up (1,000 mB)");
        item.fluidClick(fl, world, player, mop(px, y - 1, pz, 1));
        inv = SatchelInventory.of(player.inventory.getStackInSlot(0));
        SatchelSelfTest.check(world.getBlock(px, y, pz) == Blocks.water && inv.isEmpty(0), "and placing it puts it back");
        world.setBlockToAir(px, y, pz);
        world.setBlockToAir(px, y - 1, pz);
        // A bucket in the menu.
        SatchelMenu m = new SatchelMenu(player, 0);
        player.inventory.setItemStack(new ItemStack(Items.lava_bucket));
        m.apply(player, SatchelMenu.CONTAINER, 2, 0, null, null);
        inv = m.inv();
        boolean poured = inv.findFluid(new FluidStack(FluidRegistry.LAVA, 1)) >= 0 && player.inventory.getItemStack() != null
            && player.inventory.getItemStack()
                .getItem() == Items.bucket;
        int lava = inv.findFluid(new FluidStack(FluidRegistry.LAVA, 1));
        m.apply(player, SatchelMenu.CONTAINER, lava, 0, null, null);
        SatchelSelfTest.check(poured && player.inventory.getItemStack() != null && player.inventory.getItemStack()
            .getItem() == Items.lava_bucket && m.inv()
                .isEmpty(lava),
            "a lava bucket clicked on a tank pours in; the empty bucket clicked again fills from it");
        player.inventory.setItemStack(null);
        clearInventory();
    }

    static MovingObjectPosition mop(int x, int y, int z, int side) {
        return new MovingObjectPosition(x, y, z, side, Vec3.createVectorHelper(x + 0.5, y + 1, z + 0.5));
    }

    // ---- the dock -----------------------------------------------------------------------------------------------------

    void dock() {
        clearInventory();
        int dx = bx + 10, dz = bz + 2;
        world.setBlock(dx, y, dz, ModBlocks.satchelDock);
        TileEntity te = world.getTileEntity(dx, y, dz);
        if (!(te instanceof TileSatchelDock)) {
            SelfTest.check(false, "a Satchel Dock places with its tile");
            return;
        }
        TileSatchelDock d = (TileSatchelDock) te;
        ItemStack bag = satchel(SatchelTier.LV);
        SatchelInventory.of(bag)
            .insert(new ItemStack(Items.redstone), 60, false, false);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, bag);
        ModBlocks.satchelDock.onBlockActivated(world, dx, y, dz, player, 1, 0.5F, 1F, 0.5F);
        SatchelSelfTest.check(d.docked() == bag && player.getHeldItem() == null && world.getBlockMetadata(dx, y, dz) == 1,
            "right-click with a Satchel docks it");
        ItemStack rest = PipeAccess.insert(d, 1, new ItemStack(Items.redstone, 100));
        SatchelInventory inv = d.inv();
        SatchelSelfTest.check(rest != null && rest.stackSize == 36 && inv.counts[0] == 124, "a Valier pipe puts redstone in through the "
            + "always-empty input slot (64 a transfer)");
        net.minecraft.tileentity.TileEntityHopper.func_145889_a(d, new ItemStack(Items.redstone, 6), 1);
        inv.keep[0] = 100;
        inv.save();
        ItemStack view = PipeAccess.peek(d, 0);
        ItemStack got = PipeAccess.take(d, 0, 64);
        SatchelSelfTest.check(inv.counts[0] == 100 && view != null && view.stackSize == 30 && got != null && got.stackSize == 30,
            "a hopper puts 6 in (130); with keep 100, automation sees and takes 30");
        ModBlocks.satchelDock.onBlockActivated(world, dx, y, dz, player, 1, 0.5F, 1F, 0.5F);
        SatchelSelfTest.check(player.getHeldItem() == bag && d.docked() == null && SatchelInventory.of(bag).counts[0] == 100,
            "an empty hand takes it back, with what the pipes did to it");
        // A Flask as a tank.
        ItemStack fl = new ItemStack(ModBlocks.flask, 1, SatchelTier.LV);
        player.inventory.setInventorySlotContents(0, fl);
        ModBlocks.satchelDock.onBlockActivated(world, dx, y, dz, player, 1, 0.5F, 1F, 0.5F);
        int in = d.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.WATER, 5000), true);
        FluidStack out = d.drain(ForgeDirection.UP, 2000, true);
        SatchelSelfTest.check(in == 5000 && out != null && out.amount == 2000 && d.getTankInfo(ForgeDirection.UP).length == 9,
            "a docked Flask is a tank: 5,000 mB in, 2,000 out, 9 tanks");
        // A Flask in hand on that tank: fill from it (sneaking) and into it.
        ItemStack hand = new ItemStack(ModBlocks.flask, 1, SatchelTier.LV);
        player.inventory.setInventorySlotContents(0, hand);
        player.setSneaking(true);
        hand.getItem()
            .onItemUse(hand, player, world, dx, y, dz, 1, 0.5F, 1F, 0.5F);
        player.setSneaking(false);
        SatchelInventory h = SatchelInventory.of(player.inventory.getStackInSlot(0));
        boolean drained = h.fluids[0] != null && h.fluids[0].amount == 3000;
        hand.getItem()
            .onItemUse(hand, player, world, dx, y, dz, 1, 0.5F, 1F, 0.5F);
        h = SatchelInventory.of(player.inventory.getStackInSlot(0));
        SatchelSelfTest.check(drained && h.isEmpty(0) && d.inv().fluids[0] != null && d.inv().fluids[0].amount == 3000,
            "a Flask in hand drains the tank (sneaking: 3,000 mB) and fills it back");
        clearInventory();
    }

    // ---- 2.3.0-pre1 review fixes -------------------------------------------------------------------------------------

    void fixes() {
        clearInventory();
        // Void overflow is for pickups: what is put in by hand or by a pipe is refused when full, never destroyed.
        ItemStack bag = satchel(SatchelTier.LV);
        SatchelInventory inv = SatchelInventory.of(bag);
        inv.insert(new ItemStack(Items.diamond), inv.capacity, false, false);
        int manual = inv.insert(new ItemStack(Items.diamond), 64, false, false);
        int picked = inv.insert(new ItemStack(Items.diamond), 64, true, false);
        SatchelSelfTest.check(manual == 0 && picked == 64 && inv.counts[0] == inv.capacity,
            "full, with void overflow on: 64 diamonds put in by hand are refused (" + manual + " taken), 64 picked up are destroyed");

        // A tool used from a Satchel wears: the worn one comes out into the inventory.
        ItemStack tools = satchel(SatchelTier.LV);
        SatchelInventory.of(tools)
            .insert(new ItemStack(Items.flint_and_steel), 3, false, false);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, tools);
        int px = bx + 4, pz = bz + 10;
        world.setBlock(px, y - 1, pz, Blocks.stone);
        world.setBlockToAir(px, y, pz);
        boolean used = tools.getItem()
            .onItemUse(tools, player, world, px, y - 1, pz, 1, 0.5F, 1F, 0.5F);
        boolean fire = world.getBlock(px, y, pz) == Blocks.fire;
        world.setBlockToAir(px, y, pz);
        world.setBlockToAir(px, y - 1, pz);
        SatchelInventory now = SatchelInventory.of(player.inventory.getStackInSlot(0));
        ItemStack worn = null;
        for (int k = 1; k < player.inventory.mainInventory.length; k++) {
            ItemStack s = player.inventory.mainInventory[k];
            if (s != null && s.getItem() == Items.flint_and_steel) worn = s;
        }
        SatchelSelfTest.check(used && fire && now.counts[0] == 2 && worn != null && worn.getItemDamage() == 1,
            "flint and steel used from a Satchel lights the fire and wears: 2 left inside, the worn one in the inventory ("
                + now.counts[0] + ", " + (worn == null ? "none" : "damage " + worn.getItemDamage()) + ")");
        clearInventory();

        // The cache keeps no Satchel item alive: its inventory holds the item only weakly (a strong hold from the
        // WeakHashMap's value keeps the key forever). Checked field by field: servers run with System.gc() turned off.
        ItemStack lone = satchel(SatchelTier.LV);
        SatchelInventory li = SatchelInventory.of(lone);
        li.insert(new ItemStack(Items.apple), 5, false, false);
        boolean holds = false;
        try {
            for (java.lang.reflect.Field f : SatchelInventory.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(li);
                if (v == lone) holds = true;
                if (v instanceof Object[]) for (Object o : (Object[]) v) if (o == lone) holds = true;
            }
        } catch (ReflectiveOperationException e) {
            holds = true;
        }
        SatchelSelfTest.check(!holds && SatchelInventory.of(lone) == li, "a Satchel's cached inventory holds its item only weakly, so "
            + "the cache lets go of items nobody holds");

        // Finding an item's slot is a lookup kept up to date as slots empty and fill.
        ItemStack big = satchel(SatchelTier.LUV);
        SatchelInventory bi = SatchelInventory.of(big);
        net.minecraft.item.Item[] kinds = { Items.apple, Items.arrow, Items.bone, Items.book, Items.bread, Items.brick, Items.coal,
            Items.egg };
        for (net.minecraft.item.Item it : kinds) bi.insert(new ItemStack(it), 10, false, false);
        boolean found = bi.find(new ItemStack(Items.egg)) == 7 && bi.find(new ItemStack(Items.diamond)) == -1;
        bi.extract(0, 10, false, false); // the apples go: slot 0 is empty again
        bi.insert(new ItemStack(Items.diamond), 5, false, false);
        boolean refilled = bi.find(new ItemStack(Items.diamond)) == 0 && bi.find(new ItemStack(Items.apple)) == -1
            && bi.find(new ItemStack(Items.egg)) == 7;
        SatchelSelfTest.check(found && refilled, "an item's slot is found by lookup: the 8th of 8 items in slot 7; the apples taken out, "
            + "diamonds go into their emptied slot 0 and are found there");

        // A hopper under a dock that can't pass a pulled item on: nothing is added.
        int dx = bx + 6, dz = bz + 10;
        world.setBlock(dx, y, dz, ModBlocks.satchelDock);
        TileSatchelDock d = (TileSatchelDock) world.getTileEntity(dx, y, dz);
        ItemStack docked = satchel(SatchelTier.LV);
        SatchelInventory.of(docked)
            .insert(new ItemStack(Blocks.cobblestone), 500, false, false);
        d.dock(docked);
        world.setBlock(dx, y - 1, dz, Blocks.hopper);
        net.minecraft.tileentity.TileEntityHopper h = (net.minecraft.tileentity.TileEntityHopper) world.getTileEntity(dx, y - 1, dz);
        for (int k = 0; k < h.getSizeInventory(); k++) h.setInventorySlotContents(k, new ItemStack(Blocks.dirt, 64));
        for (int k = 0; k < 5; k++) net.minecraft.tileentity.TileEntityHopper.func_145891_a(h);
        int left = d.inv().counts[0];
        for (int k = 0; k < h.getSizeInventory(); k++) h.setInventorySlotContents(k, null);
        world.setBlockToAir(dx, y - 1, dz);
        d.docked = null;
        world.setBlockToAir(dx, y, dz);
        SatchelSelfTest.check(left == 500, "a full hopper under a dock pulling 5 times adds nothing (" + left + " of 500)");
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    void clearInventory() {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) player.inventory.setInventorySlotContents(i, null);
        player.inventory.setItemStack(null);
    }

    void clear() {
        for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) for (int yy = y - 1; yy <= y + 1; yy++) {
            TileEntity te = world.getTileEntity(x, yy, z);
            if (te instanceof TileSatchelDock) ((TileSatchelDock) te).docked = null;
            if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
        }
        if (player != null) clearInventory();
    }
}
