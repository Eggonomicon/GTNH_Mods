package com.valier.tweaks.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.valier.tweaks.TweaksConfig;

/**
 * A Valier Barrel Controller (2.4.0): one block in front of every Valier Barrel touching it, or touching a barrel
 * that does (chained, up to {@code barrelControllerMax} barrels within {@code barrelControllerRange} blocks; only loaded
 * ones, it never loads chunks). A linked barrel counts once per channel, as its channel shares one store.
 * <ul>
 * <li><b>In:</b> whatever goes in (hoppers, pipes, conveyors, a right-click, AE2) goes to the barrel holding that item
 * (or locked to it; ore dictionary barrels take what they would take). An item no barrel holds goes into an empty,
 * unlocked barrel only with "fill empty barrels" on (its menu; off by default), else it stays where it came from.
 * Barrels' own void overflow and keep settings apply.</li>
 * <li><b>Out:</b> one slot per store, each the barrel's own output slot, so automation can take any item the network
 * holds; and one AE2 storage bus on it sees the whole network ({@link ControllerAe}).</li>
 * <li><b>Cost:</b> it never ticks. The network is found once and found again only after a barrel within its reach
 * appeared, went or changed channel ({@link #changed}). Putting an item in is one hash lookup and the
 * barrel's own insert; a barrel that changed its item since is noticed then and dropped from the lookup, an item the
 * lookup doesn't know gets one pass over the network's stores, and empty barrels are listed again at most once a
 * second.</li>
 * </ul>
 */
@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.implementations.tiles.ITileStorageMonitorable", modid = "appliedenergistics2")
public class TileBarrelController extends TileEntity
    implements ISidedInventory, ManyStorage, appeng.api.implementations.tiles.ITileStorageMonitorable {

    /** Bumped when a barrel appears or goes (loaded, unloaded, placed, broken) or relinks; where is kept in a ring. */
    public static long topology;
    private static final int RING = 256;
    private static final long[] ringSeq = new long[RING];
    private static final int[] ringDim = new int[RING], ringX = new int[RING], ringY = new int[RING], ringZ = new int[RING];
    /** Controllers an AE2 storage bus listens to: found again a tick after the topology changes (server thread). */
    static final Set<TileBarrelController> aeWatched = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static long aeSeenTopology = -1;
    static final int IN = 0;
    private static final int[][] DIRS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    /** An item no barrel holds goes into an empty barrel (saved). */
    public boolean fillEmpty;
    /** The side its front faces (2 to 5). */
    public int facing = 3;

    // The network (server, rebuilt when topology changed).
    private long builtFor = -1;
    final List<TileBarrel> barrels = new ArrayList<>();
    /** One per store (a channel counts once), and a barrel showing each (its output slot). */
    final List<BarrelStore> stores = new ArrayList<>();
    final List<TileBarrel> storeBarrel = new ArrayList<>();
    private final Map<Key, List<BarrelStore>> byItem = new HashMap<>();
    /** The same stores by item and damage only (any NBT): for pipe filters that don't match NBT. */
    private final Map<Key, List<BarrelStore>> byItemAnyNbt = new HashMap<>();
    private final List<BarrelStore> ore = new ArrayList<>();
    private final List<BarrelStore> empty = new ArrayList<>();
    private long emptiesListed = Long.MIN_VALUE / 2;
    /** When each item was last looked for over the whole network, so a stuck hopper doesn't make it look every time. */
    private final Map<Key, Long> missed = new HashMap<>();
    private int[] slots = { IN };
    /** Output slots given out since the last markDirty (another mod may change their stacks in place). */
    private final Set<Integer> handedOut = new HashSet<>(4);
    /** True when the network hit {@code barrelControllerMax}. */
    boolean capped;
    /** Times the network was found (the self-test reads it). */
    int rebuilds;
    /** AE2's view, made on first use. */
    Object ae;
    /** {@link #manyPeek()}'s snapshot, the tick and network it was made for, and where {@link #manyPeekSome} goes on. */
    private List<ItemStack> peek = new ArrayList<>();
    private long peekTick = Long.MIN_VALUE;
    private int peekBuilt = -1;
    private int peekFrom;

    @Override
    public boolean canUpdate() {
        return false;
    }

    // ---- the network ------------------------------------------------------------------------------------------------

    /** A barrel appeared, went or relinked here (server; the client's barrels are ignored). */
    static void changed(TileEntity te) {
        if (te.getWorldObj() == null || te.getWorldObj().isRemote) return;
        long seq = ++topology;
        int i = (int) (seq % RING);
        ringSeq[i] = seq;
        ringDim[i] = te.getWorldObj().provider.dimensionId;
        ringX[i] = te.xCoord;
        ringY[i] = te.yCoord;
        ringZ[i] = te.zCoord;
    }

    /** Whether a change since the network was found could touch it (within its reach, or too many to tell). */
    private boolean touched() {
        if (builtFor < 0 || topology - builtFor > RING) return true;
        int dim = worldObj.provider.dimensionId, r = Math.max(1, TweaksConfig.barrelControllerRange) + 1;
        for (long s = builtFor + 1; s <= topology; s++) {
            int i = (int) (s % RING);
            if (ringSeq[i] != s) return true;
            if (ringDim[i] == dim && Math.abs(ringX[i] - xCoord) <= r && Math.abs(ringY[i] - yCoord) <= r
                && Math.abs(ringZ[i] - zCoord) <= r) return true;
        }
        return false;
    }

    /** Brings the network up to date (server). */
    void network() {
        if (worldObj == null || worldObj.isRemote || builtFor == topology) return;
        if (!worldObj.blockExists(xCoord, yCoord, zCoord)) return; // its chunk is still loading: later
        boolean again = touched();
        builtFor = topology;
        if (!again) return;
        rebuilds++;
        flood();
        index();
        if (ae != null) ControllerAe.Hook.rebuilt(this);
    }

    private void flood() {
        barrels.clear();
        capped = false;
        int max = Math.max(1, TweaksConfig.barrelControllerMax), range = Math.max(1, TweaksConfig.barrelControllerRange);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen.add(key(xCoord, yCoord, zCoord));
        for (int[] d : DIRS) queue.add(new int[] { xCoord + d[0], yCoord + d[1], zCoord + d[2] });
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (!seen.add(key(p[0], p[1], p[2]))) continue;
            if (Math.abs(p[0] - xCoord) > range || Math.abs(p[1] - yCoord) > range || Math.abs(p[2] - zCoord) > range) continue;
            if (p[1] < 0 || p[1] > 255 || !worldObj.blockExists(p[0], p[1], p[2])) continue;
            TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (!(te instanceof TileBarrel) || te.isInvalid()) continue;
            if (barrels.size() >= max) {
                capped = true;
                break;
            }
            barrels.add((TileBarrel) te);
            for (int[] d : DIRS) queue.add(new int[] { p[0] + d[0], p[1] + d[1], p[2] + d[2] });
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The stores and the item lookup, from the barrels. */
    void index() {
        stores.clear();
        storeBarrel.clear();
        byItem.clear();
        byItemAnyNbt.clear();
        ore.clear();
        empty.clear();
        IdentityHashMap<BarrelStore, Boolean> seen = new IdentityHashMap<>();
        for (TileBarrel b : barrels) {
            if (b.isInvalid()) continue;
            BarrelStore st = b.store();
            if (seen.put(st, Boolean.TRUE) != null) continue;
            stores.add(st);
            storeBarrel.add(b);
            if (st.item != null) known(st);
            else if (!st.locked && !st.creative) empty.add(st);
            if (st.ore) ore.add(st);
        }
        emptiesListed = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        missed.clear();
        slots = new int[1 + stores.size()];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        handedOut.clear();
    }

    /** Puts a store holding an item into both lookups; true when either didn't have it yet. */
    private boolean known(BarrelStore st) {
        Key k = new Key(st.item);
        boolean added = false;
        List<BarrelStore> l = byItem.computeIfAbsent(k.kept(), x -> new ArrayList<>(1));
        if (!l.contains(st)) added = l.add(st);
        List<BarrelStore> a = byItemAnyNbt.computeIfAbsent(k.anyNbt(), x -> new ArrayList<>(1));
        if (!a.contains(st)) added |= a.add(st);
        return added;
    }

    /**
     * Looks over the network's stores for ones holding s (exactly, or with {@code anyNbt} its item and damage) that the
     * lookup doesn't know yet: a barrel can be given a new item by hand. At most once a second per item; true when it
     * found one.
     */
    private boolean lookOver(Key lookup, ItemStack s, boolean anyNbt) {
        long now = worldObj.getTotalWorldTime();
        Long at = missed.get(lookup);
        if (at != null && now - at < 20) return false;
        if (missed.size() > 4096) missed.clear();
        missed.put(lookup.kept(), now);
        boolean found = false;
        for (BarrelStore st : stores) {
            if (st.item == null || st.item.getItem() != s.getItem() || st.item.getItemDamage() != s.getItemDamage()) continue;
            if (anyNbt || st.matches(s)) found |= known(st);
        }
        return found;
    }

    /** Empty barrels again (a barrel may have been emptied since), at most once a second. */
    private boolean relistEmpties() {
        long now = worldObj.getTotalWorldTime();
        if (now - emptiesListed < 20) return false;
        emptiesListed = now;
        empty.clear();
        for (BarrelStore st : stores) if (st.item == null && !st.locked && !st.creative) empty.add(st);
        return true;
    }

    /** From {@link BarrelSync}'s tick: controllers AE2 listens to follow barrels joining and leaving. */
    static void refreshWatched() {
        if (aeWatched.isEmpty() || aeSeenTopology == topology) return;
        aeSeenTopology = topology;
        for (TileBarrelController c : aeWatched.toArray(new TileBarrelController[0])) {
            if (c.isInvalid() || c.worldObj == null) aeWatched.remove(c);
            else c.network();
        }
    }

    // ---- putting things in -----------------------------------------------------------------------------------------

    /**
     * Puts up to n of s into the network; returns how many it took. {@code exact}: only barrels holding exactly that item
     * (AE2 keeps each item apart, so no ore dictionary).
     */
    public long insert(ItemStack s, long n, boolean simulate, boolean exact) {
        return insert(s, n, simulate, exact, fillEmpty);
    }

    /** As {@link #insert(ItemStack, long, boolean, boolean)}; {@code fill}: an item no barrel holds may start one. */
    long insert(ItemStack s, long n, boolean simulate, boolean exact, boolean fill) {
        if (s == null || s.getItem() == null || n <= 0 || worldObj == null || worldObj.isRemote) return 0;
        network();
        Key k = new Key(s);
        if (!byItem.containsKey(k)) lookOver(k, s, false);
        long left = n - into(byItem.get(k), k, s, n, simulate);
        if (left > 0 && !exact) for (BarrelStore st : ore) {
            if (left <= 0) break;
            if (!st.matches(s) && st.takes(s)) left -= st.insert(s, left, simulate);
        }
        // Its barrels are full: another barrel may have been given the item by hand since.
        if (left > 0 && lookOver(k, s, false)) left -= into(byItem.get(k), k, s, left, simulate);
        if (left > 0 && fill) left -= intoEmpty(k, s, left, simulate);
        return n - left;
    }

    /** Into the barrels the lookup has for the item (dropping any that hold something else now). */
    private long into(List<BarrelStore> list, Key k, ItemStack s, long n, boolean simulate) {
        if (list == null) return 0;
        long took = 0;
        for (Iterator<BarrelStore> it = list.iterator(); it.hasNext() && took < n;) {
            BarrelStore st = it.next();
            if (!st.matches(s)) {
                it.remove();
                continue;
            }
            took += st.insert(s, n - took, simulate);
        }
        if (list.isEmpty()) byItem.remove(k);
        return took;
    }

    private long intoEmpty(Key k, ItemStack s, long n, boolean simulate) {
        long took = into0(k, s, n, simulate);
        if (took < n && relistEmpties()) took += into0(k, s, n - took, simulate);
        return took;
    }

    private long into0(Key k, ItemStack s, long n, boolean simulate) {
        long took = 0;
        for (Iterator<BarrelStore> it = empty.iterator(); it.hasNext() && took < n;) {
            BarrelStore st = it.next();
            if (st.item != null || st.locked) {
                it.remove();
                continue;
            }
            long t = st.insert(s, n - took, simulate);
            if (t > 0 && !simulate) {
                it.remove();
                known(st);
            }
            took += t;
            if (simulate) break; // the next empty barrel would take the rest only after this one is full
        }
        return took;
    }

    /** What is in the network of that item, for automation (AE2). */
    long available(ItemStack s) {
        if (s == null || s.getItem() == null || worldObj == null || worldObj.isRemote) return 0;
        network();
        Key k = new Key(s);
        if (!byItem.containsKey(k)) lookOver(k, s, false);
        List<BarrelStore> list = byItem.get(k);
        long n = 0;
        if (list != null) for (BarrelStore st : list) if (st.matches(s)) n += st.available(true);
        return n;
    }

    /** Takes up to n of s out for automation (AE2, pipes); returns how many. */
    long take(ItemStack s, long n, boolean simulate) {
        if (s == null || s.getItem() == null || n <= 0 || worldObj == null || worldObj.isRemote) return 0;
        network();
        Key k = new Key(s);
        if (!byItem.containsKey(k)) lookOver(k, s, false);
        long took = takeFrom(byItem.get(k), k, s, n, simulate);
        if (took < n && lookOver(k, s, false)) took += takeFrom(byItem.get(k), k, s, n - took, simulate);
        return took;
    }

    private long takeFrom(List<BarrelStore> list, Key k, ItemStack s, long n, boolean simulate) {
        if (list == null) return 0;
        long took = 0;
        for (Iterator<BarrelStore> it = list.iterator(); it.hasNext() && took < n;) {
            BarrelStore st = it.next();
            if (!st.matches(s)) {
                it.remove(); // it holds something else now
                continue;
            }
            long q = Math.min(n - took, st.available(true));
            if (q <= 0) continue;
            if (!simulate) st.take(q);
            took += q;
        }
        if (list.isEmpty()) byItem.remove(k);
        return took;
    }

    // ---- ManyStorage (Valier pipes) ------------------------------------------------------------------------------------

    @Override
    public List<ItemStack> manyPeek() {
        network();
        long now = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        if (now == peekTick && peekBuilt == rebuilds) return peek;
        peekTick = now;
        peekBuilt = rebuilds;
        Map<Key, long[]> sum = new HashMap<>();
        Map<Key, ItemStack> item = new HashMap<>();
        for (BarrelStore st : stores) {
            long n = st.available(true);
            if (n <= 0 || st.item == null) continue;
            Key k = new Key(st.item);
            item.putIfAbsent(k, st.item);
            sum.computeIfAbsent(k, x -> new long[1])[0] += n;
        }
        List<ItemStack> out = new ArrayList<>(sum.size());
        for (Map.Entry<Key, long[]> e : sum.entrySet()) out.add(big(item.get(e.getKey()), e.getValue()[0]));
        peek = java.util.Collections.unmodifiableList(out);
        return peek;
    }

    @Override
    public List<ItemStack> manyPeekSome(int max, java.util.function.Predicate<ItemStack> test) {
        List<ItemStack> all = manyPeek();
        int n = all.size();
        List<ItemStack> out = new ArrayList<>(Math.min(Math.max(0, max), n));
        if (n == 0 || max <= 0) return out;
        int start = Math.floorMod(peekFrom, n);
        for (int i = 0; i < n; i++) {
            int at = (start + i) % n;
            ItemStack s = all.get(at);
            if (test != null && !test.test(s)) continue;
            out.add(s);
            if (out.size() >= max) {
                peekFrom = at + 1;
                return out;
            }
        }
        peekFrom = start + 1;
        return out;
    }

    @Override
    public List<ItemStack> manyPeek(ItemStack like, boolean anyNbt) {
        List<ItemStack> out = new ArrayList<>(1);
        if (like == null || like.getItem() == null) return out;
        network();
        Key k = new Key(like);
        Key lookup = anyNbt ? k.anyNbt() : k;
        Map<Key, List<BarrelStore>> map = anyNbt ? byItemAnyNbt : byItem;
        if (!map.containsKey(lookup)) lookOver(lookup, like, anyNbt); // a barrel may have been given it by hand since
        List<BarrelStore> list = map.get(lookup);
        if (list == null) return out;
        Map<Key, long[]> sum = new HashMap<>();
        Map<Key, ItemStack> item = new HashMap<>();
        for (Iterator<BarrelStore> it = list.iterator(); it.hasNext();) {
            BarrelStore st = it.next();
            if (st.item == null || st.item.getItem() != like.getItem() || st.item.getItemDamage() != like.getItemDamage()
                || !anyNbt && !st.matches(like)) {
                it.remove(); // it holds something else now
                continue;
            }
            long n = Math.max(0, st.available(true));
            Key sk = new Key(st.item);
            item.putIfAbsent(sk, st.item);
            sum.computeIfAbsent(sk, x -> new long[1])[0] += n;
        }
        if (list.isEmpty()) map.remove(lookup);
        for (Map.Entry<Key, long[]> e : sum.entrySet()) out.add(big(item.get(e.getKey()), e.getValue()[0]));
        return out;
    }

    private static ItemStack big(ItemStack item, long n) {
        ItemStack b = item.copy();
        b.stackSize = (int) Math.min(Integer.MAX_VALUE, n);
        return b;
    }

    @Override
    public ItemStack manyExtract(ItemStack s, int n) {
        if (s == null || n <= 0) return null;
        long took = take(s, n, false);
        if (took <= 0) return null;
        return big(s, took);
    }

    @Override
    public int manyRoom(ItemStack s, int max) {
        return (int) insert(s, max, true, false);
    }

    @Override
    public int manyInsert(ItemStack s, int max) {
        return (int) insert(s, Math.min(max, s.stackSize), false, false);
    }

    // ---- ISidedInventory: slot 0 takes, every other slot is a barrel's output -----------------------------------------

    @Override
    public int getSizeInventory() {
        network();
        return 1 + storeBarrel.size();
    }

    private TileBarrel barrelOf(int slot) {
        network();
        if (slot < 1 || slot > storeBarrel.size()) return null;
        TileBarrel b = storeBarrel.get(slot - 1);
        return b.isInvalid() ? null : b;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        TileBarrel b = barrelOf(slot);
        if (b == null) return null;
        handedOut.add(slot);
        return b.getStackInSlot(TileBarrel.OUT);
    }

    @Override
    public ItemStack decrStackSize(int slot, int n) {
        TileBarrel b = barrelOf(slot);
        return b == null ? null : b.decrStackSize(TileBarrel.OUT, n);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack s) {
        if (slot == IN) {
            if (s == null || s.stackSize <= 0) return;
            long took = insert(s, s.stackSize, false, false);
            if (took < s.stackSize && worldObj != null) { // a mod that didn't ask first: never lose it
                ItemStack rest = s.copy();
                rest.stackSize = (int) (s.stackSize - took);
                com.valier.tweaks.LootSpawners.dropAt(worldObj, xCoord, yCoord + 1, zCoord, rest);
            }
            return;
        }
        TileBarrel b = barrelOf(slot);
        if (b != null) b.setInventorySlotContents(TileBarrel.OUT, s);
    }

    /** Another mod changed a stack it was given in place: the barrels it came from follow. */
    @Override
    public void markDirty() {
        if (!handedOut.isEmpty()) {
            for (int slot : handedOut) {
                TileBarrel b = slot >= 1 && slot <= storeBarrel.size() ? storeBarrel.get(slot - 1) : null;
                if (b != null && !b.isInvalid()) b.markDirty();
            }
            handedOut.clear();
        }
        if (worldObj != null && !worldObj.isRemote) worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    @Override
    public String getInventoryName() {
        return "Valier Barrel Controller";
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
        return slot == IN && s != null && insert(s, s.stackSize, true, false) >= s.stackSize;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        network();
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack s, int side) {
        return slot == IN && isItemValidForSlot(IN, s);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack s, int side) {
        return slot != IN;
    }

    /** An AE2 storage bus on it sees and reaches the whole network (only with AE2). */
    @Override
    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    public appeng.api.storage.IStorageMonitorable getMonitorable(net.minecraftforge.common.util.ForgeDirection side,
        appeng.api.networking.security.BaseActionSource src) {
        return worldObj == null || worldObj.isRemote ? null : ControllerAe.of(this);
    }

    // ---- life ------------------------------------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        builtFor = -1;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (ae != null) ControllerAe.Hook.gone(this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (ae != null) ControllerAe.Hook.gone(this);
    }

    // ---- what the menu shows ------------------------------------------------------------------------------------------

    /** Finds the network again on next use (the self-test's perf check). */
    void lookAgain() {
        builtFor = -1;
    }

    /** {barrels, stores, kinds of item, empty barrels}. */
    int[] counts() {
        network();
        int kinds = 0, empties = 0;
        Set<Key> k = new HashSet<>();
        for (BarrelStore st : stores) {
            if (st.item != null) k.add(new Key(st.item));
            else if (!st.locked && !st.creative) empties++;
        }
        kinds = k.size();
        return new int[] { barrels.size(), stores.size(), kinds, empties };
    }

    // ---- saving ----------------------------------------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (fillEmpty) nbt.setBoolean("FillEmpty", true);
        nbt.setByte("Facing", (byte) facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        fillEmpty = nbt.getBoolean("FillEmpty");
        facing = TileBarrel.facingOf(nbt.getByte("Facing"));
        builtFor = -1;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("Facing", (byte) facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        int old = facing;
        facing = TileBarrel.facingOf(pkt.func_148857_g()
            .getByte("Facing"));
        if (old != facing && worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    /** An item as the lookup knows it: item, damage and NBT. */
    static final class Key {

        final Item item;
        final int damage;
        final NBTTagCompound tag;
        private final int hash;

        Key(ItemStack s) {
            this(s.getItem(), s.getItemDamage(), s.getTagCompound());
        }

        private Key(Item item, int damage, NBTTagCompound tag) {
            this.item = item;
            this.damage = damage;
            this.tag = tag;
            hash = (System.identityHashCode(item) * 31 + damage) * 31 + (tag == null ? 0 : tag.hashCode());
        }

        /** The same item and damage, any NBT (the key of {@code byItemAnyNbt}). */
        Key anyNbt() {
            return tag == null ? this : new Key(item, damage, null);
        }

        /** A copy safe to keep (its own NBT, not the caller's). */
        Key kept() {
            return tag == null ? this : new Key(item, damage, (NBTTagCompound) tag.copy());
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return item == k.item && damage == k.damage && (tag == null ? k.tag == null : tag.equals(k.tag));
        }
    }
}
