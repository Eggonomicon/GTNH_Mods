package com.valier.tweaks;

import static com.valier.tweaks.SelfTest.check;
import static com.valier.tweaks.SelfTest.log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.block.PortalBlock;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.world.DimensionConfig;

/**
 * {@code valiertweaks selftest pdimreset} (2.3.6): a real void personal dimension is made through Personal Space's own
 * portal code (as its menu does), a stone block is built in it, and then:
 * <ul>
 * <li>refusals: not at a portal, someone else's (claimed) portal, the console without a number, dimension 0, a non-op
 * naming a number, someone inside, a confirmation too late, a confirmation with nothing waiting;</li>
 * <li>the reset: confirmed by the portal's owner, the dimension unloads, everything but Personal Space's settings file
 * moves to pdim-reset-backups, generation is unlocked once;</li>
 * <li>new layers (bedrock, 3 dirt, grass) through the menu's code; when the dimension loads again the portal lands at the
 * new ground level + 1 (6, was 129), the return portal is built there the next tick, the grass is at y 4, the old stone
 * and the old height are empty;</li>
 * <li>a portal in a chunk that loads later gets the same height; the saved data round-trips.</li>
 * </ul>
 * The test world keeps the dimension it made (Personal Space has no way to delete one); each run makes a new one.
 */
public final class PdimResetSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int cx = SelfTest.CX - 30, cz = SelfTest.CZ - 30;
    final int x = (cx << 4) + 8, z = (cz << 4) + 8;
    int y, dim, ticks, stage;
    PortalTileEntity portal;
    String folder;
    final List<String> said = new ArrayList<>();
    FakePlayer owner, other;
    ICommandSender console;

    void start() {
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) world.theChunkProviderServer.loadChunk(cx + i, cz + k);
        SelfTest.keepLoaded(world, cx - 1, cz - 1, cx + 1, cz + 1);
        y = world.getHeightValue(x, z);
        owner = fake("PdimOwner", "00000000-0000-4000-8000-000000000d02");
        other = fake("PdimOther", "00000000-0000-4000-8000-000000000d03");
        console = new ICommandSender() {

            @Override
            public String getCommandSenderName() {
                return "PdimTestConsole";
            }

            @Override
            public IChatComponent func_145748_c_() {
                return new ChatComponentText(getCommandSenderName());
            }

            @Override
            public void addChatMessage(IChatComponent message) {
                said.add(message.getUnformattedText());
            }

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return true;
            }

            @Override
            public ChunkCoordinates getPlayerCoordinates() {
                return new ChunkCoordinates(0, 0, 0);
            }

            @Override
            public World getEntityWorld() {
                return world;
            }
        };
        owner.setPosition(x + 2.5, y, z + 0.5);
        other.setPosition(x - 1.5, y, z + 0.5);

        String r = run(owner, "reset");
        check(r.contains("Stand next to the portal"), "with no portal near, /pdim reset says to stand next to one (" + r + ")");

        // A portal and a void dimension, the way Personal Space's menu makes them.
        world.setBlock(x, y, z, PersonalSpaceMod.BLOCK_PORTAL, 0, 3);
        portal = world.getTileEntity(x, y, z) instanceof PortalTileEntity ? (PortalTileEntity) world.getTileEntity(x, y, z) : null;
        check(portal != null, "a Personal Space portal block is placed at " + x + "," + y + "," + z);
        if (portal == null) {
            SelfTest.summary();
            return;
        }
        r = run(owner, "reset");
        check(r.contains("Stand next to the portal"), "a portal that leads nowhere yet doesn't count (" + r + ")");
        DimensionConfig cfg = new DimensionConfig();
        cfg.setLayers("");
        cfg.setBiomeId("Plains");
        portal.updateSettings(owner, cfg);
        dim = portal.targetDimId;
        WorldServer d = DimensionManager.getWorld(dim);
        check(portal.active && dim > 0 && d != null, "Personal Space made void personal dimension " + dim + " and loaded it");
        if (d == null) {
            cleanup();
            SelfTest.summary();
            return;
        }
        check(portal.targetPosY == 129, "its portal lands at y 129, the void's ground level + 1 (" + portal.targetPosY + ")");
        check(d.getBlock(portal.targetPosX, portal.targetPosY, portal.targetPosZ) instanceof PortalBlock,
            "the return portal stands at " + portal.targetPosX + "," + portal.targetPosY + "," + portal.targetPosZ);
        folder = d.provider.getSaveFolder();
        d.setBlock(portal.targetPosX + 3, 140, portal.targetPosZ, Blocks.stone);
        try {
            d.saveAllChunks(true, null);
        } catch (Exception e) {
            check(false, "the new dimension saves (" + e + ")");
        }

        // Who may, and when not.
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        r = run(other, "reset");
        MinecraftForge.EVENT_BUS.unregister(guard);
        check(r.contains("claimed by someone else") && !PdimReset.pending.containsKey("PdimOther"),
            "someone who couldn't break the portal block (a claim) may not reset it (" + r + ")");
        r = run(console, "reset");
        check(r.contains("name the dimension"), "the console has to name the dimension (" + r + ")");
        r = run(console, "reset", "0");
        check(r.contains("isn't a personal dimension"), "dimension 0 can't be reset (" + r + ")");
        r = run(owner, "reset", String.valueOf(dim));
        check(r.startsWith("Only ops"), "a player who isn't an op can't name a dimension (" + r + ")");
        d.playerEntities.add(other);
        r = run(console, "reset", String.valueOf(dim));
        d.playerEntities.remove(other);
        check(r.contains("Someone is in personal dimension " + dim) && r.contains("PdimOther") && !PdimReset.pending.containsKey("PdimTestConsole"),
            "nobody may reset a dimension someone is in (" + r + ")");
        r = run(owner, "reset");
        check(r.contains("wipes personal dimension " + dim) && r.contains("/pdim reset confirm within") && PdimReset.pending.containsKey("PdimOwner"),
            "the portal's owner is asked to confirm (" + r + ")");
        PdimReset.pending.put("PdimOwner", new PdimReset.Pending(dim, System.currentTimeMillis() - 1));
        r = run(owner, "reset", "confirm");
        check(r.contains("wasn't confirmed in time") && PdimReset.jobs.isEmpty(), "a confirmation that comes too late does nothing (" + r + ")");
        r = run(owner, "reset", "confirm");
        check(r.contains("no reset waiting"), "a confirmation with nothing waiting does nothing (" + r + ")");
        check(new File(dir(), "region").isDirectory(), "the dimension's chunks are still on disk after all that");

        // The real one.
        run(owner, "reset");
        r = run(owner, "reset", "confirm");
        check(r.contains("Resetting personal dimension " + dim) && PdimReset.jobs.size() == 1, "the owner's confirmation starts the reset (" + r + ")");
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        log("waiting for personal dimension " + dim + " to unload and reset");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            if (stage == 0) {
                if (!PdimReset.lastResult.containsKey(dim)) {
                    if (ticks < 300) return;
                    check(false, "the reset finished within 15 seconds");
                    finish();
                    return;
                }
                afterReset();
                stage = 1;
            } else {
                afterReload();
                finish();
            }
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] pdimreset threw", t);
            check(false, "pdimreset checks ran without an exception (" + t + ")");
            finish();
        }
    }

    private void afterReset() {
        String result = PdimReset.lastResult.get(dim);
        check(result.startsWith("reset: " + PdimReset.BACKUPS + "/" + folder + "-"), "the reset finished in " + ticks + " ticks (" + result + ")");
        check(DimensionManager.getWorld(dim) == null, "the dimension was unloaded");
        String[] left = dir().list();
        check(left != null && Arrays.asList(left).equals(Arrays.asList(PdimReset.METADATA)),
            "only Personal Space's settings file is left in " + folder + " (" + Arrays.toString(left) + ")");
        File backup = MinecraftServer.getServer().getFile(result.substring("reset: ".length()));
        File[] mca = new File(backup, "region").listFiles();
        check(mca != null && mca.length > 0, "the old chunks are in the backup folder (" + (mca == null ? 0 : mca.length) + " region files)");
        DimensionConfig cfg = DimensionConfig.getForDimension(dim, false);
        check(cfg != null && cfg.getAllowGenerationChanges(), "its layers and biome may be changed once more");
        check(PdimReset.Data.get().waiting.contains(dim), "it waits for its fresh start (saved)");

        // New layers through the menu's code: bedrock, 3 dirt, grass.
        DimensionConfig next = new DimensionConfig();
        next.setLayers("minecraft:bedrock;minecraft:dirt*3;minecraft:grass");
        next.setBiomeId("Plains");
        portal.updateSettings(owner, next);
        check(cfg != null && cfg.getGroundLevel() == 5 && !cfg.getAllowGenerationChanges(),
            "the new layers took (ground level " + (cfg == null ? -1 : cfg.getGroundLevel()) + ") and generation is locked again");
        check(portal.targetPosY == 129, "Personal Space alone leaves the portal landing at y 129 (" + portal.targetPosY + ")");

        DimensionManager.initDimension(dim); // what the next trip through the portal does first
        check(DimensionManager.getWorld(dim) != null, "the dimension loads again");
        check(portal.targetPosY == 6, "as it loads, the portal is pointed at the new ground level + 1, y 6 (" + portal.targetPosY + ")");
        check(PdimReset.relink.contains(portal), "its return portal is due to be built the next tick");
        PdimReset.Data data = PdimReset.Data.get();
        check(!data.waiting.contains(dim) && Integer.valueOf(6).equals(data.arrival.get(dim)),
            "with the new settings in, it no longer waits, and its arrival height (6) is saved");
    }

    private void afterReload() {
        WorldServer d = DimensionManager.getWorld(dim);
        int tx = portal.targetPosX, tz = portal.targetPosZ;
        check(d != null && d.getBlock(tx, 6, tz) instanceof PortalBlock, "the return portal was built again at " + tx + ",6," + tz);
        PortalTileEntity back = d != null && d.getTileEntity(tx, 6, tz) instanceof PortalTileEntity ? (PortalTileEntity) d.getTileEntity(tx, 6, tz) : null;
        check(back != null && back.active && back.targetDimId == 0 && back.targetPosX == x && back.targetPosY == y && back.targetPosZ == z,
            "the return portal leads back to the overworld portal");
        check(d != null && d.getBlock(tx, 4, tz) == Blocks.grass && d.getBlock(tx, 0, tz) == Blocks.bedrock,
            "the dimension generated fresh with the new layers (grass at y 4, bedrock at y 0)");
        check(d != null && d.isAirBlock(tx, 129, tz) && d.isAirBlock(tx + 3, 140, tz),
            "nothing is left at the old height, and the stone built before the reset is gone");
        check(PdimReset.relink.isEmpty(), "nothing else waits to be relinked");

        // A portal in a chunk that loads later.
        portal.targetPosY = 129;
        int n = PdimReset.fixChunk(world.getChunkFromBlockCoords(x, z), PdimReset.Data.get());
        check(n == 1 && portal.targetPosY == 6, "a portal in a chunk that loads later gets the same height (" + portal.targetPosY + ")");

        NBTTagCompound tag = new NBTTagCompound();
        PdimReset.Data.get().writeToNBT(tag);
        PdimReset.Data copy = new PdimReset.Data("copy");
        copy.readFromNBT(tag);
        check(Integer.valueOf(6).equals(copy.arrival.get(dim)) && copy.waiting.equals(PdimReset.Data.get().waiting), "the reset data saves and loads");
    }

    private void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        cleanup();
        SelfTest.summary();
    }

    private void cleanup() {
        world.setBlockToAir(x, y, z);
        PdimReset.pending.clear();
    }

    private File dir() {
        return new File(DimensionManager.getCurrentSaveRootDirectory(), folder);
    }

    private String run(ICommandSender sender, String... args) {
        said.clear();
        try {
            new PdimCommand().processCommand(sender, args);
        } catch (RuntimeException e) {
            said.add("threw " + e);
        }
        return String.join(" | ", said);
    }

    private FakePlayer fake(String name, String uuid) {
        return new FakePlayer(world, new GameProfile(UUID.fromString(uuid), name)) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said.add(message.getUnformattedText());
            }
        };
    }
}
