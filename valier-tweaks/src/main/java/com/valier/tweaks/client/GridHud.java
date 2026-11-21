package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.valier.tweaks.grid.GridWaila;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA lines for Valier Grid blocks: the network, what this block moved last second, a cell's energy, and the
 * network's in and out. WAILA option "Valier Grid" turns it off. Uses no client-only classes (WAILA runs it on both
 * sides).
 */
public final class GridHud implements IWailaDataProvider {

    static final String OPTION = "valiertweaks.grid";

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.addConfig("Valier", OPTION, "option.valiertweaks.grid", true);
        registrar.registerBodyProvider(new GridHud(), com.valier.tweaks.grid.TileGrid.class);
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (tip == null || accessor == null || (config != null && !config.getConfig(OPTION, true))) return tip;
        NBTTagCompound nbt = accessor.getNBTData();
        if (nbt == null || !nbt.hasKey(GridWaila.TAG, 10)) return tip;
        tip.addAll(lines(nbt.getCompoundTag(GridWaila.TAG)));
        return tip;
    }

    public static List<String> lines(NBTTagCompound s) {
        List<String> out = new ArrayList<>();
        String grey = EnumChatFormatting.GRAY.toString();
        String net = s.getString("Net");
        if (net.isEmpty()) {
            out.add(EnumChatFormatting.RED + "Not on a network");
            return out;
        }
        out.add(grey + "Network: " + EnumChatFormatting.LIGHT_PURPLE + net + (s.hasKey("Colour") ? grey + " (" + s.getString("Colour") + ")"
            : ""));
        out.add(grey + String.format("This block: %,d EU/s", s.getLong("Moved")));
        if (s.hasKey("Capacity")) out.add(grey + String.format("Stored: %,d / %,d EU", s.getLong("Stored"), s.getLong("Capacity")));
        out.add(grey + String.format("Network: in %,d, out %,d EU/s", s.getLong("In"), s.getLong("Out")));
        if (s.hasKey("Lost")) out.add(grey + String.format("Lost on the way: %,d EU/s", s.getLong("Lost")));
        return out;
    }

    @Override
    public NBTTagCompound getNBTData(net.minecraft.entity.player.EntityPlayerMP player, net.minecraft.tileentity.TileEntity te,
        NBTTagCompound tag, net.minecraft.world.World world, int x, int y, int z) {
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
