package com.valier.claims;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

/**
 * {@code /valierclaims status}: what each team's claims block. {@code /valierclaims reapply <team|all>}: give teams the
 * default spawn settings again, replacing what they chose. {@code /valierclaims spawns [count]}: the latest hostile
 * mobs that turned up in such claims anyway, and where they came from.
 */
public class ClaimsCommand extends CommandBase {

    private static final String USAGE = "/valierclaims <status|reapply <team|all>|spawns [count]>";

    @Override
    public String getCommandName() {
        return "valierclaims";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(USAGE);
        if (!Universe.loaded()) {
            say(sender, "ServerUtilities hasn't loaded its teams yet.");
            return;
        }
        switch (args[0]) {
            case "status": {
                List<ForgeTeam> teams = savedTeams();
                say(sender, teams.size() + " team(s). New teams' claims block: " + describeDefaults());
                for (ForgeTeam team : teams) {
                    ServerUtilitiesTeamData data = ServerUtilitiesTeamData.get(team);
                    ClaimDefaults.Marker marker = ClaimDefaults.marker(team);
                    int chunks = ClaimedChunks.isActive() ? ClaimedChunks.instance.getTeamChunks(team, OptionalInt.empty())
                        .size() : 0;
                    say(
                        sender,
                        "  " + team.getId()
                            + " ("
                            + team.type.getName()
                            + ", "
                            + chunks
                            + " claimed chunks): blocks "
                            + ClaimDefaults.describe(data)
                            + (marker != null && marker.version() > 0 ? "" : " [defaults not given yet]"));
                }
                return;
            }
            case "reapply": {
                if (args.length < 2) throw new WrongUsageException(USAGE);
                List<ForgeTeam> teams = new ArrayList<>();
                if ("all".equals(args[1])) teams.addAll(savedTeams());
                else {
                    ForgeTeam team = Universe.get()
                        .getTeam(args[1]);
                    if (team == null || !team.type.save) {
                        say(sender, "No team '" + args[1] + "'. /valierclaims status lists them.");
                        return;
                    }
                    teams.add(team);
                }
                for (ForgeTeam team : teams) say(
                    sender,
                    team.getId() + ": "
                        + (ClaimDefaults.apply(team)
                            ? "claims now block " + ClaimDefaults.describe(ServerUtilitiesTeamData.get(team))
                            : "couldn't change it, see the log"));
                return;
            }
            case "spawns": {
                if (!ClaimsConfig.logHostileMobs) {
                    say(sender, "Logging is off (logHostileMobs in config/valierclaims.cfg).");
                    return;
                }
                int count = args.length >= 2 ? parseIntBounded(sender, args[1], 1, 50) : 10;
                List<String> lines = SpawnLog.recent();
                say(
                    sender,
                    lines.isEmpty() ? "No hostile mobs have turned up in blocking claims since the server started."
                        : "Hostile mobs that turned up in blocking claims (latest " + Math.min(count, lines.size())
                            + " of "
                            + lines.size()
                            + "; all of them are in "
                            + SpawnLog.FILE.getPath()
                            + "):");
                for (String line : lines.subList(Math.max(0, lines.size() - count), lines.size())) say(sender, "  " + line);
                return;
            }
            case "selftest":
                if (SelfTest.ENABLED && args.length >= 2) {
                    SelfTest.run(sender, args[1]);
                    return;
                }
                // fall through
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "reapply", "spawns");
        if (args.length == 2 && "reapply".equals(args[0]) && Universe.loaded()) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            for (ForgeTeam team : savedTeams()) ids.add(team.getId());
            return getListOfStringsFromIterableMatchingLastWord(args, ids);
        }
        return null;
    }

    private static List<ForgeTeam> savedTeams() {
        List<ForgeTeam> teams = new ArrayList<>();
        for (ForgeTeam team : Universe.get()
            .getTeams()) if (team.type.save) teams.add(team);
        return teams;
    }

    private static String describeDefaults() {
        if (!ClaimsConfig.enabled) return "whatever ServerUtilities defaults to (this mod is off)";
        return ClaimDefaults.describe(ClaimsConfig.defaultBlocked);
    }

    static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
