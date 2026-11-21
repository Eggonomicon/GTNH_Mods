package com.valier.tweaks.pipe;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Clicks on a pipe (server side, with the arm the player looks at):
 * <ul>
 * <li>sneak + wrench on an arm: shut that side, or open it again; on the centre: pick the pipe up ({@link Events}: a
 * click event, because Forge doesn't hand every wrench's sneak-click to the block);</li>
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
            if (side == TilePipe.SOURCE) pickUp(p, player, held);
            else toggle(p, side, player, held);
            return true;
        }
        if (side == TilePipe.SOURCE) { // a creative pipe's centre: its source menu
            if (player.isSneaking() || !may(p, player)) return !player.isSneaking();
            PipeGui.open(player, p, side);
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

    /** Sneak + wrench on an arm: that side shut, or open again. */
    static void toggle(TilePipe p, int side, EntityPlayer player, ItemStack wrench) {
        if (!may(p, player)) return;
        p.toggleSide(side);
        boolean shut = (p.blocked >> side & 1) != 0;
        tell(player, EnumChatFormatting.GRAY, "Pipe side " + words(side) + (shut ? " shut." : " open again."));
        LootSpawners.wrenchUsed(wrench, player, p.xCoord, p.yCoord, p.zCoord);
    }

    /** Sneak + wrench on the centre: the pipe goes into the player's inventory, with anything its faces held back. */
    public static void pickUp(TilePipe p, EntityPlayer player, ItemStack wrench) {
        if (!may(p, player)) return;
        World w = p.getWorldObj();
        int x = p.xCoord, y = p.yCoord, z = p.zCoord;
        Block block = w.getBlock(x, y, z);
        int tier = p.tier();
        int held = 0;
        for (FaceConfig f : p.faces) {
            if (f == null) continue;
            for (ItemStack s : f.heldItems) {
                LootSpawners.give(player, s);
                held++;
            }
            f.heldItems.clear();
            f.heldFluid = null;
        }
        w.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (tier << 12)); // the sound and bits of a break
        w.setBlockToAir(x, y, z); // it holds nothing now, so nothing else drops
        LootSpawners.give(player, new ItemStack(block, 1, tier));
        LootSpawners.wrenchUsed(wrench, player, x, y, z);
        tell(player, EnumChatFormatting.GRAY, "Picked up the " + PipeTier.NAMES[tier] + " " + p.kind().word + " pipe"
            + (held > 0 ? ", and what it was holding back." : "."));
    }

    /** Sneak + wrench on a pipe, wherever the wrench's own mod sends the click. */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onClick(PlayerInteractEvent event) {
            if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
            EntityPlayer player = event.entityPlayer;
            World world = event.world;
            if (world.isRemote || player == null || !player.isSneaking()) return;
            TileEntity te = world.getTileEntity(event.x, event.y, event.z);
            if (!(te instanceof TilePipe)) return;
            ItemStack held = player.getHeldItem();
            if (!LootSpawners.isWrench(held, player, event.x, event.y, event.z)) return;
            event.setCanceled(true);
            TilePipe p = (TilePipe) te;
            int arm = BlockPipe.armLookedAt(world, event.x, event.y, event.z, player);
            if (arm == 6) pickUp(p, player, held);
            else toggle(p, arm >= 0 && arm <= 5 ? arm : event.face, player, held);
        }
    }

    public static boolean may(TilePipe p, EntityPlayer player) {
        if (p.creative() && !creativeAllowed(player)) {
            tell(player, EnumChatFormatting.RED, "Only ops and players in creative mode can change a creative pipe.");
            return false;
        }
        if (LootSpawners.mayChange(player, p.getWorldObj(), p.xCoord, p.yCoord, p.zCoord)) return true;
        tell(player, EnumChatFormatting.RED, "You can't change this pipe: it's protected.");
        return false;
    }

    /** Creative pipes (endless items) are for ops and players in creative mode only. */
    public static boolean creativeAllowed(EntityPlayer player) {
        if (player == null) return false;
        if (player.capabilities.isCreativeMode) return true;
        return player instanceof net.minecraft.entity.player.EntityPlayerMP && net.minecraft.server.MinecraftServer.getServer() != null
            && net.minecraft.server.MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152596_g(((net.minecraft.entity.player.EntityPlayerMP) player).getGameProfile());
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
