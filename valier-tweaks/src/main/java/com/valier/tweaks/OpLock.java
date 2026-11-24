package com.valier.tweaks;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.command.server.CommandDeOp;
import net.minecraft.command.server.CommandOp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.CommandEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Who may op, and who hears about it (2.3.4, config section {@code permissions}):
 * <ul>
 * <li>{@code opLock}: only the server console can op anyone. {@code /op} from a player is refused and logged, whatever
 * their op level or rank (ServerUtilities can give a rank {@code command.op}). So is {@code /op} from a command block or
 * RCON. Every other command works as before, and {@code /deop} is left alone.</li>
 * <li>{@code opQuiet}: the console's {@code /op} and {@code /deop} tell nobody in game. Vanilla shows every op online,
 * the newly opped player too, a grey "[Server: Opped X]" line; this does the same op or de-op and only the console
 * hears it.</li>
 * </ul>
 * Forge only posts the command event once the sender is allowed the command, so this is the last word: nothing that
 * gets past the usual permission checks gets past this.
 */
public final class OpLock { // public: Forge calls its handler from another class loader

    /** How many {@code /op} commands it refused since the server started (the self-test reads it). */
    static int refused;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        boolean op = isOp(event.command);
        if (!op && !isDeop(event.command)) return;
        if (isConsole(event.sender)) {
            if (TweaksConfig.opQuiet) {
                event.setCanceled(true);
                quietly(event, op);
            }
            return;
        }
        if (!op || !TweaksConfig.opLock) return;
        event.setCanceled(true);
        refused++;
        ChatComponentText m = new ChatComponentText("Op is locked on this server: only the server console can op players.");
        m.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        event.sender.addChatMessage(m);
        ValierTweaks.LOG.warn("Op lock: refused /op {} from {}", String.join(" ", event.parameters),
            event.sender.getCommandSenderName());
    }

    /**
     * What vanilla's {@code /op} and {@code /deop} do, without their notice to the ops online: only the sender (the
     * console) is told. Errors go back to the command handler, which shows them to the sender as usual.
     */
    static void quietly(CommandEvent e, boolean op) {
        if (e.parameters.length != 1 || e.parameters[0].isEmpty()) {
            e.exception = new WrongUsageException(op ? "commands.op.usage" : "commands.deop.usage");
            return;
        }
        String name = e.parameters[0];
        MinecraftServer server = MinecraftServer.getServer();
        ServerConfigurationManager players = server.getConfigurationManager();
        GameProfile gp = op ? server.func_152358_ax() // getPlayerProfileCache
            .func_152655_a(name) // getGameProfileForUsername
            : players.func_152603_m() // getOppedPlayers
                .func_152700_a(name); // getGameProfileFromName
        if (gp == null) {
            e.exception = new CommandException(op ? "commands.op.failed" : "commands.deop.failed", name);
            return;
        }
        if (op) players.func_152605_a(gp); // addOp
        else players.func_152610_b(gp); // removeOp
        if (Loader.isModLoaded("serverutilities")) Ranks.forget(); // its default op rank follows the op list
        e.sender.addChatMessage(new ChatComponentTranslation(op ? "commands.op.success" : "commands.deop.success", name));
    }

    static boolean isOp(ICommand c) {
        return c instanceof CommandOp || c != null && "op".equalsIgnoreCase(c.getCommandName());
    }

    static boolean isDeop(ICommand c) {
        return c instanceof CommandDeOp || c != null && "deop".equalsIgnoreCase(c.getCommandName());
    }

    /** The server's own console (a dedicated server's window or terminal). Not RCON, a command block or a player. */
    static boolean isConsole(ICommandSender s) {
        return s instanceof MinecraftServer;
    }

    /** Names ServerUtilities' classes, so it's only loaded when ServerUtilities is there. */
    static final class Ranks {

        static void forget() {
            if (serverutils.ranks.Ranks.isActive() && serverutils.ranks.Ranks.INSTANCE != null) serverutils.ranks.Ranks.INSTANCE.clearCache();
        }
    }
}
