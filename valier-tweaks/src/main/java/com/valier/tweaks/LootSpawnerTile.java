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
import net.minecraft.util.EnumChatFormatting;

import com.valier.tweaks.pipe.FilterList;

/**
 * A mob spawner turned into a loot spawner ({@link LootSpawners}): it never spawns a mob. Instead, every spawner cycle
 * it makes the loot of the mobs it would have spawned and pushes it into the inventories and pipes touching it.
 * <p>
 * Since 2.0.0 it is the tile of the Valier Loot Spawner block ({@link BlockLootSpawner}) on the server; players'
 * computers have a plain spawner tile that draws the mob ({@link LootSpawnerClientTile}). Its state is kept in a
 * {@value #MARK} compound inside its SpawnData, as before 2.0.0, when a loot spawner was a vanilla spawner saved as
 * {@code id} "MobSpawner": {@link LootSpawners} turns those into the block when their chunk loads.
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
    /** The XP its mobs would have dropped comes out as bottles o' enchanting. */
    boolean xpBottles;
    /** What the player paid to switch {@link #xpBottles} on: given back when switched off, dropped when it breaks. */
    ItemStack xpPaid;
    /** XP made but not yet a bottle. */
    int xp;
    /**
     * The loot filter, set in its menu ({@link LootMenu}): what the Void list names is destroyed as it is rolled, and
     * when the Allow list names anything, only what it names comes out. Saved in the mark as "Allow" and "Void".
     */
    public final FilterList allow = new FilterList(), voids = new FilterList();
    /** Lapis blocks (by default) put in: they set the Looting its mobs' loot is rolled with. */
    final Upgrade looting = new Upgrade(LootSpawners.Kind.LOOTING);
    /** Cursed earth (by default) put in: its batches come faster ({@link #speedFactor}). */
    final Upgrade speed = new Upgrade(LootSpawners.Kind.SPEED);
    /**
     * The spawner's own RequiredPlayerRange, saved and sent as it is. The live vanilla logic gets 0, so it never counts
     * as active and never spawns a mob, even when another mod runs it: EnderIO runs a spawner up to 6 times when a
     * player breaks it, or when a break event is only asked (as {@link LootSpawners#mayChange} does).
     */
    int playerRange = 16;

    // Not saved: what its filter destroyed, per minute over the last hour (the menu shows it).
    private final long[] voidedPerMinute = new long[60];
    private long voidMinute = Long.MIN_VALUE;
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

    /**
     * Blocks put into a loot spawner one per click, whose count sets a level: Looting and Speed. The mark keeps them as
     * LootingBlocks, LootingPaid and LootingItem (Speed... the same way).
     */
    static final class Upgrade {

        final LootSpawners.Kind kind;
        /** How many are in it. */
        int blocks;
        /** How many of them were paid for (not in creative): these drop when it breaks. */
        int paid;
        /** One of the item put in, so what drops is what was paid. */
        ItemStack item;

        Upgrade(LootSpawners.Kind kind) {
            this.kind = kind;
        }

        void read(NBTTagCompound mark) {
            blocks = mark.getInteger(kind.word + "Blocks");
            paid = mark.getInteger(kind.word + "Paid");
            item = mark.hasKey(kind.word + "Item", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag(kind.word + "Item"))
                : null;
        }

        void write(NBTTagCompound mark) {
            if (blocks > 0) mark.setInteger(kind.word + "Blocks", blocks);
            if (paid > 0) mark.setInteger(kind.word + "Paid", paid);
            if (item != null) mark.setTag(kind.word + "Item", item.writeToNBT(new NBTTagCompound()));
        }

        /** The level the blocks in it give (for Speed, without one under it: see {@link #speedLevel}). */
        int level() {
            return kind.level(blocks);
        }

        /** What was paid, as stacks: what drops when it breaks, or comes back when taken out. */
        List<ItemStack> refund() {
            return LootSpawners.refund(item, paid);
        }

        void clear() {
            blocks = 0;
            paid = 0;
            item = null;
        }
    }

    /** Its Speed level: the speed items in it, and one more for one right under it ({@link LootSpawners#speedBelow}). */
    int speedLevel() {
        return speed.kind.level(speed.blocks + (LootSpawners.speedBelow(this) ? 1 : 0));
    }

    /** How many times its normal speed it makes batches: once more for each Speed level. */
    int speedFactor() {
        return 1 + speedLevel();
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
        // Extra Utilities' cursed earth runs the vanilla countdown of a spawner on it (100 ticks off every 5 s) and at 0
        // spawns its mobs itself, real ones. Kept full every tick, that countdown never gets there.
        logic().spawnDelay = Short.MAX_VALUE;
        // Stagger spawners over the 20 ticks so they don't all work on the same tick.
        if ((++ticks + ((xCoord * 31 + zCoord * 17 + yCoord) & 0xFFFF)) % 20 != 0) return;
        // A tile left over from before its chunk unloaded and loaded again in the same tick: only the chunk's own works.
        net.minecraft.world.chunk.Chunk chunk = worldObj.getChunkFromBlockCoords(xCoord, zCoord);
        if (chunk.chunkTileEntityMap.get(new net.minecraft.world.ChunkPosition(xCoord & 15, yCoord, zCoord & 15)) != this) return;
        long t0 = ValierCost.start();
        try {
            LootSpawners.cycle(this);
        } catch (Throwable t) {
            if (!warned) ValierTweaks.LOG.warn("Loot spawner at " + where() + " failed; it will keep trying", t);
            warned = true;
            timer = Math.max(timer, maxDelay);
        } finally {
            ValierCost.add(ValierCost.Part.LOOT, t0);
        }
    }

    String where() {
        return (worldObj != null ? "dim " + worldObj.provider.dimensionId + " " : "") + xCoord + " " + yCoord + " " + zCoord;
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        playerRange = nbt.hasKey("RequiredPlayerRange", 99) ? nbt.getShort("RequiredPlayerRange") : 16;
        super.readFromNBT(inert(nbt));
        MobSpawnerBaseLogic logic = logic();
        logic.spawnDelay = Short.MAX_VALUE;
        if (!isMarked(this)) mark(this); // not marked yet (shouldn't happen): mark it now
        MobSpawnerBaseLogic.WeightedRandomMinecart data = logic.getRandomEntity();
        NBTTagCompound mark = data.field_98222_b.getCompoundTag(MARK);
        egg = mark.getBoolean("Egg");
        timer = mark.hasKey("Timer") ? mark.getInteger("Timer") : -1;
        noDamaged = mark.getBoolean("NoDamaged");
        filterPaid = mark.hasKey("Paid", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag("Paid")) : null;
        xpBottles = mark.getBoolean("XpBottles");
        xpPaid = mark.hasKey("XpPaid", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag("XpPaid")) : null;
        xp = mark.getInteger("Xp");
        looting.read(mark);
        speed.read(mark);
        allow.read(mark.getCompoundTag("Allow"));
        voids.read(mark.getCompoundTag("Void"));
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

    /** A copy of a spawner's data whose logic never counts as active (RequiredPlayerRange 0; see {@link #playerRange}). */
    static NBTTagCompound inert(NBTTagCompound nbt) {
        NBTTagCompound copy = (NBTTagCompound) nbt.copy();
        if (!copy.hasKey("MaxNearbyEntities", 99)) copy.setShort("MaxNearbyEntities", (short) 6); // vanilla reads both or neither
        copy.setShort("RequiredPlayerRange", (short) 0);
        return copy;
    }

    /** Writes the state into the mark (saved under this tile's own id since 2.0.0). */
    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        writeAll(nbt);
        savedSinceBreak = true;
    }

    /** Everything, as {@link #writeToNBT} saves it, but not counted as a save (see {@link #savedSinceBreak}). */
    void writeAll(NBTTagCompound nbt) {
        MobSpawnerBaseLogic logic = logic();
        if (!isMarked(this)) mark(this);
        logic.spawnDelay = Short.MAX_VALUE;
        super.writeToNBT(nbt); // TileEntity writes our registered id; the spawner logic writes a copy of SpawnData
        nbt.setShort("RequiredPlayerRange", (short) playerRange); // its own, not the live logic's 0
        NBTTagCompound mark = new NBTTagCompound();
        mark.setBoolean("Egg", egg);
        mark.setInteger("Timer", timer);
        if (noDamaged) mark.setBoolean("NoDamaged", true);
        if (filterPaid != null) mark.setTag("Paid", filterPaid.writeToNBT(new NBTTagCompound()));
        if (xpBottles) mark.setBoolean("XpBottles", true);
        if (xpPaid != null) mark.setTag("XpPaid", xpPaid.writeToNBT(new NBTTagCompound()));
        if (xp > 0) mark.setInteger("Xp", xp);
        looting.write(mark);
        speed.write(mark);
        writeList(mark, "Allow", allow);
        writeList(mark, "Void", voids);
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
    }

    /**
     * What clients get: the vanilla spawner packet (their tile is a plain spawner, {@link LootSpawnerClientTile}, so the
     * mob spins in the cage), without the mark, plus the Looting and Speed levels for the name players see. "Delay" is
     * the time to the next roll, so the little mob spins faster as the next batch of loot comes up.
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeAll(nbt); // not writeToNBT: a packet isn't a save (see savedSinceBreak)
        nbt.removeTag("SpawnPotentials");
        NBTTagCompound data = nbt.getCompoundTag("SpawnData");
        data.removeTag(MARK);
        if (egg && data.hasNoTags()) nbt.removeTag("SpawnData");
        nbt.setInteger("ValierLooting", looting.level());
        nbt.setInteger("ValierSpeed", speedLevel());
        nbt.setShort("Delay", (short) Math.max(0, Math.min(Short.MAX_VALUE, timer)));
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    /** "Valier Loot Spawner: Zombie, Looting II, Speed I", what players' WAILA shows inside "Monster Spawner (...)". */
    static String hoverName(String type, int looting, int speed) {
        return EnumChatFormatting.GOLD + "Valier Loot Spawner" + EnumChatFormatting.RESET + ": " + LootSpawners.mobWords(type)
            + (looting > 0 ? ", Looting " + LootSpawners.roman(looting) : "")
            + (speed > 0 ? ", Speed " + LootSpawners.roman(speed) : "");
    }

    /**
     * What a wrench takes away in the item ({@link LootSpawners#pickUp}): everything a placed one needs (mob, spawn
     * data, settings, the damaged-items filter and what was paid for it), without its place, timer or loot.
     */
    NBTTagCompound saveForItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeAll(nbt);
        for (String key : new String[] { "x", "y", "z", "id", "Delay" }) nbt.removeTag(key);
        NBTTagCompound mark = nbt.getCompoundTag("SpawnData")
            .getCompoundTag(MARK);
        mark.removeTag("Items");
        mark.removeTag("Timer");
        mark.removeTag("Xp"); // the part of a bottle made so far: equal spawners' items then stack
        return (NBTTagCompound) nbt.copy(); // vanilla's SpawnPotentials entries share compounds with the live logic
    }

    private static void writeList(NBTTagCompound mark, String key, FilterList l) {
        NBTTagCompound t = new NBTTagCompound();
        l.write(t);
        if (!t.hasNoTags()) mark.setTag(key, t);
    }

    // ---- the loot filter -----------------------------------------------------------------------------------------

    /** Whether this rolled item comes out: not named by the Void list, and named by the Allow list if it names anything. */
    boolean lets(ItemStack s) {
        return s != null && !voids.matches(s, null) && allow.allows(s, null);
    }

    /** Whether the filter does anything at all. */
    boolean filters() {
        return voids.active() || allow.active();
    }

    void countVoided(int n) {
        rollMinutes();
        voidedPerMinute[(int) Math.floorMod(voidMinute, 60L)] += n;
    }

    /** Items its filter destroyed over the last hour. */
    long voidedLastHour() {
        rollMinutes();
        long sum = 0;
        for (long v : voidedPerMinute) sum += v;
        return sum;
    }

    private void rollMinutes() {
        long now = worldObj == null ? 0 : worldObj.getTotalWorldTime() / 1200;
        if (voidMinute == Long.MIN_VALUE) {
            voidMinute = now;
            return;
        }
        if (now <= voidMinute) return;
        long steps = Math.min(60, now - voidMinute);
        for (long k = 1; k <= steps; k++) voidedPerMinute[(int) Math.floorMod(voidMinute + k, 60L)] = 0;
        voidMinute = now;
    }

    /** Destroys what it keeps that its filter no longer lets out. Returns how many items. */
    int purge() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] != null && !lets(items[i])) {
                n += items[i].stackSize;
                items[i] = null;
            }
        }
        if (n > 0) {
            countVoided(n);
            markDirty();
        }
        return n;
    }

    // ---- the buffer ----------------------------------------------------------------------------------------------

    boolean isEmpty() {
        for (ItemStack s : items) if (s != null) return false;
        return true;
    }

    /** Something drops when it breaks: loot it holds, or what was paid for {@link #noDamaged} or {@link #xpBottles}. */
    boolean hasDrops() {
        return filterPaid != null || xpPaid != null || looting.paid > 0 || speed.paid > 0 || !isEmpty();
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
