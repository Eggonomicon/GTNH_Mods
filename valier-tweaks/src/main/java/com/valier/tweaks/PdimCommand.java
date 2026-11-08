package com.valier.tweaks;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * {@code /pdim peaceful [on|off]} in a Personal Space dimension: shows or switches peaceful there. Anyone who could
 * break blocks where they stand may switch it (the same rule Personal Space uses for its portal settings, so a claim
 * keeps visitors out); ops always may. Ops and the console can also name a dimension ({@code /pdim peaceful on 183})
 * and list the peaceful ones ({@code /pdim list}).
 */
public class PdimCommand extends CommandBase {

    private static final String USAGE = "/pdim peaceful [on|off] [dimension]  |  /pdim list";

    @Override
    public String getCommandName() {
        return "pdim";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(USAGE);
        if ("list".equalsIgnoreCase(args[0])) {
            Set<Integer> dims = PeacefulDims.peacefulDims();
            say(sender, dims.isEmpty() ? "No personal dimension is peaceful." : "Peaceful personal dimensions: " + dims);
            return;
        }
        if (!"peaceful".equalsIgnoreCase(args[0]) || args.length > 3) throw new WrongUsageException(USAGE);
        if (!TweaksConfig.peacefulDimensions) {
            say(sender, "Peaceful personal dimensions are turned off in the server's config.");
            return;
        }
        boolean op = isOp(sender);
        World world;
        if (args.length == 3) {
            if (!op) {
                say(sender, "Only ops can name a dimension. Stand in your personal dimension and leave the number off.");
                return;
            }
            world = DimensionManager.getWorld(parseInt(sender, args[2]));
            if (world == null) {
                say(sender, "Dimension " + args[2] + " isn't loaded. Someone needs to be there, or it doesn't exist.");
                return;
            }
        } else if (sender instanceof EntityPlayer) {
            world = ((EntityPlayer) sender).worldObj;
        } else {
            throw new WrongUsageException(USAGE);
        }
        int dim = world.provider.dimensionId;
        if (!PeacefulDims.isPersonal(world)) {
            say(sender, "Peaceful can only be set in a personal dimension (made with a Personal Space portal).");
            return;
        }
        if (args.length == 1) {
            say(sender, "Peaceful is " + (PeacefulDims.isPeaceful(dim) ? "on" : "off") + " in this personal dimension (" + dim
                + "). Change it with /pdim peaceful on or /pdim peaceful off.");
            return;
        }
        boolean on;
        if ("on".equalsIgnoreCase(args[1])) on = true;
        else if ("off".equalsIgnoreCase(args[1])) on = false;
        else throw new WrongUsageException(USAGE);
        if (!op && !mayChange((EntityPlayer) sender)) {
            say(sender, "You can't change this personal dimension: it's claimed by someone else.");
            return;
        }
        boolean changed = PeacefulDims.setPeaceful(dim, on);
        int removed = on ? PeacefulDims.sweep(world) : 0;
        String what = on
            ? "Peaceful is on in personal dimension " + dim + ": hostile mobs won't spawn here"
                + (removed > 0 ? ", and " + removed + " already here were removed." : ".")
            : "Peaceful is off in personal dimension " + dim + ": hostile mobs can spawn here again.";
        say(sender, changed ? what : "Peaceful was already " + (on ? "on" : "off") + " in personal dimension " + dim + ".");
        if (changed) ValierTweaks.LOG.info("{} turned peaceful {} in dimension {}", sender.getCommandSenderName(), on ? "on" : "off", dim);
    }

    /**
     * Whether the player could break the block they stand on: asks with a break event, so claims (ServerUtilities)
     * and other protection decide, exactly as for Personal Space's own portal settings.
     */
    static boolean mayChange(EntityPlayer player) {
        World world = player.worldObj;
        int x = MathHelper.floor_double(player.posX), y = MathHelper.floor_double(player.posY) - 1,
            z = MathHelper.floor_double(player.posZ);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x, y, z, world, world.getBlock(x, y, z),
            world.getBlockMetadata(x, y, z), player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    static boolean isOp(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return sender.canCommandSenderUseCommand(2, "pdim");
        return MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152596_g(((EntityPlayerMP) sender).getGameProfile());
    }

    private static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "peaceful", "list");
        if (args.length == 2 && "peaceful".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "on", "off");
        return Collections.emptyList();
    }
}
