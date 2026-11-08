package gtnhserver.mmfreepower;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import gregtech.api.events.OreInteractEvent;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Ore veins on the map for manipulator mining. Breaking a GregTech ore with a pickaxe calls the ore block's
 * {@code onBlockHarvested}, which posts GregTech's {@link OreInteractEvent}; VisualProspecting answers it on the server
 * by sending the player (and their VisualProspecting team) the vein that ore belongs to, so the vein's name shows on
 * their map. A Matter Manipulator removes blocks without calling {@code onBlockHarvested}, so its ores never reached
 * the map.
 * <p>
 * Before a manipulator removes a natural (world-generated) ore that isn't a small ore, {@link #beforeRemove} posts the
 * same event, the way the pickaxe's harvest would. VisualProspecting's answer depends only on the dimension, the chunk
 * and the ore's material, so the event goes out once per chunk and material for each player instead of for every
 * block. Small and player-placed ores are skipped: VisualProspecting ignores those.
 */
public final class VeinReveal {

    /** Chunk and material keys each player's manipulator has already reported (until they log out). */
    private static final Map<EntityPlayer, LongOpenHashSet> REPORTED = new WeakHashMap<>();
    private static boolean warned;

    private VeinReveal() {}

    /** Called with the block still in place, before the manipulator removes it. */
    public static void beforeRemove(World world, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote || !(player instanceof EntityPlayerMP) || player instanceof FakePlayer) return;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        try (OreInfo<IOreMaterial> info = OreManager.getOreInfo(world, x, y, z)) {
            if (info == null || !info.isNatural || info.isSmall || info.material == null) return;
            LongOpenHashSet reported = REPORTED.get(player);
            if (reported == null) REPORTED.put(player, reported = new LongOpenHashSet());
            if (reported.size() > 100_000) reported.clear();
            if (!reported.add(key(world.provider.dimensionId, x >> 4, z >> 4, info.material))) return;
        } catch (Throwable t) {
            warnOnce("reading the ore at " + x + " " + y + " " + z, t);
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.post(new OreInteractEvent(world, x, y, z, block, meta, player));
        } catch (Throwable t) {
            warnOnce("telling VisualProspecting about the ore at " + x + " " + y + " " + z, t);
        }
    }

    /** Forgets what this player's manipulator reported (the self-test uses it). */
    public static void forget(EntityPlayer player) {
        REPORTED.remove(player);
    }

    private static long key(int dim, int chunkX, int chunkZ, IOreMaterial material) {
        long k = ((long) dim & 0xFFFFL) << 48 | ((long) chunkX & 0xFFFFFFL) << 24 | (long) chunkZ & 0xFFFFFFL;
        return k * 0x9E3779B97F4A7C15L + System.identityHashCode(material);
    }

    private static void warnOnce(String what, Throwable t) {
        if (warned) return;
        warned = true;
        MMFreePower.LOG.warn("Failed " + what + " for the map (only logged once)", t);
    }
}
