package com.valier.tweaks.pipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierTweaks;

/**
 * Clicks on a pipe (server side, with the arm the player looks at):
 * <ul>
 * <li>sneak + wrench: shut that side, or open it again;</li>
 * <li>right-click an arm touching a block: its menu ({@code PipeGui});</li>
 * <li>sneak + empty hand on such an arm: the next mode (Provide, Insert, Extract, Request, Off) and what it does.</li>
 * </ul>
 * Claims: whoever may not break the pipe can't change it.
 */
public final class PipeClicks {

    private PipeClicks() {}

    public static boolean activated(TilePipe p, int side, EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        boolean wrench = LootSpawners.isWrench(held, player, p.xCoord, p.yCoord, p.zCoord);
        if (wrench && player.isSneaking()) {
            if (!may(p, player)) return true;
            p.toggleSide(side);
            boolean shut = (p.blocked >> side & 1) != 0;
            tell(player, EnumChatFormatting.GRAY, "Pipe side " + words(side) + (shut ? " shut." : " open again."));
            LootSpawners.wrenchUsed(held, player, p.xCoord, p.yCoord, p.zCoord);
            return true;
        }
        if (!p.blockOn(side)) return false;
        if (player.isSneaking() && held == null) {
            if (!may(p, player)) return true;
            FaceConfig f = p.face(side);
            f.mode = f.mode.next(1);
            p.faceChanged();
            tell(player, EnumChatFormatting.GRAY, "This face: " + f.mode.words + ". " + f.mode.explain);
            return true;
        }
        if (player.isSneaking()) return false; // placing a block against the pipe, and the like
        if (!may(p, player)) return true;
        PipeGui.open(player, p, side);
        return true;
    }

    static boolean may(TilePipe p, EntityPlayer player) {
        if (LootSpawners.mayChange(player, p.getWorldObj(), p.xCoord, p.yCoord, p.zCoord)) return true;
        tell(player, EnumChatFormatting.RED, "You can't change this pipe: it's protected.");
        return false;
    }

    static String words(int side) {
        return ForgeDirection.getOrientation(side)
            .name()
            .toLowerCase();
    }

    static void tell(EntityPlayer player, EnumChatFormatting colour, String text) {
        ChatComponentText c = new ChatComponentText(text);
        c.getChatStyle()
            .setColor(colour);
        player.addChatMessage(c);
        ValierTweaks.LOG.debug("pipe: told {}: {}", player.getCommandSenderName(), text);
    }
}
