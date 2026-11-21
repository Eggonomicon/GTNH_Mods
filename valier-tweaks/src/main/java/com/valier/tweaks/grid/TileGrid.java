package com.valier.tweaks.grid;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.pipe.TeamScope;

/**
 * A Valier Grid block: plug, point, cell or controller. It never ticks itself: its network does all of them in one loop
 * on the server tick ({@link GridNet}). Its network is a name in its owner's team ({@link TeamScope}); blocks of that
 * name are one network wherever they are, in any dimension, while their chunks are loaded.
 * <p>
 * Settings: priority (higher goes first; Surge goes before everything), a limit (EU a tick, 0: the tier's most),
 * redstone (always, only with a signal, only without), and for points the voltage for cables.
 */
public abstract class TileGrid extends TileEntity {

    public static final int PLUG = 0, POINT = 1, CELL = 2, CONTROLLER = 3;
    public static final int ALWAYS = 0, WITH_SIGNAL = 1, WITHOUT_SIGNAL = 2;

    public int tier;
    UUID owner;
    /** Its network's scope (team) and name; "" when on none. */
    String scope = "", net = "";
    public int priority, redstone;
    public boolean surge;
    /** EU a tick at most (0: the tier's most). */
    public long limit;
    /** Neighbours by side, found again only when a neighbour changes or one goes away. */
    final TileEntity[] near = new TileEntity[6];
    final int[] nearKind = new int[6];
    boolean nearDirty = true;
    boolean powered;
    /** Set when a wrench took it, so breaking it drops nothing more. */
    boolean pickedUp;
    GridNet network;
    /** EU in or out this tick and last second (for menus and WAILA). */
    long moved, movedSecond, movedLast;

    // On players' computers: its network's name and colour (RGB; -1: on none).
    public String shownNet = "";
    public int shownColour = -1;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public abstract int kind();

    public String net() {
        return net;
    }

    public String scope() {
        return scope;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID id) {
        owner = id;
    }

    /** The scope a new network of this block's owner goes into. */
    public String ownerScope(UUID fallback) {
        return String.valueOf(TeamScope.of(owner != null ? owner : fallback));
    }

    /** EU a tick it moves at most. */
    public long maxPerTick() {
        long m = GridTier.perTick(tier);
        return limit > 0 ? Math.min(limit, m) : m;
    }

    boolean active() {
        if (redstone == WITH_SIGNAL) return powered;
        if (redstone == WITHOUT_SIGNAL) return !powered;
        return true;
    }

    int effectivePriority() {
        return surge ? Integer.MAX_VALUE : priority;
    }

    void neighbourChanged() {
        nearDirty = true;
        if (worldObj != null && !worldObj.isRemote) powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
    }

    /**
     * Sides whose neighbour is in another chunk (a bit each), and which of those chunks were loaded at the last look:
     * such a chunk unloads and loads again (with new tiles) without telling this block, and an unloaded one keeps no
     * tile that may be fed.
     */
    private int farSides, farLoaded;

    /** Neighbours, looked up again only when needed. */
    void refreshNear() {
        boolean again = nearDirty || farSides != 0 && farLoaded() != farLoaded;
        if (!again) for (TileEntity t : near) if (t != null && t.isInvalid()) again = true;
        if (!again) return;
        nearDirty = false;
        farSides = 0;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
            if (x >> 4 != xCoord >> 4 || z >> 4 != zCoord >> 4) farSides |= 1 << d.ordinal();
            TileEntity t = worldObj.blockExists(x, y, z) ? worldObj.getTileEntity(x, y, z) : null;
            near[d.ordinal()] = t;
            nearKind[d.ordinal()] = GridEnergy.kind(t, d);
        }
        farLoaded = farLoaded();
    }

    /** Which neighbouring chunks (by side) are loaded: one cheap lookup each, only for sides at a chunk's edge. */
    private int farLoaded() {
        int out = 0;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if ((farSides & 1 << d.ordinal()) != 0 && worldObj.blockExists(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ))
                out |= 1 << d.ordinal();
        return out;
    }

    // ---- joining its network -------------------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) GridNets.loaded(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        GridNets.leave(this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        GridNets.leave(this);
    }

    void markChunk() {
        if (worldObj != null && !worldObj.isRemote) worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("Tier", (byte) tier);
        writeSettings(nbt);
    }

    /** What a wrench and the configurator carry (not its place, nor stored energy). */
    public void writeSettings(NBTTagCompound nbt) {
        if (owner != null) nbt.setString("Owner", owner.toString());
        if (!net.isEmpty()) {
            nbt.setString("Scope", scope);
            nbt.setString("Net", net);
        }
        if (priority != 0) nbt.setInteger("Priority", priority);
        if (redstone != 0) nbt.setByte("Redstone", (byte) redstone);
        if (surge) nbt.setBoolean("Surge", true);
        if (limit > 0) nbt.setLong("Limit", limit);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tier = GridTier.clamp(nbt.getByte("Tier"));
        readSettings(nbt, true);
    }

    public void readSettings(NBTTagCompound nbt, boolean withOwner) {
        if (withOwner) {
            try {
                owner = nbt.hasKey("Owner") ? UUID.fromString(nbt.getString("Owner")) : owner;
            } catch (IllegalArgumentException e) {
                owner = null;
            }
        }
        scope = nbt.getString("Scope");
        net = nbt.getString("Net");
        priority = Math.max(-999, Math.min(999, nbt.getInteger("Priority")));
        redstone = Math.max(0, Math.min(2, nbt.getByte("Redstone")));
        surge = nbt.getBoolean("Surge");
        limit = Math.max(0, nbt.getLong("Limit"));
    }

    /** What players' computers get about it: its network's name and colour (RGB; -1: on none). */
    NBTTagCompound description() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("Net", net);
        t.setInteger("Colour", GridColours.rgb(GridColours.of(scope, net)));
        return t;
    }

    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, description());
    }

    @Override
    public void onDataPacket(NetworkManager nm, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        shownNet = t.getString("Net");
        int c = t.hasKey("Colour") ? t.getInteger("Colour") : -1;
        if (c != shownColour) {
            shownColour = c;
            if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord); // redraw the ring
        }
    }
}
