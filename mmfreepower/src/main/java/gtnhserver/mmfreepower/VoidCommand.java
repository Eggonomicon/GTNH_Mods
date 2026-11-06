package gtnhserver.mmfreepower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

/**
 * {@code /mmvoid}: a player's void list for the Valier MM Mk IV (see {@link MkIVVoid}). Anyone can manage their own
 * list; {@code purge}, which deletes matching dropped items from the ground, is for ops and the console.
 */
public class VoidCommand extends CommandBase {

    private static final String USAGE = "/mmvoid [cobble | add [item] | remove <item|number> | clear | on | off | purge [item...]]";

    @Override
    public String getCommandName() {
        return "mmvoid";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** Every player manages their own list; purge checks for an op itself. */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(java.util.Locale.ROOT);
        if ("purge".equals(sub)) {
            purge(sender, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (!(sender instanceof EntityPlayer)) throw new WrongUsageException("Only players have a void list. From the console: /mmvoid purge <item...>");
        EntityPlayer player = (EntityPlayer) sender;
        switch (sub) {
            case "list":
                list(player);
                return;
            case "cobble":
                say(
                    player,
                    MkIVVoid.toggle(player, MkIVVoid.COBBLE) ? "Cobblestone is voided now (every kind)."
                        : "Cobblestone isn't voided any more.");
                return;
            case "add": {
                String entry = args.length >= 2 ? MkIVVoid.parse(args[1]) : MkIVVoid.entryFor(player.getHeldItem());
                if (entry == null) throw new WrongUsageException(
                    args.length >= 2 ? "Not an item: " + args[1] + " (use modid:name, modid:name:meta or ore:name)"
                        : "Hold the item to void, or name it: /mmvoid add <modid:name[:meta] | ore:name>");
                say(player, MkIVVoid.add(player, entry) ? "Voiding " + MkIVVoid.describe(entry) : entry + " is already on your list.");
                return;
            }
            case "remove": {
                if (args.length < 2) throw new WrongUsageException("/mmvoid remove <item | number from /mmvoid>");
                String entry = args[1];
                List<String> entries = MkIVVoid.entries(player);
                try {
                    int n = Integer.parseInt(entry);
                    if (n >= 1 && n <= entries.size()) entry = entries.get(n - 1);
                } catch (NumberFormatException ignored) {}
                say(player, MkIVVoid.remove(player, entry) ? "No longer voiding " + entry : entry + " isn't on your list.");
                return;
            }
            case "clear":
                MkIVVoid.clear(player);
                say(player, "Your void list is empty.");
                return;
            case "on":
            case "off":
                MkIVVoid.setOn(player, "on".equals(sub));
                say(player, "on".equals(sub) ? "Voiding is on." : "Voiding is paused (your list is kept).");
                return;
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private static void list(EntityPlayer player) {
        List<String> entries = MkIVVoid.entries(player);
        if (entries.isEmpty()) {
            say(player, "Your Valier MM Mk IV voids nothing. /mmvoid cobble toggles cobblestone; /mmvoid add voids the item you hold.");
            return;
        }
        say(player, "Your Valier MM Mk IV deletes these instead of dropping them" + (MkIVVoid.isOn(player) ? ":" : " (paused, /mmvoid on):"));
        for (int i = 0; i < entries.size(); i++) say(player, " " + (i + 1) + ". " + MkIVVoid.describe(entries.get(i)));
    }

    private static void purge(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(2, "mmvoid")) throw new WrongUsageException("Only ops can purge dropped items.");
        List<String> entries = new ArrayList<>();
        for (String arg : args) {
            String entry = MkIVVoid.parse(arg);
            if (entry == null) throw new WrongUsageException("Not an item: " + arg + " (use modid:name, modid:name:meta or ore:name)");
            entries.add(entry);
        }
        if (entries.isEmpty() && sender instanceof EntityPlayer) entries = MkIVVoid.entries((EntityPlayer) sender);
        if (entries.isEmpty()) throw new WrongUsageException("/mmvoid purge <item...>, e.g. /mmvoid purge " + MkIVVoid.COBBLE);
        long[] removed = MkIVVoid.purge(entries);
        String text = "Deleted " + removed[0] + " dropped item stack(s), " + removed[1] + " items, matching " + entries
            + " (loaded chunks only).";
        say(sender, text);
        MMFreePower.LOG.info(sender.getCommandSenderName() + ": " + text);
    }

    private static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "cobble", "add", "remove", "clear", "on", "off", "purge");
        return null;
    }
}
