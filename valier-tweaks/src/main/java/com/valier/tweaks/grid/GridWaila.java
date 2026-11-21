package com.valier.tweaks.grid;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Adds a Grid block's state to the data WAILA sends a player looking at it ({@value #TAG}); {@code client.GridHud}
 * draws it. Only plain numbers the server tick keeps up to date are read here (WAILA asks from its own thread). Only
 * touched through WAILA's IMC, when WAILA is loaded.
 */
public final class GridWaila implements IWailaDataProvider {

    public static final String TAG = "ValierGrid";

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.registerNBTProvider(new GridWaila(), TileGrid.class);
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
        if (tag == null || !(te instanceof TileGrid)) return tag;
        TileGrid t = (TileGrid) te;
        NBTTagCompound s = new NBTTagCompound();
        s.setString("Net", t.net);
        s.setLong("Moved", t.movedLast);
        s.setInteger("Kind", t.kind());
        if (t instanceof TileGridCell) {
            s.setLong("Stored", ((TileGridCell) t).stored);
            s.setLong("Capacity", ((TileGridCell) t).capacity());
        }
        GridNet n = t.network;
        if (n != null) {
            s.setLong("In", n.lastIn);
            s.setLong("Out", n.lastOut);
            s.setString("Colour", GridColours.name(n.colour));
            if (n.lastLost > 0) s.setLong("Lost", n.lastLost);
        }
        tag.setTag(TAG, s);
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
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }
}
