package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.authlib.GameProfile;

import gregtech.api.metatileentity.implementations.MTEBasicTank;

/**
 * {@code /valiertweaks revertblocks confirm} (ops and the console): before Valier Tweaks is removed from a server,
 * loot spawner blocks become marked vanilla spawners again and XP crafter blocks become GregTech tanks again (their
 * liquid XP and slots inside; their store drops on top), so nothing is lost when the mod's blocks go. Every crafter is
 * reached (their chunks are loaded for it); loot spawners only in loaded chunks (stand near them). Until the next
 * restart nothing is turned back into this mod's blocks. Pipes can't be kept: they drop as nothing when the mod goes.
 */
final class RevertBlocks {

    /** Set by the command: no more conversions into this mod's blocks until the server restarts. */
    static boolean reverting;

    private RevertBlocks() {}

    static List<String> run() {
        reverting = true;
        int spawners = 0, crafters = 0, failed = 0;
        for (WorldServer world : DimensionManager.getWorlds()) {
            // Crafters: every record, loading its chunk.
            XpCrafters.Data d = XpCrafters.data(world, false);
            if (d != null) {
                for (XpCrafters.Crafter c : new ArrayList<>(d.crafters.values())) {
                    world.theChunkProviderServer.loadChunk(c.x >> 4, c.z >> 4);
                    TileXpCrafter t = XpCrafters.tankAt(world, c.x, c.y, c.z);
                    if (t == null) continue;
                    if (toTank(world, d, c, t)) crafters++;
                    else failed++;
                }
            }
            List<LootSpawnerTile> found = new ArrayList<>();
            for (Object o : world.loadedTileEntityList) {
                if (o instanceof LootSpawnerTile && !((TileEntity) o).isInvalid()
                    && world.getBlock(((TileEntity) o).xCoord, ((TileEntity) o).yCoord, ((TileEntity) o).zCoord) == ModBlocks.lootSpawner)
                    found.add((LootSpawnerTile) o);
            }
            for (LootSpawnerTile t : found) if (LootSpawners.toVanilla(world, t) != null) spawners++;
        }
        List<String> out = new ArrayList<>();
        out.add("Turned back: " + spawners + " loot spawners (marked vanilla spawners), " + crafters + " XP crafters (GregTech "
            + "tanks)" + (failed > 0 ? "; " + failed + " crafters couldn't be (see the log)" : "") + ".");
        out.add("Until the server restarts nothing becomes a Valier block again. Loot spawners in unloaded chunks weren't "
            + "reached; pipes can't be kept.");
        ValierTweaks.LOG.info("revertblocks: {}", out.get(0));
        return out;
    }

    /** A crafter block becomes the GregTech tank of the config's crafter tank setting, its fluid and slots inside. */
    static boolean toTank(WorldServer world, XpCrafters.Data d, XpCrafters.Crafter c, TileXpCrafter t) {
        Item item = LootSpawners.itemOf(TweaksConfig.xpCrafterTank);
        if (!(item instanceof ItemBlock)) return false;
        FluidStack fluid = t.getFluid() == null ? null : t.getFluid()
            .copy();
        ItemStack in = t.slots[TileXpCrafter.IN], out = t.slots[TileXpCrafter.OUT];
        t.moving = true; // its contents go into the tank: nothing drops
        world.setBlockToAir(c.x, c.y, c.z);
        ItemStack tank = new ItemStack(item, 1, Math.max(0, LootSpawners.metaOf(TweaksConfig.xpCrafterTank)));
        boolean ok = ((ItemBlock) item).placeBlockAt(tank,
            FakePlayerFactory.get(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000c3"), "[ValierRevert]")),
            world, c.x, c.y, c.z, 1, 0.5F, 0.5F, 0.5F, tank.getItemDamage());
        MTEBasicTank gt = ok ? XpCrafters.gtTankAt(world, c.x, c.y, c.z) : null;
        if (gt == null) {
            ValierTweaks.LOG.error("revertblocks: the XP crafter at {} couldn't become a GregTech tank; its contents drop", c.where());
            if (in != null) LootSpawners.dropAt(world, c.x, c.y, c.z, in);
            if (out != null) LootSpawners.dropAt(world, c.x, c.y, c.z, out);
            for (ItemStack s : c.store) LootSpawners.dropAt(world, c.x, c.y, c.z, s);
            d.remove(c);
            return false;
        }
        gt.mFluid = fluid;
        gt.mInventory[gt.getInputSlot()] = in;
        gt.mInventory[gt.getOutputSlot()] = out;
        for (int i = 0; i < XpCrafters.Crafter.STORE; i++) {
            LootSpawners.dropAt(world, c.x, c.y + 1, c.z, c.store[i]);
            c.store[i] = null;
        }
        d.markDirty(); // the record stays: Valier Tweaks 1.x reads it, and a later 2.x turns it back into the block
        return true;
    }
}
