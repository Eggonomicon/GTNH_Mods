package com.valier.tweaks.pipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.network.IGuiHandler;

/** Opens pipe face menus (GUI ids 10 to 15: 10 + the face's side; 16: a creative pipe's source). */
public final class PipeGui implements IGuiHandler {

    public static final int BASE = 10;

    public static void open(EntityPlayer player, TilePipe p, int side) {
        player.openGui(ValierTweaks.instance, BASE + side, p.getWorldObj(), p.xCoord, p.yCoord, p.zCoord);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int side = id - BASE;
        TileEntity te = world.getTileEntity(x, y, z);
        if (side < 0 || side > TilePipe.SOURCE || !(te instanceof TilePipe)) return null;
        TilePipe p = (TilePipe) te;
        FaceConfig f = p.face(side);
        return f == null ? null : new ContainerPipeFace(player.inventory, p, side, f);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int side = id - BASE;
        TileEntity te = world.getTileEntity(x, y, z);
        if (side < 0 || side > TilePipe.SOURCE || !(te instanceof TilePipe)) return null;
        return ValierTweaks.proxy.pipeGui(new ContainerPipeFace(player.inventory, (TilePipe) te, side, new FaceConfig()));
    }
}
