package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeFaceMessage;
import com.valier.tweaks.pipe.ValierMenu;

/**
 * A barrel's menu (sneak + right-click with an empty hand): what it holds, lock, void overflow, keep (for automation),
 * and its channel: type a name (or click one of your team's channels) and link, or unlink. The server sends its state
 * as one compound ({@link PipeFaceMessage}); buttons come back as {@link com.valier.tweaks.pipe.PipeActionMessage}s
 * with the codes below.
 */
public class BarrelMenu extends Container implements ValierMenu {

    public static final int LOCK = 30, VOID = 31, KEEP = 32, LINK = 33, UNLINK = 34;
    public static final int INV_Y = 140, HOTBAR_Y = 198;

    final TileBarrel tile;
    final World world;
    public final int x, y, z;
    // What the screen shows (from the server).
    public ItemStack item;
    public long count, capacity, keep, stacks;
    public int tier, barrels;
    public boolean locked, voidOverflow;
    public String channel = "", note = "";
    public final List<String[]> channels = new ArrayList<>();
    private String lastSent;

    public BarrelMenu(InventoryPlayer inv, TileBarrel tile, World world, int x, int y, int z) {
        this.tile = tile;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c, 8 + c * 18, HOTBAR_Y));
    }

    boolean server() {
        return tile != null && !world.isRemote;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false;
        return tile == null || !tile.isInvalid() && world.getTileEntity(x, y, z) == tile;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }

    @Override
    public void apply(EntityPlayer player, int action, int a, int b, ItemStack stack, String text) {
        if (!server() || !BarrelClicks.may(player, tile)) return;
        BarrelStore st = tile.store();
        note = "";
        switch (action) {
            case LOCK:
                if (!st.locked && st.item == null) {
                    note = "Put an item in first: a lock keeps its item.";
                    break;
                }
                st.setLocked(!st.locked);
                break;
            case VOID:
                st.voidOverflow = !st.voidOverflow;
                st.changed();
                break;
            case KEEP: {
                long step = Math.max(-100_000, Math.min(100_000, a));
                st.keep = Math.max(0, Math.min(Integer.MAX_VALUE, st.keep + step));
                st.changed();
                break;
            }
            case LINK: {
                BarrelChannels d = BarrelChannels.get();
                if (d == null) return;
                String scope = tile.channel()
                    .isEmpty() ? tile.scopeOf(player.getUniqueID()) : tile.scope;
                String why = d.join(tile, scope, text);
                if (why != null) note = why;
                break;
            }
            case UNLINK: {
                BarrelChannels d = BarrelChannels.get();
                if (d != null && !tile.channel()
                    .isEmpty()) d.leave(tile);
                break;
            }
            default:
                return;
        }
        tile.markDirty();
        detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!server()) return;
        NBTTagCompound t = state(null);
        String s = t.toString();
        if (s.equals(lastSent)) return;
        lastSent = s;
        for (Object o : crafters) {
            if (o instanceof EntityPlayerMP) ValierTweaks.NET.sendTo(new PipeFaceMessage(windowId, state((EntityPlayerMP) o)),
                (EntityPlayerMP) o);
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        lastSent = null;
        detectAndSendChanges();
    }

    /** What the screen is sent (the channel list is the viewer's team's, or the barrel's when linked). */
    NBTTagCompound state(EntityPlayer viewer) {
        BarrelStore st = tile.store();
        NBTTagCompound t = new NBTTagCompound();
        if (st.item != null) t.setTag("Item", st.item.writeToNBT(new NBTTagCompound()));
        t.setLong("Amount", st.count);
        t.setLong("Capacity", st.capacity());
        t.setLong("Stacks", st.stacks);
        t.setLong("Keep", st.keep);
        t.setInteger("Tier", tile.tier);
        t.setBoolean("Locked", st.locked);
        t.setBoolean("Void", st.voidOverflow);
        BarrelChannels.Channel c = tile.linked();
        t.setString("Channel", c == null ? "" : c.name);
        t.setInteger("Barrels", c == null ? 1 : c.barrels());
        t.setString("Note", note);
        BarrelChannels d = BarrelChannels.get();
        NBTTagList list = new NBTTagList();
        if (d != null) {
            String scope = c != null ? c.scope : tile.scopeOf(viewer == null ? null : viewer.getUniqueID());
            int n = 0;
            for (BarrelChannels.Channel ch : d.of(scope)) {
                if (n++ >= 12) break;
                NBTTagCompound e = new NBTTagCompound();
                e.setString("Name", ch.name);
                e.setString("What", ch.store.item == null ? "empty" : String.format("%,d", ch.store.count) + " " + ch.store.item.getDisplayName());
                e.setInteger("Barrels", ch.barrels());
                list.appendTag(e);
            }
        }
        t.setTag("Channels", list);
        return t;
    }

    @Override
    public void receive(NBTTagCompound t) {
        item = t.hasKey("Item", 10) ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Item")) : null;
        count = t.getLong("Amount");
        capacity = t.getLong("Capacity");
        stacks = t.getLong("Stacks");
        keep = t.getLong("Keep");
        tier = t.getInteger("Tier");
        locked = t.getBoolean("Locked");
        voidOverflow = t.getBoolean("Void");
        channel = t.getString("Channel");
        barrels = t.getInteger("Barrels");
        note = t.getString("Note");
        channels.clear();
        NBTTagList list = t.getTagList("Channels", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            channels.add(new String[] { e.getString("Name"), e.getString("What"), String.valueOf(e.getInteger("Barrels")) });
        }
    }

    public static Object server(EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileBarrel) || !LootSpawners.mayChange(player, world, x, y, z)) return null;
        return new BarrelMenu(player.inventory, (TileBarrel) te, world, x, y, z);
    }

    public static BarrelMenu client(EntityPlayer player, World world, int x, int y, int z) {
        return new BarrelMenu(player.inventory, null, world, x, y, z);
    }
}
