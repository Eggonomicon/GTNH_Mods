package com.valier.tweaks;

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
 * Adds what an XP crafter is doing to the block data WAILA sends a player looking at it, as a {@value #TAG} compound
 * (see {@link XpCrafters#hudInfo}). Players with the optional Valier Client mod see it as the hover name and a few
 * lines; WAILA itself, and every other client, ignore it.
 * <p>
 * WAILA asks on its network thread, so this only reads what {@link XpCrafters} made ready on the server thread.
 * Registered through WAILA's IMC only when WAILA is loaded: nothing else touches this class.
 */
public final class XpCrafterWaila implements IWailaDataProvider {

    /** The compound added for a player looking at an XP crafter. */
    public static final String TAG = "ValierXpCrafter";

    /** Called by WAILA (IMC {@code register}), on the server and on clients; only the server's is ever asked. */
    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.registerNBTProvider(new XpCrafterWaila(), TileXpCrafter.class);
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y,
        int z) {
        if (tag == null || world == null || world.isRemote || !(te instanceof TileXpCrafter)) return tag;
        NBTTagCompound info = XpCrafters.hudAt(world.provider.dimensionId, x, y, z);
        if (info != null) tag.setTag(TAG, info);
        return tag;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }
}
