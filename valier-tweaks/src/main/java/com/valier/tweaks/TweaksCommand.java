package com.valier.tweaks;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

/**
 * {@code /valiertweaks pipes} (how the pipes are doing; {@code pipes reset} starts the numbers again) and, only on a
 * test server started with the self-test flag, {@code /valiertweaks selftest <step>}.
 */
public class TweaksCommand extends CommandBase {

    private static final String USAGE = "/valiertweaks pipes [reset] | revertblocks"
        + (SelfTest.ENABLED ? " | selftest trees|peaceful|boilers|lootspawner|xpcrafter|railboiler|mod|pipes|pipeperf" : "");

    @Override
    public String getCommandName() {
        return "valiertweaks";
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
        if (args.length >= 1 && "revertblocks".equals(args[0])) {
            if (args.length < 2 || !"confirm".equals(args[1])) {
                sender.addChatMessage(new net.minecraft.util.ChatComponentText("Before removing Valier Tweaks: turns loot "
                    + "spawner and XP crafter blocks back into vanilla spawners and GregTech tanks. Type /valiertweaks "
                    + "revertblocks confirm to do it."));
                return;
            }
            for (String line : RevertBlocks.run()) sender.addChatMessage(new net.minecraft.util.ChatComponentText(line));
            return;
        }
        if (args.length >= 1 && "pipes".equals(args[0])) {
            if (args.length >= 2 && "reset".equals(args[1])) com.valier.tweaks.pipe.PipeNetworks.resetStats();
            for (String line : com.valier.tweaks.pipe.PipeNetworks.stats())
                sender.addChatMessage(new net.minecraft.util.ChatComponentText(line));
            return;
        }
        if (!SelfTest.ENABLED || args.length < 2 || !"selftest".equals(args[0])) throw new WrongUsageException(USAGE);
        SelfTest.run(sender, args[1]);
    }
}
