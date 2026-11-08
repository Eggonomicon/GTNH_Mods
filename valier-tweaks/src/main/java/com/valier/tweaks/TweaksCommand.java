package com.valier.tweaks;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

/** {@code /valiertweaks selftest <step>}: only registered on a test server started with the self-test flag. */
public class TweaksCommand extends CommandBase {

    private static final String USAGE = "/valiertweaks selftest trees|peaceful";

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
        if (args.length < 2 || !"selftest".equals(args[0])) throw new WrongUsageException(USAGE);
        SelfTest.run(sender, args[1]);
    }
}
