package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
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
import gregtech.common.items.IDMetaTool01;

/**
 * {@code valiertweaks selftest lootspawner}, test server only. In an empty chunk of the flat test world:
 * <ul>
 * <li>clicks: a sneaking fake player with diamond blocks makes a loot spawner (one block used, no block placed); not
 * sneaking does nothing; an empty hand shows the status or a hint; a protected spawner and a blocked mob are refused;</li>
 * <li>rolls: the loot of zombies, blazes (player-kill drops), spiders, slimes (splitting) and wither skeletons (spawn
 * data, rare skull), nothing for a blocked mob or a non-mob, and nothing joins the world;</li>
 * <li>cycles: loot into a chest, held when the chest is full and handed over later, nothing with nothing touching it,
 * into a hopper beside it, pulled by a hopper under it, never put into, and into a GregTech item pipe;</li>
 * <li>damaged items: an emerald block switches a loot spawner to throwing worn gear away (kept in it, given back when
 * switched off, refused where protected, not on a plain spawner); zombies' worn swords stop coming out, undamaged
 * ones still do;</li>
 * <li>wrench: a GregTech wrench (not its hammer, not a pickaxe) picks a loot spawner up as a "Valier Loot Spawner"
 * item, its loot dropped, not where protected, not without sneaking, not a plain spawner, not with the setting off;
 * placed, the item is the same loot spawner again (mob, settings, emerald block), even after NEI names a mob; a claim
 * that stops placing keeps the item; with loot spawners off it places a plain spawner; the hover packet names it and
 * carries no spawn data (the setting off: the mob and its spawn data); with a player beside it a loot spawner's own
 * logic is never active (EnderIO runs a spawner when a player breaks it), nor is a plain spawner while it is asked for
 * permission to convert it;</li>
 * <li>eggs: with lootSpawnerSpawnEggs off (the default) a spawn egg changes nothing; on, a skeleton egg makes a zombie
 * loot spawner a skeleton one (one egg used, its loot and emerald block kept, saved, the hover renamed); the same mob,
 * an egg without a mob, a blocked mob and a protected spawner use nothing; plain spawners are left alone;</li>
 * <li>overflow: with the chest below full, the loot goes into the chest beside it;</li>
 * <li>gold block: XP counted as a player kill (blazes 10, cows 1-3); a gold block switches a loot spawner to making
 * bottles o' enchanting (4 blazes: 5 bottles, 5 XP kept; then 6 more, 3 kept), kept in it, saved, refused where
 * protected, given back when switched off, only a hint on a plain spawner;</li>
 * <li>lapis blocks: 1, 3 and 6 give Looting I, II and III (the setting changes that), zombies rolled with Looting III
 * drop far more flesh; one block per click, refused where protected and past the most; status, hover and the wrench's
 * item name the level, and the blocks drop when it is broken;</li>
 * <li>Infernal Mobs (its rarity set to 1 for the test): witches become elites and their armour comes out undamaged,
 * with the elites' extra XP, and none stay in its list; switched off, no elites;</li>
 * <li>Special Mobs: every blaze (none plain; some variants release small blazes, killed too) and some zombies come
 * as its variants (killed for loot, never in the world or its
 * queue); switched off, none;</li>
 * <li>taking back: a sneaking hit with an empty hand gives back the lapis, gold and emerald blocks and switches those
 * off; not without sneaking, not where protected, and an empty one says so;</li>
 * <li>cursed earth (Extra Utilities): 1, 3 and 6 give Speed I, II and III, batches 2, 3 and 4 times as often (the one
 * counting down too); one per click, refused where protected and past the most; status, hover, the wrench's item and
 * a save keep it; taken back, normal speed; one under it counts as one (not with the setting off); only a hint on a
 * plain spawner; and Extra Utilities' own spawner code (cursed earth under any spawner) counts a plain spawner down but
 * never gets a loot spawner's count to 0, so no real mob comes out;</li>
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
    final int wx = (SelfTest.CX - 1) << 4; // the wrench tests, on the other side from the unloaded chunk
    int y, ticks;
    boolean done;
    final String[] said = new String[1];
    FakePlayer player;
    LootSpawnerTile s4;
    int[] s4p, s6p, s7p, s10p;

    boolean infernalWas, specialWas;

    void start() {
        infernalWas = TweaksConfig.lootSpawnerInfernalMobs;
        specialWas = TweaksConfig.lootSpawnerSpecialMobs;
        TweaksConfig.lootSpawnerInfernalMobs = false; // elites' extra loot would make the counts below random
        TweaksConfig.lootSpawnerSpecialMobs = false; // and so would Special Mobs' variants
        world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
        world.theChunkProviderServer.loadChunk(rx >> 4, bz >> 4);
        world.theChunkProviderServer.loadChunk(wx >> 4, bz >> 4);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        clear(bx, bz);
        clear(rx, bz);
        clear(wx, bz);
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
        damagedItems();
        wrench();
        eggs();
        upgrades();
        infernal();
        special();
        takeBack();
        speed();
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
        SelfTest.check(ev.isCanceled() && t1 instanceof LootSpawnerTile && world.getBlock(s1[0], y, s1[2]) == ModBlocks.lootSpawner,
            "sneak-right-click with a diamond block makes a Valier Loot Spawner block (and the diamond block isn't placed)");
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

    // ---- damaged items ----------------------------------------------------------------------------------------------

    void damagedItems() {
        int[] s1 = { bx + 2, y, bz + 2 }, s2 = { bx + 6, y, bz + 2 };
        TileEntity t1 = te(s1);
        if (!(t1 instanceof LootSpawnerTile)) {
            SelfTest.check(false, "the zombie loot spawner from the clicks is there (" + t1 + ")");
            return;
        }
        LootSpawnerTile l1 = (LootSpawnerTile) t1;
        SelfTest.check(LootSpawners.damaged(worn(Items.iron_sword)) && LootSpawners.damaged(worn(Items.iron_chestplate))
            && !LootSpawners.damaged(new ItemStack(Items.iron_sword)) && !LootSpawners.damaged(new ItemStack(Items.rotten_flesh))
            && !LootSpawners.damaged(new ItemStack(Items.dye, 1, 4)), "damaged means worn gear (not new gear, not items with a meta)");
        player.setSneaking(true);
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.emerald_block, 3));
        l1.addToBuffer(worn(Items.iron_sword));
        l1.addToBuffer(new ItemStack(Items.rotten_flesh, 2));
        said[0] = null;
        PlayerInteractEvent ev = click(s1);
        SelfTest.check(ev.isCanceled() && l1.noDamaged && held() == 2 && l1.filterPaid != null && l1.filterPaid.getItem() == emerald()
            && l1.filterPaid.stackSize == 1 && said[0] != null && said[0].contains("throws damaged items away"),
            "sneak-right-click with an emerald block makes it throw damaged items away, keeping the block (" + said[0] + ")");
        SelfTest.check(countHeld(l1, Items.iron_sword) == 0 && countHeld(l1, Items.rotten_flesh) == 2,
            "a worn sword it was keeping is thrown away, the rest kept");
        player.inventory.setInventorySlotContents(0, null);
        said[0] = null;
        click(s1);
        SelfTest.check(said[0] != null && said[0].contains("Damaged items are thrown away"), "its status says so");
        NBTTagCompound tag = new NBTTagCompound();
        l1.writeToNBT(tag);
        LootSpawnerTile copy = new LootSpawnerTile();
        copy.readFromNBT(tag);
        SelfTest.check(copy.noDamaged && copy.filterPaid != null && copy.filterPaid.getItem() == emerald(),
            "saved and loaded (or moved with a dolly), it still throws them away and still has the emerald block");
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.emerald_block, 2));
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(s1);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(l1.noDamaged && held() == 2 && said[0] != null && said[0].contains("protected"),
            "where a claim protects it, it can't be switched (" + said[0] + ")");
        said[0] = null;
        ev = click(s1);
        SelfTest.check(ev.isCanceled() && !l1.noDamaged && l1.filterPaid == null && held() == 3,
            "doing it again switches that off and gives the emerald block back (" + said[0] + ")");
        TileEntity v2 = te(s2);
        said[0] = null;
        ev = click(s2);
        SelfTest.check(ev.isCanceled() && te(s2) == v2 && held() == 3 && said[0] != null && said[0].contains("loot spawner first"),
            "on a plain spawner an emerald block does nothing but say how (" + said[0] + ")");
        player.inventory.setInventorySlotContents(0, null);
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) l1.items[i] = null;

        // Zombies holding iron swords that always drop: worn (drop chance 1, as mobs' own gear drops), or undamaged
        // (drop chance 2, as gear a mob picked up drops).
        int[] pa = { bx + 14, y, bz + 10 }, pb = { bx + 14, y, bz + 14 };
        LootSpawnerTile la = swordZombies(pa, 1.0F), lb = swordZombies(pb, 2.0F);
        IInventory ca = (IInventory) world.getTileEntity(pa[0], y - 1, pa[2]);
        IInventory cb = (IInventory) world.getTileEntity(pb[0], y - 1, pb[2]);
        la.timer = 0;
        LootSpawners.cycle(la);
        SelfTest.check(la.rolls == 1 && countWorn(ca, Items.iron_sword) >= 4,
            "zombies' swords drop worn, and come out as usual (" + countWorn(ca, Items.iron_sword) + " worn swords)");
        clearInv(ca);
        la.noDamaged = true;
        la.timer = 0;
        LootSpawners.cycle(la);
        SelfTest.check(la.rolls == 2 && count(ca, Items.iron_sword) == 0 && countHeld(la, Items.iron_sword) == 0,
            "throwing damaged items away, no worn sword comes out (" + count(ca, Items.iron_sword) + ")");
        lb.noDamaged = true;
        lb.timer = 0;
        LootSpawners.cycle(lb);
        SelfTest.check(lb.rolls == 1 && count(cb, Items.iron_sword) >= 4 && countWorn(cb, Items.iron_sword) == 0,
            "undamaged swords still come out (" + count(cb, Items.iron_sword) + ")");
    }

    LootSpawnerTile swordZombies(int[] p, float dropChance) {
        TileEntityMobSpawner v = spawner(p, "Zombie");
        NBTTagCompound props = new NBTTagCompound();
        NBTTagList equipment = new NBTTagList();
        NBTTagList chances = new NBTTagList();
        equipment.appendTag(new ItemStack(Items.iron_sword).writeToNBT(new NBTTagCompound()));
        chances.appendTag(new NBTTagFloat(dropChance));
        for (int i = 0; i < 4; i++) {
            equipment.appendTag(new NBTTagCompound());
            chances.appendTag(new NBTTagFloat(0.0F));
        }
        props.setTag("Equipment", equipment);
        props.setTag("DropChances", chances);
        MobSpawnerBaseLogic logic = v.func_145881_a();
        logic.setRandomEntity(logic.new WeightedRandomMinecart(props, "Zombie"));
        world.setBlock(p[0], y - 1, p[2], Blocks.chest);
        LootSpawnerTile.mark(v);
        return LootSpawners.swap(world, v);
    }

    static ItemStack worn(Item item) {
        ItemStack s = new ItemStack(item);
        s.setItemDamage(40);
        return s;
    }

    static Item emerald() {
        return Item.getItemFromBlock(Blocks.emerald_block);
    }

    static int countWorn(IInventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() == item && s.isItemDamaged()) n += s.stackSize;
        }
        return n;
    }

    // ---- wrench ---------------------------------------------------------------------------------------------------

    void wrench() {
        int[] w1 = { wx + 2, y, bz + 2 }, w2 = { wx + 6, y, bz + 2 }, w3 = { wx + 10, y, bz + 2 }, w4 = { wx + 14, y, bz + 2 };
        int[] w5 = { wx + 2, y, bz + 6 }, w6 = { wx + 6, y, bz + 6 }, w7 = { wx + 10, y, bz + 6 };
        ItemStack wrench = gtTool(IDMetaTool01.WRENCH), hammer = gtTool(IDMetaTool01.HARDHAMMER);
        if (wrench == null) {
            SelfTest.check(false, "made a GregTech steel wrench");
            return;
        }
        SelfTest.check(wrenchy(wrench, w1) && !wrenchy(hammer, w1) && !wrenchy(new ItemStack(Items.diamond_pickaxe), w1)
            && !wrenchy(null, w1), "a GregTech wrench is a wrench (its hammer and a pickaxe aren't)");

        // A zombie loot spawner with its own settings, loot kept, throwing damaged items away.
        TileEntityMobSpawner v1 = spawner(w1, "Zombie");
        NBTTagCompound settings = new NBTTagCompound();
        v1.writeToNBT(settings);
        settings.setShort("SpawnCount", (short) 6);
        settings.setShort("MinSpawnDelay", (short) 100);
        settings.setShort("MaxSpawnDelay", (short) 300);
        v1.readFromNBT(settings);
        LootSpawnerTile.mark(v1);
        LootSpawnerTile l1 = LootSpawners.swap(world, v1);
        l1.addToBuffer(new ItemStack(Items.leather, 3));
        l1.noDamaged = true;
        l1.filterPaid = new ItemStack(Blocks.emerald_block);

        player.inventory.setInventorySlotContents(0, wrench);
        player.setSneaking(false);
        PlayerInteractEvent ev = click(w1);
        SelfTest.check(!ev.isCanceled() && te(w1) == l1, "without sneaking, a wrench changes nothing");
        player.setSneaking(true);
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(w1);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(te(w1) == l1 && said[0] != null && said[0].contains("protected") && takeSpawnerItem() == null,
            "where a claim protects it, a wrench can't take it (" + said[0] + ")");
        said[0] = null;
        ev = click(w1);
        ItemStack item = takeSpawnerItem();
        SelfTest.check(ev.isCanceled() && world.isAirBlock(w1[0], y, w1[2]) && item != null
            && player.inventory.getStackInSlot(0) == wrench && said[0] != null && said[0].startsWith("Picked up"),
            "sneak-right-click with a GregTech wrench picks it up as an item (" + said[0] + ")");
        if (item == null) return;
        NBTTagList lore = item.getTagCompound()
            .getCompoundTag("display")
            .getTagList("Lore", 8);
        SelfTest.check(item.getDisplayName()
            .contains("Valier Loot Spawner") && item.getDisplayName()
                .contains("Zombie")
            && lore.tagCount() == 3 && lore.getStringTagAt(1)
                .contains("damaged")
            && item.getItem() == Item.getItemFromBlock(ModBlocks.lootSpawner) && LootSpawners.savedSpawner(item) != null,
            "the item is a Valier Loot Spawner (Zombie) block item that says it throws damaged items away ("
                + item.getDisplayName() + ")");
        SelfTest.check(itemsNear(w1, Items.leather) == 3 && itemsNear(w1, emerald()) == 0,
            "the loot it kept drops, the emerald block goes with the item (" + itemsNear(w1, Items.leather) + " leather, "
                + itemsNear(w1, emerald()) + " emerald blocks on the ground)");
        killItems(w1);

        // Placed again (as a player places a block: Forge's placement and its PlaceEvent).
        TileEntity t2 = placeItem(item, w2);
        SelfTest.check(item.stackSize == 0 && t2 instanceof LootSpawnerTile, "placed, the item is a loot spawner again (" + t2 + ")");
        if (!(t2 instanceof LootSpawnerTile)) return;
        LootSpawnerTile l2 = (LootSpawnerTile) t2;
        SelfTest.check("Zombie".equals(l2.logic()
            .getEntityNameToSpawn()) && l2.candidates.size() == 1 && l2.candidates.get(0).egg && l2.spawnCount == 6
            && l2.minDelay == 100 && l2.maxDelay == 300 && l2.noDamaged && l2.filterPaid != null
            && l2.filterPaid.getItem() == emerald() && l2.isEmpty() && l2.timer > 0,
            "with the same mob, settings and emerald block, none of the loot, and a fresh wait");
        l2.logic()
            .setEntityName("Pig"); // what NEI asks for after placing a spawner item whose mob it doesn't know
        NBTTagCompound after = new NBTTagCompound();
        l2.writeToNBT(after);
        SelfTest.check("Zombie".equals(l2.logic()
            .getEntityNameToSpawn()) && "Zombie".equals(after.getString("EntityId")),
            "NEI naming a mob for the placed spawner changes nothing");
        NBTTagCompound sent = packetNbt((S35PacketUpdateTileEntity) l2.getDescriptionPacket());
        SelfTest.check(sent != null && "Zombie".equals(sent.getString("EntityId")) && !sent.hasKey("SpawnData")
            && sent.hasKey("ValierLooting") && EntityList.createEntityByName(sent.getString("EntityId"), null) != null,
            "players' computers get the real mob, so it spins in the cage, and its levels for the name (" + sent + ")");
        LootSpawnerTile l5 = swordZombies(w5, 1.0F);
        NBTTagCompound sent5 = packetNbt((S35PacketUpdateTileEntity) l5.getDescriptionPacket());
        SelfTest.check(sent5 != null && "Zombie".equals(sent5.getString("EntityId")) && sent5.getCompoundTag("SpawnData")
            .hasKey("Equipment") && !sent5.getCompoundTag("SpawnData")
                .hasKey(LootSpawnerTile.MARK),
            "with spawn data (zombies with swords) players get it too, so the cage's zombie holds its sword; never the mark");
        ItemStack pick = ModBlocks.lootSpawner.getPickBlock(null, world, w5[0], y, w5[2]);
        SelfTest.check(pick != null && pick.getDisplayName().replaceAll("§.", "")
            .contains("Valier Loot Spawner (Zombie") && LootSpawners.savedSpawner(pick) != null,
            "pick-block (and WAILA's name) is the Valier Loot Spawner (Zombie) item (" + (pick == null ? null : pick.getDisplayName()) + ")");

        // Picked up again: placing it where a claim stops it places nothing and keeps the item.
        player.inventory.setInventorySlotContents(0, wrench);
        click(w2);
        ItemStack item2 = takeSpawnerItem();
        if (item2 == null) {
            SelfTest.check(false, "the placed loot spawner can be picked up again");
            return;
        }
        ItemStack spare = item2.copy();
        DenyPlaces deny = new DenyPlaces();
        MinecraftForge.EVENT_BUS.register(deny);
        TileEntity t3;
        try {
            t3 = placeItem(item2, w3);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        SelfTest.check(t3 == null && world.isAirBlock(w3[0], y, w3[2]) && item2.stackSize == 1,
            "where a claim stops placing, nothing is placed and the item is kept");

        // Loot spawners switched off: the item places a plain spawner of its mob, the emerald block dropped.
        TweaksConfig.lootSpawners = false;
        TileEntity t4;
        try {
            t4 = placeItem(spare, w4);
        } finally {
            TweaksConfig.lootSpawners = true;
        }
        SelfTest.check(t4 != null && t4.getClass() == TileEntityMobSpawner.class && !LootSpawnerTile.isMarked((TileEntityMobSpawner) t4)
            && "Zombie".equals(((TileEntityMobSpawner) t4).func_145881_a()
                .getEntityNameToSpawn())
            && world.getBlock(w4[0], y, w4[2]) == Blocks.mob_spawner && itemsNear(w4, emerald()) == 1,
            "with loot spawners switched off, the item places a plain (vanilla) zombie spawner and drops its emerald block");
        world.setBlockToAir(w4[0], y, w4[2]);
        killItems(w4);

        // A wrench's item from before 2.0.0 (a vanilla spawner item holding the same) still places a loot spawner block.
        ItemStack old = new ItemStack(Blocks.mob_spawner, 1, 54);
        old.setTagCompound((NBTTagCompound) spare.getTagCompound()
            .copy());
        TileEntity t4b = placeItem(old, w4);
        SelfTest.check(t4b instanceof LootSpawnerTile && world.getBlock(w4[0], y, w4[2]) == ModBlocks.lootSpawner
            && ((LootSpawnerTile) t4b).noDamaged && "Zombie".equals(((LootSpawnerTile) t4b).logic()
                .getEntityNameToSpawn()),
            "an item from before 2.0.0 (a vanilla spawner item) places a Valier Loot Spawner block, same mob and settings");
        if (t4b instanceof LootSpawnerTile) LootSpawners.empty((LootSpawnerTile) t4b);
        world.setBlockToAir(w4[0], y, w4[2]);

        LootSpawnerTile l6 = lootSpawner(w6, "Cow");
        TweaksConfig.lootSpawnerWrench = false;
        try {
            player.inventory.setInventorySlotContents(0, wrench);
            ev = click(w6);
        } finally {
            TweaksConfig.lootSpawnerWrench = true;
        }
        SelfTest.check(!ev.isCanceled() && te(w6) == l6, "with lootSpawnerWrench off, a wrench leaves it alone");
        TileEntityMobSpawner v7 = spawner(w7, "Spider");
        ev = click(w7);
        SelfTest.check(!ev.isCanceled() && te(w7) == v7, "a wrench leaves a plain spawner alone");
        player.inventory.setInventorySlotContents(0, null);
        inert();
    }

    /**
     * EnderIO runs a spawner up to 6 times when a player breaks it, and the permission check is a break event: with a
     * player next to it, a loot spawner's own logic never counts as active (so nothing spawns), and a plain spawner
     * being converted isn't active while it is asked. Its real range is still saved and sent.
     */
    void inert() {
        int[] e1 = { wx + 2, y, bz + 10 }, e2 = { wx + 6, y, bz + 10 }, e3 = { wx + 10, y, bz + 10 };
        LootSpawnerTile le = lootSpawner(e1, "Blaze");
        TileEntityMobSpawner plain = spawner(e2, "Blaze"), convert = spawner(e3, "Cow");
        player.setPosition(e2[0] + 0.5, y, e2[2] + 2.5);
        ActiveAtBreak seen = new ActiveAtBreak();
        world.playerEntities.add(player); // so spawners see a player next to them; nothing here spawns or ticks meanwhile
        try {
            boolean plainActive = plain.func_145881_a()
                .isActivated(), lootActive = le.logic()
                    .isActivated();
            int before = mobsNear(e1);
            for (int i = 0; i < 6; i++) { // what EnderIO does on a break
                le.logic().spawnDelay = 0;
                le.logic()
                    .updateSpawner();
            }
            SelfTest.check(plainActive && !lootActive && mobsNear(e1) == before,
                "with a player beside it, a plain spawner is active but a loot spawner isn't: run as EnderIO does on a "
                    + "break, it spawns nothing (" + before + " -> " + mobsNear(e1) + " mobs)");
            MinecraftForge.EVENT_BUS.register(seen);
            try {
                player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 1));
                player.setSneaking(true);
                click(e3);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(seen);
            }
            SelfTest.check(seen.active != null && !seen.active && te(e3) instanceof LootSpawnerTile && convert.isInvalid(),
                "a plain spawner being converted isn't active while its break event is asked (" + seen.active + ")");
        } finally {
            world.playerEntities.remove(player);
            player.setPosition(bx + 8.5, y, bz + 0.5);
            player.inventory.setInventorySlotContents(0, null);
        }
        NBTTagCompound live = new NBTTagCompound(), saved = new NBTTagCompound();
        le.logic()
            .writeToNBT(live);
        le.writeToNBT(saved);
        NBTTagCompound sent = packetNbt((S35PacketUpdateTileEntity) le.getDescriptionPacket());
        SelfTest.check(live.getShort("RequiredPlayerRange") == 0 && saved.getShort("RequiredPlayerRange") == 16
            && sent != null && sent.getShort("RequiredPlayerRange") == 16,
            "it saves and sends its own player range (16), only its live logic has 0");
    }

    // ---- spawn eggs, and loot going to other sides ----------------------------------------------------------------

    void eggs() {
        int[] g1 = { wx + 2, y, bz + 14 }, g2 = { wx + 6, y, bz + 14 }, o1 = { wx + 10, y, bz + 14 };
        LootSpawnerTile l1 = lootSpawner(g1, "Zombie");
        l1.addToBuffer(new ItemStack(Items.rotten_flesh, 3));
        l1.noDamaged = true;
        l1.filterPaid = new ItemStack(Blocks.emerald_block);
        TileEntityMobSpawner v2 = spawner(g2, "Spider");
        player.setSneaking(true);
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.spawn_egg, 3, 51)); // skeleton
        PlayerInteractEvent ev = click(g1);
        SelfTest.check(!ev.isCanceled() && "Zombie".equals(l1.logic()
            .getEntityNameToSpawn()) && held() == 3, "with lootSpawnerSpawnEggs off (the default), a spawn egg changes nothing");
        String[] blocked = TweaksConfig.lootSpawnerBlockedMobs;
        TweaksConfig.lootSpawnerSpawnEggs = true;
        try {
            said[0] = null;
            ev = click(g1);
            SelfTest.check(ev.isCanceled() && te(g1) == l1 && "Skeleton".equals(l1.logic()
                .getEntityNameToSpawn()) && l1.candidates.size() == 1 && "Skeleton".equals(l1.candidates.get(0).type)
                && l1.candidates.get(0).egg && held() == 2 && said[0] != null && said[0].contains("Skeleton"),
                "switched on, a skeleton egg makes a zombie loot spawner a skeleton one, one egg used (" + said[0] + ")");
            NBTTagCompound tag = new NBTTagCompound();
            l1.writeToNBT(tag);
            LootSpawnerTile copy = new LootSpawnerTile();
            copy.readFromNBT(tag);
            NBTTagCompound sent = packetNbt((S35PacketUpdateTileEntity) l1.getDescriptionPacket());
            SelfTest.check(countHeld(l1, Items.rotten_flesh) == 3 && l1.noDamaged && l1.filterPaid != null
                && "Skeleton".equals(copy.candidates.get(0).type) && copy.noDamaged && "Skeleton".equals(tag.getString("EntityId"))
                && sent != null && sent.getString("EntityId")
                    .contains("Skeleton"),
                "what it was keeping and its emerald block stay, it saves as a skeleton spawner, and the hover says so");
            said[0] = null;
            click(g1);
            SelfTest.check(held() == 2 && said[0] != null && said[0].contains("already"),
                "the same mob again uses nothing (" + said[0] + ")");
            player.inventory.setInventorySlotContents(0, new ItemStack(Items.spawn_egg, 1, 9999));
            said[0] = null;
            click(g1);
            SelfTest.check(held() == 1 && said[0] != null && said[0].contains("no mob") && "Skeleton".equals(l1.logic()
                .getEntityNameToSpawn()), "an egg without a mob uses nothing (" + said[0] + ")");
            TweaksConfig.lootSpawnerBlockedMobs = new String[] { "WitherBoss", "EnderDragon", "Creeper" };
            player.inventory.setInventorySlotContents(0, new ItemStack(Items.spawn_egg, 1, 50)); // creeper
            said[0] = null;
            click(g1);
            SelfTest.check(held() == 1 && said[0] != null && said[0].contains("isn't allowed") && "Skeleton".equals(l1.logic()
                .getEntityNameToSpawn()), "a blocked mob's egg is refused, nothing used (" + said[0] + ")");
            TweaksConfig.lootSpawnerBlockedMobs = blocked;
            player.inventory.setInventorySlotContents(0, new ItemStack(Items.spawn_egg, 1, 52)); // spider
            SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
            MinecraftForge.EVENT_BUS.register(guard);
            said[0] = null;
            try {
                click(g1);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(guard);
            }
            SelfTest.check(held() == 1 && said[0] != null && said[0].contains("protected") && "Skeleton".equals(l1.logic()
                .getEntityNameToSpawn()), "where a claim protects it, an egg changes nothing (" + said[0] + ")");
            ev = click(g2);
            SelfTest.check(!ev.isCanceled() && te(g2) == v2 && "Spider".equals(v2.func_145881_a()
                .getEntityNameToSpawn()) && held() == 1, "a plain spawner is left alone (vanilla's egg use goes ahead)");
        } finally {
            TweaksConfig.lootSpawnerSpawnEggs = false;
            TweaksConfig.lootSpawnerBlockedMobs = blocked;
            player.inventory.setInventorySlotContents(0, null);
        }

        // Every side: the chest below is full, so the loot goes into the chest beside it.
        LootSpawnerTile lo = lootSpawner(o1, "Cow");
        world.setBlock(o1[0], y - 1, o1[2], Blocks.chest);
        world.setBlock(o1[0] + 1, y, o1[2], Blocks.chest);
        IInventory below = (IInventory) world.getTileEntity(o1[0], y - 1, o1[2]);
        IInventory beside = (IInventory) world.getTileEntity(o1[0] + 1, y, o1[2]);
        fill(below, Blocks.cobblestone);
        lo.timer = 0;
        LootSpawners.cycle(lo);
        SelfTest.check(lo.rolls == 1 && count(beside, Items.beef) >= 4 && count(below, Items.beef) == 0 && lo.isEmpty(),
            "with the chest below full, the loot goes into the chest beside it (" + count(beside, Items.beef) + " raw beef)");
        clearInv(below);
        clearInv(beside);
    }

    // ---- gold block (XP bottles), lapis blocks (Looting), Infernal Mobs -------------------------------------------

    void upgrades() {
        int[] x1 = { wx + 14, y, bz + 10 }, x2 = { wx + 14, y, bz + 14 }, x3 = { wx + 6, y, bz }, x4 = { wx + 10, y, bz };
        Item gold = Item.getItemFromBlock(Blocks.gold_block), lapis = Item.getItemFromBlock(Blocks.lapis_block);
        int blazeXp = xpOf("Blaze", 10), cowXp = xpOf("Cow", 50);
        SelfTest.check(blazeXp == 100 && cowXp >= 50 && cowXp <= 150,
            "XP is counted as for a player kill: 10 blazes give 100, 50 cows 50 to 150 (" + blazeXp + ", " + cowXp + ")");
        LootSpawnerTile lx = lootSpawner(x1, "Blaze");
        world.setBlock(x1[0], y - 1, x1[2], Blocks.chest);
        IInventory cx = (IInventory) world.getTileEntity(x1[0], y - 1, x1[2]);
        lx.timer = 0;
        LootSpawners.cycle(lx);
        SelfTest.check(lx.rolls == 1 && count(cx, Items.experience_bottle) == 0 && lx.xp == 0,
            "without a gold block it makes no bottles o' enchanting");
        player.setSneaking(true);
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.gold_block, 3));
        said[0] = null;
        PlayerInteractEvent ev = click(x1);
        SelfTest.check(ev.isCanceled() && lx.xpBottles && held() == 2 && lx.xpPaid != null && lx.xpPaid.getItem() == gold
            && said[0] != null && said[0].contains("bottles"),
            "sneak-right-click with a gold block makes it turn XP into bottles, keeping the block (" + said[0] + ")");
        clearInv(cx);
        lx.timer = 0;
        LootSpawners.cycle(lx);
        int first = count(cx, Items.experience_bottle), keptFirst = lx.xp;
        lx.timer = 0;
        LootSpawners.cycle(lx);
        SelfTest.check(first == 5 && keptFirst == 5 && count(cx, Items.experience_bottle) == 11 && lx.xp == 3,
            "4 blazes (40 XP) give 5 bottles with 5 XP kept, the next 4 (45 XP) 6 more with 3 kept (" + first + "/" + keptFirst
                + ", " + count(cx, Items.experience_bottle) + "/" + lx.xp + ")");
        player.inventory.setInventorySlotContents(0, null);
        said[0] = null;
        click(x1);
        SelfTest.check(said[0] != null && said[0].contains("bottles o' enchanting (3 of 7"), "its status says so (" + said[0] + ")");
        NBTTagCompound tag = new NBTTagCompound();
        lx.writeToNBT(tag);
        LootSpawnerTile copy = new LootSpawnerTile();
        copy.readFromNBT(tag);
        SelfTest.check(copy.xpBottles && copy.xpPaid != null && copy.xpPaid.getItem() == gold && copy.xp == 3,
            "saved and loaded, it still makes bottles, with its gold block and the 3 XP towards the next");
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.gold_block, 2));
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(x1);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(lx.xpBottles && held() == 2 && said[0] != null && said[0].contains("protected"),
            "where a claim protects it, it can't be switched (" + said[0] + ")");
        said[0] = null;
        click(x1);
        SelfTest.check(!lx.xpBottles && lx.xpPaid == null && held() == 3,
            "doing it again switches that off and gives the gold block back (" + said[0] + ")");
        TileEntityMobSpawner v4 = spawner(x4, "Spider");
        said[0] = null;
        ev = click(x4);
        SelfTest.check(ev.isCanceled() && te(x4) == v4 && held() == 3 && said[0] != null && said[0].contains("loot spawner first"),
            "on a plain spawner a gold block only says how (" + said[0] + ")");

        // Looting from lapis blocks.
        int[] tiers = TweaksConfig.lootSpawnerLootingBlocks;
        boolean levels = LootSpawners.lootingLevel(0) == 0 && LootSpawners.lootingLevel(1) == 1 && LootSpawners.lootingLevel(2) == 1
            && LootSpawners.lootingLevel(3) == 2 && LootSpawners.lootingLevel(5) == 2 && LootSpawners.lootingLevel(6) == 3
            && LootSpawners.lootingLevel(60) == 3;
        TweaksConfig.lootSpawnerLootingBlocks = new int[] { 2, 5 };
        boolean changed = LootSpawners.lootingLevel(1) == 0 && LootSpawners.lootingLevel(6) == 2;
        TweaksConfig.lootSpawnerLootingBlocks = tiers;
        SelfTest.check(levels && changed, "1, 3 and 6 lapis blocks give Looting I, II and III; the setting changes that");
        int plainFlesh = fleshOf(0, 300), lootingFlesh = fleshOf(3, 300);
        SelfTest.check(plainFlesh > 0 && lootingFlesh > plainFlesh * 3 / 2,
            "rolled with Looting III, zombies drop far more rotten flesh (" + plainFlesh + " -> " + lootingFlesh + " from 300)");
        LootSpawnerTile ll = lootSpawner(x2, "Zombie");
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.lapis_block, 10));
        said[0] = null;
        ev = click(x2);
        SelfTest.check(ev.isCanceled() && ll.looting.blocks == 1 && LootSpawners.lootingLevel(ll.looting.blocks) == 1 && held() == 9
            && said[0] != null && said[0].startsWith("Looting I now"), "one lapis block gives Looting I (" + said[0] + ")");
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(x2);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(ll.looting.blocks == 1 && held() == 9 && said[0] != null && said[0].contains("protected"),
            "where a claim protects it, none goes in (" + said[0] + ")");
        click(x2);
        said[0] = null;
        click(x2);
        SelfTest.check(ll.looting.blocks == 3 && held() == 7 && said[0] != null && said[0].startsWith("Looting II now")
            && said[0].contains("3 more for Looting III"), "3 give Looting II, and it says what Looting III takes (" + said[0] + ")");
        click(x2);
        click(x2);
        said[0] = null;
        click(x2);
        String third = said[0];
        said[0] = null;
        click(x2);
        SelfTest.check(ll.looting.blocks == 6 && ll.looting.paid == 6 && held() == 4 && third != null && third.startsWith("Looting III now")
            && said[0] != null && said[0].contains("the most"), "6 give Looting III, and no more go in (" + third + " / " + said[0] + ")");
        player.inventory.setInventorySlotContents(0, null);
        said[0] = null;
        click(x2);
        NBTTagCompound sent = packetNbt((S35PacketUpdateTileEntity) ll.getDescriptionPacket());
        tag = new NBTTagCompound();
        ll.writeToNBT(tag);
        copy = new LootSpawnerTile();
        copy.readFromNBT(tag);
        SelfTest.check(said[0] != null && said[0].contains("Looting III (6 in it; the most)") && sent != null
            && sent.getInteger("ValierLooting") == 3 && copy.looting.blocks == 6 && copy.looting.paid == 6 && copy.looting.item != null,
            "its status and players' name for it say Looting III, and it is saved (" + said[0] + " / " + sent + ")");
        ItemStack wrench = gtTool(IDMetaTool01.WRENCH);
        player.inventory.setInventorySlotContents(0, wrench);
        click(x2);
        ItemStack item = takeSpawnerItem();
        boolean named = false;
        if (item != null) {
            NBTTagList lore = item.getTagCompound()
                .getCompoundTag("display")
                .getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) named |= lore.getStringTagAt(i)
                .contains("Looting III");
        }
        TileEntity placed = item == null ? null : placeItem(item, x3);
        SelfTest.check(named && placed instanceof LootSpawnerTile && ((LootSpawnerTile) placed).looting.blocks == 6
            && ((LootSpawnerTile) placed).looting.paid == 6, "picked up with a wrench its item says Looting III, and placed it still has it");
        world.setBlockToAir(x3[0], y, x3[2]); // what creative players, a Matter Manipulator or another mod do
        int lapisDropped = itemsNear(x3, lapis);
        SelfTest.check(lapisDropped == 6, "removed, its 6 lapis blocks drop (" + lapisDropped + ")");
        killItems(x3);
        player.inventory.setInventorySlotContents(0, null);
    }

    /** With Infernal Mobs: elites as real spawned mobs get them, their armour undamaged, their XP counted. */
    void infernal() {
        Object core = MobLoot.Infernal.core();
        if (core == null) {
            SelfTest.check(false, "Infernal Mobs is installed on the test server");
            return;
        }
        java.lang.reflect.Field rarity;
        java.util.Map<?, ?> rare;
        try {
            rarity = core.getClass()
                .getField("eliteRarity");
            Object proxy = core.getClass()
                .getField("proxy")
                .get(null);
            rare = (java.util.Map<?, ?>) Class.forName("atomicstryker.infernalmobs.common.ISidedProxy")
                .getMethod("getRareMobs")
                .invoke(proxy);
        } catch (Throwable t) {
            SelfTest.check(false, "reached Infernal Mobs' settings (" + t + ")");
            return;
        }
        int was;
        try {
            was = rarity.getInt(core);
        } catch (IllegalAccessException e) {
            return;
        }
        int before = rare.size();
        MobLoot.Candidate witch = new MobLoot.Candidate("Witch", null, true, 1);
        try {
            rarity.setInt(core, 1);
            TweaksConfig.lootSpawnerInfernalMobs = true;
            int[] xp = new int[1];
            List<ItemStack> loot = new ArrayList<>();
            for (int i = 0; i < 20; i++) loot.addAll(MobLoot.roll(world, bx + 8.5, y + 1, bz + 8.5, witch, xp, 0));
            int armour = 0, worn = 0;
            for (ItemStack st : loot) {
                if (st.getItem() instanceof ItemArmor) {
                    armour++;
                    if (st.isItemDamaged()) worn++;
                }
            }
            SelfTest.check(armour > 0 && worn == 0 && xp[0] > 100 && rare.size() == before,
                "with Infernal Mobs (rarity 1 for the test) witches are elites: " + armour + " pieces of armour, " + worn
                    + " damaged, " + xp[0] + " XP from 20 (100 without elites), none left in its list");
            TweaksConfig.lootSpawnerInfernalMobs = false;
            loot.clear();
            xp[0] = 0;
            for (int i = 0; i < 20; i++) loot.addAll(MobLoot.roll(world, bx + 8.5, y + 1, bz + 8.5, witch, xp, 0));
            armour = 0;
            for (ItemStack st : loot) if (st.getItem() instanceof ItemArmor) armour++;
            SelfTest.check(armour == 0 && xp[0] == 100, "with lootSpawnerInfernalMobs off, no elites (" + armour + " armour, " + xp[0] + " XP)");
        } catch (IllegalAccessException e) {
            SelfTest.check(false, "set Infernal Mobs' rarity (" + e + ")");
        } finally {
            try {
                rarity.setInt(core, was);
            } catch (IllegalAccessException ignored) {}
            TweaksConfig.lootSpawnerInfernalMobs = false;
        }
    }

    /** With Special Mobs: mobs come as its variants, as real spawned mobs do (on a GTNH server every blaze). */
    void special() {
        if (!MobLoot.Special.present()) {
            SelfTest.check(false, "Special Mobs is installed on the test server");
            return;
        }
        java.util.Collection<?> queue = null;
        try {
            queue = (java.util.Collection<?>) Class.forName("toast.specialMobs.TickHandler")
                .getField("entityStack")
                .get(null);
        } catch (Throwable ignored) {}
        int queued = queue == null ? -1 : queue.size(), entities = world.loadedEntityList.size();
        MobLoot.killedLog = new ArrayList<>();
        try {
            TweaksConfig.lootSpawnerSpecialMobs = true;
            rollMany("Blaze", true, null, 20);
            int blazes = specialKills(), plainBlazes = MobLoot.killedLog.size() - blazes;
            MobLoot.killedLog.clear();
            rollMany("Zombie", true, null, 60);
            int zombies = specialKills(), zombieKills = MobLoot.killedLog.size();
            SelfTest.check(blazes >= 20 && plainBlazes == 0 && zombies > 0 && zombies < zombieKills
                && world.loadedEntityList.size() == entities
                && (queue == null ? -1 : queue.size()) == queued,
                "with Special Mobs every blaze comes as a variant (" + blazes + " variant kills from 20, some release small "
                    + "blazes, " + plainBlazes + " plain) and some zombies do (" + zombies + " of " + zombieKills
                    + "), none in the world or its queue");
            TweaksConfig.lootSpawnerSpecialMobs = false;
            MobLoot.killedLog.clear();
            rollMany("Blaze", true, null, 20);
            SelfTest.check(specialKills() == 0, "with lootSpawnerSpecialMobs off, plain blazes (" + specialKills() + " variants)");
        } finally {
            MobLoot.killedLog = null;
            TweaksConfig.lootSpawnerSpecialMobs = false;
        }
    }

    int specialKills() {
        int n = 0;
        for (String c : MobLoot.killedLog) if (c.startsWith("toast.specialMobs.")) n++;
        return n;
    }

    /** A sneaking hit with an empty hand gives back what was put in. */
    void takeBack() {
        int[] t1 = { wx + 14, y, bz };
        LootSpawnerTile lt = lootSpawner(t1, "Zombie");
        lt.looting.blocks = 3;
        lt.looting.paid = 3;
        lt.looting.item = new ItemStack(Blocks.lapis_block);
        lt.xpBottles = true;
        lt.xpPaid = new ItemStack(Blocks.gold_block);
        lt.noDamaged = true;
        lt.filterPaid = new ItemStack(Blocks.emerald_block);
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        player.setSneaking(false);
        PlayerInteractEvent ev = hit(t1);
        SelfTest.check(!ev.isCanceled() && lt.looting.blocks == 3, "without sneaking, a hit takes nothing out");
        player.setSneaking(true);
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            hit(t1);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(lt.looting.blocks == 3 && lt.xpBottles && lt.noDamaged && said[0] != null && said[0].contains("protected"),
            "where a claim protects it, nothing comes out (" + said[0] + ")");
        said[0] = null;
        ev = hit(t1);
        SelfTest.check(ev.isCanceled() && lt.looting.blocks == 0 && lt.looting.paid == 0 && !lt.xpBottles && lt.xpPaid == null && !lt.noDamaged
            && lt.filterPaid == null && invCount(Item.getItemFromBlock(Blocks.lapis_block)) == 3
            && invCount(Item.getItemFromBlock(Blocks.gold_block)) == 1 && invCount(emerald()) == 1,
            "a sneaking hit with an empty hand gives back 3 lapis, the gold and the emerald block and switches those off ("
                + said[0] + ")");
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null; // empty hand again
        said[0] = null;
        hit(t1);
        SelfTest.check(said[0] != null && said[0].contains("no blocks"), "an empty one says so (" + said[0] + ")");
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
    }

    /**
     * Cursed earth: one per click for Speed, one under it counts as one, and Extra Utilities' own spawner code (cursed
     * earth under any spawner, every 5 s) never spawns real mobs from a loot spawner.
     */
    void speed() {
        Item cursed = LootSpawners.itemOf(TweaksConfig.lootSpawnerSpeedItem);
        SelfTest.check(cursed != null, "Extra Utilities' cursed earth is the Speed item (" + TweaksConfig.lootSpawnerSpeedItem + ")");
        if (cursed == null) return;
        Block earth = Block.getBlockFromItem(cursed);
        boolean belowWas = TweaksConfig.lootSpawnerSpeedBelow;
        TweaksConfig.lootSpawnerSpeedBelow = true;
        LootSpawners.Kind sk = LootSpawners.Kind.SPEED;
        SelfTest.check(sk.level(0) == 0 && sk.level(1) == 1 && sk.level(2) == 1 && sk.level(3) == 2 && sk.level(5) == 2
            && sk.level(6) == 3 && sk.level(60) == 3, "1, 3 and 6 cursed earth give Speed I, II and III");
        int[] p1 = { wx + 14, y, bz + 6 }, p2 = { wx + 2, y, bz }, plainAt = { wx + 10, y, bz };
        LootSpawnerTile sp = lootSpawner(p1, "Zombie");
        sp.minDelay = sp.maxDelay = 400;
        int plain = LootSpawners.nextDelay(sp, world);
        player.setSneaking(true);
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, new ItemStack(cursed, 10, 0));
        sp.timer = 400;
        said[0] = null;
        PlayerInteractEvent ev = click(p1);
        SelfTest.check(ev.isCanceled() && sp.speed.blocks == 1 && held() == 9 && said[0] != null
            && said[0].startsWith("Speed I now, 2× as fast") && sp.timer == 200 && plain == 400
            && LootSpawners.nextDelay(sp, world) == 200, "one cursed earth gives Speed I: batches twice as often, the one "
                + "counting down too (" + said[0] + "; " + sp.timer + ", " + LootSpawners.nextDelay(sp, world) + " of " + plain + ")");
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        said[0] = null;
        try {
            click(p1);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(sp.speed.blocks == 1 && held() == 9 && said[0] != null && said[0].contains("protected"),
            "where a claim protects it, none goes in (" + said[0] + ")");
        click(p1);
        said[0] = null;
        click(p1);
        String second = said[0];
        click(p1);
        click(p1);
        said[0] = null;
        click(p1);
        String third = said[0];
        said[0] = null;
        click(p1);
        SelfTest.check(sp.speed.blocks == 6 && sp.speed.paid == 6 && held() == 4 && second != null
            && second.startsWith("Speed II now, 3× as fast") && second.contains("3 more for Speed III") && third != null
            && third.startsWith("Speed III now, 4× as fast") && said[0] != null && said[0].contains("the most")
            && LootSpawners.nextDelay(sp, world) == 100, "3 give Speed II, 6 Speed III (4 times as often), and no more go in ("
                + second + " / " + third + " / " + said[0] + ")");
        player.inventory.setInventorySlotContents(0, null);
        said[0] = null;
        click(p1);
        NBTTagCompound sent = packetNbt((S35PacketUpdateTileEntity) sp.getDescriptionPacket());
        NBTTagCompound tag = new NBTTagCompound();
        sp.writeToNBT(tag);
        LootSpawnerTile copy = new LootSpawnerTile();
        copy.readFromNBT(tag);
        boolean lore = false;
        NBTTagList lines = LootSpawners.toItem(sp)
            .getTagCompound()
            .getCompoundTag("display")
            .getTagList("Lore", 8);
        for (int i = 0; i < lines.tagCount(); i++) lore |= lines.getStringTagAt(i)
            .contains("Speed III, 4× as fast");
        SelfTest.check(said[0] != null && said[0].contains("Speed III, 4× as fast (6 in it; the most)") && said[0].contains("Every 5–5 s")
            && sent != null && sent.getInteger("ValierSpeed") == 3
            && copy.speed.blocks == 6 && copy.speed.paid == 6 && copy.speed.item != null && lore,
            "its status, players' name for it and the wrench's item say Speed III, and it is saved (" + said[0] + " / "
                + sent + ")");
        said[0] = null;
        hit(p1);
        SelfTest.check(sp.speed.blocks == 0 && sp.speed.paid == 0 && invCount(cursed) == 6 && LootSpawners.nextDelay(sp, world) == plain
            && said[0] != null && said[0].startsWith("Speed switched off"),
            "a sneaking hit with an empty hand gives the 6 back, at its normal speed again (" + said[0] + ")");

        // One under it counts as one.
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        world.setBlock(p1[0], y - 1, p1[2], earth, 0, 3);
        boolean under = LootSpawners.speedBelow(sp) && sp.speedLevel() == 1 && LootSpawners.nextDelay(sp, world) == plain / 2;
        TweaksConfig.lootSpawnerSpeedBelow = false;
        boolean notOff = sp.speedLevel() == 0 && LootSpawners.nextDelay(sp, world) == plain;
        TweaksConfig.lootSpawnerSpeedBelow = true;
        player.inventory.setInventorySlotContents(0, new ItemStack(cursed, 2, 0));
        said[0] = null;
        click(p1);
        SelfTest.check(under && notOff && sp.speed.blocks == 1 && said[0] != null && said[0].startsWith("Still Speed I")
            && said[0].contains("(1 in it and one under it)") && said[0].contains("1 more for Speed II"),
            "cursed earth under it counts as one (not with lootSpawnerSpeedBelow off), and one more in it is still Speed I ("
                + said[0] + ")");

        // On a plain spawner, only a hint (and no cursed earth placed).
        TileEntity v = te(plainAt);
        said[0] = null;
        ev = click(plainAt);
        SelfTest.check(ev.isCanceled() && v != null && v.getClass() == TileEntityMobSpawner.class && te(plainAt) == v && held() == 1
            && said[0] != null && said[0].contains("loot spawner first") && said[0].contains("for Speed"),
            "on a plain spawner cursed earth only says how (" + said[0] + ")");
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;

        // Extra Utilities runs the vanilla countdown of any spawner on cursed earth, 100 ticks off every 5 s, and at 0
        // spawns the mobs itself.
        TileEntityMobSpawner pv = spawner(p2, "Villager");
        world.setBlock(p2[0], y - 1, p2[2], earth, 0, 3);
        pv.func_145881_a().spawnDelay = 300;
        earth.updateTick(world, p2[0], y - 1, p2[2], world.rand);
        int plainCount = pv.func_145881_a().spawnDelay;
        pv.func_145881_a().spawnDelay = 0;
        int plainBefore = mobsNear(p2);
        earth.updateTick(world, p2[0], y - 1, p2[2], world.rand);
        EntityVillager probe = new EntityVillager(world);
        probe.setLocationAndAngles(p2[0] + 0.5, y + 1, p2[2] + 0.5, 0, 0);
        SelfTest.log("cursed earth on a plain villager spawner at 0: " + (mobsNear(p2) - plainBefore) + " spawned, countdown "
            + pv.func_145881_a().spawnDelay + " after; a villager could spawn above it: " + probe.getCanSpawnHere());
        world.setBlockToAir(p2[0], y, p2[2]);
        LootSpawnerTile vl = lootSpawner(p2, "Villager");
        vl.logic().spawnDelay = 0; // as if Extra Utilities had counted it all the way down
        vl.updateEntity(); // one tick of its own
        int before = mobsNear(p2);
        for (int i = 0; i < 5; i++) earth.updateTick(world, p2[0], y - 1, p2[2], world.rand);
        int counted = vl.logic().spawnDelay, after = mobsNear(p2);
        vl.updateEntity();
        SelfTest.check(plainCount == 200 && counted == Short.MAX_VALUE - 500 && after == before
            && vl.logic().spawnDelay == Short.MAX_VALUE, "on cursed earth Extra Utilities counts a plain spawner down 100 at a "
                + "time (300 -> " + plainCount + "); a loot spawner's count is full again each tick (" + counted + " after 5 of "
                + "its runs, then " + vl.logic().spawnDelay + "), so it never reaches 0 and spawns no real mob (" + (after - before) + ")");
        for (Object o : world.getEntitiesWithinAABB(EntityVillager.class,
            AxisAlignedBB.getBoundingBox(p2[0] - 10, p2[1] - 6, p2[2] - 10, p2[0] + 11, p2[1] + 6, p2[2] + 11)))
            ((Entity) o).setDead();
        world.setBlockToAir(p2[0], y, p2[2]);
        world.setBlockToAir(p2[0], y - 1, p2[2]);
        TweaksConfig.lootSpawnerSpeedBelow = belowWas;
    }

    PlayerInteractEvent hit(int[] p) {
        return ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, p[0], p[1], p[2], 1,
            world);
    }

    int invCount(Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    int xpOf(String type, int n) {
        int[] xp = new int[1];
        MobLoot.Candidate c = new MobLoot.Candidate(type, null, true, 1);
        for (int i = 0; i < n; i++) MobLoot.roll(world, bx + 8.5, y + 1, bz + 8.5, c, xp, 0);
        return xp[0];
    }

    int fleshOf(int looting, int n) {
        int flesh = 0;
        MobLoot.Candidate c = new MobLoot.Candidate("Zombie", null, true, 1);
        for (int i = 0; i < n; i++) {
            for (ItemStack st : MobLoot.roll(world, bx + 8.5, y + 1, bz + 8.5, c, null, looting))
                if (st.getItem() == Items.rotten_flesh) flesh += st.stackSize;
        }
        return flesh;
    }

    int mobsNear(int[] p) {
        return world.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class,
            AxisAlignedBB.getBoundingBox(p[0] - 8, p[1] - 4, p[2] - 8, p[0] + 9, p[1] + 5, p[2] + 9))
            .size();
    }

    /** Whether the spawner at a break event's spot counts as active (a player near enough) as the event comes. */
    public static final class ActiveAtBreak {

        Boolean active;

        @SubscribeEvent
        public void see(BlockEvent.BreakEvent event) {
            TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
            if (te instanceof TileEntityMobSpawner) active = ((TileEntityMobSpawner) te).func_145881_a()
                .isActivated();
        }
    }

    /** A steel GregTech tool. By reflection: the tool class's other interfaces (Forestry, Railcraft) aren't compiled against. */
    static ItemStack gtTool(IDMetaTool01 id) {
        try {
            Object tools = Class.forName("gregtech.common.items.MetaGeneratedTool01")
                .getField("INSTANCE")
                .get(null);
            return (ItemStack) tools.getClass()
                .getMethod("getToolWithStats", int.class, int.class, Materials.class, Materials.class, long[].class)
                .invoke(tools, id.ID, 1, Materials.Steel, Materials.Steel, null);
        } catch (Throwable e) {
            ValierTweaks.LOG.warn("[ValierTweaks SelfTest] couldn't make a GregTech tool", e);
            return null;
        }
    }

    /** GregTech's tools ask the player's held item, so it goes in the hand first. */
    boolean wrenchy(ItemStack stack, int[] p) {
        player.inventory.setInventorySlotContents(0, stack);
        return LootSpawners.isWrench(stack, player, p[0], p[1], p[2]);
    }

    /** Takes the first spawner item out of the player's inventory. */
    ItemStack takeSpawnerItem() {
        Item spawnerItem = Item.getItemFromBlock(Blocks.mob_spawner), ours = Item.getItemFromBlock(ModBlocks.lootSpawner);
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack s = player.inventory.mainInventory[i];
            if (s != null && (s.getItem() == spawnerItem || s.getItem() == ours) && s.stackSize > 0) {
                player.inventory.mainInventory[i] = null;
                return s;
            }
        }
        return null;
    }

    /** Places the item on a stone block below {@code p}, held in the hand, the way a player does. */
    TileEntity placeItem(ItemStack item, int[] p) {
        world.setBlock(p[0], p[1] - 1, p[2], Blocks.stone);
        player.inventory.setInventorySlotContents(0, item);
        item.tryPlaceItemIntoWorld(player, world, p[0], p[1] - 1, p[2], 1, 0.5F, 1.0F, 0.5F);
        player.inventory.setInventorySlotContents(0, null);
        return te(p);
    }

    public static final class DenyPlaces {

        @SubscribeEvent
        public void protect(BlockEvent.PlaceEvent event) {
            event.setCanceled(true);
        }
    }

    // ---- saving ---------------------------------------------------------------------------------------------------

    void saving() {
        TileEntity t7 = te(s7p);
        NBTTagCompound tag = new NBTTagCompound();
        t7.writeToNBT(tag);
        SelfTest.check((ValierTweaks.MODID + ".LootSpawner").equals(tag.getString("id")) && tag.getCompoundTag("SpawnData")
            .getCompoundTag(LootSpawnerTile.MARK)
            .getTagList("Items", 10)
            .tagCount() == 1, "it saves under its own id, with what it keeps inside its spawn data (" + tag.getString("id") + ")");
        TileEntity loaded = TileEntity.createAndLoadEntity(tag);
        SelfTest.check(loaded instanceof LootSpawnerTile && ((LootSpawnerTile) loaded).stacksHeld() == 1,
            "and loads as a loot spawner tile again");

        // A loot spawner as 1.x saved it: a marked vanilla spawner ("MobSpawner") on the vanilla block.
        NBTTagCompound legacy = (NBTTagCompound) tag.copy();
        legacy.setString("id", "MobSpawner");
        int[] s8p = { bx + 2, y, bz + 10 };
        TileEntityMobSpawner v8 = spawner(s8p, "Pig");
        v8.readFromNBT(movedTo(legacy, s8p));
        int swapped = LootSpawners.scan();
        TileEntity t8 = te(s8p);
        SelfTest.check(swapped >= 1 && t8 instanceof LootSpawnerTile && world.getBlock(s8p[0], y, s8p[2]) == ModBlocks.lootSpawner
            && ((LootSpawnerTile) t8).stacksHeld() == 1 && "Cow".equals(((LootSpawnerTile) t8).candidates.get(0).type),
            "the old kind (a marked vanilla spawner, as 1.x saved it or a dolly moved it) becomes the block (the 2 s scan), "
                + "still holding its loot");

        int[] s9p = { bx + 6, y, bz + 10 };
        TileEntityMobSpawner v9 = spawner(s9p, "Pig");
        v9.readFromNBT(movedTo(legacy, s9p));
        Chunk chunk = world.getChunkFromBlockCoords(s9p[0], s9p[2]);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, new NBTTagCompound()));
        Object t9 = chunk.chunkTileEntityMap.get(new ChunkPosition(s9p[0] & 15, y, s9p[2] & 15));
        SelfTest.check(t9 instanceof LootSpawnerTile && v9.isInvalid() && world.getBlock(s9p[0], y, s9p[2]) == Blocks.mob_spawner,
            "as its chunk loads, an old one gets its loot spawner tile first (the block changes at the end of the tick)");
        if (t9 instanceof LootSpawnerTile) world.setTileEntity(s9p[0], y, s9p[2], (TileEntity) t9);
        LootSpawners.convertPending();
        TileEntity t9b = te(s9p);
        SelfTest.check(world.getBlock(s9p[0], y, s9p[2]) == ModBlocks.lootSpawner && t9b instanceof LootSpawnerTile
            && ((LootSpawnerTile) t9b).stacksHeld() == 1 && "Cow".equals(((LootSpawnerTile) t9b).candidates.get(0).type),
            "and then it is the block, with its loot and mob");

        S35PacketUpdateTileEntity packet = (S35PacketUpdateTileEntity) s4.getDescriptionPacket();
        NBTTagCompound sent = packetNbt(packet); // its getter is client-only
        SelfTest.check(sent != null && !sent.hasKey("SpawnPotentials") && !sent.getCompoundTag("SpawnData")
            .hasKey(LootSpawnerTile.MARK) && sent.hasKey("EntityId") && !sent.getString("EntityId")
                .contains("Valier"),
            "players get a plain spawner packet with the real mob, without the loot data (" + sent + ")");
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

        // Explosions, the Wither and survival players can't break it; only a wrench (or creative) takes it away.
        int[] s12 = { bx + 2, y, bz + 14 };
        LootSpawnerTile l12 = lootSpawner(s12, "Cow");
        world.createExplosion(null, s12[0] + 0.5, y + 1.5, s12[2] + 0.5, 4.0F, true);
        player.capabilities.isCreativeMode = false;
        float hardness = ModBlocks.lootSpawner.getPlayerRelativeBlockHardness(player, world, s12[0], y, s12[2]);
        SelfTest.check(te(s12) == l12 && world.getBlock(s12[0], y, s12[2]) == ModBlocks.lootSpawner && hardness == 0.0F
            && !ModBlocks.lootSpawner.canEntityDestroy(world, s12[0], y, s12[2], null),
            "an explosion next to it leaves it, the Wither can't break it, a survival player can't mine it (" + hardness + ")");

        // Switched off: marked spawners become plain spawners again, dressed as before, what they kept dropped on top.
        int[] s13 = { bx + 6, y, bz + 14 }, s14 = { bx + 10, y, bz + 14 };
        LootSpawnerTile l13 = lootSpawner(s13, "Cow");
        l13.addToBuffer(new ItemStack(Items.leather, 2));
        l13.noDamaged = true;
        l13.filterPaid = new ItemStack(Blocks.emerald_block);
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
        SelfTest.check(itemsNear(s14, Items.leather) == 2 && itemsNear(s14, emerald()) == 1,
            "and what it kept drops on top of it (" + itemsNear(s14, Items.leather) + " leather, "
                + itemsNear(s14, emerald()) + " emerald block)");
        killItems(s14);
        TileEntity t13 = te(s13);
        SelfTest.check(world.getBlock(s13[0], y, s13[2]) == Blocks.mob_spawner && t13 != null
            && t13.getClass() == TileEntityMobSpawner.class && !LootSpawnerTile.isMarked((TileEntityMobSpawner) t13)
            && itemsNear(s13, Items.leather) == 2 && itemsNear(s13, emerald()) == 1,
            "switched off, a Valier Loot Spawner block is a plain vanilla cow spawner again too, what it kept on top ("
                + itemsNear(s13, Items.leather) + " leather)");
        killItems(s13);
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
        s6.noDamaged = true;
        s6.filterPaid = new ItemStack(Blocks.emerald_block);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(s6p[0], y, s6p[2], world, ModBlocks.lootSpawner, 0, player));
        world.setBlockToAir(s6p[0], y, s6p[2]);
        TileEntity t7 = te(s7p);
        if (t7 instanceof LootSpawnerTile) ((LootSpawnerTile) t7).addToBuffer(new ItemStack(Items.leather, 3));
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(s7p[0], y, s7p[2], world, ModBlocks.lootSpawner, 0, player));

        // Unloaded and loaded again.
        s10p = new int[] { rx + 4, y, bz + 4 };
        LootSpawnerTile s10 = lootSpawner(s10p, "Cow");
        s10.addToBuffer(new ItemStack(Items.leather, 3));
        world.theChunkProviderServer.unloadChunksIfNotNearSpawn(rx >> 4, bz >> 4);

        // Left running: its chest fills by itself within two cycles. A fresh one: settings() switched loot spawners off,
        // which made the first one a plain spawner for good.
        s4 = lootSpawner(s4p, "Cow");
        clearInv((IInventory) world.getTileEntity(s4p[0], y - 1, s4p[2]));
        s4.timer = 25;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        ticks++;
        try {
            if (ticks == 3) {
                SelfTest.check(itemsNear(s6p, Items.leather) == 7 && itemsNear(s6p, emerald()) == 1,
                    "a broken loot spawner drops what it held, and the emerald block it took ("
                        + itemsNear(s6p, Items.leather) + " leather, " + itemsNear(s6p, emerald()) + " emerald block)");
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
                SelfTest.check(count(chest, Items.beef) > 0 && s4.rolls >= 1 && te(s4p) == s4,
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
        TweaksConfig.lootSpawnerInfernalMobs = infernalWas;
        TweaksConfig.lootSpawnerSpecialMobs = specialWas;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        clear(bx, bz);
        clear(rx, bz);
        clear(wx, bz);
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
