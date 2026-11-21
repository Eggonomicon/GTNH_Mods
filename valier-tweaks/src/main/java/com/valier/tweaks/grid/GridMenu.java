package com.valier.tweaks.grid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
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
 * A Grid block's menu: its network (type a name or click one of your team's, then Link; Unlink takes it off),
 * priority, Surge, limit, redstone, a point's voltage for cables, a controller's "charge me", and how the block and its
 * network are doing. The server sends it all as one compound when it changes ({@link PipeFaceMessage}).
 */
public class GridMenu extends Container implements ValierMenu {

    public static final int NET = 70, UNLINK = 71, PRIORITY = 72, SURGE = 73, LIMIT = 74, REDSTONE = 75, VOLTS = 76, CHARGE_ME = 77,
        COLOUR = 78, BLOCKS = 79;
    /** The most blocks the network page lists. */
    public static final int LISTED = 60;

    final TileGrid tile;
    final World world;
    final EntityPlayer viewer;
    public final int x, y, z;
    /** What the screen shows (from the server). */
    public NBTTagCompound state = new NBTTagCompound();
    public final List<String[]> networks = new ArrayList<>();
    /** The network page (on the client): its blocks, from the server. */
    public final List<NBTTagCompound> blocks = new ArrayList<>();
    /** The viewer has the network page open: only then is its block list built and sent. */
    boolean listing;
    private String lastSent;

    public GridMenu(EntityPlayer viewer, TileGrid tile, World world, int x, int y, int z) {
        this.viewer = viewer;
        this.tile = tile;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    boolean server() {
        return tile != null && !world.isRemote;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        if (p.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false;
        return tile == null || !tile.isInvalid() && world.getTileEntity(x, y, z) == tile;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int i) {
        return null;
    }

    @Override
    public void apply(EntityPlayer p, int action, int a, int b, ItemStack stack, String text) {
        if (!server() || !LootSpawners.mayChange(p, world, x, y, z)) return;
        TileGrid t = tile;
        switch (action) {
            case NET: {
                String name = GridNets.clean(text);
                if (name.isEmpty()) return;
                String scope = t.net.isEmpty() ? t.ownerScope(p.getUniqueID()) : t.scope;
                GridNets.link(t, scope, name);
                break;
            }
            case UNLINK:
                GridNets.link(t, t.scope, "");
                break;
            case PRIORITY:
                t.priority = Math.max(-999, Math.min(999, t.priority + Math.max(-100, Math.min(100, a))));
                if (t.network != null) t.network.resort();
                break;
            case SURGE:
                t.surge = !t.surge;
                if (t.network != null) t.network.resort();
                break;
            case LIMIT: { // a: the step (EU a tick); at the tier's most it is "no limit" again
                long full = t instanceof TileGridCell ? GridTier.perTick(t.tier) * 4
                    : t instanceof TileGridController ? GridTier.perTick(2) * 4 : GridTier.perTick(t.tier);
                long now = t.limit > 0 ? t.limit : full;
                long next = Math.max(1, Math.min(full, now + (long) a));
                t.limit = next >= full ? 0 : next;
                break;
            }
            case REDSTONE:
                t.redstone = Math.floorMod(t.redstone + (a < 0 ? -1 : 1), 3);
                break;
            case VOLTS:
                if (!(t instanceof TileGridPoint)) return;
                TileGridPoint pt = (TileGridPoint) t;
                long cur = pt.cableVolts();
                int i = 0;
                while (i < GridTier.COUNT - 1 && GridTier.VOLTS[i] < cur) i++;
                i = Math.floorMod(i + (a < 0 ? -1 : 1), t.tier + 1);
                pt.volts = i >= t.tier ? 0 : GridTier.VOLTS[i];
                break;
            case CHARGE_ME:
                if (!(t instanceof TileGridController)) return;
                TileGridController c = (TileGridController) t;
                if (!c.skip.remove(p.getUniqueID())) c.skip.add(p.getUniqueID());
                break;
            case BLOCKS: // the network page, open or closed (nothing of the block changes)
                listing = !listing;
                detectAndSendChanges();
                return;
            case COLOUR: { // the network's colour: every loaded block of it shows the new one (the rest when they load)
                if (t.net.isEmpty()) return;
                int next = Math.floorMod(GridColours.of(t.scope, t.net) + (a < 0 ? -1 : 1), 16);
                GridColours.set(t.scope, t.net, next);
                if (t.network != null) t.network.colour = next;
                if (t.network != null) for (TileGrid g : t.network.all()) g.getWorldObj()
                    .markBlockForUpdate(g.xCoord, g.yCoord, g.zCoord);
                else world.markBlockForUpdate(x, y, z);
                detectAndSendChanges();
                return; // nothing of the block itself changed
            }
            default:
                return;
        }
        t.markChunk();
        detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!server()) return;
        NBTTagCompound t = state(viewer);
        String s = t.toString();
        if (s.equals(lastSent)) return;
        lastSent = s;
        for (Object o : crafters) if (o instanceof EntityPlayerMP) ValierTweaks.NET.sendTo(new PipeFaceMessage(windowId, t), (EntityPlayerMP) o);
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        lastSent = null;
        detectAndSendChanges();
    }

    /** Everything the screen shows (server side). */
    NBTTagCompound state(EntityPlayer p) {
        TileGrid t = tile;
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("Kind", t.kind());
        s.setInteger("Tier", t.tier);
        s.setString("Net", t.net);
        s.setInteger("Priority", t.priority);
        s.setBoolean("Surge", t.surge);
        s.setLong("Limit", t.maxPerTick());
        s.setBoolean("Capped", t.limit > 0);
        s.setInteger("Redstone", t.redstone);
        s.setLong("Moved", t.movedLast);
        int colour = GridColours.of(t.scope, t.net);
        s.setInteger("Colour", GridColours.rgb(colour));
        s.setString("ColourName", GridColours.name(colour));
        s.setInteger("Loss", com.valier.tweaks.TweaksConfig.gridLossPercent);
        if (t instanceof TileGridPoint) s.setLong("Volts", ((TileGridPoint) t).cableVolts());
        if (t instanceof TileGridCell) {
            s.setLong("Stored", ((TileGridCell) t).stored);
            s.setLong("Capacity", ((TileGridCell) t).capacity());
        }
        if (t instanceof TileGridPlug) s.setLong("Buffer", ((TileGridPlug) t).eu);
        if (t instanceof TileGridController && p != null) s.setBoolean("ChargeMe", !((TileGridController) t).skip.contains(p.getUniqueID()));
        GridNet n = t.network;
        if (n != null) {
            s.setInteger("Plugs", n.plugs.size());
            s.setInteger("Points", n.points.size());
            s.setInteger("Cells", n.cells.size());
            s.setInteger("Controllers", n.controllers.size());
            s.setLong("In", n.lastIn);
            s.setLong("Out", n.lastOut);
            s.setLong("Lost", n.lastLost);
            s.setLong("MinuteIn", n.minuteIn());
            s.setLong("MinuteOut", n.minuteOut());
            s.setLong("NetStored", n.stored());
            s.setLong("NetCapacity", n.capacity());
            if (listing) blocks(s, n);
        }
        s.setBoolean("Listing", listing);
        NBTTagList list = new NBTTagList();
        String scope = t.net.isEmpty() ? t.ownerScope(p == null ? null : p.getUniqueID()) : t.scope;
        int k = 0;
        for (GridNet g : GridNets.of(scope)) {
            if (k++ >= 12) break;
            NBTTagCompound e = new NBTTagCompound();
            e.setString("Name", g.name);
            e.setInteger("Blocks", g.blocks());
            e.setInteger("Colour", GridColours.rgb(GridColours.of(g.scope, g.name)));
            list.appendTag(e);
        }
        s.setTag("Networks", list);
        return s;
    }

    /** The network page: its blocks (the first {@link #LISTED}) with place and status. */
    private void blocks(NBTTagCompound s, GridNet n) {
        NBTTagList list = new NBTTagList();
        int k = 0;
        for (TileGrid g : n.all()) {
            if (k++ >= LISTED) break;
            NBTTagCompound e = new NBTTagCompound();
            e.setByte("K", (byte) g.kind());
            e.setByte("T", (byte) g.tier);
            World w = g.getWorldObj();
            if (w != null) {
                e.setInteger("D", w.provider.dimensionId);
                e.setString("DN", w.provider.getDimensionName());
            }
            e.setInteger("X", g.xCoord);
            e.setInteger("Y", g.yCoord);
            e.setInteger("Z", g.zCoord);
            e.setLong("M", g.movedLast);
            if (!g.active()) e.setBoolean("Off", true);
            if (g.surge) e.setBoolean("Surge", true);
            else if (g.priority != 0) e.setInteger("P", g.priority);
            if (g instanceof TileGridCell) {
                e.setLong("S", ((TileGridCell) g).stored);
                e.setLong("C", ((TileGridCell) g).capacity());
            }
            if (g == tile) e.setBoolean("Here", true);
            list.appendTag(e);
        }
        s.setTag("Blocks", list);
        s.setInteger("BlockCount", n.blocks());
    }

    @Override
    public void receive(NBTTagCompound t) {
        state = t;
        blocks.clear();
        NBTTagList bl = t.getTagList("Blocks", 10);
        for (int i = 0; i < bl.tagCount(); i++) blocks.add(bl.getCompoundTagAt(i));
        networks.clear();
        NBTTagList list = t.getTagList("Networks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            networks.add(new String[] { e.getString("Name"), String.valueOf(e.getInteger("Blocks")), String.valueOf(e.getInteger("Colour")) });
        }
    }

    public static Object server(EntityPlayer p, World w, int x, int y, int z) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileGrid) || !LootSpawners.mayChange(p, w, x, y, z)) return null;
        return new GridMenu(p, (TileGrid) te, w, x, y, z);
    }

    public static GridMenu client(EntityPlayer p, World w, int x, int y, int z) {
        return new GridMenu(p, null, w, x, y, z);
    }
}
