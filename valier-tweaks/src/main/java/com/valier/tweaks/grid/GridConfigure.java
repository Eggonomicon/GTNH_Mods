package com.valier.tweaks.grid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.valier.tweaks.LootSpawners;

/**
 * The Valier Configurator on Grid blocks: sneak-right-click copies a block's settings (network, priority, Surge, limit,
 * redstone, a point's voltage); right-click pastes them onto another Grid block, which joins that network.
 */
public final class GridConfigure {

    static final String TAG = "ValierGridSettings";

    private GridConfigure() {}

    public static void use(ItemStack stack, EntityPlayer player, TileGrid t, boolean copy) {
        if (copy) {
            NBTTagCompound s = new NBTTagCompound();
            t.writeSettings(s);
            s.removeTag("Owner");
            s.removeTag("Scope"); // a network name only: pasted, it is the name in the pasted block's own team
            if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
            stack.stackTagCompound.setTag(TAG, s);
            player.inventoryContainer.detectAndSendChanges();
            tell(player, EnumChatFormatting.GREEN, "Copied: " + summary(t) + ". Right-click another Grid block to paste.");
            return;
        }
        NBTTagCompound s = stack.stackTagCompound == null ? null : stack.stackTagCompound.getCompoundTag(TAG);
        if (s == null || s.hasNoTags()) {
            tell(player, EnumChatFormatting.GRAY, "Nothing copied yet: sneak-right-click a Grid block to copy it.");
            return;
        }
        if (!LootSpawners.mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, EnumChatFormatting.RED, "You can't change this block: it's protected.");
            return;
        }
        t.readSettings(s, false);
        GridNets.link(t, t.ownerScope(player.getUniqueID()), t.net);
        if (t.network != null) t.network.resort();
        tell(player, EnumChatFormatting.GREEN, "Pasted: " + summary(t) + ".");
    }

    static String summary(TileGrid t) {
        return (t.net.isEmpty() ? "no network" : "network \"" + t.net + "\"") + ", priority " + t.priority + (t.surge ? ", Surge" : "")
            + (t.limit > 0 ? String.format(", limit %,d EU/t", t.limit) : "");
    }

    static void tell(EntityPlayer p, EnumChatFormatting colour, String text) {
        ChatComponentText c = new ChatComponentText(text);
        c.getChatStyle()
            .setColor(colour);
        p.addChatMessage(c);
    }
}
