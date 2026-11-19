package com.valier.tweaks.pipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.World;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.Loader;
import serverutils.data.ClaimedChunks;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;
import serverutils.lib.math.ChunkDimPos;

/**
 * Who a pipe belongs to, for Wireless tags and claims (ServerUtilities, when it is installed). A Wireless tag is
 * private to the pipe owner's team, or to the owner alone without a team, so nobody else can send to or take from it.
 * Pipes don't join blocks in a chunk claimed by a team their owner isn't in (or allied with).
 * <p>
 * The ServerUtilities classes are only touched when it is loaded. Team answers are kept for a minute.
 */
final class TeamScope {

    static final boolean SU = Loader.isModLoaded("serverutilities");
    private static final long KEEP_MS = 60_000;
    private static final Map<UUID, Object[]> cache = new HashMap<>();
    private static boolean warned;

    private TeamScope() {}

    /** The scope of an owner's Wireless tags: "t:<team>" or "p:<uuid>". */
    static Object of(UUID owner) {
        if (owner == null) return "nobody";
        long now = System.currentTimeMillis();
        Object[] c = cache.get(owner);
        if (c != null && now - (Long) c[1] < KEEP_MS) return c[0];
        Object s = null;
        if (SU) {
            try {
                s = Su.team(owner);
            } catch (Throwable e) {
                warn(e);
            }
        }
        if (s == null) s = "p:" + owner;
        cache.put(owner, new Object[] { s, now });
        return s;
    }

    /** Whether a pipe of this owner may join the block at x, z (claims). Unknown owners and unclaimed chunks: yes. */
    static boolean mayUse(UUID owner, World w, int x, int z) {
        if (!SU || owner == null) return true;
        try {
            return Su.mayUse(owner, w.provider.dimensionId, x >> 4, z >> 4);
        } catch (Throwable e) {
            warn(e);
            return true;
        }
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        ValierTweaks.LOG.warn("Valier pipes couldn't ask ServerUtilities about teams or claims; it won't again", e);
    }

    /** ServerUtilities itself (loaded only when it is installed). */
    private static final class Su {

        static String team(UUID id) {
            if (!Universe.loaded()) return null;
            ForgePlayer p = Universe.get()
                .getPlayer(id);
            if (p == null || !p.hasTeam() || p.team == null || !p.team.isValid()) return null;
            return "t:" + p.team.getUID();
        }

        static boolean mayUse(UUID id, int dim, int cx, int cz) {
            if (!Universe.loaded() || !ClaimedChunks.isActive()) return true;
            ForgeTeam t = ClaimedChunks.instance.getChunkTeam(new ChunkDimPos(cx, cz, dim));
            if (t == null) return true;
            ForgePlayer p = Universe.get()
                .getPlayer(id);
            return p != null && (t.isMember(p) || t.isAlly(p));
        }
    }
}
