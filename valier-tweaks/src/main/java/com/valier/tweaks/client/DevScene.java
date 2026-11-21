package com.valier.tweaks.client;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeGui;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The dev client's scene (single player's server side, see {@link DevClient}): every pipe tier of both kinds in a row,
 * pipes between chests with one face of each mode, a pipe network turning a corner, a loot spawner (cow) and an XP
 * crafter. Later it opens a pipe face's menu for the screenshot.
 */
public final class DevScene {

    static volatile boolean built;
    static volatile int tick;
    static int baseX, baseY, baseZ;
    private int wait;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WorldServer w = DimensionManager.getWorld(0);
        if (w == null || w.playerEntities.isEmpty()) return;
        if (!built) {
            if (++wait < 40) return;
            build(w);
            built = true;
            return;
        }
        tick++;
        if (tick == 215) { // the camera (the player) is beside the pipe by now: menus close beyond 8 blocks
            EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
            TileEntity te = w.getTileEntity(baseX + 2, baseY, baseZ + 6);
            if (te instanceof TilePipe) PipeGui.open(p, (TilePipe) te, ForgeDirection.WEST.ordinal());
        }
    }

    void build(WorldServer w) {
        baseX = 0;
        baseZ = 0;
        baseY = w.getHeightValue(0, 0);
        int y = baseY;
        // Every tier, item then fluid, standing alone (just the core).
        for (int t = 0; t < PipeTier.COUNT; t++) {
            pipe(w, baseX + t * 2, y, baseZ, ModBlocks.itemPipe, t);
            pipe(w, baseX + t * 2, y, baseZ + 2, ModBlocks.fluidPipe, t);
        }
        // A chest, two pipes, a chest: four faces, one per mode (Provide, Insert, Extract, Request) on two rigs.
        rig(w, baseX, y, baseZ + 6, FaceMode.EXTRACT, FaceMode.INSERT, PipeTier.ELITE);
        rig(w, baseX, y, baseZ + 9, FaceMode.PROVIDE, FaceMode.REQUEST, PipeTier.ADVANCED);
        // A line turning a corner and climbing.
        for (int i = 0; i < 4; i++) pipe(w, baseX + 8 + i, y, baseZ + 6, ModBlocks.itemPipe, PipeTier.BASIC);
        for (int i = 1; i < 4; i++) pipe(w, baseX + 11, y, baseZ + 6 + i, ModBlocks.itemPipe, PipeTier.BASIC);
        for (int i = 1; i < 3; i++) pipe(w, baseX + 11, y + i, baseZ + 9, ModBlocks.itemPipe, PipeTier.BASIC);
        // A loot spawner and an XP crafter.
        w.setBlock(baseX + 14, y, baseZ, Blocks.mob_spawner);
        TileEntity te = w.getTileEntity(baseX + 14, y, baseZ);
        if (te instanceof TileEntityMobSpawner) {
            ((TileEntityMobSpawner) te).func_145881_a()
                .setEntityName("Cow");
            com.valier.tweaks.LootSpawnerTile.mark((TileEntityMobSpawner) te);
            com.valier.tweaks.LootSpawners.swap(w, (TileEntityMobSpawner) te);
        }
        w.setBlock(baseX + 14, y, baseZ + 3, ModBlocks.xpCrafter);
        ValierTweaks.LOG.info("[DevClient] scene built at {} {} {}", baseX, y, baseZ);
    }

    static TilePipe pipe(WorldServer w, int x, int y, int z, net.minecraft.block.Block b, int tier) {
        w.setBlock(x, y, z, b, tier, 3);
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TilePipe) ((TilePipe) te).updateConnections();
        return te instanceof TilePipe ? (TilePipe) te : null;
    }

    static void rig(WorldServer w, int x, int y, int z, FaceMode left, FaceMode right, int tier) {
        w.setBlock(x + 1, y, z, Blocks.chest);
        w.setBlock(x + 4, y, z, Blocks.chest);
        ((IInventory) w.getTileEntity(x + 1, y, z)).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
        TilePipe a = pipe(w, x + 2, y, z, com.valier.tweaks.ModBlocks.itemPipe, tier);
        TilePipe b = pipe(w, x + 3, y, z, com.valier.tweaks.ModBlocks.itemPipe, tier);
        if (a == null || b == null) return;
        a.updateConnections();
        b.updateConnections();
        FaceConfig fa = a.face(ForgeDirection.WEST.ordinal()), fb = b.face(ForgeDirection.EAST.ordinal());
        if (fa != null) fa.mode = left;
        if (fb != null) {
            fb.mode = right;
            fb.allow.filter[0] = new ItemStack(Blocks.cobblestone);
            fb.allow.count[0] = 32;
        }
        if (fa != null && left == FaceMode.EXTRACT) { // something to see in the menu's filter
            fa.allow.filter[0] = new ItemStack(Blocks.cobblestone);
            fa.allow.filter[1] = new ItemStack(net.minecraft.init.Items.iron_ingot);
            fa.allow.count[1] = 16;
            fa.allow.filter[2] = new ItemStack(net.minecraft.init.Items.diamond_pickaxe);
            fa.allow.filter[3] = new ItemStack(Blocks.log, 1, 2);
            fa.allow.count[3] = 128;
            fa.allow.oreDict = true;
            fa.deny.filter[0] = new ItemStack(Blocks.dirt);
            fa.voids.filter[0] = new ItemStack(net.minecraft.init.Items.rotten_flesh);
            fa.voids.filter[1] = new ItemStack(Blocks.cobblestone);
            fa.voids.count[1] = 1000;
            fa.spread = FaceConfig.Spread.EVEN;
            fa.tag = "001";
        }
        a.faceChanged();
        b.faceChanged();
    }
}
