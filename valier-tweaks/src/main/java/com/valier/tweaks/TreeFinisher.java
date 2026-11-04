package com.valier.tweaks;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A Tinkers' Construct lumber axe fells a tree a log at a time, and stops as soon as the axe breaks (or the player
 * switches items), leaving the rest of the tree floating. This watches lumber axe chops and, once one stops with logs
 * of the tree still standing, fells those too, the same way Tinkers walks a tree (each log, then the 3x3 around it on
 * its own layer and the one above). The extra logs are broken as by the player's hand: they drop normally, each one
 * fires a block-break event (so claims protect them), and the axe takes no more wear.
 * <p>
 * A chop counts as stopped at the end of the first server tick with no lumber axe breaks: Tinkers breaks up to 128
 * logs every tick until it is done. It only counts as a tree chop if Tinkers broke logs itself (the clicked log is
 * reported twice: once by Minecraft, once by Tinkers), so an ordinary single log broken with a lumber axe, while
 * sneaking or in a log wall without leaves, isn't touched.
 */
public final class TreeFinisher {

    private static final String LUMBER_AXE = "tconstruct.items.tools.LumberAxe";

    /** Totals since the server started, for the log and the self-test. */
    static int treesFinished, logsFinished;

    private final Map<EntityPlayerMP, Chop> chops = new WeakHashMap<>();
    private final List<Finish> finishing = new ArrayList<>();
    private final Map<Class<?>, Boolean> axeClasses = new HashMap<>();
    /** True while this class breaks a log, so its own break events aren't taken for a new chop. */
    private boolean breaking;
    private long tick;

    private static final class Chop {

        final World world;
        final Set<ChunkPosition> broken = new HashSet<>();
        int breaks;
        long lastTick;

        Chop(World world) {
            this.world = world;
        }
    }

    private static final class Finish {

        final EntityPlayerMP player;
        final World world;
        final Deque<ChunkPosition> queue = new ArrayDeque<>();
        final Set<ChunkPosition> seen;
        final ChunkPosition start;
        final boolean axeBroke;
        int broken;

        Finish(EntityPlayerMP player, Chop chop, boolean axeBroke) {
            this.player = player;
            this.world = chop.world;
            this.seen = new HashSet<>(chop.broken);
            this.start = chop.broken.iterator()
                .next();
            this.axeBroke = axeBroke;
            for (ChunkPosition p : chop.broken) queueAround(p.chunkPosX, p.chunkPosY, p.chunkPosZ);
        }

        /** The 3x3 on this log's layer and the layer above, as Tinkers' TreeChopTask does. */
        void queueAround(int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                ChunkPosition p = new ChunkPosition(x + dx, y + dy, z + dz);
                if (!seen.contains(p)) queue.add(p);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (breaking || !TweaksConfig.finishLumberAxeTrees || event.world.isRemote) return;
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (!isLumberAxe(player.getCurrentEquippedItem())) return;
        if (!event.block.isWood(event.world, event.x, event.y, event.z)) return;
        Chop chop = chops.get(player);
        if (chop == null || chop.world != event.world) {
            // Tinkers only fells trees (enough leaves at the top), and never for a sneaking player.
            if (player.isSneaking() || !isTree(event.world, event.x, event.y, event.z)) return;
            chop = new Chop(event.world);
            chops.put(player, chop);
        }
        chop.broken.add(new ChunkPosition(event.x, event.y, event.z));
        chop.breaks++;
        chop.lastTick = tick;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Iterator<Map.Entry<EntityPlayerMP, Chop>> it = chops.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<EntityPlayerMP, Chop> entry = it.next();
                Chop chop = entry.getValue();
                if (chop.lastTick == tick) continue; // Tinkers is still at it
                it.remove();
                if (chop.breaks < 2) continue; // Minecraft broke one log; Tinkers never started
                finishing.add(new Finish(entry.getKey(), chop, isBroken(entry.getKey().getCurrentEquippedItem())));
            }
            for (Iterator<Finish> fit = finishing.iterator(); fit.hasNext();) if (work(fit.next())) fit.remove();
        } finally {
            tick++;
        }
    }

    /** Fells up to logsPerTick more logs of this tree; true when the tree is done. */
    private boolean work(Finish f) {
        if (f.player.isDead || f.player.worldObj != f.world) return true;
        int budget = TweaksConfig.logsPerTick;
        while (budget > 0 && !f.queue.isEmpty() && f.broken < TweaksConfig.maxExtraLogs) {
            ChunkPosition p = f.queue.poll();
            if (!f.seen.add(p)) continue;
            int x = p.chunkPosX, y = p.chunkPosY, z = p.chunkPosZ;
            if (!f.world.blockExists(x, y, z) || !f.world.getBlock(x, y, z)
                .isWood(f.world, x, y, z)) continue;
            f.queueAround(x, y, z); // Tinkers walks on past a log it may not break, and so do we
            if (breakLog(f.player, f.world, x, y, z)) {
                f.broken++;
                budget--;
            }
        }
        if (!f.queue.isEmpty() && f.broken < TweaksConfig.maxExtraLogs) return false;
        if (f.broken > 0) {
            treesFinished++;
            logsFinished += f.broken;
            ValierTweaks.LOG.info(
                "Finished a tree for {} at {} {} {}: {} more log(s), after the lumber axe {}{}",
                f.player.getCommandSenderName(),
                f.start.chunkPosX,
                f.start.chunkPosY,
                f.start.chunkPosZ,
                f.broken,
                f.axeBroke ? "broke" : "stopped (item switched)",
                f.broken >= TweaksConfig.maxExtraLogs ? "; stopped at maxExtraLogs" : "");
        }
        return true;
    }

    /** Breaks a log as the player would by hand (Minecraft's own harvest steps), unless a protection mod objects. */
    private boolean breakLog(EntityPlayerMP player, World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        breaking = true;
        try {
            BlockEvent.BreakEvent event = ForgeHooks
                .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
            if (event.isCanceled()) return false;
            boolean creative = player.capabilities.isCreativeMode;
            boolean harvest = !creative && block.canHarvestBlock(player, meta);
            block.onBlockHarvested(world, x, y, z, meta, player);
            if (block.removedByPlayer(world, player, x, y, z, harvest)) {
                block.onBlockDestroyedByPlayer(world, x, y, z, meta);
                if (harvest) {
                    block.harvestBlock(world, player, x, y, z, meta);
                    block.dropXpOnBlockBreak(world, x, y, z, event.getExpToDrop());
                }
            }
            if (player.playerNetServerHandler != null)
                player.playerNetServerHandler.sendPacket(new S23PacketBlockChange(x, y, z, world));
            return true;
        } finally {
            breaking = false;
        }
    }

    private boolean isLumberAxe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Class<?> type = stack.getItem()
            .getClass();
        Boolean known = axeClasses.get(type);
        if (known == null) {
            known = false;
            for (Class<?> c = type; c != null; c = c.getSuperclass()) if (LUMBER_AXE.equals(c.getName())) known = true;
            axeClasses.put(type, known);
        }
        return known;
    }

    private static boolean isBroken(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .getCompoundTag("InfiTool")
                .getBoolean("Broken");
    }

    private static Method detectTree;
    private static boolean lookedUp;

    /** Tinkers' own "is this a tree" check; if it can't be found, the two-breaks rule alone decides. */
    static boolean isTree(World world, int x, int y, int z) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                detectTree = Class.forName(LUMBER_AXE)
                    .getMethod("detectTree", World.class, int.class, int.class, int.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                ValierTweaks.LOG.warn("Tinkers' LumberAxe.detectTree not found, using the two-breaks rule only: " + e);
            }
        }
        if (detectTree == null) return true;
        try {
            return (Boolean) detectTree.invoke(null, world, x, y, z);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }
}
