package com.valier.tweaks;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.implementations.MTEBasicTank;

/**
 * The menu part of {@code valiertweaks selftest xpcrafter}, on a crafter of its own with nothing touching it: an
 * empty-hand right-click opens it; the icons show everything it can make, its tank and status; clicking one chooses it,
 * again stops it; icons can't be taken or covered; a liquid XP bucket poured in fills the tank; what it makes waits in its
 * store, which shows in the menu and can be taken (shift-click, click, number key) but not filled; full dust from the
 * cursor and back; where protected nothing changes and nothing is taken; too far away it closes; broken, the store
 * drops.
 */
final class XpMenuSelfTest {

    final XpSelfTest t;
    final ItemStack tiny, arsenic, dust;

    XpMenuSelfTest(XpSelfTest t) {
        this.t = t;
        this.tiny = t.tinyGallium;
        this.arsenic = t.tinyArsenic;
        this.dust = t.galliumDust;
    }

    void run() {
        int[] p = { t.bx + 4, t.y, t.bz + 10 };
        XpCrafters.Crafter c = t.place(t.crafterItem.copy(), p);
        MTEBasicTank tank = XpCrafters.tankAt(t.world, p[0], p[1], p[2]);
        if (c == null || tank == null) {
            SelfTest.check(false, "menu: a second crafter could be placed");
            return;
        }
        XpCrafters.Data d = XpCrafters.data(t.world, false);
        XpMenu.testPlayer = t.player;
        double px = t.player.posX, py = t.player.posY, pz = t.player.posZ;
        t.player.setPosition(p[0] + 0.5, p[1], p[2] - 1.5);
        try {
            open(p, c);
            if (!(t.player.openContainer instanceof XpMenu)) return;
            XpMenu m = (XpMenu) t.player.openContainer;
            icons(m, c, tank);
            choose(m, c);
            notTaken(m);
            pour(m, tank);
            store(m, d, c, tank);
            full(m, c);
            guarded(d, c);
            far(m);
            broken(p, c);
        } finally {
            t.player.setPosition(px, py, pz);
            t.player.openContainer = t.player.inventoryContainer;
            t.player.inventory.setItemStack(null);
            XpMenu.testPlayer = null;
        }
    }

    void open(int[] p, XpCrafters.Crafter c) {
        t.hold(new ItemStack(Blocks.dirt));
        t.player.setSneaking(false);
        PlayerInteractEvent held = t.click(p);
        boolean noMenu = !held.isCanceled() && !(t.player.openContainer instanceof XpMenu);
        t.hold(null);
        PlayerInteractEvent ev = t.click(p);
        SelfTest.check(noMenu && ev.isCanceled() && t.player.openContainer instanceof XpMenu
            && ((XpMenu) t.player.openContainer).crafter == c, "menu: a right-click with an empty hand opens its menu (not "
                + "with something in hand)");
    }

    void icons(XpMenu m, XpCrafters.Crafter c, MTEBasicTank tank) {
        int shown = 0, at = slotOf(m, tiny);
        for (int i = 0; i < XpMenu.RECIPES; i++) if (m.shown[i] != null) shown++;
        int all = XpCrafters.recipes()
            .size();
        XpCrafters.XpRecipe first = XpCrafters.recipes()
            .values()
            .iterator()
            .next();
        ItemStack icon = at < 0 ? null : m.getSlot(at)
            .getStack();
        String gauge = name(m.getSlot(XpMenu.GAUGE_FROM)
            .getStack());
        SelfTest.check(shown == Math.min(all, XpMenu.RECIPES) && m.getSlot(XpMenu.RECIPES_FROM)
            .getStack()
            .isItemEqual(first.output) && icon != null && XpSelfTest.lore(icon)
                .contains("8,000 mB of liquid XP each")
            && XpSelfTest.lore(icon)
                .contains("Click to make this")
            && pane(m, XpMenu.STATUS_SLOT) == XpMenu.LIGHT_GRAY && pane(m, XpMenu.GAUGE_FROM) == XpMenu.GRAY
            && gauge.contains("0 / " + XpCrafters.mb(tank.getCapacity()) + " mB") && pane(m, XpMenu.PREV_SLOT) == XpMenu.BLACK
            && pane(m, XpMenu.NEXT_SLOT) == XpMenu.BLACK, "menu: it shows the " + shown + " things it can make, cheapest "
                + "first, with their cost, its empty tank (" + gauge + ") and status, and no pages (" + (icon == null ? null
                    : XpSelfTest.lore(icon)) + ")");
    }

    void choose(XpMenu m, XpCrafters.Crafter c) {
        int at = slotOf(m, tiny);
        ItemStack back = m.slotClick(at, 0, 0, t.player);
        ItemStack icon = m.getSlot(at)
            .getStack();
        boolean chosen = c.makes != null && c.makes.isItemEqual(tiny) && back == null && t.player.inventory.getItemStack() == null
            && icon.isItemEnchanted() && XpSelfTest.lore(icon)
                .contains("Making this now");
        m.slotClick(at, 0, 0, t.player);
        boolean stopped = c.makes == null && t.said[0] != null && t.said[0].contains("stopped");
        m.slotClick(slotOf(m, arsenic), 0, 1, t.player); // shift-click
        boolean shift = c.makes != null && c.makes.isItemEqual(arsenic);
        m.slotClick(at, 0, 0, t.player);
        SelfTest.check(chosen && stopped && shift && c.makes.isItemEqual(tiny), "menu: clicking tiny gallium dust makes it "
            + "make that (it shines, \"Making this now\"), again stops it, a shift-click chooses too (" + t.said[0] + ")");
    }

    void notTaken(XpMenu m) {
        ItemStack before = m.getSlot(XpMenu.XP_SLOT)
            .getStack();
        m.slotClick(XpMenu.XP_SLOT, 0, 0, t.player);
        boolean empty = t.player.inventory.getItemStack() == null;
        t.player.inventory.setItemStack(new ItemStack(Blocks.dirt));
        m.slotClick(XpMenu.HELP_SLOT, 0, 0, t.player);
        ItemStack cursor = t.player.inventory.getItemStack();
        t.hold(null);
        m.slotClick(XpMenu.MADE_SLOT, 0, 2, t.player); // number key 1
        m.slotClick(XpMenu.HELP_SLOT, 0, 4, t.player); // Q
        SelfTest.check(empty && cursor != null && cursor.getItem() == Item.getItemFromBlock(Blocks.dirt) && t.player.getHeldItem() == null
            && ItemStack.areItemStacksEqual(before, m.getSlot(XpMenu.XP_SLOT)
                .getStack())
            && m.getSlot(XpMenu.HELP_SLOT)
                .getStack()
                .getItem() == Items.book,
            "menu: its icons can't be taken (click, number key, Q) or covered with something on the cursor");
        t.player.inventory.setItemStack(null);
    }

    void pour(XpMenu m, MTEBasicTank tank) {
        ItemStack bucket = FluidContainerRegistry.fillFluidContainer(new FluidStack(t.xp, 1000), new ItemStack(Items.bucket));
        if (bucket == null) {
            SelfTest.check(false, "menu: a bucket of liquid XP exists to pour in");
            return;
        }
        t.player.inventory.setItemStack(bucket.copy());
        m.slotClick(XpMenu.XP_SLOT, 0, 0, t.player);
        ItemStack cursor = t.player.inventory.getItemStack();
        String gauge = name(m.getSlot(XpMenu.GAUGE_FROM)
            .getStack());
        boolean poured = XpSelfTest.amount(tank) == 1000 && cursor != null && cursor.getItem() == Items.bucket
            && pane(m, XpMenu.GAUGE_FROM) == XpMenu.LIME && pane(m, XpMenu.GAUGE_FROM + 1) == XpMenu.GRAY
            && gauge.contains("1,000 / ");
        t.player.inventory.setItemStack(new ItemStack(Blocks.dirt));
        m.slotClick(XpMenu.XP_SLOT, 0, 0, t.player);
        SelfTest.check(poured && XpSelfTest.amount(tank) == 1000 && t.said[0] != null && t.said[0].contains("no liquid XP"),
            "menu: clicking liquid XP holding a bucket of it pours it in (the empty bucket stays on the cursor, the gauge "
                + "shows it: " + gauge + "); dirt is refused");
        t.player.inventory.setItemStack(null);
    }

    void store(XpMenu m, XpCrafters.Data d, XpCrafters.Crafter c, MTEBasicTank tank) {
        tank.fill(new FluidStack(t.xp, 15000), true);
        XpCrafters.run(t.world, d, c);
        m.refresh();
        ItemStack shown = m.getSlot(XpMenu.STORE_FROM)
            .getStack();
        SelfTest.check(c.stored() == 2 && c.status == XpCrafters.Status.WORKING && shown != null && shown.isItemEqual(tiny)
            && shown.stackSize == 2 && name(m.getSlot(XpMenu.MADE_SLOT)
                .getStack()).contains("Made: 2")
            && pane(m, XpMenu.STATUS_SLOT) == XpMenu.LIME, "menu: with nothing touching it, the 2 it made from 16,000 mB "
                + "wait in its store, shown in the menu (" + c.status + ", " + shown + ")");

        for (int i = 0; i < XpCrafters.Crafter.STORE; i++) c.store[i] = XpSelfTest.copy(tiny, 64);
        tank.fill(new FluidStack(t.xp, 8000), true);
        XpCrafters.run(t.world, d, c);
        m.refresh();
        SelfTest.check(c.status == XpCrafters.Status.NO_OUTPUT && XpSelfTest.amount(tank) == 8000 && c.stored() == 576
            && pane(m, XpMenu.STATUS_SLOT) == XpMenu.RED, "menu: with its store full it takes no liquid XP, and says why in red ("
                + name(m.getSlot(XpMenu.STATUS_SLOT)
                    .getStack())
                + ")");

        for (int i = 0; i < XpCrafters.Crafter.STORE; i++) c.store[i] = null;
        c.store[0] = XpSelfTest.copy(tiny, 5);
        c.store[1] = XpSelfTest.copy(tiny, 3);
        c.store[3] = XpSelfTest.copy(tiny, 4);
        int before = inInventory(tiny);
        m.slotClick(XpMenu.STORE_FROM, 0, 1, t.player); // shift-click
        boolean shifted = c.store[0] == null && inInventory(tiny) == before + 5;
        m.slotClick(XpMenu.STORE_FROM + 1, 0, 0, t.player);
        ItemStack cursor = t.player.inventory.getItemStack();
        boolean picked = c.store[1] == null && cursor != null && cursor.isItemEqual(tiny) && cursor.stackSize == 3;
        m.slotClick(XpMenu.STORE_FROM + 2, 0, 0, t.player);
        boolean refused = c.store[2] == null && t.player.inventory.getItemStack() != null
            && t.player.inventory.getItemStack().stackSize == 3;
        t.player.inventory.setItemStack(null);
        t.hold(null);
        m.slotClick(XpMenu.STORE_FROM + 3, 0, 2, t.player); // number key 1: into the first hotbar slot
        ItemStack hotbar = t.player.inventory.getStackInSlot(0);
        boolean keyed = c.store[3] == null && hotbar != null && hotbar.stackSize == 4;
        SelfTest.check(shifted && picked && refused && keyed, "menu: its store can be taken from (shift-click into the "
            + "inventory, click onto the cursor, number key into the hotbar) but nothing can be put in");
        t.hold(null);
        clearInventory();
    }

    void full(XpMenu m, XpCrafters.Crafter c) {
        t.player.inventory.setItemStack(new ItemStack(Blocks.emerald_block, 2));
        m.slotClick(XpMenu.FULL_SLOT, 0, 0, t.player);
        ItemStack cursor = t.player.inventory.getItemStack();
        ItemStack icon = m.getSlot(slotOf(m, dust))
            .getStack();
        boolean on = c.full && c.fullPaid != null && cursor != null && cursor.stackSize == 1 && m.getSlot(XpMenu.FULL_SLOT)
            .getStack()
            .isItemEnchanted() && XpSelfTest.lore(icon)
                .contains("72,000 mB of liquid XP each (full dust)");
        t.player.inventory.setItemStack(null);
        m.slotClick(XpMenu.FULL_SLOT, 0, 0, t.player);
        SelfTest.check(on && !c.full && c.fullPaid == null && t.inInventory(Blocks.emerald_block) == 1, "menu: clicking full "
            + "dust holding emerald blocks puts one in (the gallium icon becomes gallium dust, 72,000 mB), again gives it back");
        clearInventory();
    }

    void guarded(XpCrafters.Data d, XpCrafters.Crafter c) {
        c.store[0] = XpSelfTest.copy(tiny, 2);
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        XpMenu m;
        try {
            m = new XpMenu(t.player, t.world, d, c);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        m.slotClick(slotOf(m, arsenic), 0, 0, t.player);
        boolean kept = c.makes.isItemEqual(tiny) && t.said[0] != null && t.said[0].contains("protected");
        m.slotClick(XpMenu.STORE_FROM, 0, 1, t.player);
        m.slotClick(XpMenu.STORE_FROM, 0, 0, t.player);
        SelfTest.check(kept && c.store[0] != null && c.store[0].stackSize == 2 && t.player.inventory.getItemStack() == null
            && XpSelfTest.lore(m.getSlot(slotOf(m, arsenic))
                .getStack())
                .contains("Protected"),
            "menu: where protected it can be looked at, but not changed or taken from");
        c.store[0] = null;
    }

    void far(XpMenu m) {
        boolean near = m.canInteractWith(t.player);
        double x = t.player.posX, y = t.player.posY, z = t.player.posZ;
        t.player.setPosition(x + 20, y, z);
        boolean away = !m.canInteractWith(t.player);
        t.player.setPosition(x, y, z);
        SelfTest.check(near && away, "menu: it stays open within 8 blocks, not further away");
    }

    void broken(int[] p, XpCrafters.Crafter c) {
        for (EntityItem e : t.itemsNear(p)) e.setDead();
        c.store[0] = XpSelfTest.copy(tiny, 7);
        t.world.func_147480_a(p[0], p[1], p[2], true);
        int items = 0;
        boolean crafter = false;
        for (EntityItem e : t.itemsNear(p)) {
            ItemStack s = e.getEntityItem();
            if (s.isItemEqual(tiny)) items += s.stackSize;
            if (XpCrafters.isCrafterItem(s)) crafter = true;
            e.setDead();
        }
        SelfTest.check(items == 7 && crafter && !t.player.openContainer.canInteractWith(t.player), "menu: broken, its store "
            + "drops (" + items + " tiny gallium dust) with the crafter item, and the menu can't be used any more");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** The recipe slot showing this item (or its full dust), or -1. */
    static int slotOf(XpMenu m, ItemStack what) {
        for (int i = 0; i < XpMenu.RECIPES; i++) {
            ItemStack s = m.getSlot(XpMenu.RECIPES_FROM + i)
                .getStack();
            if (s != null && s.isItemEqual(what)) return XpMenu.RECIPES_FROM + i;
        }
        return -1;
    }

    /** The colour of a pane icon, or -1 if it isn't one. */
    static int pane(XpMenu m, int slot) {
        ItemStack s = m.getSlot(slot)
            .getStack();
        return s != null && s.getItem() == Item.getItemFromBlock(Blocks.stained_glass_pane) ? s.getItemDamage() : -1;
    }

    static String name(ItemStack s) {
        return s == null ? "" : s.getDisplayName()
            .replaceAll("§.", "");
    }

    int inInventory(ItemStack what) {
        int n = 0;
        for (ItemStack s : t.player.inventory.mainInventory) if (s != null && s.isItemEqual(what)) n += s.stackSize;
        return n;
    }

    void clearInventory() {
        for (int i = 0; i < t.player.inventory.mainInventory.length; i++) t.player.inventory.mainInventory[i] = null;
    }
}
