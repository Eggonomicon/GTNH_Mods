package com.valier.tweaks;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

/**
 * {@code /valiertweaks} (or {@code /valier}, 2.3.3), for ops: {@code cost} (how much server time Valier's blocks take,
 * {@link ValierCost}), {@code pipes} (how the pipes are doing; {@code pipes reset} starts the numbers again),
 * {@code revertblocks}, and, only on a test server started with the self-test flag, {@code selftest <step>}.
 */
public class TweaksCommand extends CommandBase {

    private static final String USAGE = "/valier cost | pipes [reset] | revertblocks"
        + (SelfTest.ENABLED ? " | selftest trees|peaceful|boilers|lootspawner|xpcrafter|railboiler|mod|pipes|pipeperf|"
            + "barrels|barrelperf|satchels|grid|gridperf|cost|oplock" : "");

    @Override
    public String getCommandName() {
        return "valiertweaks";
    }

    @Override
    public java.util.List<String> getCommandAliases() {
        return java.util.Collections.singletonList("valier");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "cost", "pipes", "revertblocks") : null;
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
        if (args.length >= 1 && "cost".equals(args[0])) {
            java.util.List<String> lines = ValierCost.report();
            for (int i = 0; i < lines.size(); i++) {
                net.minecraft.util.ChatComponentText c = new net.minecraft.util.ChatComponentText(lines.get(i));
                if (i == 0) c.getChatStyle()
                    .setColor(net.minecraft.util.EnumChatFormatting.GOLD);
                else if (i == lines.size() - 1) c.getChatStyle()
                    .setColor(net.minecraft.util.EnumChatFormatting.YELLOW);
                sender.addChatMessage(c);
            }
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
