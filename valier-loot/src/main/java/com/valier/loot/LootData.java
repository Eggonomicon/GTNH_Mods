package com.valier.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Loot-chest state for one world, kept inside the chunks themselves: each chunk's state is saved in its own NBT (under
 * {@code Level.ValierLoot}) by LootEvents' chunk save/load handlers, and held in memory only while the chunk is loaded.
 * So a chest's loot is always saved together with its emptied block, and nothing grows into one big file.
 * <p>
 * Per position:
 * <ul>
 * <li><b>pending</b>: a worldgen chest just found whose loot is still in the block; locked by the end of the tick (or
 * as soon as its chunk is loaded again).</li>
 * <li><b>opened</b> (locked): the loot has moved into a template here and the block is empty, so hoppers, pipes,
 * drones and breaking get nothing; every player gets a copy of the template the first time they open it.</li>
 * <li><b>ignored</b>: a chest that must never become loot (placed by a player, or removed by an admin).</li>
 * </ul>
 */
public class LootData {

    /** Slots in one chest half. */
    public static final int SIZE = 27;
    static final String TAG = "ValierLoot";

    private static final Map<World, LootData> WORLDS = new WeakHashMap<>();

    private final World world;
    private final Map<Long, ChunkLoot> chunks = new HashMap<>();
    /** Chunks being unloaded: their state is dropped from memory once their last save has written it. */
    private final Set<Long> unloading = new HashSet<>();

    private LootData(World world) {
        this.world = world;
    }

    public static LootData get(World world) {
        LootData data = WORLDS.get(world);
        if (data == null) WORLDS.put(world, data = new LootData(world));
        return data;
    }

    static void unload(World world) {
        WORLDS.remove(world);
    }

    /** The loot state of one chunk. */
    private final class ChunkLoot {

        final Set<Long> pending = new HashSet<>();
        final Set<Long> ignored = new HashSet<>();
        final Map<Long, LootChest> opened = new HashMap<>();

        boolean isEmpty() {
            return pending.isEmpty() && ignored.isEmpty() && opened.isEmpty();
        }
    }

    // ---- Positions ----

    // Packs a block position into a long: x in the top 26 bits, then y in 12 bits, then z in the low 26 bits.
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public static int unpackX(long pos) {
        return (int) (pos >> 38);
    }

    public static int unpackY(long pos) {
        return (int) ((pos << 26) >> 52);
    }

    public static int unpackZ(long pos) {
        return (int) ((pos << 38) >> 38);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static long chunkOf(long pos) {
        return chunkKey(unpackX(pos) >> 4, unpackZ(pos) >> 4);
    }

    /** The state of the chunk holding pos. With create, made if missing, but only for a loaded chunk. */
    private ChunkLoot loot(long pos, boolean create) {
        long key = chunkOf(pos);
        ChunkLoot loot = chunks.get(key);
        if (loot == null && create && loaded(key)) chunks.put(key, loot = new ChunkLoot());
        return loot;
    }

    private boolean loaded(long chunkKey) {
        return world.getChunkProvider()
            .chunkExists((int) (chunkKey >> 32), (int) chunkKey);
    }

    /** Makes sure the chunk holding pos is saved again (with its new loot state). */
    void markDirty(long pos) {
        long key = chunkOf(pos);
        if (loaded(key)) world.getChunkFromChunkCoords((int) (key >> 32), (int) key)
            .setChunkModified();
    }

    // ---- Chunk save and load (called by LootEvents) ----

    /** Replaces the chunk's state with what was saved in it; registers worldgen chests found as it loaded. */
    void readChunk(Chunk chunk, NBTTagCompound level, List<Long> newChests) {
        long key = chunkKey(chunk.xPosition, chunk.zPosition);
        unloading.remove(key);
        chunks.remove(key);
        ChunkLoot loot = new ChunkLoot();
        if (level.hasKey(TAG, 10)) {
            NBTTagCompound tag = level.getCompoundTag(TAG);
            readPositions(tag.getIntArray("Pending"), loot.pending);
            readPositions(tag.getIntArray("Ignored"), loot.ignored);
            NBTTagList list = tag.getTagList("Opened", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound c = list.getCompoundTagAt(i);
                int[] p = c.getIntArray("Pos");
                if (p.length != 3) continue;
                long pos = pack(p[0], p[1], p[2]);
                LootChest chest = new LootChest(pos, readItems(c.getTagList("Template", 10)));
                NBTTagList players = c.getTagList("Players", 10);
                for (int j = 0; j < players.tagCount(); j++) {
                    NBTTagCompound pc = players.getCompoundTagAt(j);
                    chest.copies.put(
                        new UUID(pc.getLong("Most"), pc.getLong("Least")),
                        readItems(pc.getTagList("Items", 10)));
                }
                loot.opened.put(pos, chest);
            }
        }
        boolean added = false;
        for (long pos : newChests) {
            if (!loot.opened.containsKey(pos) && !loot.ignored.contains(pos)) added |= loot.pending.add(pos);
        }
        if (added) chunk.setChunkModified();
        if (!loot.isEmpty()) chunks.put(key, loot);
    }

    /** Writes the chunk's state into its save; forgets it if the chunk is unloading. */
    void writeChunk(Chunk chunk, NBTTagCompound level) {
        long key = chunkKey(chunk.xPosition, chunk.zPosition);
        ChunkLoot loot = chunks.get(key);
        if (loot != null && !loot.isEmpty()) level.setTag(TAG, write(loot));
        if (unloading.remove(key)) chunks.remove(key);
    }

    void markUnloading(Chunk chunk) {
        long key = chunkKey(chunk.xPosition, chunk.zPosition);
        if (chunks.containsKey(key)) unloading.add(key);
    }

    /** Positions in the chunk whose loot hasn't been locked away yet. */
    List<Long> pendingIn(int chunkX, int chunkZ) {
        ChunkLoot loot = chunks.get(chunkKey(chunkX, chunkZ));
        return loot == null ? new ArrayList<>() : new ArrayList<>(loot.pending);
    }

    // ---- Queries and changes ----

    public boolean isLoot(long pos) {
        ChunkLoot loot = loot(pos, false);
        return loot != null && (loot.pending.contains(pos) || loot.opened.containsKey(pos));
    }

    public boolean isPending(long pos) {
        ChunkLoot loot = loot(pos, false);
        return loot != null && loot.pending.contains(pos);
    }

    public LootChest getOpened(long pos) {
        ChunkLoot loot = loot(pos, false);
        return loot == null ? null : loot.opened.get(pos);
    }

    public boolean isIgnored(long pos) {
        ChunkLoot loot = loot(pos, false);
        return loot != null && loot.ignored.contains(pos);
    }

    /** Registers a worldgen chest, unless it is already known or must be ignored. */
    public void addPending(long pos) {
        ChunkLoot loot = loot(pos, true);
        if (loot != null && !loot.opened.containsKey(pos) && !loot.ignored.contains(pos) && loot.pending.add(pos)) {
            markDirty(pos);
        }
    }

    /** Marks a position as a player's chest, so it never becomes loot. */
    public void ignore(long pos) {
        ChunkLoot loot = loot(pos, true);
        if (loot == null) return;
        boolean changed = loot.ignored.add(pos);
        changed |= loot.pending.remove(pos);
        changed |= loot.opened.remove(pos) != null;
        if (changed) markDirty(pos);
    }

    /** Forgets a position entirely (its chest is gone, or about to be replaced). */
    public void forget(long pos) {
        ChunkLoot loot = loot(pos, false);
        if (loot == null) return;
        boolean changed = loot.ignored.remove(pos);
        changed |= loot.pending.remove(pos);
        changed |= loot.opened.remove(pos) != null;
        if (changed) markDirty(pos);
    }

    /**
     * Returns the loot record for the chest at x, y, z, first moving a pending chest's contents into a new template
     * and emptying the block. Returns null if the position is not a loot chest.
     */
    public LootChest open(int x, int y, int z) {
        long pos = pack(x, y, z);
        ChunkLoot loot = loot(pos, false);
        if (loot == null) return null;
        LootChest chest = loot.opened.get(pos);
        if (chest != null) return chest;
        if (!loot.pending.remove(pos)) return null;
        chest = new LootChest(pos, takeContents(x, y, z));
        loot.opened.put(pos, chest);
        markDirty(pos);
        return chest;
    }

    /** Puts an opened chest's template back into the block and stops it being a loot chest. */
    public void restore(int x, int y, int z) {
        long pos = pack(x, y, z);
        LootChest chest = getOpened(pos);
        TileEntity te = world.getTileEntity(x, y, z);
        if (chest != null && te instanceof TileEntityChest) {
            TileEntityChest block = (TileEntityChest) te;
            for (int i = 0; i < Math.min(SIZE, block.getSizeInventory()); i++) {
                if (block.getStackInSlot(i) == null && chest.template[i] != null) {
                    block.setInventorySlotContents(i, ItemStack.copyItemStack(chest.template[i]));
                }
            }
        }
        ignore(pos);
    }

    /**
     * Empties the chest block. getStackInSlotOnClosing clears a slot without TileEntity.markDirty, which would look at
     * the neighbouring blocks and so could load or generate the chunk next door.
     */
    private ItemStack[] takeContents(int x, int y, int z) {
        ItemStack[] stacks = new ItemStack[SIZE];
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityChest) {
            TileEntityChest block = (TileEntityChest) te;
            for (int i = 0; i < Math.min(SIZE, block.getSizeInventory()); i++) {
                stacks[i] = block.getStackInSlotOnClosing(i);
            }
        }
        return stacks;
    }

    /** Loot chests (pending, locked) in this world's loaded chunks. */
    public int[] loadedCounts() {
        int pending = 0, opened = 0;
        for (ChunkLoot loot : chunks.values()) {
            pending += loot.pending.size();
            opened += loot.opened.size();
        }
        return new int[] { pending, opened };
    }

    /** One locked loot chest: the original loot, and each player's own copy of it. */
    public final class LootChest {

        final long pos;
        final ItemStack[] template;
        final Map<UUID, ItemStack[]> copies = new HashMap<>();

        LootChest(long pos, ItemStack[] template) {
            this.pos = pos;
            this.template = template;
        }

        /**
         * The player's own copy, made the first time they ask: the chest's loot, their own random roll, or both (see
         * LootConfig.randomMode).
         */
        public ItemStack[] copyFor(UUID player) {
            ItemStack[] copy = copies.get(player);
            if (copy == null) {
                copy = new ItemStack[SIZE];
                if (LootConfig.randomMode != LootConfig.RandomMode.REPLACE) {
                    for (int i = 0; i < SIZE; i++) copy[i] = ItemStack.copyItemStack(template[i]);
                }
                if (LootConfig.randomMode != LootConfig.RandomMode.OFF) {
                    RandomLoot.addRoll(world, unpackX(pos), unpackZ(pos), copy, world.rand);
                }
                copies.put(player, copy);
                markDirty(pos);
            }
            return copy;
        }

        public int playerCount() {
            return copies.size();
        }

        public int templateStacks() {
            int n = 0;
            for (ItemStack s : template) if (s != null) n++;
            return n;
        }

        /** Throws away every player's copy, so everyone gets the full loot again. */
        public void resetCopies() {
            copies.clear();
            markDirty(pos);
        }
    }

    // ---- NBT ----

    private static NBTTagCompound write(ChunkLoot loot) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!loot.pending.isEmpty()) tag.setTag("Pending", new NBTTagIntArray(writePositions(loot.pending)));
        if (!loot.ignored.isEmpty()) tag.setTag("Ignored", new NBTTagIntArray(writePositions(loot.ignored)));
        NBTTagList list = new NBTTagList();
        for (LootChest chest : loot.opened.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setIntArray("Pos", new int[] { unpackX(chest.pos), unpackY(chest.pos), unpackZ(chest.pos) });
            c.setTag("Template", writeItems(chest.template));
            NBTTagList players = new NBTTagList();
            for (Map.Entry<UUID, ItemStack[]> p : chest.copies.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setLong("Most", p.getKey().getMostSignificantBits());
                pc.setLong("Least", p.getKey().getLeastSignificantBits());
                pc.setTag("Items", writeItems(p.getValue()));
                players.appendTag(pc);
            }
            c.setTag("Players", players);
            list.appendTag(c);
        }
        if (list.tagCount() > 0) tag.setTag("Opened", list);
        return tag;
    }

    private static void readPositions(int[] xyz, Set<Long> into) {
        for (int i = 0; i + 2 < xyz.length; i += 3) into.add(pack(xyz[i], xyz[i + 1], xyz[i + 2]));
    }

    private static int[] writePositions(Set<Long> positions) {
        int[] xyz = new int[positions.size() * 3];
        int i = 0;
        for (long pos : positions) {
            xyz[i++] = unpackX(pos);
            xyz[i++] = unpackY(pos);
            xyz[i++] = unpackZ(pos);
        }
        return xyz;
    }

    private static NBTTagList writeItems(ItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null) continue;
            NBTTagCompound s = new NBTTagCompound();
            s.setByte("Slot", (byte) i);
            stacks[i].writeToNBT(s);
            list.appendTag(s);
        }
        return list;
    }

    private static ItemStack[] readItems(NBTTagList list) {
        ItemStack[] stacks = new ItemStack[SIZE];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound s = list.getCompoundTagAt(i);
            int slot = s.getByte("Slot") & 255;
            if (slot < SIZE) stacks[slot] = ItemStack.loadItemStackFromNBT(s);
        }
        return stacks;
    }
}
