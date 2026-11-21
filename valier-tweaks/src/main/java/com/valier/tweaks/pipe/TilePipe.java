package com.valier.tweaks.pipe;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * A pipe. It never ticks and holds nothing in transit: {@link PipeNetworks} finds which pipes join up and runs only the
 * Extract and Request faces, each on its own timer, moving straight from block to block. The pipe keeps which of its
 * sides join (other pipes of its kind, or a block with an inventory or tank), which sides a player shut with a wrench,
 * and the settings of each face touching a block ({@link FaceConfig}).
 * <p>
 * Players' computers get one number per pipe: its joins and the face modes ({@link #renderState}), all the renderer
 * reads.
 */
public class TilePipe extends TileEntity {

    /** Sides shut with a wrench (bit per ForgeDirection ordinal). */
    int blocked;
    /** Bits 0-5: joined to a pipe on that side; bits 6-11: touching a block with an inventory (or tank) there. */
    int conn;
    final FaceConfig[] faces = new FaceConfig[6];
    /** A creative pipe's centre: what it makes, endlessly ({@link CreativeSource}); null on other tiers. */
    FaceConfig source;
    /** The "side" of a creative pipe's source settings (menus, the network). */
    public static final int SOURCE = 6;
    UUID owner;
    String ownerName = "";
    /** Players' computers: {@link #conn} plus three bits per side for the face modes, from bit 12. */
    public volatile int renderState;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public PipeKind kind() {
        Block b = getBlockType();
        return b instanceof BlockPipe ? ((BlockPipe) b).kind : PipeKind.ITEM;
    }

    public int tier() {
        return PipeTier.clamp(getBlockMetadata());
    }

    public boolean pipeOn(int side) {
        return (conn >> side & 1) != 0;
    }

    public boolean blockOn(int side) {
        return (conn >> (6 + side) & 1) != 0;
    }

    public boolean creative() {
        return tier() == PipeTier.CREATIVE;
    }

    /**
     * The face settings of a side touching a block (made with the defaults the first time), or null. Side
     * {@link #SOURCE}: a creative pipe's source settings.
     */
    public FaceConfig face(int side) {
        if (side == SOURCE) {
            if (!creative()) return null;
            if (source == null) source = new FaceConfig();
            return source;
        }
        if (side < 0 || side > 5 || !blockOn(side)) return null;
        if (faces[side] == null) faces[side] = new FaceConfig();
        return faces[side];
    }

    /** An item pipe whose face on that side takes things out of the block there (Extract, or Provide for Requests). */
    public boolean pullsFrom(int side) {
        if (kind() != PipeKind.ITEM || !blockOn(side)) return false;
        FaceConfig f = faces[side];
        FaceMode m = f == null ? FaceMode.PROVIDE : f.mode;
        return m == FaceMode.EXTRACT || m == FaceMode.PROVIDE || m == FaceMode.PUSH_PULL;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    // ---- joins ------------------------------------------------------------------------------------------------------

    /** Works out its joins again (server); tells players and the network when they changed. */
    public void updateConnections() {
        if (worldObj == null || worldObj.isRemote) return;
        int c = 0;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int s = d.ordinal();
            if ((blocked >> s & 1) != 0) continue;
            int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
            if (y < 0 || y >= worldObj.getHeight() || !worldObj.blockExists(x, y, z)) continue; // never load a chunk
            TileEntity te = worldObj.getTileEntity(x, y, z);
            if (te instanceof TilePipe) {
                TilePipe p = (TilePipe) te;
                if (p.kind() == kind() && (p.blocked >> d.getOpposite()
                    .ordinal() & 1) == 0) c |= 1 << s;
            } else if (isTarget(kind(), te, d.getOpposite()) && TeamScope.mayUse(owner, worldObj, x, z)) c |= 1 << (6 + s);
        }
        if (c != conn) {
            conn = c;
            for (int s = 0; s < 6; s++) if (!blockOn(s) && faces[s] != null && faces[s].heldItems.isEmpty() && faces[s].heldFluid == null)
                faces[s] = null; // forget the settings of a side that no longer touches anything (unless it holds something)
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            PipeNetworks.dirty(worldObj, xCoord, yCoord, zCoord);
        }
    }

    /** A block a pipe of this kind works with: an inventory with slots (items), a tank (fluids); never another pipe. */
    public static boolean isTarget(PipeKind kind, TileEntity te, ForgeDirection from) {
        if (te == null || te.isInvalid() || te instanceof TilePipe) return false;
        if (kind == PipeKind.ITEM) return te instanceof IInventory && ((IInventory) te).getSizeInventory() > 0;
        if (!(te instanceof IFluidHandler)) return false;
        try {
            FluidTankInfo[] info = ((IFluidHandler) te).getTankInfo(from);
            return info != null && info.length > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** A wrench on a side: shut it (or open it again); the pipe on the other side follows. */
    public void toggleSide(int side) {
        blocked ^= 1 << side;
        updateConnections();
        ForgeDirection d = ForgeDirection.getOrientation(side);
        TileEntity te = worldObj.getTileEntity(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
        if (te instanceof TilePipe) ((TilePipe) te).updateConnections();
        markDirty();
    }

    /** Settings changed (mode, filter, tag...): players see the new mode, and the network takes it in. */
    public void faceChanged() {
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        PipeNetworks.dirty(worldObj, xCoord, yCoord, zCoord);
    }

    int renderBits() {
        int s = conn & 0xFFF;
        for (int i = 0; i < 6; i++) {
            FaceConfig f = blockOn(i) ? faces[i] : null;
            int m = f == null ? FaceMode.PROVIDE.ordinal() : f.mode.ordinal();
            s |= (m & 7) << (12 + 3 * i);
        }
        return s;
    }

    // ---- the network --------------------------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) PipeNetworks.dirty(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (worldObj != null && !worldObj.isRemote) PipeNetworks.dirty(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (worldObj != null && !worldObj.isRemote) PipeNetworks.dirty(worldObj, xCoord, yCoord, zCoord);
    }

    // ---- players' computers --------------------------------------------------------------------------------------

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("S", renderBits());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        if (t == null) return;
        renderState = t.getInteger("S");
        conn = renderState & 0xFFF;
        if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("Blocked", (byte) blocked);
        nbt.setShort("Conn", (short) conn);
        NBTTagList list = new NBTTagList();
        for (int s = 0; s < 6; s++) {
            if (faces[s] == null) continue;
            NBTTagCompound f = faces[s].write();
            f.setByte("Side", (byte) s);
            list.appendTag(f);
        }
        if (list.tagCount() > 0) nbt.setTag("Faces", list);
        if (source != null) nbt.setTag("Source", source.settings());
        if (owner != null) {
            nbt.setLong("OwnerMost", owner.getMostSignificantBits());
            nbt.setLong("OwnerLeast", owner.getLeastSignificantBits());
            nbt.setString("OwnerName", ownerName);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        blocked = nbt.getByte("Blocked") & 63;
        conn = nbt.getShort("Conn") & 0xFFF;
        for (int s = 0; s < 6; s++) faces[s] = null;
        NBTTagList list = nbt.getTagList("Faces", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound f = list.getCompoundTagAt(i);
            int s = f.getByte("Side");
            if (s >= 0 && s < 6) faces[s] = FaceConfig.read(f);
        }
        source = nbt.hasKey("Source", 10) ? FaceConfig.read(nbt.getCompoundTag("Source")) : null;
        owner = nbt.hasKey("OwnerMost") ? new UUID(nbt.getLong("OwnerMost"), nbt.getLong("OwnerLeast")) : null;
        ownerName = nbt.getString("OwnerName");
    }
}
