package com.valier.tweaks.grid;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.valier.tweaks.LootSpawners;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Sneak + right-click with a wrench on a Grid block: it comes off as an item keeping its settings and energy. */
public final class GridEvents {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote) return;
        EntityPlayer p = event.entityPlayer;
        World w = event.world;
        Block b = w.getBlock(event.x, event.y, event.z);
        if (!p.isSneaking() || !(b instanceof BlockGrid)) return;
        ItemStack held = p.getHeldItem();
        if (!LootSpawners.isWrench(held, p, event.x, event.y, event.z)) return;
        TileEntity te = w.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof TileGrid)) return;
        event.setCanceled(true);
        if (!LootSpawners.mayChange(p, w, event.x, event.y, event.z)) return;
        pickUp(p, (BlockGrid) b, (TileGrid) te, held);
    }

    static void pickUp(EntityPlayer p, BlockGrid b, TileGrid t, ItemStack wrench) {
        World w = t.getWorldObj();
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        ItemStack item = b.toItem(t);
        t.pickedUp = true;
        w.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(b) + (w.getBlockMetadata(x, y, z) << 12));
        w.setBlockToAir(x, y, z);
        LootSpawners.give(p, item);
        LootSpawners.wrenchUsed(wrench, p, x, y, z);
    }
}
