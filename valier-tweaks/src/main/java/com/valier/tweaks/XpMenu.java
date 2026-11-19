package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;


/**
 * An XP crafter's menu (a right-click on it, see {@link XpCrafters#menuItem}): a six-row chest window the server fills
 * with icons, so every client can show it without a
 * mod. Clicks on the icons are buttons; nothing can be taken out of or put into them, and after every click the whole
 * window is sent again, so what a client guessed a click would do is put right.
 * <ul>
 * <li>Top row: liquid XP (click holding a bucket or cell of it to pour it in, or solid XP such as essence berries to put
 * them in), a gauge of its tank, its status.</li>
 * <li>Three rows: everything it can make, cheapest first, with the liquid XP each takes. Click one to make it; click
 * the one it makes to stop.</li>
 * <li>One row: its store, what it has made that nothing touching it took yet. These are real items: take them.</li>
 * <li>Bottom row: full dust (click holding the full-dust item to switch it on, again to get it back), pages when
 * there are more than 27 things it can make, how many it has made, how it works.</li>
 * </ul>
 */
final class XpMenu extends Container {

    static final String TITLE = "Valier XP Crafter";
    static final int SIZE = 54;
    static final int XP_SLOT = 0, GAUGE_FROM = 1, GAUGE = 7, STATUS_SLOT = 8;
    static final int RECIPES_FROM = 9, RECIPES = 27;
    static final int STORE_FROM = 36;
    static final int FULL_SLOT = 46, PREV_SLOT = 48, MADE_SLOT = 49, NEXT_SLOT = 50, HELP_SLOT = 52;
    /** Stained glass pane colours. */
    static final int LIME = 5, YELLOW = 4, RED = 14, GRAY = 7, LIGHT_GRAY = 8, BLACK = 15;
    /** How often (ticks) an open menu reads the crafter again. */
    static final int REFRESH = 10;

    /** The self-test's fake player, which may open menus (other fake players don't). */
    static EntityPlayer testPlayer;

    final EntityPlayer player;
    final World world;
    final XpCrafters.Data data;
    final XpCrafters.Crafter crafter;
    final InventoryBasic icons = new InventoryBasic(TITLE, true, SIZE);
    final IInventory store;
    /** Whether this player may change it and take from it (claims decide), asked when the menu opens. */
    final boolean mayChange;
    /** The recipe at each recipe slot on the page shown. */
    final XpCrafters.XpRecipe[] shown = new XpCrafters.XpRecipe[RECIPES];
    int page, ticks;

    XpMenu(EntityPlayer player, World world, XpCrafters.Data data, XpCrafters.Crafter crafter) {
        this.player = player;
        this.world = world;
        this.data = data;
        this.crafter = crafter;
        this.store = new Store(data, crafter);
        this.mayChange = LootSpawners.mayChange(player, world, crafter.x, crafter.y, crafter.z);
        // The same slots, in the same order and places, as a client's six-row chest window.
        for (int i = 0; i < SIZE; i++) {
            int x = 8 + (i % 9) * 18, y = 18 + (i / 9) * 18;
            addSlotToContainer(isStore(i) ? new StoreSlot(this, i - STORE_FROM, x, y) : new IconSlot(icons, i, x, y));
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 198));
        refresh();
    }

    /** Opens the menu for a player: a chest window to their client, this container on the server. */
    static void open(EntityPlayer player, World world, XpCrafters.Data data, XpCrafters.Crafter crafter) {
        XpMenu menu = new XpMenu(player, world, data, crafter);
        if (player instanceof EntityPlayerMP && ((EntityPlayerMP) player).playerNetServerHandler != null) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            if (mp.openContainer != mp.inventoryContainer) mp.closeScreen();
            mp.currentWindowId = mp.currentWindowId % 100 + 1;
            mp.playerNetServerHandler.sendPacket(new S2DPacketOpenWindow(mp.currentWindowId, 0, TITLE, SIZE, true));
            menu.windowId = mp.currentWindowId;
            mp.openContainer = menu;
            menu.addCraftingToCrafters(mp);
        } else {
            player.openContainer = menu; // the self-test's player: no client
        }
    }

    static boolean isStore(int slot) {
        return slot >= STORE_FROM && slot < STORE_FROM + XpCrafters.Crafter.STORE;
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /** Fills the icons from what the crafter knows now. */
    void refresh() {
        XpCrafters.Crafter c = crafter;
        for (int i = 0; i < SIZE; i++) if (!isStore(i)) icons.setInventorySlotContents(i, filler());

        TileXpCrafter tank = XpCrafters.tankAt(world, c.x, c.y, c.z);
        FluidStack f = tank == null ? null : tank.getFluid();
        int amount = f == null || f.getFluid() == null ? 0 : f.amount, capacity = tank == null ? 0 : tank.getCapacity();
        boolean xp = amount <= 0 || XpCrafters.isXp(f.getFluid());
        List<String> lore = new ArrayList<>();
        if (xp) lore.add(EnumChatFormatting.GRAY + "In its tank: " + XpCrafters.mb(amount) + " / " + XpCrafters.mb(capacity) + " mB");
        else lore.add(EnumChatFormatting.RED + "Its tank holds " + XpCrafters.mb(amount) + " mB of " + f.getLocalizedName()
            + ", not liquid XP");
        if (c.bank > 0) lore.add(EnumChatFormatting.GRAY + "Taken towards the next: " + XpCrafters.mb(c.bank) + " mB");
        lore.add(EnumChatFormatting.YELLOW + "Click holding a bucket or cell of");
        lore.add(EnumChatFormatting.YELLOW + "liquid XP, or essence berries,");
        lore.add(EnumChatFormatting.YELLOW + "to put it in (shift: the stack)");
        icons.setInventorySlotContents(XP_SLOT, icon(new ItemStack(Items.experience_bottle), EnumChatFormatting.GREEN + "Liquid XP", lore));
        int lit = amount <= 0 || capacity <= 0 ? 0 : Math.max(1, Math.min(GAUGE, Math.round((float) GAUGE * amount / capacity)));
        String gauge = (xp ? EnumChatFormatting.GREEN + "Liquid XP: " : EnumChatFormatting.RED + "Not liquid XP: ") + XpCrafters.mb(amount)
            + " / " + XpCrafters.mb(capacity) + " mB";
        for (int i = 0; i < GAUGE; i++) icons.setInventorySlotContents(GAUGE_FROM + i, pane(i < lit ? (xp ? LIME : RED) : GRAY, gauge, null));
        icons.setInventorySlotContents(STATUS_SLOT, pane(colour(c.status), statusColour(c.status) + capital(c.status.words), null));

        XpCrafters.XpRecipe chosen = c.makes == null ? null : XpCrafters.recipeFor(c.makes);
        List<XpCrafters.XpRecipe> all = new ArrayList<>(XpCrafters.recipes()
            .values());
        int pages = Math.max(1, (all.size() + RECIPES - 1) / RECIPES);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < RECIPES; i++) {
            int n = page * RECIPES + i;
            XpCrafters.XpRecipe r = n < all.size() ? all.get(n) : null;
            shown[i] = r;
            if (r != null) icons.setInventorySlotContents(RECIPES_FROM + i, recipeIcon(r, r == chosen));
        }

        Item fullItem = LootSpawners.itemOf(TweaksConfig.xpCrafterFullItem);
        if (fullItem != null) {
            ItemStack shownFull = new ItemStack(fullItem, 1, Math.max(0, LootSpawners.metaOf(TweaksConfig.xpCrafterFullItem)));
            String name = shownFull.getDisplayName();
            lore = new ArrayList<>();
            if (c.full) {
                lore.add(EnumChatFormatting.GRAY + "Tiny and small piles are made");
                lore.add(EnumChatFormatting.GRAY + "as the whole dust instead");
                lore.add(EnumChatFormatting.YELLOW + (c.fullPaid != null ? "Click to switch it off and get the " + name + " back"
                    : "Click to switch it off"));
            } else {
                lore.add(EnumChatFormatting.GRAY + "Makes the whole dust instead of a tiny");
                lore.add(EnumChatFormatting.GRAY + "or small pile, for " + TweaksConfig.xpCrafterTinyTimes + " or "
                    + TweaksConfig.xpCrafterSmallTimes + " times the liquid XP");
                lore.add(EnumChatFormatting.YELLOW + "Click holding a " + name + " (it stays in it)");
            }
            ItemStack full = icon(shownFull, c.full ? EnumChatFormatting.GREEN + "Full dust: on" : EnumChatFormatting.GRAY + "Full dust: off", lore);
            if (c.full) glow(full);
            icons.setInventorySlotContents(FULL_SLOT, full);
        }
        if (pages > 1) {
            if (page > 0) icons.setInventorySlotContents(PREV_SLOT, icon(new ItemStack(Items.arrow), EnumChatFormatting.YELLOW
                + "Previous page (" + page + " of " + pages + ")", null));
            if (page < pages - 1) icons.setInventorySlotContents(NEXT_SLOT, icon(new ItemStack(Items.arrow), EnumChatFormatting.YELLOW
                + "Next page (" + (page + 2) + " of " + pages + ")", null));
        }
        lore = new ArrayList<>();
        int stored = c.stored();
        lore.add(EnumChatFormatting.GRAY + "In its store: " + XpCrafters.mb(stored));
        icons.setInventorySlotContents(MADE_SLOT, icon(new ItemStack(Items.paper), EnumChatFormatting.WHITE + "Made: " + XpCrafters.mb(c.made), lore));
        lore = new ArrayList<>();
        for (String line : new String[] { "Fill it with liquid XP: pipes, or pour", "a bucket or cell into it here.",
            "Essence berries count too: piped into", "it, in a chest touching it, or put in here.",
            "Click what it should make. What it makes", "goes into the chests, inventories and",
            "pipes touching it, or waits in its", "store (the row above) for you.",
            "Broken, it keeps its settings and liquid", "XP in the item; its store drops." })
            lore.add(EnumChatFormatting.GRAY + line);
        icons.setInventorySlotContents(HELP_SLOT, icon(new ItemStack(Items.book), EnumChatFormatting.WHITE + "How it works", lore));
    }

    ItemStack recipeIcon(XpCrafters.XpRecipe r, boolean chosen) {
        XpCrafters.Crafter c = crafter;
        XpCrafters.Full f = XpCrafters.full(r.output);
        boolean whole = c.full && f != null;
        ItemStack s = whole ? f.dust.copy() : r.output.copy();
        int cost = whole ? (int) Math.min(Integer.MAX_VALUE, (long) r.cost * Math.max(1, f.times)) : r.cost;
        List<String> lore = new ArrayList<>();
        lore.add(EnumChatFormatting.GRAY + XpCrafters.mb(cost) + " mB of liquid XP each" + (whole ? " (full dust)" : ""));
        if (whole) lore.add(EnumChatFormatting.DARK_GRAY + "Full dust off: " + XpCrafters.countName(r.output) + ", "
            + XpCrafters.mb(r.cost) + " mB");
        else if (f != null) lore.add(EnumChatFormatting.DARK_GRAY + "Full dust: " + XpCrafters.countName(f.dust) + ", "
            + XpCrafters.mb((long) r.cost * f.times) + " mB");
        if (chosen) {
            lore.add(EnumChatFormatting.GREEN + "Making this now");
            if (mayChange) lore.add(EnumChatFormatting.YELLOW + "Click to stop");
        } else lore.add(mayChange ? EnumChatFormatting.YELLOW + "Click to make this" : EnumChatFormatting.RED + "Protected: you can't change it");
        icon(s, null, lore);
        if (chosen) glow(s);
        return s;
    }

    static ItemStack filler() {
        return pane(BLACK, " ", null);
    }

    static ItemStack pane(int colour, String name, List<String> lore) {
        return icon(new ItemStack(Blocks.stained_glass_pane, 1, colour), name, lore);
    }

    /** Sets an icon's name (null: its own) and tooltip lines. */
    static ItemStack icon(ItemStack s, String name, List<String> lore) {
        NBTTagCompound tag = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        if (name != null) display.setString("Name", EnumChatFormatting.RESET + name);
        if (lore != null && !lore.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (String line : lore) list.appendTag(new NBTTagString(line));
            display.setTag("Lore", list);
        }
        tag.setTag("display", display);
        s.setTagCompound(tag);
        return s;
    }

    /** The enchanted shine, with no enchantment. */
    static void glow(ItemStack s) {
        s.getTagCompound()
            .setTag("ench", new NBTTagList());
    }

    static int colour(XpCrafters.Status s) {
        switch (s) {
            case WORKING:
                return LIME;
            case NEEDS_XP:
            case FULL:
                return YELLOW;
            case NO_RECIPE:
                return LIGHT_GRAY;
            default:
                return RED;
        }
    }

    static EnumChatFormatting statusColour(XpCrafters.Status s) {
        switch (colour(s)) {
            case LIME:
                return EnumChatFormatting.GREEN;
            case YELLOW:
                return EnumChatFormatting.YELLOW;
            case RED:
                return EnumChatFormatting.RED;
            default:
                return EnumChatFormatting.GRAY;
        }
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- clicks -----------------------------------------------------------------------------------------------------

    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer p) {
        ItemStack result = null;
        if (slot >= 0 && slot < SIZE && !isStore(slot)) {
            if (mode == 0 || mode == 1) click(slot, mode == 1, p);
        } else {
            result = super.slotClick(slot, button, mode, p);
        }
        refresh();
        resend(p);
        return result;
    }

    /** An icon clicked (shift: shift-clicked). */
    void click(int slot, boolean shift, EntityPlayer p) {
        XpCrafters.Crafter c = crafter;
        if (slot == XP_SLOT) {
            pour(p, shift);
        } else if (slot >= RECIPES_FROM && slot < RECIPES_FROM + RECIPES) {
            XpCrafters.XpRecipe r = shown[slot - RECIPES_FROM];
            if (r == null) return;
            if (!mayChange) {
                protectedMsg(p);
                return;
            }
            XpCrafters.XpRecipe now = c.makes == null ? null : XpCrafters.recipeFor(c.makes);
            if (now == r) XpCrafters.stop(p, world, data, c);
            else XpCrafters.choose(p, world, data, c, r);
        } else if (slot == FULL_SLOT && LootSpawners.itemOf(TweaksConfig.xpCrafterFullItem) != null) {
            if (!mayChange) {
                protectedMsg(p);
                return;
            }
            if (c.full) {
                XpCrafters.switchFullOff(p, data, c, "Full dust off");
                return;
            }
            ItemStack cursor = p.inventory.getItemStack();
            if (p.capabilities.isCreativeMode) XpCrafters.fullOn(p, world, data, c, null);
            else if (XpCrafters.isFullItem(cursor)) {
                ItemStack paid = cursor.splitStack(1);
                if (cursor.stackSize <= 0) p.inventory.setItemStack(null);
                XpCrafters.fullOn(p, world, data, c, paid);
            } else tell(p, EnumChatFormatting.YELLOW, "Pick up a " + new ItemStack(LootSpawners.itemOf(TweaksConfig.xpCrafterFullItem), 1,
                Math.max(0, LootSpawners.metaOf(TweaksConfig.xpCrafterFullItem))).getDisplayName() + " and click this with it.");
        } else if (slot == PREV_SLOT && page > 0) {
            page--;
        } else if (slot == NEXT_SLOT) {
            page++;
        }
    }

    /**
     * Pours the liquid XP container held on the cursor into the tank (shift: all of them), the empties to the player; solid
     * XP on the cursor goes into the XP it holds towards the next craft (shift: the whole stack).
     */
    void pour(EntityPlayer p, boolean all) {
        TileXpCrafter tank = XpCrafters.tankAt(world, crafter.x, crafter.y, crafter.z);
        ItemStack cursor = p.inventory.getItemStack();
        if (tank == null) return;
        int solid = XpCrafters.solidXp(cursor);
        if (solid > 0) {
            int n = all ? cursor.stackSize : 1;
            crafter.bank = (int) Math.min(Integer.MAX_VALUE, crafter.bank + (long) n * solid);
            cursor.stackSize -= n;
            if (cursor.stackSize <= 0) p.inventory.setItemStack(null);
            data.markDirty();
            XpCrafters.hudUpdate(world, crafter);
            ValierTweaks.LOG.info("{} put {} solid XP ({} mB) into the XP crafter at {}", p.getCommandSenderName(), n, n * solid,
                crafter.where());
            return;
        }
        if (cursor == null || XpCrafters.xpAmount(cursor) <= 0) {
            if (cursor != null) tell(p, EnumChatFormatting.RED, cursor.getDisplayName() + " holds no liquid XP and isn't solid XP.");
            return;
        }
        int poured = 0;
        while (cursor != null && cursor.stackSize > 0) {
            ItemStack one = cursor.copy();
            one.stackSize = 1;
            FluidStack in = null;
            ItemStack empty = null;
            if (FluidContainerRegistry.isFilledContainer(one)) {
                in = FluidContainerRegistry.getFluidForFilledItem(one);
                empty = FluidContainerRegistry.drainFluidContainer(one);
            } else if (one.getItem() instanceof IFluidContainerItem) {
                IFluidContainerItem item = (IFluidContainerItem) one.getItem();
                FluidStack held = item.getFluid(one);
                in = held == null ? null : item.drain(one, held.amount, true);
                empty = one;
            }
            if (in == null || in.amount <= 0 || !XpCrafters.isXp(in.getFluid()) || tank.fill(in, false) < in.amount) break;
            tank.fill(in, true);
            poured += in.amount;
            cursor.stackSize--;
            if (cursor.stackSize <= 0) {
                p.inventory.setItemStack(empty);
                cursor = null;
            } else if (empty != null) LootSpawners.give(p, empty);
            if (!all) break;
        }
        if (cursor != null && cursor.stackSize <= 0) p.inventory.setItemStack(null);
        if (poured > 0) {
            XpCrafters.hudUpdate(world, crafter);
            ValierTweaks.LOG.info("{} poured {} mB of liquid XP into the XP crafter at {}", p.getCommandSenderName(), poured, crafter.where());
        } else tell(p, EnumChatFormatting.RED, "That doesn't fit in its tank.");
    }

    void protectedMsg(EntityPlayer p) {
        tell(p, EnumChatFormatting.RED, "You can't change this XP crafter: it's protected.");
    }

    static void tell(EntityPlayer p, EnumChatFormatting colour, String s) {
        IChatComponent msg = new ChatComponentText(s);
        msg.getChatStyle()
            .setColor(colour);
        p.addChatMessage(msg);
    }

    /** The whole window again, and what the cursor holds: a client's guess at a click is put right. */
    void resend(EntityPlayer p) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null && p.openContainer == this)
            ((EntityPlayerMP) p).sendContainerAndContentsToPlayer(this, getInventory());
    }

    @Override
    public void detectAndSendChanges() {
        if (++ticks % REFRESH == 0) refresh();
        super.detectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        XpCrafters.Crafter c = crafter;
        return XpCrafters.crafterAt(world, c.x, c.y, c.z) == c && XpCrafters.tankState(world, c.x, c.y, c.z) != 0
            && p.worldObj == world && p.getDistanceSq(c.x + 0.5, c.y + 0.5, c.z + 0.5) <= 64;
    }

    /** Shift-click on the store: into the player's inventory. Nothing goes into the menu this way. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        if (!isStore(index)) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack() || !slot.canTakeStack(p)) return null;
        ItemStack s = slot.getStack(), copy = s.copy();
        if (!mergeItemStack(s, SIZE, SIZE + 36, true)) return null;
        if (s.stackSize <= 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }

    /** A double-click gathering items never takes from the menu. */
    @Override
    public boolean func_94530_a(ItemStack stack, Slot slot) {
        return slot.slotNumber >= SIZE;
    }

    @Override
    public boolean canDragIntoSlot(Slot slot) {
        return slot.slotNumber >= SIZE;
    }

    // ---- slots ------------------------------------------------------------------------------------------------------

    /** An icon: shown, never taken or replaced. */
    static final class IconSlot extends Slot {

        IconSlot(IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return false;
        }
    }

    /** A slot of the store: items can be taken (if the player may change the crafter), never put in. */
    static final class StoreSlot extends Slot {

        final XpMenu menu;

        StoreSlot(XpMenu menu, int index, int x, int y) {
            super(menu.store, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return menu.mayChange;
        }
    }

    /** The crafter's store as an inventory, for its slots. */
    static final class Store implements IInventory {

        final XpCrafters.Data data;
        final XpCrafters.Crafter c;

        Store(XpCrafters.Data data, XpCrafters.Crafter c) {
            this.data = data;
            this.c = c;
        }

        @Override
        public int getSizeInventory() {
            return XpCrafters.Crafter.STORE;
        }

        @Override
        public ItemStack getStackInSlot(int i) {
            return c.store[i];
        }

        @Override
        public ItemStack decrStackSize(int i, int n) {
            ItemStack s = c.store[i];
            if (s == null) return null;
            ItemStack out;
            if (s.stackSize <= n) {
                out = s;
                c.store[i] = null;
            } else out = s.splitStack(n);
            markDirty();
            return out;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int i) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int i, ItemStack s) {
            c.store[i] = s == null || s.stackSize <= 0 ? null : s;
            markDirty();
        }

        @Override
        public String getInventoryName() {
            return TITLE;
        }

        @Override
        public boolean hasCustomInventoryName() {
            return true;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {
            data.markDirty();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer p) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int i, ItemStack s) {
            return false;
        }
    }
}
