package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEItemPipe;
import gregtech.api.util.GTOreDictUnificator;

/**
 * {@code valiertweaks selftest lootspawner}, test server only. In an empty chunk of the flat test world:
 * <ul>
 * <li>clicks: a sneaking fake player with diamond blocks makes a loot spawner (one block used, no block placed); not
 * sneaking does nothing; an empty hand shows the status or a hint; a protected spawner and a blocked mob are refused;</li>
 * <li>rolls: the loot of zombies, blazes (player-kill drops), spiders, slimes (splitting) and wither skeletons (spawn
 * data, rare skull), nothing for a blocked mob or a non-mob, and nothing joins the world;</li>
 * <li>cycles: loot into a chest, held when the chest is full and handed over later, nothing with nothing touching it,
 * into a hopper beside it, pulled by a hopper under it, never put into, and into a GregTech item pipe;</li>
 * <li>saving: saves as a plain spawner with its loot inside, loads as a plain spawner without the mod, comes back after
 * a dolly-style move (the scan) and as its chunk loads; clients get a plain spawner packet;</li>
 * <li>settings: a misspelled conversion item makes none (not free ones); blown up, its loot drops; switched off, it is
 * a plain spawner again with its loot dropped;</li>
 * <li>over the next 70 ticks: a broken one drops what it held (a permission check alone doesn't), its chunk unloads and
 * loads with it still a loot spawner, and one left running fills its chest on its own.</li>
 * </ul>
 */
public final class LootSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = SelfTest.CX << 4, bz = (SelfTest.CZ + 3) << 4;
    final int rx = (SelfTest.CX + 2) << 4; // a chunk that is unloaded and loaded again
    int y, ticks;
    boolean done;
    final String[] said = new String[1];
    FakePlayer player;
    LootSpawnerTile s4;
    int[] s4p, s6p, s7p, s10p;

    void start() {
        world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
        world.theChunkProviderServer.loadChunk(rx >> 4, bz >> 4);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        clear(bx, bz);
        clear(rx, bz);
        player = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000d02"), "LootTester")) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said[0] = message.getUnformattedText();
            }
        };
        player.setPosition(bx + 8.5, y, bz + 0.5);
        clicks();
        rolls();
        cycles();
        saving();
        settings();
        later();
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        SelfTest.log("waiting 70 ticks (spill, chunk reload, a spawner running on its own)");
    }

    // ---- clicks ---------------------------------------------------------------------------------------------------

    void clicks() {
        int[] s1 = { bx + 2, y, bz + 2 };
        TileEntityMobSpawner v1 = spawner(s1, "Zombie");
        world.setBlock(s1[0], y - 1, s1[2], Blocks.chest);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 3));
        player.setSneaking(false);
        PlayerInteractEvent ev = click(s1);
        SelfTest.check(!ev.isCanceled() && te(s1) == v1, "without sneaking, a click with a diamond block changes nothing");
        player.setSneaking(true);
        said[0] = null;
        ev = click(s1);
        TileEntity t1 = te(s1);
        SelfTest.check(ev.isCanceled() && t1 instanceof LootSpawnerTile,
            "sneak-right-click with a diamond block makes a loot spawner (and the block isn't placed)");
        SelfTest.check(held() == 2, "one diamond block is used up (" + held() + " left)");
        SelfTest.check(said[0] != null && said[0].startsWith("Loot spawner made") && said[0].contains("Zombie"),
            "the player is told (" + said[0] + ")");
        if (t1 instanceof LootSpawnerTile) {
            LootSpawnerTile l1 = (LootSpawnerTile) t1;
            SelfTest.check(LootSpawnerTile.isMarked(l1) && l1.candidates.size() == 1 && "Zombie".equals(l1.candidates.get(0).type)
                && l1.candidates.get(0).egg, "it keeps its mob, is marked, and dresses mobs as a vanilla spawner does");
            SelfTest.check(l1.logic().spawnDelay == Short.MAX_VALUE, "its own mob spawning is off");
        }
        player.inventory.setInventorySlotContents(0, null);
        said[0] = null;
        ev = click(s1);
        SelfTest.check(ev.isCanceled() && said[0] != null && said[0].startsWith("Loot spawner: Zombie"),
            "sneak-right-click with an empty hand shows its status (" + said[0] + ")");

        int[] s2 = { bx + 6, y, bz + 2 };
        TileEntityMobSpawner v2 = spawner(s2, "Spider");
        said[0] = null;
        ev = click(s2);
        SelfTest.check(ev.isCanceled() && te(s2) == v2 && said[0] != null && said[0].contains("with a Block of Diamond"),
            "an empty hand on a plain spawner says how to make one (" + said[0] + ")");
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 2));
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(s2);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(te(s2) == v2 && held() == 2 && said[0] != null && said[0].contains("protected"),
            "where a claim protects the spawner it is refused, nothing used (" + said[0] + ")");
        int[] s3 = { bx + 10, y, bz + 2 };
        TileEntityMobSpawner v3 = spawner(s3, "WitherBoss");
        said[0] = null;
        click(s3);
        SelfTest.check(te(s3) == v3 && held() == 2 && said[0] != null && said[0].contains("can't be made"),
            "a blocked mob's spawner (the Wither) is refused (" + said[0] + ")");
    }

    // ---- rolls ----------------------------------------------------------------------------------------------------

    void rolls() {
        int before = world.loadedEntityList.size();
        SelfTest.check(has(rollMany("Zombie", true, null, 300), Items.rotten_flesh, -1), "zombie loot has rotten flesh");
        SelfTest.check(has(rollMany("Blaze", true, null, 100), Items.blaze_rod, -1),
            "blaze loot has blaze rods (they only drop for a player kill)");
        SelfTest.check(has(rollMany("Spider", true, null, 300), Items.string, -1), "spider loot has string");
        SelfTest.check(has(rollMany("Slime", true, null, 100), Items.slime_ball, -1),
            "slime loot has slimeballs (big slimes split, and the small ones drop them)");
        NBTTagCompound wither = new NBTTagCompound();
        wither.setByte("SkeletonType", (byte) 1);
        List<ItemStack> ws = rollMany("Skeleton", false, wither, 400);
        SelfTest.check(has(ws, Items.skull, 1), "wither skeletons from spawn data drop the rare wither skull");
        SelfTest.check(!has(ws, Items.arrow, -1), "and no arrows (the spawn data made them wither skeletons)");
        SelfTest.check(rollMany("WitherBoss", true, null, 5).isEmpty(), "a blocked mob makes nothing");
        SelfTest.check(rollMany("Arrow", true, null, 5).isEmpty(), "something that isn't a mob makes nothing");
        SelfTest.check(world.loadedEntityList.size() == before,
            "nothing joined the world while rolling (" + before + " -> " + world.loadedEntityList.size() + " entities)");

        // A baby zombie (5%) may take a real chicken nearby (5%) as its mount: 2000 rolls make that happen ~5 times.
        EntityChicken chicken = new EntityChicken(world);
        chicken.setLocationAndAngles(bx + 8.5, y + 1, bz + 8.5, 0, 0);
        world.spawnEntityInWorld(chicken);
        rollMany("Zombie", true, null, 2000);
        SelfTest.check(!chicken.func_152116_bZ() && chicken.riddenByEntity == null && !chicken.isDead,
            "a real chicken next to it is never turned into a zombie's mount");
        chicken.setDead();
    }

    List<ItemStack> rollMany(String type, boolean egg, NBTTagCompound props, int n) {
        List<ItemStack> out = new ArrayList<>();
        MobLoot.Candidate c = new MobLoot.Candidate(type, props, egg, 1);
        for (int i = 0; i < n; i++) out.addAll(MobLoot.roll(world, bx + 8.5, y + 1, bz + 8.5, c));
        return out;
    }

    // ---- cycles ---------------------------------------------------------------------------------------------------

    void cycles() {
        s4p = new int[] { bx + 2, y, bz + 6 };
        s4 = lootSpawner(s4p, "Cow");
        world.setBlock(s4p[0], y - 1, s4p[2], Blocks.chest);
        IInventory chest = (IInventory) world.getTileEntity(s4p[0], y - 1, s4p[2]);
        s4.timer = 0;
        LootSpawners.cycle(s4);
        SelfTest.check(count(chest, Items.beef) >= 4 && s4.isEmpty() && s4.rolls == 1,
            "a cycle puts the loot of 4 cows into the chest below (" + count(chest, Items.beef) + " raw beef)");
        SelfTest.check(s4.timer >= 200 && s4.timer <= 800, "then waits 10-40 s for the next (" + s4.timer + " ticks)");
        fill(chest, Blocks.cobblestone);
        s4.timer = 0;
        LootSpawners.cycle(s4);
        SelfTest.check(!s4.isEmpty() && s4.status == LootSpawners.Status.FULL,
            "with the chest full it keeps that batch itself (" + s4.stacksHeld() + " stacks)");
        s4.timer = 0;
        LootSpawners.cycle(s4);
        SelfTest.check(s4.rolls == 2, "and makes no more while it holds loot (" + s4.rolls + " batches)");
        clearInv(chest);
        LootSpawners.cycle(s4);
        SelfTest.check(s4.isEmpty() && count(chest, Items.beef) > 0, "with room again, it hands over what it kept");

        int[] s5p = { bx + 6, y, bz + 6 };
        LootSpawnerTile s5 = lootSpawner(s5p, "Cow");
        s5.timer = 0;
        LootSpawners.cycle(s5);
        SelfTest.check(s5.rolls == 0 && s5.status == LootSpawners.Status.NO_OUTPUT, "with nothing touching it, it makes nothing");
        world.setBlock(s5p[0] + 1, y, s5p[2], Blocks.hopper);
        LootSpawners.cycle(s5);
        IInventory hopper = (IInventory) world.getTileEntity(s5p[0] + 1, y, s5p[2]);
        SelfTest.check(s5.rolls == 1 && count(hopper, Items.beef) > 0, "a hopper beside it gets the loot");

        s7p = new int[] { bx + 10, y, bz + 6 };
        world.setBlock(s7p[0], y - 1, s7p[2], Blocks.hopper);
        LootSpawnerTile s7 = lootSpawner(s7p, "Cow");
        s7.addToBuffer(new ItemStack(Items.beef, 5));
        TileEntityHopper h7 = (TileEntityHopper) world.getTileEntity(s7p[0], y - 1, s7p[2]);
        boolean pulled = TileEntityHopper.func_145891_a(h7);
        SelfTest.check(pulled && count(h7, Items.beef) == 1 && s7.stacksHeld() == 1 && s7.items[0].stackSize == 4,
            "a hopper under it pulls from what it keeps");
        SelfTest.check(!s7.canInsertItem(0, new ItemStack(Items.apple), 1) && !s7.isItemValidForSlot(0, new ItemStack(Items.apple))
            && TileEntityHopper.func_145889_a(s7, new ItemStack(Items.apple), 1) != null, "nothing can be put into it");
        gtPipe();
    }

    /** A GregTech tin item pipe beside a loot spawner connects to it (it is an inventory) and takes its loot. */
    void gtPipe() {
        int[] sp = { bx + 14, y, bz + 6 };
        LootSpawnerTile s = lootSpawner(sp, "Cow");
        ItemStack pipeStack = GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Tin, 1);
        int px = sp[0], pz = sp[2] + 1; // south of the spawner
        boolean placed = pipeStack != null && pipeStack.getItem() instanceof ItemBlock
            && ((ItemBlock) pipeStack.getItem()).placeBlockAt(pipeStack, player, world, px, y, pz, 2, 0.5F, 0.5F, 0F,
                pipeStack.getItemDamage());
        TileEntity te = world.getTileEntity(px, y, pz);
        IGregTechTileEntity gt = te instanceof IGregTechTileEntity ? (IGregTechTileEntity) te : null;
        IMetaTileEntity meta = gt == null ? null : gt.getMetaTileEntity();
        if (!(meta instanceof MTEItemPipe)) {
            SelfTest.check(false, "placed a GregTech tin item pipe (" + pipeStack + " -> " + te + ")");
            return;
        }
        MTEItemPipe pipe = (MTEItemPipe) meta;
        pipe.connect(ForgeDirection.NORTH); // towards the spawner
        SelfTest.check(pipe.isConnectedAtSide(ForgeDirection.NORTH), "a GregTech item pipe connects to a loot spawner");
        ItemStack left = LootSpawners.insertInto(world, px, y, pz, ForgeDirection.NORTH, new ItemStack(Items.beef, 5));
        SelfTest.check(left == null && count(gt, Items.beef) == 5, "and takes its loot (" + count(gt, Items.beef) + " raw beef in the pipe)");
        clearInv(gt);
        s.timer = 0;
        LootSpawners.cycle(s);
        SelfTest.check(s.rolls == 1 && count(gt, Items.beef) + countHeld(s, Items.beef) >= 4,
            "a cycle hands the pipe its loot (" + count(gt, Items.beef) + " in the pipe, " + countHeld(s, Items.beef) + " kept)");
        clearInv(gt);
    }

    static int countHeld(LootSpawnerTile s, Item item) {
        int n = 0;
        for (ItemStack st : s.items) if (st != null && st.getItem() == item) n += st.stackSize;
        return n;
    }

    // ---- saving ---------------------------------------------------------------------------------------------------

    void saving() {
        TileEntity t7 = te(s7p);
        NBTTagCompound tag = new NBTTagCompound();
        t7.writeToNBT(tag);
        SelfTest.check("MobSpawner".equals(tag.getString("id")) && tag.getCompoundTag("SpawnData")
            .getCompoundTag(LootSpawnerTile.MARK)
            .getTagList("Items", 10)
            .tagCount() == 1, "it saves as a plain spawner, with what it keeps inside its spawn data");
        TileEntity plain = TileEntity.createAndLoadEntity(tag);
        SelfTest.check(plain != null && plain.getClass() == TileEntityMobSpawner.class
            && LootSpawnerTile.isMarked((TileEntityMobSpawner) plain)
            && "Cow".equals(((TileEntityMobSpawner) plain).func_145881_a()
                .getEntityNameToSpawn()),
            "without this mod it would load as a plain cow spawner");

        int[] s8p = { bx + 2, y, bz + 10 };
        TileEntityMobSpawner v8 = spawner(s8p, "Pig");
        v8.readFromNBT(movedTo(tag, s8p));
        int swapped = LootSpawners.scan();
        TileEntity t8 = te(s8p);
        SelfTest.check(swapped >= 1 && t8 instanceof LootSpawnerTile && ((LootSpawnerTile) t8).stacksHeld() == 1
            && "Cow".equals(((LootSpawnerTile) t8).candidates.get(0).type),
            "moved the way a dolly does, it is a loot spawner again (the 2 s scan), still holding its loot");

        int[] s9p = { bx + 6, y, bz + 10 };
        TileEntityMobSpawner v9 = spawner(s9p, "Pig");
        v9.readFromNBT(movedTo(tag, s9p));
        Chunk chunk = world.getChunkFromBlockCoords(s9p[0], s9p[2]);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, new NBTTagCompound()));
        Object t9 = chunk.chunkTileEntityMap.get(new ChunkPosition(s9p[0] & 15, y, s9p[2] & 15));
        SelfTest.check(t9 instanceof LootSpawnerTile && v9.isInvalid(), "as its chunk loads, a saved loot spawner is swapped back first");
        if (t9 instanceof LootSpawnerTile) world.setTileEntity(s9p[0], y, s9p[2], (TileEntity) t9);

        S35PacketUpdateTileEntity packet = (S35PacketUpdateTileEntity) s4.getDescriptionPacket();
        NBTTagCompound sent = packetNbt(packet); // its getter is client-only
        SelfTest.check(sent != null && !sent.getCompoundTag("SpawnData")
            .hasKey(LootSpawnerTile.MARK) && !sent.hasKey("SpawnPotentials") && "Cow".equals(sent.getString("EntityId")),
            "clients get a plain spawner packet, without the loot data");
    }

    static NBTTagCompound packetNbt(S35PacketUpdateTileEntity packet) {
        try {
            for (java.lang.reflect.Field f : S35PacketUpdateTileEntity.class.getDeclaredFields()) {
                if (f.getType() == NBTTagCompound.class) {
                    f.setAccessible(true);
                    return (NBTTagCompound) f.get(packet);
                }
            }
        } catch (Exception e) {
            ValierTweaks.LOG.warn("[ValierTweaks SelfTest] couldn't read the packet", e);
        }
        return null;
    }

    // ---- settings ---------------------------------------------------------------------------------------------------

    void settings() {
        // A misspelled conversion item: none can be made (not free ones).
        String item = TweaksConfig.lootSpawnerItem;
        int[] s11 = { bx + 14, y, bz + 2 };
        TileEntityMobSpawner v11 = spawner(s11, "Pig");
        try {
            TweaksConfig.lootSpawnerItem = "minecraft:diamond_blokc";
            player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 2));
            said[0] = null;
            click(s11);
            boolean refusedHeld = te(s11) == v11 && held() == 2 && said[0] != null && said[0].contains("doesn't exist");
            player.inventory.setInventorySlotContents(0, null);
            said[0] = null;
            click(s11);
            SelfTest.check(refusedHeld && te(s11) == v11 && said[0] != null && said[0].contains("doesn't exist"),
                "with a misspelled item in the config, none can be made, not even free (" + said[0] + ")");
        } finally {
            TweaksConfig.lootSpawnerItem = item;
        }

        // Blown up (drops come before the block goes): what it keeps joins the drops.
        int[] s12 = { bx + 2, y, bz + 14 };
        LootSpawnerTile l12 = lootSpawner(s12, "Cow");
        l12.addToBuffer(new ItemStack(Items.leather, 4));
        ArrayList<ItemStack> drops = new ArrayList<>();
        MinecraftForge.EVENT_BUS.post(new BlockEvent.HarvestDropsEvent(s12[0], y, s12[2], world, Blocks.mob_spawner, 0, 0,
            1.0F, drops, null, false));
        SelfTest.check(has(drops, Items.leather, -1) && l12.isEmpty(), "blown up, what it kept is among the drops");

        // Switched off: marked spawners become plain spawners again, dressed as before, what they kept dropped on top.
        int[] s13 = { bx + 6, y, bz + 14 }, s14 = { bx + 10, y, bz + 14 };
        LootSpawnerTile l13 = lootSpawner(s13, "Cow");
        l13.addToBuffer(new ItemStack(Items.leather, 2));
        NBTTagCompound tag = new NBTTagCompound();
        l13.writeToNBT(tag);
        TileEntityMobSpawner v14 = spawner(s14, "Pig");
        v14.readFromNBT(movedTo(tag, s14));
        TweaksConfig.lootSpawners = false;
        try {
            LootSpawners.scan();
        } finally {
            TweaksConfig.lootSpawners = true;
        }
        SelfTest.check(te(s14) == v14 && !LootSpawnerTile.isMarked(v14) && v14.func_145881_a()
            .getRandomEntity() == null && v14.func_145881_a().spawnDelay == 20
            && "Cow".equals(v14.func_145881_a()
                .getEntityNameToSpawn()),
            "switched off in the config, a loot spawner is a plain cow spawner again, ready to spawn");
        SelfTest.check(itemsNear(s14, Items.leather) == 2, "and what it kept drops on top of it (" + itemsNear(s14, Items.leather) + " leather)");
        killItems(s14);
    }

    NBTTagCompound movedTo(NBTTagCompound tag, int[] p) {
        NBTTagCompound moved = (NBTTagCompound) tag.copy();
        moved.setInteger("x", p[0]);
        moved.setInteger("y", p[1]);
        moved.setInteger("z", p[2]);
        return moved;
    }

    // ---- over the next ticks --------------------------------------------------------------------------------------

    void later() {
        // Broken: what it holds drops next tick. Only a break event, no break (a permission check): nothing drops.
        s6p = new int[] { bx + 10, y, bz + 10 };
        LootSpawnerTile s6 = lootSpawner(s6p, "Cow");
        s6.addToBuffer(new ItemStack(Items.leather, 7));
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(s6p[0], y, s6p[2], world, Blocks.mob_spawner, 0, player));
        world.setBlockToAir(s6p[0], y, s6p[2]);
        TileEntity t7 = te(s7p);
        if (t7 instanceof LootSpawnerTile) ((LootSpawnerTile) t7).addToBuffer(new ItemStack(Items.leather, 3));
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(s7p[0], y, s7p[2], world, Blocks.mob_spawner, 0, player));

        // Unloaded and loaded again.
        s10p = new int[] { rx + 4, y, bz + 4 };
        LootSpawnerTile s10 = lootSpawner(s10p, "Cow");
        s10.addToBuffer(new ItemStack(Items.leather, 3));
        world.theChunkProviderServer.unloadChunksIfNotNearSpawn(rx >> 4, bz >> 4);

        // Left running: its chest fills by itself within two cycles.
        clearInv((IInventory) world.getTileEntity(s4p[0], y - 1, s4p[2]));
        s4.timer = 25;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        ticks++;
        try {
            if (ticks == 3) {
                SelfTest.check(itemsNear(s6p, Items.leather) == 7, "a broken loot spawner drops what it held ("
                    + itemsNear(s6p, Items.leather) + " leather)");
                SelfTest.check(itemsNear(s7p, Items.leather) == 0, "a break event alone (a permission check) drops nothing");
                killItems(s6p);
            } else if (ticks == 15) {
                boolean unloaded = !world.theChunkProviderServer.chunkExists(rx >> 4, bz >> 4);
                world.theChunkProviderServer.loadChunk(rx >> 4, bz >> 4);
                TileEntity t10 = te(s10p);
                SelfTest.check(unloaded && t10 instanceof LootSpawnerTile && ((LootSpawnerTile) t10).stacksHeld() == 1
                    && world.loadedTileEntityList.contains(t10),
                    "its chunk unloads and loads again, and it is still a running loot spawner with its loot (unloaded "
                        + unloaded + ", " + (t10 == null ? "no tile" : t10.getClass()
                            .getSimpleName()) + ")");
            } else if (ticks == 70) {
                IInventory chest = (IInventory) world.getTileEntity(s4p[0], y - 1, s4p[2]);
                SelfTest.check(count(chest, Items.beef) > 0 && s4.rolls >= 3,
                    "left running, it filled its chest on its own (" + count(chest, Items.beef) + " raw beef, " + s4.rolls + " batches)");
                finish();
            }
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] lootspawner threw", t);
            SelfTest.check(false, "lootspawner ran without an exception (" + t + ")");
            finish();
        }
    }

    void finish() {
        done = true;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        clear(bx, bz);
        clear(rx, bz);
        SelfTest.summary();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    void clear(int x0, int z0) {
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) for (int yy = y - 1; yy <= y + 1; yy++) {
            TileEntity te = world.getTileEntity(x, yy, z);
            if (te instanceof IInventory && !(te instanceof LootSpawnerTile)) clearInv((IInventory) te);
            if (te instanceof LootSpawnerTile) for (int i = 0; i < LootSpawnerTile.SLOTS; i++) ((LootSpawnerTile) te).items[i] = null;
            if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
        }
        killItems(new int[] { x0 + 8, y, z0 + 8 });
    }

    TileEntityMobSpawner spawner(int[] p, String type) {
        world.setBlock(p[0], p[1], p[2], Blocks.mob_spawner);
        TileEntityMobSpawner te = (TileEntityMobSpawner) world.getTileEntity(p[0], p[1], p[2]);
        te.func_145881_a()
            .setEntityName(type);
        return te;
    }

    LootSpawnerTile lootSpawner(int[] p, String type) {
        TileEntityMobSpawner v = spawner(p, type);
        LootSpawnerTile.mark(v);
        return LootSpawners.swap(world, v);
    }

    TileEntity te(int[] p) {
        return world.getTileEntity(p[0], p[1], p[2]);
    }

    PlayerInteractEvent click(int[] p) {
        return ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, p[0], p[1], p[2], 1,
            world);
    }

    int held() {
        ItemStack s = player.inventory.getStackInSlot(0);
        return s == null ? 0 : s.stackSize;
    }

    static boolean has(List<ItemStack> stacks, Item item, int meta) {
        for (ItemStack s : stacks) if (s.getItem() == item && (meta < 0 || s.getItemDamage() == meta)) return true;
        return false;
    }

    static int count(IInventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    static void fill(IInventory inv, Block block) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, new ItemStack(block, 64));
    }

    static void clearInv(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, null);
    }

    List<?> itemEntities(int[] p) {
        return world.getEntitiesWithinAABB(EntityItem.class,
            AxisAlignedBB.getBoundingBox(p[0] - 2, p[1] - 2, p[2] - 2, p[0] + 3, p[1] + 3, p[2] + 3));
    }

    int itemsNear(int[] p, Item item) {
        int n = 0;
        for (Object o : itemEntities(p)) {
            ItemStack s = ((EntityItem) o).getEntityItem();
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    void killItems(int[] p) {
        for (Object o : world.getEntitiesWithinAABB(EntityItem.class,
            AxisAlignedBB.getBoundingBox(p[0] - 10, p[1] - 4, p[2] - 10, p[0] + 10, p[1] + 4, p[2] + 10)))
            ((EntityItem) o).setDead();
    }
}
