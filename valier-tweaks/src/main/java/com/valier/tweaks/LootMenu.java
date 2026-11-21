package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.valier.tweaks.pipe.ContainerPipeFace;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FilterList;
import com.valier.tweaks.pipe.ListView;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.pipe.PipeFaceMessage;
import com.valier.tweaks.pipe.ValierMenu;

/**
 * A loot spawner's menu (both sides; a plain right-click opens it for players who may change the spawner). Three
 * views, picked by tabs: its store (the 27 slots of loot nothing touching it took yet: take from them, never put in),
 * and its two filter lists, Allow and Void ({@link LootSpawnerTile#lets}), shown in 18 ghost slots with the same
 * toolbar as a pipe face's lists (items or mods, damage, NBT, ore dictionary, snapshot of the store, clear). Buttons
 * come as {@link PipeActionMessage}s; the server sends the lists and the spawner's state as one compound
 * ({@link PipeFaceMessage}) whenever they change.
 * <p>
 * Slots: 0-17 the ghosts, 18-44 the store, then the player's inventory. The screen moves whichever of the first two
 * isn't shown off screen.
 */
public class LootMenu extends Container implements ValierMenu {

    /** The store view; the lists are {@link FaceConfig#ALLOW} and {@link FaceConfig#VOID}. */
    public static final int STORE = -1;
    public static final int GHOST_Y = 82, STORE_Y = 61, INV_Y = 133, HOTBAR_Y = 191;
    public static final int STORE_FROM = FaceConfig.FILTER, INV_FROM = STORE_FROM + LootSpawnerTile.SLOTS;

    /** The spawner (server only; null on players' computers). */
    final LootSpawnerTile tile;
    final World world;
    public final int x, y, z;
    /** The client's copy of the lists (the server uses the tile's). */
    final FilterList allowCopy = new FilterList(), voidCopy = new FilterList();
    /** Which view is shown, and the list the ghost slots show (the last list picked). */
    public boolean store = true;
    public int list = FaceConfig.VOID;
    /** From the server, for the screen. */
    public String line1 = "", line2 = "", line3 = "", full = "", note = "";
    public long voided;
    private String lastSent;

    public LootMenu(InventoryPlayer inv, LootSpawnerTile tile, IInventory store, World world, int x, int y, int z) {
        this.tile = tile;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        IInventory view = new ListView(this::fl);
        for (int i = 0; i < FaceConfig.FILTER; i++) addSlotToContainer(new ContainerPipeFace.Ghost(view, i, 8 + (i % 9) * 18,
            GHOST_Y + (i / 9) * 18));
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) addSlotToContainer(new TakeOnly(store, i, 8 + (i % 9) * 18, STORE_Y + (i / 9) * 18));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c, 8 + c * 18, HOTBAR_Y));
    }

    public FilterList listAt(int l) {
        if (tile != null) return l == FaceConfig.ALLOW ? tile.allow : tile.voids;
        return l == FaceConfig.ALLOW ? allowCopy : voidCopy;
    }

    /** The list the ghost slots show. */
    public FilterList fl() {
        return listAt(list);
    }

    boolean server() {
        return tile != null && !world.isRemote;
    }

    boolean may(EntityPlayer player) {
        return LootSpawners.mayChange(player, world, x, y, z);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false;
        if (tile == null) return true;
        return !tile.isInvalid() && world.getTileEntity(x, y, z) == tile;
    }

    // ---- clicks --------------------------------------------------------------------------------------------------

    /** Ghost slots: left-click with an item sets it, right-click clears; never take. */
    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        if (slot >= 0 && slot < STORE_FROM) {
            if (mode == 0 && server() && !store && may(player)) {
                ItemStack cursor = player.inventory.getItemStack();
                if (button == 1) {
                    fl().filter[slot] = null;
                    fl().count[slot] = 0;
                    changed();
                } else if (cursor != null) {
                    fl().filter[slot] = ContainerPipeFace.one(cursor);
                    changed();
                }
            }
            return null;
        }
        if (mode == 6) return null; // double-click collect would reach into the ghosts
        return super.slotClick(slot, button, mode, player);
    }

    @Override
    public boolean func_94530_a(ItemStack stack, Slot slot) {
        return !(slot instanceof ContainerPipeFace.Ghost);
    }

    /** Shift-click: from the store into the inventory; from the inventory into the list shown, as a ghost. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || slot instanceof ContainerPipeFace.Ghost || !slot.getHasStack()) return null;
        ItemStack s = slot.getStack();
        if (index >= STORE_FROM && index < INV_FROM) {
            ItemStack copy = s.copy();
            if (!mergeItemStack(s, INV_FROM, INV_FROM + 36, true)) return null;
            if (s.stackSize <= 0) slot.putStack(null);
            else slot.onSlotChanged();
            return copy;
        }
        if (store || !server() || !may(player)) return null;
        FilterList l = fl();
        ItemStack one = ContainerPipeFace.one(s);
        for (int i = 0; i < FaceConfig.FILTER; i++) if (l.filter[i] != null && ItemStack.areItemStacksEqual(l.filter[i], one)) return null;
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            if (l.filter[i] == null) {
                l.filter[i] = one;
                changed();
                break;
            }
        }
        return null;
    }

    @Override
    public void apply(EntityPlayer player, int action, int a, int b, ItemStack stack, String text) {
        if (!server()) return;
        if (action == PipeActionMessage.LIST) { // only what the menu shows
            if (a == STORE) store = true;
            else if (a == FaceConfig.ALLOW || a == FaceConfig.VOID) {
                store = false;
                list = a;
            }
            detectAndSendChanges();
            return;
        }
        if (store || !may(player)) return;
        FilterList l = fl();
        switch (action) {
            case PipeActionMessage.KIND: // items or mods: a spawner has no block whose items it could match
                if (a != FaceConfig.Kind.ITEMS.ordinal() && a != FaceConfig.Kind.MODS.ordinal()) return;
                l.kind = FaceConfig.Kind.values()[a];
                break;
            case PipeActionMessage.DAMAGE: {
                FaceConfig.Damage[] all = FaceConfig.Damage.values();
                l.damage = all[Math.floorMod(l.damage.ordinal() + (a < 0 ? -1 : 1), all.length)];
                break;
            }
            case PipeActionMessage.NBT:
                l.matchNbt = !l.matchNbt;
                break;
            case PipeActionMessage.ORE:
                l.oreDict = !l.oreDict;
                break;
            case PipeActionMessage.SNAPSHOT:
                snapshot(l);
                break;
            case PipeActionMessage.CLEAR:
                l.clear();
                break;
            case PipeActionMessage.SET_SLOT:
                if (a < 0 || a >= FaceConfig.FILTER || stack == null || stack.getItem() == null) return;
                l.filter[a] = ContainerPipeFace.one(stack);
                break;
            default:
                return;
        }
        changed();
    }

    /** The list becomes what the store holds now (each item once, up to 18). */
    void snapshot(FilterList l) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack s : tile.items) {
            if (s == null || found.size() >= FaceConfig.FILTER) continue;
            boolean seen = false;
            for (ItemStack f : found) seen |= MobLoot.sameItem(f, s);
            if (!seen) found.add(ContainerPipeFace.one(s));
        }
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            l.filter[i] = i < found.size() ? found.get(i) : null;
            l.count[i] = 0;
        }
        l.kind = FaceConfig.Kind.ITEMS;
    }

    /** A list changed: what the store keeps that the filter no longer lets out is destroyed, and the player is told. */
    void changed() {
        int gone = tile.purge();
        note = gone > 0 ? "The filter destroyed " + gone + " stored item" + (gone == 1 ? "" : "s") : "";
        tile.markDirty();
        ValierTweaks.LOG.info("The loot filter of the spawner at {} changed (Allow {}, Void {})", tile.where(), tile.allow.entries(),
            tile.voids.entries());
        detectAndSendChanges();
    }

    // ---- what the screen shows -----------------------------------------------------------------------------------

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!server()) return;
        NBTTagCompound t = state();
        String s = t.toString();
        if (s.equals(lastSent)) return;
        lastSent = s;
        for (Object o : crafters) {
            if (o instanceof EntityPlayerMP) ValierTweaks.NET.sendTo(new PipeFaceMessage(windowId, t), (EntityPlayerMP) o);
        }
    }

    /** What the screen is sent: the lists, the view, and how the spawner is doing (server side). */
    NBTTagCompound state() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound a = new NBTTagCompound(), v = new NBTTagCompound();
        tile.allow.write(a);
        tile.voids.write(v);
        t.setTag("Allow", a);
        t.setTag("Void", v);
        t.setBoolean("Store", store);
        t.setByte("List", (byte) list);
        String[] lines = lines(tile);
        t.setString("L1", lines[0]);
        t.setString("L2", lines[1]);
        t.setString("L3", lines[2]);
        t.setString("Full", LootSpawners.status(tile)
            .getUnformattedText());
        t.setLong("Voided", tile.voidedLastHour());
        t.setString("Note", note);
        return t;
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        lastSent = null;
        detectAndSendChanges();
    }

    @Override
    public void receive(NBTTagCompound t) {
        allowCopy.read(t.getCompoundTag("Allow"));
        voidCopy.read(t.getCompoundTag("Void"));
        store = t.getBoolean("Store");
        int l = t.getByte("List");
        list = l == FaceConfig.ALLOW ? FaceConfig.ALLOW : FaceConfig.VOID;
        line1 = t.getString("L1");
        line2 = t.getString("L2");
        line3 = t.getString("L3");
        full = t.getString("Full");
        voided = t.getLong("Voided");
        note = t.getString("Note");
    }

    /** Three short lines: the mob and how much a batch is; what it is doing; its upgrades. */
    static String[] lines(LootSpawnerTile t) {
        String mob = t.candidates.isEmpty() ? "Nothing" : t.candidates.size() == 1 ? LootSpawners.mobWords(t.candidates.get(0).type)
            : t.candidates.size() + " kinds of mob";
        int count = Math.max(1, t.spawnCount);
        String l1 = mob + ", " + count + (count == 1 ? " mob" : " mobs") + " a batch";
        String l2;
        switch (t.status) {
            case WORKING:
            case STARTING:
                l2 = t.timer > 0 ? "Working: next batch in " + Math.round(t.timer / 20.0) + " s" : "Working";
                break;
            case FULL:
                l2 = "Waiting: everything touching it is full";
                break;
            case NO_OUTPUT:
                l2 = "Waiting: nothing touching it takes items";
                break;
            case NO_PLAYER:
                l2 = "Waiting: no player within 16 blocks";
                break;
            default:
                l2 = "It makes nothing";
        }
        List<String> up = new ArrayList<>();
        if (t.looting.level() > 0) up.add("Looting " + LootSpawners.roman(t.looting.level()));
        if (t.speedLevel() > 0) up.add("Speed " + LootSpawners.roman(t.speedLevel()));
        if (t.xpBottles) up.add("XP bottles");
        if (t.noDamaged) up.add("no damaged items");
        String l3 = up.isEmpty() ? "No upgrades" : String.join(", ", up);
        return new String[] { l1, l2, l3 };
    }

    /** The store's slots: take only (the spawner fills them). */
    static final class TakeOnly extends Slot {

        TakeOnly(IInventory inv, int i, int x, int y) {
            super(inv, i, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return false;
        }
    }

    /** Opens the menu (server side), or tells a player who may not change the spawner how it is doing. */
    static void open(EntityPlayer player, LootSpawnerTile t) {
        if (LootSpawners.mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord))
            player.openGui(ValierTweaks.instance, ValierGui.LOOT, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord);
        else LootSpawners.tellStatus(player, t);
    }

    static Object server(EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof LootSpawnerTile)) return null;
        LootSpawnerTile t = (LootSpawnerTile) te;
        if (!LootSpawners.mayChange(player, world, x, y, z)) return null;
        return new LootMenu(player.inventory, t, t, world, x, y, z);
    }

    static LootMenu client(EntityPlayer player, World world, int x, int y, int z) {
        return new LootMenu(player.inventory, null, new net.minecraft.inventory.InventoryBasic("Loot Spawner", false,
            LootSpawnerTile.SLOTS), world, x, y, z);
    }
}
