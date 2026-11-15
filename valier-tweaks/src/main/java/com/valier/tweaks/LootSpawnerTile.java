package com.valier.tweaks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntityMobSpawner;

/**
 * A mob spawner turned into a loot spawner ({@link LootSpawners}): it never spawns a mob. Instead, every spawner cycle
 * it makes the loot of the mobs it would have spawned and pushes it into the inventories and pipes touching it.
 * <p>
 * Clients need nothing: they see an ordinary spawner (the description packet is the vanilla one). In the world save it
 * is an ordinary spawner too ({@code id} "MobSpawner"), marked by a {@value #MARK} compound inside its SpawnData, which
 * vanilla keeps through every copy (saves, the JABBA dolly, NBT tools). {@link LootSpawners} turns a marked vanilla
 * spawner back into this tile when its chunk loads or when one turns up some other way. Without this mod it is an
 * ordinary spawner of the same mob again.
 * <p>
 * It is also an inventory (27 slots, take-only) holding loot that nothing around it could take yet, so hoppers, EnderIO
 * conduits on extract, AE2 import buses and the like can pull from it, and pipes connect to it.
 */
public class LootSpawnerTile extends TileEntityMobSpawner implements ISidedInventory {

    /** The compound inside the spawner's SpawnData that marks a loot spawner and holds its state. */
    public static final String MARK = "ValierLootSpawner";
    public static final int SLOTS = 27;
    private static final int[] ALL_SLOTS = new int[SLOTS];
    static {
        for (int i = 0; i < SLOTS; i++) ALL_SLOTS[i] = i;
    }

    final ItemStack[] items = new ItemStack[SLOTS];
    /** Ticks to the next roll; -1 until the first cycle picks one. */
    int timer = -1;
    /** The spawner had no SpawnData of its own, so vanilla would have dressed its mobs with onSpawnWithEgg. */
    boolean egg;
    /** From the spawner's own settings (vanilla defaults 200, 800, 4). */
    int minDelay = 200, maxDelay = 800, spawnCount = 4;
    /** What it can spawn: one entry, or its SpawnPotentials. */
    List<MobLoot.Candidate> candidates = Collections.emptyList();
    /** Damaged items (worn tools, weapons and armour) are thrown away instead of handed out. */
    boolean noDamaged;
    /** What the player paid to switch {@link #noDamaged} on: given back when switched off, dropped when it breaks. */
    ItemStack filterPaid;

    // Not saved: what the status message reports.
    LootSpawners.Status status = LootSpawners.Status.STARTING;
    int rolls;
    /** Set when a player's break event names this spawner; cleared by any save after it (a mover took it). */
    boolean savedSinceBreak;
    private int ticks;
    private boolean warned;

    public MobSpawnerBaseLogic logic() {
        return func_145881_a();
    }

    /** True if this vanilla spawner carries the loot spawner mark. */
    public static boolean isMarked(TileEntityMobSpawner spawner) {
        MobSpawnerBaseLogic.WeightedRandomMinecart data = spawner.func_145881_a()
            .getRandomEntity();
        return data != null && data.field_98222_b != null && data.field_98222_b.hasKey(MARK, 10);
    }

    /**
     * Marks a vanilla spawner as a loot spawner, giving it SpawnData if it has none (the mark has to live there).
     * {@link LootSpawners#swap} then makes it this tile.
     */
    public static void mark(TileEntityMobSpawner spawner) {
        MobSpawnerBaseLogic logic = spawner.func_145881_a();
        MobSpawnerBaseLogic.WeightedRandomMinecart old = logic.getRandomEntity();
        boolean egg = old == null;
        NBTTagCompound mark = new NBTTagCompound();
        mark.setBoolean("Egg", egg);
        setData(logic, egg ? new NBTTagCompound() : (NBTTagCompound) old.field_98222_b.copy(), mark,
            egg ? logic.getEntityNameToSpawn() : old.entityTypeName);
        logic.spawnDelay = Short.MAX_VALUE; // in case it ticks as a vanilla spawner before the swap
    }

    /**
     * Gives the logic new SpawnData: a fresh compound, never one changed later, because a world save may still be
     * writing the old one out on the file thread (vanilla's SpawnPotentials entry shares it).
     */
    private static void setData(MobSpawnerBaseLogic logic, NBTTagCompound props, NBTTagCompound mark, String type) {
        props.setTag(MARK, mark);
        logic.setRandomEntity(logic.new WeightedRandomMinecart(props, type));
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            super.updateEntity(); // never this class on a client, but stay vanilla there if it ever is
            return;
        }
        // Stagger spawners over the 20 ticks so they don't all work on the same tick.
        if ((++ticks + ((xCoord * 31 + zCoord * 17 + yCoord) & 0xFFFF)) % 20 != 0) return;
        // A tile left over from before its chunk unloaded and loaded again in the same tick: only the chunk's own works.
        net.minecraft.world.chunk.Chunk chunk = worldObj.getChunkFromBlockCoords(xCoord, zCoord);
        if (chunk.chunkTileEntityMap.get(new net.minecraft.world.ChunkPosition(xCoord & 15, yCoord, zCoord & 15)) != this) return;
        try {
            LootSpawners.cycle(this);
        } catch (Throwable t) {
            if (!warned) ValierTweaks.LOG.warn("Loot spawner at " + where() + " failed; it will keep trying", t);
            warned = true;
            timer = Math.max(timer, maxDelay);
        }
    }

    String where() {
        return (worldObj != null ? "dim " + worldObj.provider.dimensionId + " " : "") + xCoord + " " + yCoord + " " + zCoord;
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        MobSpawnerBaseLogic logic = logic();
        logic.spawnDelay = Short.MAX_VALUE;
        if (!isMarked(this)) mark(this); // not marked yet (shouldn't happen): mark it now
        MobSpawnerBaseLogic.WeightedRandomMinecart data = logic.getRandomEntity();
        NBTTagCompound mark = data.field_98222_b.getCompoundTag(MARK);
        egg = mark.getBoolean("Egg");
        timer = mark.hasKey("Timer") ? mark.getInteger("Timer") : -1;
        noDamaged = mark.getBoolean("NoDamaged");
        filterPaid = mark.hasKey("Paid", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag("Paid")) : null;
        for (int i = 0; i < SLOTS; i++) items[i] = null;
        NBTTagList list = mark.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 255;
            if (slot < SLOTS) items[slot] = ItemStack.loadItemStackFromNBT(tag);
        }
        // The SpawnData the logic keeps holds only the bare mark; writeToNBT adds the current timer and items to the
        // saved copy. It is never changed after this (see setData).
        NBTTagCompound bare = new NBTTagCompound();
        bare.setBoolean("Egg", egg);
        setData(logic, (NBTTagCompound) data.field_98222_b.copy(), bare, data.entityTypeName);
        if (nbt.hasKey("MinSpawnDelay", 99)) {
            minDelay = nbt.getShort("MinSpawnDelay");
            maxDelay = nbt.getShort("MaxSpawnDelay");
            spawnCount = nbt.getShort("SpawnCount");
        }
        candidates = MobLoot.candidates(nbt, egg);
    }

    /** Writes the state into the mark, then saves as a vanilla spawner ("MobSpawner") so a world without the mod keeps it. */
    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        writeAll(nbt);
        savedSinceBreak = true;
    }

    private void writeAll(NBTTagCompound nbt) {
        MobSpawnerBaseLogic logic = logic();
        if (!isMarked(this)) mark(this);
        logic.spawnDelay = Short.MAX_VALUE;
        super.writeToNBT(nbt); // TileEntity writes our registered id; the spawner logic writes a copy of SpawnData
        NBTTagCompound mark = new NBTTagCompound();
        mark.setBoolean("Egg", egg);
        mark.setInteger("Timer", timer);
        if (noDamaged) mark.setBoolean("NoDamaged", true);
        if (filterPaid != null) mark.setTag("Paid", filterPaid.writeToNBT(new NBTTagCompound()));
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Slot", (byte) i);
            items[i].writeToNBT(tag);
            list.appendTag(tag);
        }
        mark.setTag("Items", list);
        NBTTagCompound data = nbt.getCompoundTag("SpawnData");
        data.setTag(MARK, mark);
        nbt.setTag("SpawnData", data);
        nbt.setString("id", "MobSpawner");
    }

    /**
     * What clients get: the vanilla spawner packet, without the mark. "Delay" is the time to the next roll, so the
     * little mob spins faster as the next batch of loot comes up.
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeAll(nbt); // not writeToNBT: a packet isn't a save (see savedSinceBreak)
        nbt.removeTag("SpawnPotentials");
        NBTTagCompound data = nbt.getCompoundTag("SpawnData");
        data.removeTag(MARK);
        if (egg && data.hasNoTags()) nbt.removeTag("SpawnData");
        nbt.setShort("Delay", (short) Math.max(0, Math.min(Short.MAX_VALUE, timer)));
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    // ---- the buffer ----------------------------------------------------------------------------------------------

    boolean isEmpty() {
        for (ItemStack s : items) if (s != null) return false;
        return true;
    }

    /** Something drops when it breaks: loot it holds, or what was paid for {@link #noDamaged}. */
    boolean hasDrops() {
        return filterPaid != null || !isEmpty();
    }

    int freeSlots() {
        int n = 0;
        for (ItemStack s : items) if (s == null) n++;
        return n;
    }

    int stacksHeld() {
        int n = 0;
        for (ItemStack s : items) if (s != null) n++;
        return n;
    }

    /** Puts a stack in the buffer (merging first). Returns what didn't fit. */
    ItemStack addToBuffer(ItemStack stack) {
        for (int i = 0; i < SLOTS && stack != null; i++) {
            ItemStack in = items[i];
            if (in != null && MobLoot.sameItem(in, stack)) {
                int move = Math.min(stack.stackSize, in.getMaxStackSize() - in.stackSize);
                if (move > 0) {
                    in.stackSize += move;
                    stack.stackSize -= move;
                    if (stack.stackSize <= 0) stack = null;
                }
            }
        }
        for (int i = 0; i < SLOTS && stack != null; i++) {
            if (items[i] == null) {
                items[i] = stack;
                stack = null;
            }
        }
        markDirty();
        return stack;
    }

    List<ItemStack> held() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) if (s != null) out.add(s);
        return out;
    }

    // ---- ISidedInventory: take-only ------------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOTS ? items[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        if (slot < 0 || slot >= SLOTS || items[slot] == null || count <= 0) return null;
        ItemStack out;
        if (items[slot].stackSize <= count) {
            out = items[slot];
            items[slot] = null;
        } else {
            out = items[slot].splitStack(count);
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) return;
        items[slot] = stack != null && stack.stackSize > 0 ? stack : null;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "Loot Spawner";
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
    public boolean isUseableByPlayer(EntityPlayer player) {
        return false; // no GUI
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return false;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return true;
    }
}
