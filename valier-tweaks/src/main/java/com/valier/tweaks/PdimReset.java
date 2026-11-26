package com.valier.tweaks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.world.DimensionConfig;

/**
 * {@code /pdim reset} (2.3.6, config {@code pdim/pdimReset}): wipes a Personal Space dimension so it generates again
 * from scratch, and gives one more change of its layers and biome at the portal. Personal Space itself locks a
 * dimension's generation once it exists and has no way to start over.
 * <p>
 * <b>Who:</b> a player stands next to the portal that leads there (within {@value #RADIUS} blocks, outside the
 * dimension) and may reset it if they could break that portal block, the same rule Personal Space uses for the portal's
 * settings, so a claim keeps others out. Ops and the console may name any personal dimension. Nothing happens until the
 * same sender types {@code /pdim reset confirm} within {@code pdimResetConfirmSeconds}.
 * <p>
 * <b>What:</b> only while nobody is in the dimension: it is unloaded, then everything in its folder except Personal
 * Space's settings file ({@value #METADATA}) is moved to {@code pdim-reset-backups/<folder>-<time>/} next to the world
 * (a move on the same disk, instant whatever the size, and it can be moved back by hand). Its generation settings are
 * unlocked once ({@code pspace allow-worldgen-change}).
 * <p>
 * <b>The arrival point:</b> a portal remembers where it lands (the ground level + 1 when the dimension was made) and
 * Personal Space never changes it, and it only builds the return portal when a dimension is first made or a portal item
 * is placed. Its teleporter reads the landing point when the portal is clicked, before the dimension loads. So the click
 * that starts the first trip after a reset points every loaded portal leading there at the new ground level + 1 (or,
 * for real terrain, up in the air, moved onto the ground at the end of the tick), and the return portal is built at the
 * end of that tick ({@link PortalTileEntity#linkOtherPortal}). Portals in chunks that weren't loaded then get the same
 * landing point when their chunk loads. Saved in {@code data/valiertweaks_pdimreset.dat}.
 * <p>
 * Nothing here runs without work: the server tick returns at once unless a reset or a relink is waiting, and chunk
 * loads only look at tile entities once some dimension has been reset.
 */
public final class PdimReset {

    static final String METADATA = PersonalSpaceMod.DIM_METADATA_FILE;
    static final int RADIUS = 4;
    static final int UNLOAD_WAIT_TICKS = 200;
    static final String BACKUPS = "pdim-reset-backups";

    /** A reset that was asked for and waits for its confirmation, by sender name. */
    static final Map<String, Pending> pending = new HashMap<>();
    /** Confirmed resets waiting for their dimension to unload. */
    static final List<Job> jobs = new ArrayList<>();
    /** Portals whose return portal is built again on the next tick. */
    static final List<PortalTileEntity> relink = new ArrayList<>();
    /** Real-terrain dimensions whose arrival spot is found on the ground the tick their portal relinks. */
    static final Set<Integer> groundPending = new java.util.HashSet<>();
    /** Reset dimensions whose fresh start (first trip with the new settings) happens this tick. */
    static final Set<Integer> freshPending = new java.util.HashSet<>();
    /** Where a real-terrain dimension's portals land until the ground under them is known: in the air. */
    static final int AIR_ARRIVAL = 250;
    /** How far from a portal's spot the ground for its arrival is looked for, in blocks. */
    static final int GROUND_SEARCH = 32;
    /** What the last reset of each dimension did (for the self-test and the log). */
    static final Map<Integer, String> lastResult = new HashMap<>();

    static final class Pending {

        final int dim;
        final long expires;

        Pending(int dim, long expires) {
            this.dim = dim;
            this.expires = expires;
        }
    }

    static final class Job {

        final int dim;
        final String by;
        final String folder;
        int ticks;

        Job(int dim, String by, String folder) {
            this.dim = dim;
            this.by = by;
            this.folder = folder;
        }
    }

    /** Whether resets can work here (Personal Space loaded and the setting on). */
    static boolean available() {
        return TweaksConfig.pdimReset && Loader.isModLoaded("personalspace");
    }

    // ---- the command's two steps ---------------------------------------------------------------------------------

    /**
     * {@code /pdim reset [dimension]}: finds the dimension, checks who may, and asks for the confirmation. A player
     * without a dimension number uses the portal they stand next to.
     */
    static void ask(ICommandSender sender, Integer named, boolean op) {
        int dim;
        if (named != null) {
            if (!op) {
                say(sender, "Only ops can name a dimension. Stand next to your personal dimension's portal and leave the number off.");
                return;
            }
            dim = named;
            if (!isPersonalDim(dim)) {
                say(sender, "Dimension " + dim + " isn't a personal dimension (made with a Personal Space portal).");
                return;
            }
        } else if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PortalTileEntity portal = nearestPortal(player);
            if (portal == null) {
                say(sender, "Stand next to the portal of the personal dimension you want to reset (within " + RADIUS
                    + " blocks), outside that dimension.");
                return;
            }
            if (!op && !mayChange(player, portal)) {
                say(sender, "You can't reset this personal dimension: its portal is claimed by someone else.");
                return;
            }
            dim = portal.targetDimId;
        } else {
            say(sender, "From the console, name the dimension: /pdim reset <dimension>");
            return;
        }
        String inside = playersIn(dim);
        if (inside != null) {
            say(sender, "Someone is in personal dimension " + dim + " (" + inside + "). Everyone has to leave it first.");
            return;
        }
        int seconds = Math.max(1, TweaksConfig.pdimResetConfirmSeconds);
        pending.put(sender.getCommandSenderName(), new Pending(dim, System.currentTimeMillis() + seconds * 1000L));
        say(sender, "This wipes personal dimension " + dim + ": everything built there, chunk loaders and items in it. It then"
            + " generates again from scratch. Its settings stay, and you get one more change of its layers and biome at"
            + " the portal before going in. The old files are kept on the server, so an admin can undo it.");
        say(sender, "To go ahead, type /pdim reset " + (named != null ? named + " " : "") + "confirm within " + seconds
            + " seconds.");
    }

    /** {@code /pdim reset [dimension] confirm}: checks everything again, then unloads the dimension and resets it. */
    static void confirm(ICommandSender sender, Integer named, boolean op) {
        Pending p = pending.remove(sender.getCommandSenderName());
        if (p == null || System.currentTimeMillis() > p.expires) {
            say(sender, p == null ? "There's no reset waiting to be confirmed. Start with /pdim reset."
                : "That reset wasn't confirmed in time. Start again with /pdim reset.");
            return;
        }
        if (named != null && named != p.dim) {
            say(sender, "The reset waiting for you is for dimension " + p.dim + ", not " + named + ". Start again with /pdim reset.");
            return;
        }
        if (named == null && sender instanceof EntityPlayerMP) { // still at that dimension's portal, still allowed
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PortalTileEntity portal = nearestPortal(player);
            if (portal == null || portal.targetDimId != p.dim || !op && !mayChange(player, portal)) {
                say(sender, "You're no longer at the portal of personal dimension " + p.dim + ". Start again there with /pdim reset.");
                return;
            }
        }
        if (!isPersonalDim(p.dim)) {
            say(sender, "Dimension " + p.dim + " isn't a personal dimension any more.");
            return;
        }
        String inside = playersIn(p.dim);
        if (inside != null) {
            say(sender, "Someone is in personal dimension " + p.dim + " (" + inside + "). Everyone has to leave it first.");
            return;
        }
        for (Job j : jobs) if (j.dim == p.dim) {
            say(sender, "Personal dimension " + p.dim + " is already being reset.");
            return;
        }
        WorldServer world = DimensionManager.getWorld(p.dim);
        String folder = world != null ? world.provider.getSaveFolder() : saveFolder(p.dim);
        jobs.add(new Job(p.dim, sender.getCommandSenderName(), folder)); // unloaded at the start of the next tick
        lastResult.remove(p.dim);
        say(sender, "Resetting personal dimension " + p.dim + "...");
        ValierTweaks.LOG.info("[pdim reset] {} confirmed resetting personal dimension {} ({})", sender.getCommandSenderName(), p.dim, folder);
    }

    // ---- server ticks: finishing resets, building return portals --------------------------------------------------

    /**
     * Forge unloads a queued dimension even with players in it, so the queueing happens here, at the start of a tick,
     * right after checking it's empty: the unload runs after the worlds tick and before player packets (a portal
     * click) are handled, so nobody can step in between. The files move at the end of the tick, once it's gone.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (jobs.isEmpty() && relink.isEmpty()) return;
        if (event.phase == TickEvent.Phase.START) {
            for (Iterator<Job> it = jobs.iterator(); it.hasNext();) {
                Job job = it.next();
                if (DimensionManager.getWorld(job.dim) == null) continue;
                String inside = playersIn(job.dim);
                if (inside != null) {
                    it.remove();
                    finish(job, null, inside + " went into personal dimension " + job.dim + ", so it wasn't reset.");
                } else {
                    DimensionManager.unloadWorld(job.dim);
                }
            }
            return;
        }
        if (!relink.isEmpty()) {
            List<PortalTileEntity> now = new ArrayList<>(relink);
            relink.clear();
            for (PortalTileEntity te : now) {
                if (te.isInvalid() || te.getWorldObj() == null || !te.active) continue;
                int dim = te.targetDimId;
                try {
                    if (groundPending.remove(dim)) settle(te);
                    te.linkOtherPortal(true, null);
                } catch (RuntimeException e) {
                    ValierTweaks.LOG.warn("[pdim reset] couldn't build the return portal for the portal at {},{},{}", te.xCoord, te.yCoord, te.zCoord, e);
                }
                if (freshPending.remove(dim)) {
                    Data data = Data.get();
                    data.waiting.remove(dim);
                    data.markDirty();
                }
            }
            groundPending.clear();
            freshPending.clear();
        }
        for (Iterator<Job> it = jobs.iterator(); it.hasNext();) {
            Job job = it.next();
            if (DimensionManager.getWorld(job.dim) != null) { // loaded again since (the start of the next tick tries again)
                if (++job.ticks < UNLOAD_WAIT_TICKS) continue;
                it.remove();
                finish(job, null, "personal dimension " + job.dim + " didn't unload, so nothing was changed. Try again later.");
                continue;
            }
            it.remove();
            try {
                String backup = moveAway(job.folder);
                unlock(job.dim);
                Data data = Data.get();
                data.waiting.add(job.dim);
                data.markDirty();
                finish(job, backup, null);
            } catch (IOException | RuntimeException e) {
                ValierTweaks.LOG.error("[pdim reset] resetting personal dimension {} failed", job.dim, e);
                finish(job, null, "resetting personal dimension " + job.dim + " failed (" + e.getMessage()
                    + "). Its files were left as they were.");
            }
        }
    }

    private static void finish(Job job, String backup, String problem) {
        String text;
        if (problem != null) {
            text = "Sorry: " + problem;
            lastResult.put(job.dim, "failed: " + problem);
        } else {
            text = "Personal dimension " + job.dim + " is reset. Open its portal's settings now (sneak + right-click), pick"
                + " the layers and biome (one change) and press Done; the portal won't let anyone in before that. For real"
                + " terrain instead of flat layers (Jungle Canyon), also type /pdim terrain realistic. Then go in: it"
                + " generates fresh." + (backup != null ? " The old files are in " + backup + " on the server." : "");
            lastResult.put(job.dim, backup != null ? "reset: " + backup : "reset: nothing on disk");
            ValierTweaks.LOG.info("[pdim reset] personal dimension {} reset for {}; old files: {}", job.dim, job.by, backup != null ? backup : "none");
        }
        EntityPlayerMP player = MinecraftServer.getServer().getConfigurationManager().func_152612_a(job.by);
        if (player != null) say(player, text);
        else ValierTweaks.LOG.info("[pdim reset] {}", text);
    }

    // ---- the first trip after a reset: the arrival point and the return portal -----------------------------------------

    /**
     * The first trip after a reset, as its portal is clicked and before Personal Space's teleporter reads where to land
     * (it reads it before the dimension even loads): every loaded portal leading there is pointed at the new ground
     * level + 1 (flat layers) or up in the air (real terrain, where the ground isn't known before it generates). At the
     * end of the tick the return portal is built and, for real terrain, the arrival moves onto dry ground (settle).
     */
    static void prepare(PortalTileEntity portal, DimensionConfig cfg, Data data) {
        int dim = portal.targetDimId;
        boolean real = Loader.isModLoaded("rwg2") && PdimTerrain.isRealistic(dim);
        int y = real ? AIR_ARRIVAL : cfg.getGroundLevel() + 1;
        if (!real) {
            data.arrival.put(dim, y);
            data.arrivalXZ.remove(dim);
            data.markDirty();
        }
        int n = aimAll(dim, y, null, null, false);
        if (!relink.contains(portal)) relink.add(portal);
        if (real) groundPending.add(dim);
        freshPending.add(dim);
        ValierTweaks.LOG.info("[pdim reset] first trip into personal dimension {} after its reset: landing at y {}{}, {} portal(s) leading there", dim, y,
            real ? " until the ground is found (real terrain)" : "", n);
    }

    /** Aims every loaded portal leading to {@code dim} (from outside it); returns how many there are. */
    private static int aimAll(int dim, int y, int[] xz, World skip, boolean relinkThem) {
        int fixed = 0;
        for (WorldServer w : DimensionManager.getWorlds()) {
            if (w == null || w == skip) continue;
            for (Object c : w.theChunkProviderServer.loadedChunks) {
                for (Object o : ((Chunk) c).chunkTileEntityMap.values()) {
                    if (o instanceof PortalTileEntity && aim((PortalTileEntity) o, dim, y, xz)) {
                        if (relinkThem) relink.add((PortalTileEntity) o);
                        fixed++;
                    }
                }
            }
        }
        return fixed;
    }

    /**
     * A real-terrain dimension, just generated around its portal's spot: finds dry ground near it (not water, not
     * leaves, room for the portal and a player), points every loaded portal there, and moves players who just arrived,
     * wherever they are in that column (in the air, or inside a hill if they came another way), onto it.
     */
    static void settle(PortalTileEntity te) {
        int dim = te.targetDimId;
        WorldServer w = DimensionManager.getWorld(dim);
        if (w == null) {
            DimensionManager.initDimension(dim);
            w = DimensionManager.getWorld(dim);
        }
        if (w == null) return;
        int oldX = te.targetPosX, oldZ = te.targetPosZ;
        int[] spot = findGround(w, oldX, oldZ, te.targetFacing.offsetX, te.targetFacing.offsetZ);
        if (spot == null) spot = new int[] { oldX, Math.max(2, w.getHeightValue(oldX, oldZ)), oldZ }; // on whatever is highest
        Data data = Data.get();
        data.arrival.put(dim, spot[1]);
        data.arrivalXZ.put(dim, new int[] { spot[0], spot[2] });
        data.markDirty();
        aimAll(dim, spot[1], new int[] { spot[0], spot[2] }, w, false);
        int moved = 0;
        for (Object o : new ArrayList<Object>(w.playerEntities)) {
            if (!(o instanceof EntityPlayerMP)) continue;
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (Math.abs(p.posX - (oldX + 0.5)) > 8 || Math.abs(p.posZ - (oldZ + 0.5)) > 8) continue;
            p.fallDistance = 0;
            double tx = te.getTargetTeleportX() + 0.5, ty = te.getTargetTeleportY(), tz = te.getTargetTeleportZ() + 0.5;
            if (p.playerNetServerHandler != null) p.playerNetServerHandler.setPlayerLocation(tx, ty, tz, p.rotationYaw, p.rotationPitch);
            else p.setPosition(tx, ty, tz); // a fake player (the self-test)
            moved++;
        }
        ValierTweaks.LOG.info("[pdim reset] personal dimension {} (real terrain): arrival on the ground at {},{},{}, {} player(s) moved down", dim,
            spot[0], spot[1], spot[2], moved);
    }

    /**
     * The nearest dry, open spot within {@value #GROUND_SEARCH} blocks: {x, first air above the ground, z}, or null.
     * Asking an unloaded chunk for blocks gives an empty one here, so each candidate's chunk is loaded first, with the
     * eight around it, so it has its trees and plants too (a chunk is decorated once its neighbours exist).
     */
    static int[] findGround(World w, int x0, int z0, int fx, int fz) {
        java.util.Set<Long> loaded = new java.util.HashSet<>();
        List<int[]> spots = new ArrayList<>();
        for (int dx = -GROUND_SEARCH; dx <= GROUND_SEARCH; dx++) for (int dz = -GROUND_SEARCH; dz <= GROUND_SEARCH; dz++)
            if ((Math.abs(dx) <= 4 && Math.abs(dz) <= 4) || (dx % 2 == 0 && dz % 2 == 0)) spots.add(new int[] { dx, dz });
        spots.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        for (int[] d : spots) {
            int x = x0 + d[0], z = z0 + d[1];
            if (w instanceof WorldServer && loaded.add(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL))) {
                for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) ((WorldServer) w).theChunkProviderServer.loadChunk((x >> 4) + i, (z >> 4) + k);
            }
            int y = w.getTopSolidOrLiquidBlock(x, z);
            if (y < 2 || y > 250) continue;
            if (open(w, x, y, z) && open(w, x + fx, y, z + fz)) return new int[] { x, y, z };
        }
        return null;
    }

    /** Air at y and y + 1 (no water, no leaves) over solid ground. */
    private static boolean open(World w, int x, int y, int z) {
        return w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z) && w.getBlock(x, y - 1, z).getMaterial().isSolid()
            && !w.getBlock(x, y - 1, z).getMaterial().isLiquid();
    }

    /**
     * Right after a reset, the portal sends nobody in until its new settings are saved (Done in its settings, even
     * without changes): a trip in first would generate the area with the old settings. Once they are, the click that
     * starts the first trip prepares its landing ({@link #prepare}).
     */
    @SubscribeEvent
    public void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (event.action != net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote
            || event.entityPlayer.isSneaking()) return;
        Data data = Data.peek();
        if (data == null || data.waiting.isEmpty()) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof PortalTileEntity)) return;
        PortalTileEntity portal = (PortalTileEntity) te;
        if (!portal.active || portal.targetDimId == event.world.provider.dimensionId || !data.waiting.contains(portal.targetDimId)) return;
        DimensionConfig cfg = DimensionConfig.getForDimension(portal.targetDimId, false);
        if (cfg == null) return;
        if (!cfg.getAllowGenerationChanges()) {
            prepare(portal, cfg, data);
            return;
        }
        event.setCanceled(true);
        say(event.entityPlayer, "Personal dimension " + portal.targetDimId + " was reset: pick its layers and biome first."
            + " Sneak + right-click this portal and press Done (even without changes), then right-click to go in.");
    }

    /** Portals that weren't loaded when their dimension came back get the same arrival height. */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world.isRemote) return;
        Data data = Data.peek();
        if (data == null || data.arrival.isEmpty()) return;
        fixChunk(event.getChunk(), data);
    }

    static int fixChunk(Chunk chunk, Data data) {
        int fixed = 0;
        for (Object o : chunk.chunkTileEntityMap.values()) {
            if (!(o instanceof PortalTileEntity)) continue;
            PortalTileEntity te = (PortalTileEntity) o;
            Integer y = te.active ? data.arrival.get(te.targetDimId) : null;
            if (y != null && aim(te, te.targetDimId, y, data.arrivalXZ.get(te.targetDimId))) fixed++;
        }
        return fixed;
    }

    /**
     * Points an active portal leading to {@code dim} (from outside it) at height {@code y}, and at {@code xz} if given;
     * true if it leads there.
     */
    private static boolean aim(PortalTileEntity te, int dim, int y, int[] xz) {
        if (!te.active || te.targetDimId != dim || te.getWorldObj() == null || te.getWorldObj().provider.dimensionId == dim) return false;
        boolean changed = te.targetPosY != y || xz != null && (te.targetPosX != xz[0] || te.targetPosZ != xz[1]);
        if (changed) {
            te.targetPosY = y;
            if (xz != null) {
                te.targetPosX = xz[0];
                te.targetPosZ = xz[1];
            }
            te.markDirty();
        }
        return true;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Whether Personal Space made this dimension. */
    static boolean isPersonalDim(int dim) {
        return dim != 0 && DimensionConfig.getForDimension(dim, false) != null;
    }

    /** The nearest active portal within {@value #RADIUS} blocks that leads to another, personal dimension. */
    static PortalTileEntity nearestPortal(EntityPlayer player) {
        World world = player.worldObj;
        int px = (int) Math.floor(player.posX), py = (int) Math.floor(player.posY), pz = (int) Math.floor(player.posZ);
        PortalTileEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = px - RADIUS; x <= px + RADIUS; x++) for (int y = Math.max(0, py - RADIUS); y <= Math.min(255, py + RADIUS); y++)
            for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
                if (!world.blockExists(x, y, z)) continue;
                TileEntity te = world.getTileEntity(x, y, z);
                if (!(te instanceof PortalTileEntity)) continue;
                PortalTileEntity p = (PortalTileEntity) te;
                if (!p.active || p.targetDimId == world.provider.dimensionId || !isPersonalDim(p.targetDimId)) continue;
                double d = player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        return best;
    }

    /** Whether the player could break the portal block: spawn protection, then claims and other protection. */
    static boolean mayChange(EntityPlayer player, PortalTileEntity portal) {
        World world = portal.getWorldObj();
        int x = portal.xCoord, y = portal.yCoord, z = portal.zCoord;
        if (!world.canMineBlock(player, x, y, z)) return false;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x, y, z, world, world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    /** The names of the players in a dimension, or null if nobody is there. */
    static String playersIn(int dim) {
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null || world.playerEntities.isEmpty()) return null;
        StringBuilder names = new StringBuilder();
        for (Object o : world.playerEntities) {
            if (names.length() > 0) names.append(", ");
            names.append(((EntityPlayer) o).getCommandSenderName());
        }
        return names.toString();
    }

    /** The dimension's folder name under the world folder, without loading it. */
    static String saveFolder(int dim) {
        try {
            WorldProvider provider = DimensionManager.createProviderFor(dim);
            String folder = provider.getSaveFolder();
            if (folder != null && !folder.isEmpty()) return folder;
        } catch (RuntimeException e) {
            ValierTweaks.LOG.warn("[pdim reset] couldn't ask dimension {} for its folder", dim, e);
        }
        return "PERSONAL_DIM_" + dim;
    }

    /**
     * Moves everything in the dimension's folder except Personal Space's settings file into a new backup folder.
     * Returns that folder's path (relative to the server folder), or null when the dimension had nothing on disk. On a
     * failure, whatever was already moved goes back.
     */
    static String moveAway(String folderName) throws IOException {
        File dir = new File(DimensionManager.getCurrentSaveRootDirectory(), folderName);
        File[] children = dir.listFiles();
        if (children == null) return null;
        List<File> toMove = new ArrayList<>();
        for (File f : children) if (!METADATA.equals(f.getName())) toMove.add(f);
        if (toMove.isEmpty()) return null;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String rel = BACKUPS + "/" + folderName + "-" + stamp;
        File backup = MinecraftServer.getServer().getFile(rel);
        for (int n = 2; backup.exists(); n++) backup = MinecraftServer.getServer().getFile(rel = BACKUPS + "/" + folderName + "-" + stamp + "-" + n);
        if (!backup.mkdirs()) throw new IOException("couldn't make " + backup);
        List<File[]> moved = new ArrayList<>();
        try {
            for (File f : toMove) {
                File to = new File(backup, f.getName());
                Files.move(f.toPath(), to.toPath());
                moved.add(new File[] { f, to });
            }
        } catch (IOException e) {
            for (int i = moved.size() - 1; i >= 0; i--) {
                try {
                    Files.move(moved.get(i)[1].toPath(), moved.get(i)[0].toPath());
                } catch (IOException back) {
                    ValierTweaks.LOG.error("[pdim reset] couldn't move {} back to {}", moved.get(i)[1], moved.get(i)[0], back);
                }
            }
            throw e;
        }
        return rel;
    }

    /** One more change of the dimension's generation settings, through Personal Space's own command (it tells clients). */
    static void unlock(int dim) {
        MinecraftServer server = MinecraftServer.getServer();
        server.getCommandManager().executeCommand(server, "pspace allow-worldgen-change " + dim);
        DimensionConfig cfg = DimensionConfig.getForDimension(dim, false);
        if (cfg != null && !cfg.getAllowGenerationChanges()) cfg.setAllowGenerationChanges(true);
    }

    static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    /**
     * Dimensions reset and not yet started fresh ({@code waiting}), and the arrival height of each reset dimension's
     * portals ({@code arrival}), kept in the overworld's shared map storage.
     */
    public static final class Data extends WorldSavedData {

        static final String NAME = "valiertweaks_pdimreset";
        final Set<Integer> waiting = new TreeSet<>();
        final Map<Integer, Integer> arrival = new TreeMap<>();
        /** Real-terrain dimensions: where on the ground their portals land ({x, z}). */
        final Map<Integer, int[]> arrivalXZ = new TreeMap<>();
        private static Data cached;
        private static Object cachedFor;

        public Data(String name) {
            super(name);
        }

        static Data get() {
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld == null) return cached != null ? cached : new Data(NAME);
            if (cached != null && cachedFor == overworld.mapStorage) return cached;
            Data data = (Data) overworld.mapStorage.loadData(Data.class, NAME);
            if (data == null) {
                data = new Data(NAME);
                overworld.mapStorage.setData(NAME, data);
            }
            cached = data;
            cachedFor = overworld.mapStorage;
            return data;
        }

        /** The data without making the overworld load it (for chunk loads during the overworld's own start). */
        static Data peek() {
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld == null) return null;
            return cached != null && cachedFor == overworld.mapStorage ? cached : get();
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            waiting.clear();
            arrival.clear();
            for (int d : tag.getIntArray("Waiting")) waiting.add(d);
            int[] a = tag.getIntArray("Arrival");
            for (int i = 0; i + 1 < a.length; i += 2) arrival.put(a[i], a[i + 1]);
            arrivalXZ.clear();
            int[] b = tag.getIntArray("ArrivalXZ");
            for (int i = 0; i + 2 < b.length; i += 3) arrivalXZ.put(b[i], new int[] { b[i + 1], b[i + 2] });
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            int[] w = new int[waiting.size()];
            int i = 0;
            for (int d : waiting) w[i++] = d;
            tag.setIntArray("Waiting", w);
            int[] a = new int[arrival.size() * 2];
            i = 0;
            for (Map.Entry<Integer, Integer> e : arrival.entrySet()) {
                a[i++] = e.getKey();
                a[i++] = e.getValue();
            }
            tag.setIntArray("Arrival", a);
            int[] b = new int[arrivalXZ.size() * 3];
            i = 0;
            for (Map.Entry<Integer, int[]> e : arrivalXZ.entrySet()) {
                b[i++] = e.getKey();
                b[i++] = e.getValue()[0];
                b[i++] = e.getValue()[1];
            }
            tag.setIntArray("ArrivalXZ", b);
        }
    }
}
