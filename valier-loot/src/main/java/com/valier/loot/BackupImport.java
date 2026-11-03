package com.valier.loot;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * {@code /lootchest import <candidates file> <backup world folder> [apply]} (server console only): gives chests from
 * before this mod was installed their original loot back, as loot chests.
 * <p>
 * The candidates file lists {@code dim x y z} per line. For each, the chest still standing there is checked against
 * the same chest in an older backup of the world (read straight from its region files, never written). It is restored
 * only if:
 * <ul>
 * <li>it is a vanilla chest that isn't already loot or a player's;</li>
 * <li>the backup has loot in it;</li>
 * <li>what's in it now is part of that loot (looted or untouched, not used as storage);</li>
 * <li>the other half of its double chest, if any, is being restored too (or is empty).</li>
 * </ul>
 * Without {@code apply} nothing changes; either way a report is written next to the candidates file.
 */
public final class BackupImport {

    private static final int[][] HORIZONTAL = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private BackupImport() {}

    private static final class Candidate {

        final int dim, x, y, z;
        String outcome;
        ItemStack[] original;

        Candidate(int dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static void run(ICommandSender sender, String candidatesPath, String backupPath, boolean apply) {
        File candidatesFile = new File(candidatesPath), backup = new File(backupPath);
        if (!candidatesFile.isFile() || !backup.isDirectory()) {
            reply(sender, "Need an existing candidates file and backup world folder.");
            return;
        }
        List<Candidate> candidates = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(candidatesFile.toPath(), StandardCharsets.UTF_8)) {
                String[] f = line.trim()
                    .split("\\s+");
                if (f.length != 4 || f[0].startsWith("#")) continue;
                candidates.add(
                    new Candidate(
                        Integer.parseInt(f[0]),
                        Integer.parseInt(f[1]),
                        Integer.parseInt(f[2]),
                        Integer.parseInt(f[3])));
            }
        } catch (IOException | NumberFormatException e) {
            reply(sender, "Could not read " + candidatesFile + ": " + e);
            return;
        }

        Map<String, NBTTagCompound> backupChunks = new HashMap<>();
        for (Candidate c : candidates) check(c, backup, backupChunks);

        // A restored chest must not end up joined to a full chest that isn't restored with it.
        Set<String> restoring = new HashSet<>();
        for (Candidate c : candidates) if (c.original != null) restoring.add(c.dim + " " + c.x + " " + c.y + " " + c.z);
        for (Candidate c : candidates) {
            if (c.original == null) continue;
            World world = DimensionManager.getWorld(c.dim);
            Block block = world.getBlock(c.x, c.y, c.z);
            for (int[] d : HORIZONTAL) {
                int nx = c.x + d[0], nz = c.z + d[1];
                TileEntity te = world.getTileEntity(nx, c.y, nz);
                if (world.getBlock(nx, c.y, nz) != block || !LootEvents.isVanillaChest(te)) continue;
                LootData data = LootData.get(world);
                if (restoring.contains(c.dim + " " + nx + " " + c.y + " " + nz) || data.isLoot(LootData.pack(nx, c.y, nz))
                    || !data.isIgnored(LootData.pack(nx, c.y, nz)) && !LootEvents.hasItems((TileEntityChest) te)) continue;
                c.outcome = "skip: joined to a chest that isn't being restored";
                c.original = null;
            }
        }

        if (apply) {
            // First put every chest's original loot back in its block and register it, then lock them: the halves of
            // a double chest are locked together, each with its own original loot.
            for (Candidate c : candidates) {
                if (c.original == null) continue;
                World world = DimensionManager.getWorld(c.dim);
                TileEntityChest chest = (TileEntityChest) world.getTileEntity(c.x, c.y, c.z);
                for (int i = 0; i < chest.getSizeInventory(); i++) chest.getStackInSlotOnClosing(i);
                for (int i = 0; i < c.original.length && i < chest.getSizeInventory(); i++) {
                    if (c.original[i] != null) chest.setInventorySlotContents(i, c.original[i].copy());
                }
                LootData.get(world)
                    .addPending(LootData.pack(c.x, c.y, c.z));
            }
            for (Candidate c : candidates) {
                if (c.original == null) continue;
                World world = DimensionManager.getWorld(c.dim);
                LootEvents.lock(world, LootData.get(world), c.x, c.y, c.z);
                c.outcome = "restored";
            }
            // An empty half (no loot of its own in the backup) is restored together with the other half.
            for (Candidate c : candidates) {
                World world = DimensionManager.getWorld(c.dim);
                if (c.original == null && world != null && c.outcome.startsWith("skip: no chest with loot")
                    && LootData.get(world)
                        .getOpened(LootData.pack(c.x, c.y, c.z)) != null) c.outcome = "restored with the other half";
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Candidate c : candidates) counts.merge(c.outcome, 1, Integer::sum);
        File report = new File(candidatesFile.getParentFile(), apply ? "import-applied.txt" : "import-dryrun.txt");
        try (PrintWriter out = new PrintWriter(report, "UTF-8")) {
            out.println((apply ? "Applied" : "Dry run") + " of " + candidatesFile + " against " + backup);
            for (Map.Entry<String, Integer> e : counts.entrySet()) out.println(e.getValue() + "  " + e.getKey());
            out.println();
            for (Candidate c : candidates) {
                out.println(c.dim + " " + c.x + " " + c.y + " " + c.z + "  " + c.outcome + describe(c.original));
            }
        } catch (IOException e) {
            reply(sender, "Could not write " + report + ": " + e);
        }
        reply(
            sender,
            (apply ? "Import applied: " : "Import dry run (nothing changed): ") + counts + ". Report: " + report);
    }

    private static void check(Candidate c, File backup, Map<String, NBTTagCompound> backupChunks) {
        World world = DimensionManager.getWorld(c.dim);
        if (!(world instanceof WorldServer)) {
            c.outcome = "skip: dimension not loaded";
            return;
        }
        ((WorldServer) world).theChunkProviderServer.loadChunk(c.x >> 4, c.z >> 4);
        TileEntity te = world.getTileEntity(c.x, c.y, c.z);
        if (!LootEvents.isVanillaChest(te)) {
            c.outcome = "skip: no vanilla chest there now";
            return;
        }
        LootData data = LootData.get(world);
        long pos = LootData.pack(c.x, c.y, c.z);
        if (data.isLoot(pos)) {
            c.outcome = "skip: already a loot chest";
            return;
        }
        if (data.isIgnored(pos)) {
            c.outcome = "skip: a player's chest";
            return;
        }
        ItemStack[] original;
        try {
            original = backupChest(backup, backupChunks, c.dim, c.x, c.y, c.z);
        } catch (IOException e) {
            c.outcome = "skip: could not read the backup (" + e.getMessage() + ")";
            return;
        }
        if (original == null) {
            c.outcome = "skip: no chest with loot there in the backup";
            return;
        }
        if (!isPartOf((TileEntityChest) te, original)) {
            c.outcome = "skip: holds items that weren't in the original loot (used as storage?)";
            return;
        }
        c.original = original;
        c.outcome = "would restore";
    }

    /** True if every item now in the chest was in the original loot, in no larger amount. */
    private static boolean isPartOf(TileEntityChest chest, ItemStack[] original) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : original) if (s != null) left.add(s.copy());
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack now = chest.getStackInSlot(i);
            if (now == null) continue;
            int need = now.stackSize;
            for (ItemStack o : left) {
                if (need == 0) break;
                if (o.stackSize == 0 || !o.isItemEqual(now) || !ItemStack.areItemStackTagsEqual(o, now)) continue;
                int take = Math.min(need, o.stackSize);
                o.stackSize -= take;
                need -= take;
            }
            if (need > 0) return false;
        }
        return true;
    }

    /** The loot in the backup's chest at x, y, z, or null if there was no chest with items there. */
    private static ItemStack[] backupChest(File backup, Map<String, NBTTagCompound> cache, int dim, int x, int y, int z)
        throws IOException {
        int cx = x >> 4, cz = z >> 4;
        String key = dim + " " + cx + " " + cz;
        NBTTagCompound chunk = cache.get(key);
        if (chunk == null && !cache.containsKey(key)) {
            File dir = dim == 0 ? new File(backup, "region") : new File(backup, "DIM" + dim + "/region");
            chunk = readChunk(new File(dir, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca"), cx, cz);
            cache.put(key, chunk);
        }
        if (chunk == null) return null;
        NBTTagList tiles = chunk.getCompoundTag("Level")
            .getTagList("TileEntities", 10);
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if (!"Chest".equals(tile.getString("id")) || tile.getInteger("x") != x
                || tile.getInteger("y") != y
                || tile.getInteger("z") != z) continue;
            ItemStack[] stacks = new ItemStack[LootData.SIZE];
            boolean any = false;
            NBTTagList items = tile.getTagList("Items", 10);
            for (int j = 0; j < items.tagCount(); j++) {
                NBTTagCompound s = items.getCompoundTagAt(j);
                int slot = s.getByte("Slot") & 255;
                ItemStack stack = ItemStack.loadItemStackFromNBT(s);
                if (slot < stacks.length && stack != null) {
                    stacks[slot] = stack;
                    any = true;
                }
            }
            return any ? stacks : null;
        }
        return null;
    }

    /** Reads one chunk's NBT from a region file, opened read-only (Minecraft's own RegionFile opens it for writing). */
    private static NBTTagCompound readChunk(File region, int cx, int cz) throws IOException {
        if (!region.isFile()) return null;
        try (RandomAccessFile f = new RandomAccessFile(region, "r")) {
            f.seek(4L * ((cx & 31) + (cz & 31) * 32));
            int location = f.readInt();
            if (location == 0) return null;
            f.seek(4096L * (location >>> 8));
            int length = f.readInt();
            byte type = f.readByte();
            if (length <= 1 || length > 1 << 24) return null;
            byte[] data = new byte[length - 1];
            f.readFully(data);
            InputStream raw = new ByteArrayInputStream(data);
            InputStream in = type == 1 ? new GZIPInputStream(raw) : new InflaterInputStream(raw);
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(in))) {
                return CompressedStreamTools.read(dis);
            }
        }
    }

    private static String describe(ItemStack[] stacks) {
        if (stacks == null) return "";
        StringBuilder sb = new StringBuilder("  [");
        for (ItemStack s : stacks) {
            if (s == null) continue;
            if (sb.length() > 3) sb.append(", ");
            sb.append(s.stackSize)
                .append("x ")
                .append(s.getDisplayName());
        }
        return sb.append("]")
            .toString();
    }

    private static void reply(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
