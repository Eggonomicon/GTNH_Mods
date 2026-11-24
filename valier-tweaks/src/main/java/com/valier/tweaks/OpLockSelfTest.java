package com.valier.tweaks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.server.CommandOp;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.CommandEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;

/**
 * {@code valiertweaks selftest oplock} (2.3.4), all at once:
 * <ul>
 * <li>the op lock: a player who may use {@code /op} (made op for the test) is refused through the real command handler,
 * nobody gets opped, they are told why, and their other commands still run; RCON is refused; the console is not; with
 * the lock off nothing is refused;</li>
 * <li>opQuiet: the console's /op and /deop work and an op online hears nothing; with it off, vanilla's notice does reach
 * them;</li>
 * <li>WorldEdit by rank: WorldEdit asks ranks; a player in a rank with {@code worldedit: true} and
 * {@code worldedit.reload: false} may use //set and the wand but not reload, a player without it may use nothing, also
 * through WorldEdit's own player check; WorldEdit's command nodes (//copy's, for one) are registered with ServerUtilities.</li>
 * </ul>
 * The test rank, its player entries and the test op are removed again.
 */
final class OpLockSelfTest {

    private OpLockSelfTest() {}

    static void run() {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer world = DimensionManager.getWorld(0);
        ServerConfigurationManager players = server.getConfigurationManager();
        boolean was = TweaksConfig.opLock, wasQuiet = TweaksConfig.opQuiet;
        TweaksConfig.opLock = true;
        TweaksConfig.opQuiet = false; // the console's /op below must stay a bare event, not a real op
        GameProfile gp = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000d4"), "OpLockTester");
        List<String> heard = new ArrayList<>();
        FakePlayer p = new FakePlayer(world, gp) {

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return true; // as an op (or a rank with command.op) would
            }

            @Override
            public void addChatMessage(IChatComponent message) {
                heard.add(message.getUnformattedText());
            }
        };
        try {
            players.func_152605_a(gp); // addOp: op it, so nothing but the lock stands in the way
            int before = OpLock.refused;
            server.getCommandManager()
                .executeCommand(p, "op OpLockTarget");
            boolean opped = Arrays.asList(players.func_152606_n()) // getOppedPlayerNames
                .contains("OpLockTarget");
            boolean told = heard.stream()
                .anyMatch(s -> s.contains("only the server console"));
            SelfTest.check(OpLock.refused == before + 1 && !opped && told,
                "op lock: an op player's /op is refused through the real command handler, nobody is opped, and they're told why ("
                    + (OpLock.refused - before) + " refused, heard " + heard + ")");
            int r = server.getCommandManager()
                .executeCommand(p, "list");
            SelfTest.check(r > 0 && OpLock.refused == before + 1, "op lock: the same player's other commands still run (/list gave "
                + r + ")");
            CommandEvent rcon = new CommandEvent(new CommandOp(), RConConsoleSource.instance, new String[] { "OpLockTarget" });
            CommandEvent console = new CommandEvent(new CommandOp(), server, new String[] { "OpLockTarget" });
            boolean rconStopped = MinecraftForge.EVENT_BUS.post(rcon), consoleStopped = MinecraftForge.EVENT_BUS.post(console);
            SelfTest.check(rconStopped && !consoleStopped, "op lock: /op over RCON is refused, from the console it goes through ("
                + rconStopped + ", " + consoleStopped + ")");
            TweaksConfig.opLock = false;
            CommandEvent off = new CommandEvent(new CommandOp(), p, new String[] { "OpLockTarget" });
            boolean offStopped = MinecraftForge.EVENT_BUS.post(off);
            SelfTest.check(!offStopped, "op lock off (the default): a player's /op isn't stopped by it");
        } finally {
            players.func_152610_b(gp); // removeOp: de-op the tester
            TweaksConfig.opLock = was;
        }
        quiet(server, world, players, gp);
        TweaksConfig.opQuiet = wasQuiet;
        if (Loader.isModLoaded("worldedit") && Loader.isModLoaded("serverutilities")) WorldEditCheck.run(world);
        else SelfTest.check(false, "WorldEdit and ServerUtilities are loaded on the test server (for WorldEdit by rank)");
    }

    /**
     * opQuiet: an op "online" (a watcher put in the player list for the moment) hears nothing of the console's /op and
     * /deop, which still work; with opQuiet off, vanilla's "[Server: Opped X]" does reach the watcher (so the test can
     * see a notice at all). The tester's name is put in the profile cache, so no name lookup goes to Mojang.
     */
    static void quiet(MinecraftServer server, WorldServer world, ServerConfigurationManager players, GameProfile gp) {
        GameProfile wp = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000d5"), "OpLockWatcher");
        List<String> watcherHeard = new ArrayList<>();
        FakePlayer watcher = new FakePlayer(world, wp) {

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return true;
            }

            @Override
            public void addChatMessage(IChatComponent message) {
                watcherHeard.add(message.getUnformattedText());
            }
        };
        List<net.minecraft.entity.player.EntityPlayerMP> online = players.playerEntityList;
        try {
            server.func_152358_ax() // getPlayerProfileCache
                .func_152649_a(gp); // addEntry: "OpLockTester" is known without asking Mojang
            players.func_152605_a(wp); // addOp: the watcher is an op, who vanilla tells about ops
            online.add(watcher);
            TweaksConfig.opQuiet = true;
            server.getCommandManager()
                .executeCommand(server, "op OpLockTester");
            boolean opped = players.func_152596_g(gp); // canSendCommands: is op
            server.getCommandManager()
                .executeCommand(server, "deop OpLockTester");
            boolean deopped = !players.func_152596_g(gp);
            SelfTest.check(opped && deopped && watcherHeard.isEmpty(),
                "opQuiet: the console's /op and /deop work, and an op online hears nothing of them (" + opped + ", " + deopped
                    + ", heard " + watcherHeard + ")");
            TweaksConfig.opQuiet = false;
            server.getCommandManager()
                .executeCommand(server, "op OpLockTester");
            boolean vanillaTold = watcherHeard.stream()
                .anyMatch(s -> s.contains("OpLockTester"));
            server.getCommandManager()
                .executeCommand(server, "deop OpLockTester");
            SelfTest.check(vanillaTold, "opQuiet off: vanilla's notice of the console's /op reaches an op online (heard "
                + watcherHeard + ")");
        } finally {
            online.remove(watcher);
            players.func_152610_b(wp); // removeOp
            players.func_152610_b(gp);
        }
    }

    /** Names WorldEdit's and ServerUtilities' classes, so it's only loaded when both are there. */
    static final class WorldEditCheck {

        static void run(WorldServer world) {
            SelfTest.check(WorldEditRanks.installed(), "WorldEdit asks ServerUtilities ranks (ours is in front of its own check)");
            serverutils.ranks.Ranks ranks = serverutils.ranks.Ranks.INSTANCE;
            if (!serverutils.ranks.Ranks.isActive() || ranks == null) {
                SelfTest.check(false, "ServerUtilities ranks are on (for WorldEdit by rank)");
                return;
            }
            GameProfile eg = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000e5"), "EditorTester");
            GameProfile pg = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000e6"), "PlainTester");
            FakePlayer editor = new FakePlayer(world, eg), plain = new FakePlayer(world, pg);
            serverutils.ranks.Rank rank = new serverutils.ranks.Rank(ranks, "valiertesteditor");
            try {
                rank.add();
                rank.setPermission("worldedit", true);
                rank.setPermission("worldedit.reload", false);
                ranks.getPlayerRank(eg)
                    .addParent(rank);
                ranks.clearCache();
                com.sk89q.worldedit.forge.ForgePermissionsProvider we = com.sk89q.worldedit.forge.ForgeWorldEdit.inst
                    .getPermissionsProvider();
                boolean set = we.hasPermission(editor, "worldedit.region.set"), wand = we.hasPermission(editor, "worldedit.selection.pos");
                boolean reload = we.hasPermission(editor, "worldedit.reload"), plainSet = we.hasPermission(plain, "worldedit.region.set");
                SelfTest.check(set && wand && !reload && !plainSet,
                    "WorldEdit by rank: worldedit: true gives //set and the wand, worldedit.reload: false takes reload away, a player "
                        + "without the rank gets nothing (" + set + " " + wand + " " + reload + " " + plainSet + ")");
                boolean own = com.sk89q.worldedit.forge.ForgeWorldEdit.inst.wrap(editor)
                    .hasPermission("worldedit.clipboard.copy")
                    && !com.sk89q.worldedit.forge.ForgeWorldEdit.inst.wrap(plain)
                        .hasPermission("worldedit.clipboard.copy");
                SelfTest.check(own, "WorldEdit's own player check agrees (//copy for the editor, not for the other player)");
                boolean listed = serverutils.lib.util.permission.PermissionAPI.getPermissionHandler()
                    .getRegisteredNodes()
                    .contains("worldedit.clipboard.copy"); // //set is made apart in this WorldEdit and isn't registered
                SelfTest.check(listed && WorldEditRanks.registered > 20, "WorldEdit's command nodes are registered with "
                    + "ServerUtilities (worldedit.clipboard.copy; " + WorldEditRanks.registered + " nodes)");
            } finally {
                ranks.getPlayerRank(eg)
                    .remove();
                ranks.getPlayerRank(pg)
                    .remove();
                rank.remove();
                ranks.clearCache();
            }
        }
    }
}
