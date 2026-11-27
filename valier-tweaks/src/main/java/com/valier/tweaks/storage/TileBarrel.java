package com.valier.tweaks.storage;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.valier.tweaks.pipe.TeamScope;

/**
 * A Valier Barrel: one kind of item, lots of it ({@link BarrelStore}): its own, or its channel's when linked
 * ({@link BarrelChannels}). It never ticks: it only does something when something puts items in or takes them out.
 * <p>
 * To other mods it is an inventory of two slots, as barrels usually are: slot 0 always looks empty and takes what is
 * put in it (hoppers, pipes), slot 1 shows up to a stack of what automation may take (all but the keep amount).
 * Valier pipes skip the slots ({@link BigStorage}). Players' computers get the item, count and capacity for the label
 * ({@link #shownItem}), sent at most every 10 ticks ({@link BarrelSync}).
 */
@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.implementations.tiles.ITileStorageMonitorable", modid = "appliedenergistics2")
public class TileBarrel extends TileEntity
    implements ISidedInventory, BigStorage, appeng.api.implementations.tiles.ITileStorageMonitorable {

    public static final int IN = 0, OUT = 1;
    private static final int[] SLOTS = { IN, OUT };

    public int tier;
    /** The side its label faces (2 to 5). */
    public int facing = 3;
    /** Who placed it; channels are in their team ({@link TeamScope}). */
    UUID owner;
    /** The scope and name of its channel ("" when not linked). */
    String scope = "", channel = "";
    /** Its own store (used when not linked; what it keeps when it leaves a channel). */
    final BarrelStore own = new BarrelStore();
    private BarrelChannels.Channel linked;
    private BarrelStore watching;
    private int lastComparator = -1;
    /** Set when a wrench took it, so breaking it drops nothing more. */
    boolean pickedUp;
    // Slot 1 as other mods see it: the stack given out, and its size then (to notice when they change it in place).
    private ItemStack view;
    private int given;

    // On players' computers: what the label shows.
    public ItemStack shownItem;
    public long shownCount, shownCapacity;
    public boolean shownLocked, shownVoid;
    public String shownChannel = "";
    public int shownBarrels;
    /** The label's text and the count it was made for (client only). */
    public String label;
    public long labelFor = -1;

    public TileBarrel() {
        own.onChange = this::ownChanged;
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    // ---- its store -----------------------------------------------------------------------------------------------

    /** The store it uses now (server). On players' computers: its own, which stays empty. */
    public BarrelStore store() {
        if (worldObj == null || worldObj.isRemote) return own;
        own.stacks = BarrelTier.stacks(tier);
        own.setCreative(tier == BarrelTier.CREATIVE);
        if (!channel.isEmpty() && !own.creative) {
            if (linked == null || linked.removed) {
                BarrelChannels data = BarrelChannels.get();
                linked = data == null ? null : data.resolve(this);
                if (linked == null) channel = ""; // its channel is gone
            }
            if (linked != null) {
                watch(linked.store);
                return linked.store;
            }
        }
        watch(own);
        return own;
    }

    BarrelChannels.Channel linked() {
        return linked;
    }

    void linkTo(BarrelChannels.Channel c) {
        linked = c;
        TileBarrelController.changed(this); // controllers near it find their barrels' stores again
        store();
        markChunk();
        // Storage buses and the like look again (they see the channel's store now, or its own).
        if (worldObj != null && !worldObj.isRemote) worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
    }

    /** An AE2 storage bus on it sees its whole count (only with AE2). */
    @Override
    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    public appeng.api.storage.IStorageMonitorable getMonitorable(net.minecraftforge.common.util.ForgeDirection side,
        appeng.api.networking.security.BaseActionSource src) {
        return worldObj == null || worldObj.isRemote ? null : BarrelAe.of(store());
    }

    private void watch(BarrelStore s) {
        if (watching == s) return;
        if (watching != null) watching.viewers.remove(this);
        watching = s;
        s.viewers.add(this);
        BarrelSync.mark(s); // show it now
    }

    private void unwatch() {
        if (watching != null) watching.viewers.remove(this);
        watching = null;
    }

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) {
            BarrelSync.loaded(this);
            TileBarrelController.changed(this); // a barrel appeared: controllers near it look for their barrels again
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unwatch();
        TileBarrelController.changed(this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unwatch();
        TileBarrelController.changed(this);
    }

    private void ownChanged() {
        markChunk();
    }

    private void markChunk() {
        if (worldObj != null && !worldObj.isRemote) worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    /** Its store changed (at most every 10 ticks): players watching get the label, comparators the level. */
    void storeShown() {
        if (worldObj == null || worldObj.isRemote || isInvalid()) return;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        int c = store().comparator();
        if (c != lastComparator) {
            lastComparator = c;
            worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());
        }
    }

    public String scopeOf(UUID player) {
        return String.valueOf(TeamScope.of(owner != null ? owner : player));
    }

    public String channel() {
        return channel;
    }

    // ---- BigStorage (Valier pipes) -------------------------------------------------------------------------------

    @Override
    public int bigRoom(ItemStack s, int max) {
        return (int) store().room(s, max);
    }

    @Override
    public int bigInsert(ItemStack s, int max) {
        return (int) store().insert(s, Math.min(max, s.stackSize), false);
    }

    @Override
    public ItemStack bigPeek() {
        BarrelStore st = store();
        long n = st.available(true);
        if (n <= 0) return null;
        ItemStack out = st.item.copy();
        out.stackSize = (int) Math.min(Integer.MAX_VALUE, n);
        return out;
    }

    @Override
    public ItemStack bigExtract(int max) {
        return store().extract(max, true, false);
    }

    // ---- ISidedInventory (everything else) -----------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot != OUT) return null;
        reconcile();
        BarrelStore st = store();
        long n = st.available(true);
        if (n <= 0) {
            view = null;
            given = 0;
            return null;
        }
        int size = (int) Math.min(n, st.item.getMaxStackSize());
        if (view == null || !BarrelStore.same(view, st.item)) view = st.item.copy();
        view.stackSize = size;
        given = size;
        return view;
    }

    /** Another mod changed the stack slot 1 gave it in place (and called markDirty): the store follows. */
    private void reconcile() {
        if (view == null || view.stackSize == given) return;
        BarrelStore st = store();
        int delta = view.stackSize - given;
        given = view.stackSize;
        if (delta < 0) st.extract(-delta, false, false);
        else st.insert(view, delta, false);
    }

    @Override
    public ItemStack decrStackSize(int slot, int n) {
        if (slot != OUT || n <= 0) return null;
        reconcile();
        BarrelStore st = store();
        long avail = st.item == null ? 0 : st.available(true);
        int shown = (int) Math.min(avail, st.item == null ? 0 : st.item.getMaxStackSize());
        ItemStack out = shown <= 0 ? null : st.extract(Math.min(n, shown), true, false);
        view = null;
        // The slot now shows what it showed less what was taken. A hopper that can't pass the item on puts back the stack
        // it first saw: only what it took goes back in (counting from 0 here gave it a whole stack more).
        given = Math.max(0, shown - (out == null ? 0 : out.stackSize));
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack s) {
        BarrelStore st = store();
        if (slot == IN) {
            if (s != null && s.stackSize > 0) {
                long took = st.insert(s, s.stackSize, false);
                if (took < s.stackSize && worldObj != null) { // a mod that didn't ask first: never lose it
                    ItemStack rest = s.copy();
                    rest.stackSize = (int) (s.stackSize - took);
                    com.valier.tweaks.LootSpawners.dropAt(worldObj, xCoord, yCoord + 1, zCoord, rest);
                }
            }
            return;
        }
        if (slot != OUT) return;
        int now = s == null ? 0 : s.stackSize;
        int delta = now - given;
        if (delta < 0) st.extract(-delta, false, false);
        else if (delta > 0 && st.matches(s)) st.insert(s, delta, false);
        view = null;
        given = 0;
    }

    @Override
    public void markDirty() {
        reconcile();
        markChunk();
    }

    @Override
    public String getInventoryName() {
        return "Valier Barrel";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack s) {
        return slot == IN && s != null && store().room(s, s.stackSize) >= s.stackSize;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack s, int side) {
        return slot == IN && isItemValidForSlot(IN, s);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack s, int side) {
        return slot == OUT;
    }

    // ---- saving and the label ------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("Tier", (byte) tier);
        nbt.setByte("Facing", (byte) facing);
        if (owner != null) nbt.setString("Owner", owner.toString());
        if (!channel.isEmpty()) {
            nbt.setString("Scope", scope);
            nbt.setString("Channel", channel);
        }
        nbt.setTag("Store", own.write(new NBTTagCompound()));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tier = BarrelTier.clamp(nbt.getByte("Tier"));
        facing = facingOf(nbt.getByte("Facing"));
        try {
            owner = nbt.hasKey("Owner") ? UUID.fromString(nbt.getString("Owner")) : null;
        } catch (IllegalArgumentException e) {
            owner = null;
        }
        scope = nbt.getString("Scope");
        channel = nbt.getString("Channel");
        own.read(nbt.getCompoundTag("Store"));
        linked = null;
    }

    static int facingOf(int f) {
        return f >= 2 && f <= 5 ? f : 3;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        BarrelStore st = store();
        t.setByte("Tier", (byte) tier);
        t.setByte("Facing", (byte) facing);
        if (st.item != null) t.setTag("Item", st.item.writeToNBT(new NBTTagCompound()));
        t.setLong("Amount", st.count);
        t.setLong("Capacity", st.capacity());
        if (st.locked) t.setBoolean("Locked", true);
        if (st.voidOverflow) t.setBoolean("Void", true);
        if (linked != null) {
            t.setString("Channel", linked.name);
            t.setInteger("Barrels", linked.barrels());
        }
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        int oldFacing = facing;
        tier = BarrelTier.clamp(t.getByte("Tier"));
        facing = facingOf(t.getByte("Facing"));
        shownItem = t.hasKey("Item", 10) ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Item")) : null;
        shownCount = t.getLong("Amount");
        shownCapacity = t.getLong("Capacity");
        shownLocked = t.getBoolean("Locked");
        shownVoid = t.getBoolean("Void");
        shownChannel = t.getString("Channel");
        shownBarrels = t.getInteger("Barrels");
        if (oldFacing != facing && worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    /** What this barrel's item carries (its own contents and channel), for a wrench or a break. */
    NBTTagCompound saveForItem() {
        NBTTagCompound t = new NBTTagCompound();
        if (!own.isEmpty() || own.locked || own.voidOverflow || own.keep > 0) t.setTag("Store", own.write(new NBTTagCompound()));
        if (!channel.isEmpty()) {
            t.setString("Scope", scope);
            t.setString("Channel", channel);
        }
        return t;
    }
}
