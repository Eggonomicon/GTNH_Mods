package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA lines for a Valier barrel, as JABBA's: what it holds ("640 of 32,768 Iron Ingot"), its tier and size, its
 * channel, lock and void. Read from what the barrel's label already shows on the player's computer (kept up to date by
 * the server, at most every 10 ticks), so it asks the server for nothing. WAILA option "Valier barrels" turns it off.
 * Uses no client-only classes (WAILA registers it on both sides). Only touched through WAILA's IMC.
 */
public final class BarrelWaila implements IWailaDataProvider {

    static final String OPTION = "valiertweaks.barrel";

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.addConfig("Valier", OPTION, "option.valiertweaks.barrel", true);
        registrar.registerBodyProvider(new BarrelWaila(), TileBarrel.class);
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (tip == null || accessor == null || (config != null && !config.getConfig(OPTION, true))) return tip;
        TileEntity te = accessor.getTileEntity();
        if (te instanceof TileBarrel) tip.addAll(lines((TileBarrel) te));
        return tip;
    }

    public static List<String> lines(TileBarrel t) {
        List<String> out = new ArrayList<>();
        String grey = EnumChatFormatting.GRAY.toString(), white = EnumChatFormatting.WHITE.toString();
        ItemStack item = t.shownItem;
        if (t.tier == BarrelTier.CREATIVE) {
            out.add(item == null ? grey + "Empty: right-click it with an item" : white + "Endless " + EnumChatFormatting.AQUA
                + item.getDisplayName());
            out.add(grey + "Tier: " + EnumChatFormatting.LIGHT_PURPLE + "Creative" + grey + (t.shownVoid ? " (destroys its item put in)"
                : " (takes nothing in)"));
            return out;
        }
        if (item == null) out.add(grey + "Empty");
        else out.add(white + String.format("%,d", t.shownCount) + grey + " of " + white + String.format("%,d", t.shownCapacity) + " "
            + EnumChatFormatting.AQUA + item.getDisplayName());
        int per = item == null ? 64 : Math.max(1, item.getMaxStackSize());
        long stacks = t.shownCapacity > 0 ? t.shownCapacity / per : BarrelTier.stacks(t.tier);
        String size = per > 1 && item != null && t.shownCount >= per
            ? stacks(t.shownCount / per) + " + " + t.shownCount % per + " of " + stacks(stacks)
            : stacks(stacks);
        out.add(grey + "Tier: " + white + BarrelTier.NAMES[BarrelTier.clamp(t.tier)] + grey + " (" + size + ")");
        if (!t.shownChannel.isEmpty()) out.add(grey + "Linked: " + EnumChatFormatting.LIGHT_PURPLE + t.shownChannel + grey + " ("
            + t.shownBarrels + (t.shownBarrels == 1 ? " barrel)" : " barrels)"));
        if (t.shownLocked || t.shownVoid) out.add(EnumChatFormatting.YELLOW + (t.shownLocked ? "Locked" : "")
            + (t.shownLocked && t.shownVoid ? ", " : "") + (t.shownVoid ? "voids overflow" : ""));
        return out;
    }

    static String stacks(long n) {
        return String.format("%,d %s", n, n == 1 ? "stack" : "stacks");
    }

    @Override
    public NBTTagCompound getNBTData(net.minecraft.entity.player.EntityPlayerMP player, TileEntity te, NBTTagCompound tag,
        net.minecraft.world.World world, int x, int y, int z) {
        return tag;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }
}
