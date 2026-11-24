package com.valier.tweaks;

import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;

import com.sk89q.worldedit.forge.ForgePermissionsProvider;
import com.sk89q.worldedit.forge.ForgeWorldEdit;

import serverutils.lib.util.permission.DefaultPermissionLevel;
import serverutils.lib.util.permission.PermissionAPI;
import serverutils.ranks.Ranks;

/**
 * WorldEdit by rank (2.3.4, config {@code permissions/worldEditRanks}). GTNH's WorldEdit only asks "is this player an
 * op?" (or "in creative?", if its own config allows that). With this it also asks the player's ServerUtilities rank,
 * so a rank with {@code worldedit: true} gets WorldEdit without being op.
 * <p>
 * The nodes are WorldEdit's own: {@code worldedit.region.set}, {@code worldedit.clipboard.copy} and so on. A rank can
 * allow a whole branch ({@code worldedit}, {@code worldedit.region}) and turn single nodes back off
 * ({@code worldedit.reload: false}). The more exact node wins. WorldEdit's command nodes are registered with
 * ServerUtilities (default: ops only). Its in-game rank editor only lists nodes under its own prefixes, so they are set
 * with {@code /ranks set_permission} instead.
 * <p>
 * Only touched when WorldEdit and ServerUtilities are both loaded: it names their classes.
 */
final class WorldEditRanks implements ForgePermissionsProvider {

    /** WorldEdit's own check (ops, cheat mode, creative if allowed), asked first. */
    final ForgePermissionsProvider old;
    /** WorldEdit command nodes registered with ServerUtilities since the server started (the self-test reads it). */
    static int registered;

    WorldEditRanks(ForgePermissionsProvider old) {
        this.old = old;
    }

    /** Puts itself in front of WorldEdit's provider, once (at server start, after WorldEdit made its own). */
    static void install() {
        ForgeWorldEdit we = ForgeWorldEdit.inst;
        if (we == null) {
            ValierTweaks.LOG.warn("WorldEdit by rank: WorldEdit isn't set up; ranks won't give WorldEdit");
            return;
        }
        ForgePermissionsProvider current = we.getPermissionsProvider();
        if (current instanceof WorldEditRanks) return;
        we.setPermissionsProvider(new WorldEditRanks(current));
        ValierTweaks.LOG.info("WorldEdit by rank: ServerUtilities ranks can now give WorldEdit (worldedit.* nodes)");
    }

    /** Whether ranks are the ones deciding now (for the self-test). */
    static boolean installed() {
        return ForgeWorldEdit.inst != null && ForgeWorldEdit.inst.getPermissionsProvider() instanceof WorldEditRanks;
    }

    @Override
    public boolean hasPermission(EntityPlayerMP player, String node) {
        if (old != null && old.hasPermission(player, node)) return true;
        return Ranks.isActive() && PermissionAPI.hasPermission(player, node);
    }

    @Override
    public void registerPermission(ICommand command, String node) {
        if (old != null) old.registerPermission(command, node);
        try {
            PermissionAPI.registerNode(node, DefaultPermissionLevel.OP,
                command == null ? "WorldEdit" : "WorldEdit: /" + command.getCommandName()); // some come without a command
            registered++;
        } catch (Throwable t) { // a node ServerUtilities won't take still works through the rank lookup
            ValierTweaks.LOG.warn("WorldEdit by rank: couldn't register {} with ServerUtilities ({})", node, t.toString());
        }
    }
}
