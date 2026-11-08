package com.valier.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Finds worldgen chests, locks their loot away, and shows each player their own copy when they open one.
 * <p>
 * A chest counts as worldgen loot when either
 * <ul>
 * <li>its chunk loads from disk and no player has ever been near that chunk (its InhabitedTime is 0), so nobody can
 * have placed it; or</li>
 * <li>it appears while a brand-new chunk is being populated (structures being generated).</li>
 * </ul>
 * As soon as a chest is found its loot moves into the mod's saved data and the real block is left empty, so nothing
 * that reaches the block directly (hoppers, pipes, drones, dollies, wrenches, explosions) can take or change the loot.
 * The two halves of a double chest always count as one loot chest. Chests players place are recorded and never count.
 */
public class LootEvents {

    private static final int[][] HORIZONTAL = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final int[][] ALL_SIDES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    /**
     * Weight for {@link #generator}: the highest, so it runs after every structure mod's world generator, which all
     * run after PopulateChunkEvent.Post.
     */
    public static final int GENERATOR_WEIGHT = Integer.MAX_VALUE;

    /** For each chunk being populated (keyed by chunk coordinates), what was around it when population started. */
    private final Map<World, Map<Long, Snapshot>> populating = new HashMap<>();
    /**
     * Loot chests whose chunk just loaded. Their loot is locked away at the end of the tick: during the load event the
     * chunk isn't registered as loaded yet, so touching the block there would load it a second time.
     */
    private final Map<World, Set<Long>> toLock = new HashMap<>();
    /**
     * Loot chests someone broke. Checked at the end of each tick until the block really is gone (or the deadline
     * passes): then the record is dropped, and the breaker's share of the loot with it.
     */
    private final Map<World, Map<Long, Breaking>> toForget = new HashMap<>();

    /** How long to wait for a broken loot chest's block to disappear: 5 minutes. */
    private static final long BREAK_WAIT_TICKS = 6000;

    private static final class Breaking {

        /** The survival player whose share drops; null for creative mode or no player. */
        final UUID breaker;
        final long deadline;

        Breaking(UUID breaker, long deadline) {
            this.breaker = breaker;
            this.deadline = deadline;
        }
    }

    /** The chunks around a populating chunk that were loaded when it started, and the chests in them. */
    private static final class Snapshot {

        final Set<Long> loadedChunks = new HashSet<>();
        final Set<Long> chests = new HashSet<>();
    }

    // ---- Finding loot chests ----

    /**
     * The last step of populating a new chunk.
     * <ul>
     * <li>If the chunk generator posted PopulateChunkEvent.Pre (vanilla's and RWG2's do), every chest with loot that
     * appeared in the surrounding 3x3 chunks since then is a structure's.</li>
     * <li>If it didn't (flat worlds, some modded dimensions), only chests in this chunk count. Players never get a
     * chunk before it is populated, so nothing in it can be theirs.</li>
     * </ul>
     */
    public final IWorldGenerator generator = (random, chunkX, chunkZ, world, chunkGenerator, chunkProvider) -> {
        if (!LootConfig.enabled || !LootConfig.registerNewChunks || world.isRemote) return;
        Map<Long, Snapshot> forWorld = populating.get(world);
        Snapshot before = forWorld == null ? null : forWorld.remove(LootData.chunkKey(chunkX, chunkZ));
        LootData data = null;
        for (TileEntityChest chest : before != null ? chestsIn(world, before.loadedChunks)
            : chestsIn(world, Collections.singleton(LootData.chunkKey(chunkX, chunkZ)))) {
            long pos = LootData.pack(chest.xCoord, chest.yCoord, chest.zCoord);
            if (before != null && before.chests.contains(pos) || !hasItems(chest)) continue;
            if (data == null) data = LootData.get(world);
            data.forget(pos); // a regenerated chunk's chest replaces whatever was recorded there
            data.addPending(pos);
            lock(world, data, chest.xCoord, chest.yCoord, chest.zCoord);
        }
    };

    /**
     * Reads the chunk's loot state from its save (always, even when the mod is switched off, so its next save keeps
     * it), registers the worldgen chests of a chunk no player has been near, and queues every chest in it whose loot
     * still has to be locked away. Only the NBT is read: the chunk isn't registered as loaded yet, so touching the
     * world here would load it a second time.
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkDataEvent.Load event) {
        World world = event.world;
        if (world == null || world.isRemote) return;
        NBTTagCompound root = event.getData();
        NBTTagCompound level = root.hasKey("Level", 10) ? root.getCompoundTag("Level") : root;
        List<Long> newChests = new ArrayList<>();
        if (LootConfig.enabled && level.getLong("InhabitedTime") <= LootConfig.maxInhabitedTicks) {
            NBTTagList tiles = level.getTagList("TileEntities", 10);
            for (int i = 0; i < tiles.tagCount(); i++) {
                NBTTagCompound tile = tiles.getCompoundTagAt(i);
                if ("Chest".equals(tile.getString("id")) && tile.getTagList("Items", 10)
                    .tagCount() > 0) {
                    newChests.add(LootData.pack(tile.getInteger("x"), tile.getInteger("y"), tile.getInteger("z")));
                }
            }
        }
        Chunk chunk = event.getChunk();
        LootData data = LootData.get(world);
        data.readChunk(chunk, level, newChests);
        if (!LootConfig.enabled) return;
        for (long pos : data.pendingIn(chunk.xPosition, chunk.zPosition)) queue(toLock, world, pos);
    }

    /**
     * InhabitedTime only catches up when the last player leaves a chunk or every 8000 ticks, so a chunk a player
     * entered a few minutes before a save (a restart, a crash) would be saved as "never visited" and its chests could
     * wrongly become loot next time. Mark chunks being watched right now as visited.
     */
    @SubscribeEvent
    public void onChunkSave(ChunkDataEvent.Save event) {
        if (!(event.world instanceof WorldServer)) return;
        Chunk chunk = event.getChunk();
        NBTTagCompound root = event.getData();
        NBTTagCompound level = root.hasKey("Level", 10) ? root.getCompoundTag("Level") : root;
        if (chunk.inhabitedTime <= LootConfig.maxInhabitedTicks && ((WorldServer) event.world).getPlayerManager()
            .func_152621_a(chunk.xPosition, chunk.zPosition)) {
            chunk.inhabitedTime = LootConfig.maxInhabitedTicks + 1L;
            level.setLong("InhabitedTime", chunk.inhabitedTime);
        }
        LootData.get(event.world)
            .writeChunk(chunk, level);
    }

    /** A chunk is unloaded just before its last save; its loot state is dropped from memory after that save. */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world != null && !event.world.isRemote) LootData.get(event.world)
            .markUnloading(event.getChunk());
    }

    @SubscribeEvent
    public void onPopulate(PopulateChunkEvent.Pre event) {
        World world = event.world;
        if (!LootConfig.enabled || !LootConfig.registerNewChunks || world == null || world.isRemote) return;
        // 3x3 rather than the 2x2 vanilla population writes into: modded structures often spill over.
        Snapshot before = new Snapshot();
        for (int cx = event.chunkX - 1; cx <= event.chunkX + 1; cx++) {
            for (int cz = event.chunkZ - 1; cz <= event.chunkZ + 1; cz++) {
                if (world.getChunkProvider()
                    .chunkExists(cx, cz)) before.loadedChunks.add(LootData.chunkKey(cx, cz));
            }
        }
        for (TileEntityChest chest : chestsIn(world, before.loadedChunks)) {
            before.chests.add(LootData.pack(chest.xCoord, chest.yCoord, chest.zCoord));
        }
        Map<Long, Snapshot> forWorld = populating.get(world);
        if (forWorld == null) populating.put(world, forWorld = new HashMap<>());
        forWorld.put(LootData.chunkKey(event.chunkX, event.chunkZ), before);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        World world = event.world;
        Set<Long> lock = toLock.remove(world);
        Map<Long, Breaking> forget = toForget.get(world);
        if (lock == null && forget == null) return;
        LootData data = LootData.get(world);
        if (lock != null) {
            for (long pos : lock) {
                int x = LootData.unpackX(pos), y = LootData.unpackY(pos), z = LootData.unpackZ(pos);
                if (!data.isPending(pos) || !world.blockExists(x, y, z)) continue; // still pending: locked on next load
                if (isVanillaChest(world.getTileEntity(x, y, z))) lock(world, data, x, y, z);
                else data.forget(pos);
            }
        }
        if (forget != null) {
            Iterator<Map.Entry<Long, Breaking>> it = forget.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Breaking> broken = it.next();
                long pos = broken.getKey();
                int x = LootData.unpackX(pos), y = LootData.unpackY(pos), z = LootData.unpackZ(pos);
                if (!world.blockExists(x, y, z) || world.getBlock(x, y, z) instanceof BlockChest) {
                    // Not gone (yet): a Matter Manipulator removes blocks some ticks after asking. Give up eventually.
                    if (world.getTotalWorldTime() > broken.getValue().deadline) it.remove();
                    continue;
                }
                it.remove();
                closeWindows(world, x, y, z);
                // The breaker keeps their share: what's left in their copy, or all of it if they never opened it.
                LootData.LootChest chest = data.getOpened(pos);
                UUID breaker = broken.getValue().breaker;
                if (chest != null && breaker != null) drop(world, x, y, z, chest.copyFor(breaker));
                data.forget(pos);
            }
            if (forget.isEmpty()) toForget.remove(world);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        populating.remove(event.world);
        toLock.remove(event.world);
        toForget.remove(event.world);
        LootData.unload(event.world);
    }

    // ---- Opening ----

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote
            || !LootConfig.enabled) return;
        World world = event.world;
        int x = event.x, y = event.y, z = event.z;
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlockChest) || !isVanillaChest(world.getTileEntity(x, y, z))) return;
        LootData data = LootData.get(world);
        long pos = LootData.pack(x, y, z);
        EntityPlayer player = event.entityPlayer;
        if (!data.isLoot(pos)) {
            // A chest joined to a loot chest after its loot was locked (only possible without events, e.g. a dolly)
            // would open as a vanilla double chest over the loot chest's block. Refuse instead.
            if (data.isIgnored(pos) || !hasLootNeighbour(world, data, x, y, z, block)) return;
            event.setCanceled(true);
            tell(player, "This chest is joined to a loot chest. Move it away to open it.");
            return;
        }
        ItemStack held = player.getHeldItem();
        if (player.isSneaking() && held != null) {
            // Sneak-clicking with a block places it against the chest, as in vanilla; nothing may open or use the
            // real chest (dollies, wrenches and the like act on the block itself).
            if (held.getItem() instanceof ItemBlock) event.useBlock = Event.Result.DENY;
            else event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (player instanceof FakePlayer || !(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        UUID id = mp.getGameProfile()
            .getId();
        IInventory view = buildView(id, world, x, y, z, data);
        NBTTagCompound persisted = mp.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!LootConfig.firstOpenMessage.isEmpty() && !persisted.getBoolean("ValierLootInformed")) {
            persisted.setBoolean("ValierLootInformed", true);
            mp.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            tell(mp, LootConfig.firstOpenMessage);
        }
        mp.displayGUIChest(view);
    }

    /**
     * The window contents a player sees for the loot chest at x, y, z: their own copy of this half, plus their copy
     * of the other half if it is a double chest (west/north half first, as vanilla orders them).
     */
    public static IInventory buildView(UUID player, World world, int x, int y, int z, LootData data) {
        LootView view = half(player, world, x, y, z, data);
        Block block = world.getBlock(x, y, z);
        for (int[] d : HORIZONTAL) {
            int nx = x + d[0], nz = z + d[1];
            if (!data.isLoot(LootData.pack(nx, y, nz)) || world.getBlock(nx, y, nz) != block
                || !isVanillaChest(world.getTileEntity(nx, y, nz))) continue;
            LootView other = half(player, world, nx, y, nz, data);
            return nx < x || nz < z ? new ChestWindow(other, view) : new ChestWindow(view, other);
        }
        return new ChestWindow(view, null);
    }

    private static LootView half(UUID player, World world, int x, int y, int z, LootData data) {
        LootData.LootChest chest = lock(world, data, x, y, z);
        return new LootView(chest.copyFor(player), data, world, x, y, z);
    }

    /**
     * Locks a loot chest's loot away (moving it out of the block, if that hasn't happened yet). At that moment the
     * other half of its double chest becomes loot too if it is empty (an empty worldgen half) or was found as loot
     * itself. Only then, and never a full chest nobody registered: a chest that turns up next to it later (moved there
     * by a dolly, which fires no events) is never adopted, or its owner's items would become everyone's loot. Returns
     * the loot record, or null if x, y, z is not loot.
     */
    public static LootData.LootChest lock(World world, LootData data, int x, int y, int z) {
        boolean fresh = data.isPending(LootData.pack(x, y, z));
        LootData.LootChest chest = data.open(x, y, z);
        if (chest == null || !fresh) return chest;
        Block block = world.getBlock(x, y, z);
        for (int[] d : HORIZONTAL) {
            int nx = x + d[0], nz = z + d[1];
            long n = LootData.pack(nx, y, nz);
            if (data.getOpened(n) != null || data.isIgnored(n) || !world.blockExists(nx, y, nz)
                || world.getBlock(nx, y, nz) != block) continue;
            TileEntity te = world.getTileEntity(nx, y, nz);
            // Only an empty half, or one found as loot itself: a full chest nobody registered is a player's.
            if (!isVanillaChest(te) || !data.isPending(n) && hasItems((TileEntityChest) te)) continue;
            data.addPending(n);
            data.open(nx, y, nz);
        }
        return chest;
    }

    private static boolean hasLootNeighbour(World world, LootData data, int x, int y, int z, Block block) {
        for (int[] d : HORIZONTAL) {
            int nx = x + d[0], nz = z + d[1];
            if (data.isLoot(LootData.pack(nx, y, nz)) && world.getBlock(nx, y, nz) == block) return true;
        }
        return false;
    }

    /** Closes any loot window open on the chest at x, y, z (before its copies change under it). */
    public static void closeWindows(World world, int x, int y, int z) {
        for (Object o : world.playerEntities) {
            if (!(o instanceof EntityPlayerMP) || o instanceof FakePlayer) continue;
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (!(player.openContainer instanceof ContainerChest)) continue;
            IInventory inv = ((ContainerChest) player.openContainer).getLowerChestInventory();
            if (inv instanceof ChestWindow && ((ChestWindow) inv).shows(x, y, z)) player.closeScreen();
        }
    }

    // ---- Protection ----

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        World world = event.world;
        if (world.isRemote || !LootConfig.enabled || !(event.block instanceof BlockChest)) return;
        LootData data = LootData.get(world);
        if (!data.isLoot(LootData.pack(event.x, event.y, event.z))) return;
        EntityPlayer player = event.getPlayer();
        if (!LootConfig.protectFromBreaking || player != null && player.capabilities.isCreativeMode) return;
        // A Matter Manipulator (it asks with this event before removing a block) only removes loot chests if the
        // config allows it, sneaking or not; by default it skips them. It asks again on every attempt, so the
        // explanation is sent at most every 10 seconds.
        if (player != null && !(player instanceof FakePlayer) && isMatterManipulator(player.getHeldItem())) {
            if (LootConfig.matterManipulatorBreaks) return;
            event.setCanceled(true);
            tellAtMostEvery(
                player,
                10_000L,
                "Matter Manipulators skip loot chests: everyone gets their own copy of what's inside. Open it to take "
                    + "your share"
                    + (LootConfig.breakWhileSneaking ? ", or break it by hand while sneaking." : "."));
            return;
        }
        // As in Lootr, a player can still break one on purpose by sneaking (to build a base in a village, say).
        // Machines never can.
        if (player != null && !(player instanceof FakePlayer) && LootConfig.breakWhileSneaking && player.isSneaking())
            return;
        event.setCanceled(true);
        tell(
            player,
            LootConfig.breakWhileSneaking
                ? "This loot chest gives everyone their own copy. To break it anyway, sneak while mining it: your share "
                    + "drops, and everyone else loses theirs."
                : "Loot chests can't be broken: everyone gets their own copy of what's inside.");
    }

    /** The Matter Manipulator (any tier), recognised by class so this mod needs no compile-time link to it. */
    static boolean isMatterManipulator(ItemStack held) {
        return held != null && held.getItem() != null
            && held.getItem()
                .getClass()
                .getName()
                .startsWith("com.recursive_pineapple.matter_manipulator.");
    }

    /**
     * Runs last, and only if nothing (this mod, a claim) cancelled the break. The break can still fail after the
     * event (or, for a Matter Manipulator, happen ticks later), so the loot record and the breaker's share are only
     * dealt with once the chest really is gone.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBroken(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || !LootConfig.enabled) return;
        long pos = LootData.pack(event.x, event.y, event.z);
        if (!LootData.get(event.world)
            .isLoot(pos)) return;
        EntityPlayer player = event.getPlayer();
        UUID breaker = player != null && !(player instanceof FakePlayer) && !player.capabilities.isCreativeMode
            ? player.getGameProfile()
                .getId()
            : null;
        Map<Long, Breaking> forWorld = toForget.get(event.world);
        if (forWorld == null) toForget.put(event.world, forWorld = new HashMap<>());
        forWorld.put(pos, new Breaking(breaker, event.world.getTotalWorldTime() + BREAK_WAIT_TICKS));
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        World world = event.world;
        if (world.isRemote || !LootConfig.enabled) return;
        LootData data = LootData.get(world);
        int x = event.x, y = event.y, z = event.z;
        long pos = LootData.pack(x, y, z);
        if (data.isLoot(pos)) data.forget(pos); // a block placed where a lost loot chest used to be
        if (event.placedBlock instanceof BlockChest) {
            if (LootConfig.blockAdjacentChests) {
                for (int[] d : HORIZONTAL) {
                    int nx = x + d[0], nz = z + d[1];
                    if (data.isLoot(LootData.pack(nx, y, nz)) && world.getBlock(nx, y, nz) instanceof BlockChest) {
                        event.setCanceled(true);
                        tell(event.player, "You can't place a chest next to a loot chest.");
                        return;
                    }
                }
            }
            data.ignore(pos);
        }
        // Anything placed against a loot chest whose loot is still in the block (a hopper, a pipe) would reach it,
        // so lock the loot away first.
        for (int[] d : ALL_SIDES) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (data.isPending(LootData.pack(nx, ny, nz)) && isVanillaChest(world.getTileEntity(nx, ny, nz))) {
                lock(world, data, nx, ny, nz);
            }
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.world;
        if (world.isRemote || !LootConfig.enabled || !LootConfig.protectFromBreaking) return;
        LootData data = LootData.get(world);
        Iterator<ChunkPosition> it = event.getAffectedBlocks()
            .iterator();
        while (it.hasNext()) {
            ChunkPosition p = it.next();
            if (data.isLoot(LootData.pack(p.chunkPosX, p.chunkPosY, p.chunkPosZ))
                && world.getBlock(p.chunkPosX, p.chunkPosY, p.chunkPosZ) instanceof BlockChest) it.remove();
        }
    }

    // ---- Helpers ----

    private static void queue(Map<World, Set<Long>> queue, World world, long pos) {
        Set<Long> set = queue.get(world);
        if (set == null) queue.put(world, set = new HashSet<>());
        set.add(pos);
    }

    /** Vanilla chests in those of the given chunks that are loaded (never loads a chunk). */
    private static List<TileEntityChest> chestsIn(World world, Collection<Long> chunkKeys) {
        List<TileEntityChest> found = new ArrayList<>();
        for (long key : chunkKeys) {
            int cx = (int) (key >> 32), cz = (int) key;
            if (!world.getChunkProvider()
                .chunkExists(cx, cz)) continue;
            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
            for (Object o : chunk.chunkTileEntityMap.values()) {
                if (isVanillaChest((TileEntity) o)) found.add((TileEntityChest) o);
            }
        }
        return found;
    }

    public static boolean isVanillaChest(TileEntity te) {
        return te != null && te.getClass() == TileEntityChest.class;
    }

    static boolean hasItems(TileEntityChest chest) {
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (chest.getStackInSlot(i) != null) return true;
        }
        return false;
    }

    /** Tells a real, connected player something; machines and test players are skipped. */
    static void tell(EntityPlayer player, String text) {
        if (player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
            && ((EntityPlayerMP) player).playerNetServerHandler != null) player.addChatMessage(message(text));
    }

    private static final Map<UUID, Long> lastTold = new HashMap<>();

    /** Like {@link #tell}, but at most once per interval for each player. */
    static void tellAtMostEvery(EntityPlayer player, long millis, String text) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        UUID id = player.getGameProfile()
            .getId();
        Long last = lastTold.get(id);
        if (last != null && now - last < millis) return;
        lastTold.put(id, now);
        tell(player, text);
    }

    /** Drops the stacks at the chest's position, the way a broken chest spills its contents. */
    static void drop(World world, int x, int y, int z, ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) continue;
            EntityItem item = new EntityItem(
                world,
                x + 0.2D + world.rand.nextDouble() * 0.6D,
                y + 0.2D + world.rand.nextDouble() * 0.6D,
                z + 0.2D + world.rand.nextDouble() * 0.6D,
                stack.copy());
            item.delayBeforeCanPickup = 10;
            world.spawnEntityInWorld(item);
        }
    }

    static ChatComponentText message(String text) {
        ChatComponentText msg = new ChatComponentText(text);
        msg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
        return msg;
    }
}
