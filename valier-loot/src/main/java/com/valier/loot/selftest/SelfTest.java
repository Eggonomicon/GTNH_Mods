package com.valier.loot.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.mojang.authlib.GameProfile;
import com.valier.loot.BackupImport;
import com.valier.loot.LootConfig;
import com.valier.loot.LootData;
import com.valier.loot.LootEvents;
import com.valier.loot.RandomLoot;
import com.valier.loot.ValierLoot;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Test-server-only checks, enabled with {@code -Dvalierloot.selftest=true}. They need three boots of a flat test world,
 * because the real "chunk loads from disk" path only runs after a restart:
 * <ol>
 * <li>{@code lootchest selftest prepare}, then {@code lootchest selftest setup}: builds the test chests far from
 * spawn, then save and stop;</li>
 * <li>{@code lootchest selftest gen}, then {@code lootchest selftest run}: loads those chunks, checks which chests became
 * loot, opens them as fake players, tries to break and build around them, then save and stop;</li>
 * <li>{@code lootchest selftest verify}: checks everything from boot 2 was saved.</li>
 * </ol>
 * Each check logs one {@code [ValierLoot SelfTest] PASS/FAIL} line, then a SUMMARY line.
 */
public final class SelfTest {

    public static final boolean ENABLED = Boolean.getBoolean("valierloot.selftest");

    private static final int Y = 40;
    private static final int[] A = { 3000, Y, 3000 }; // single loot chest
    private static final int[] B1 = { 3010, Y, 3000 }; // double loot chest, west half
    private static final int[] B2 = { 3011, Y, 3000 }; // double loot chest, east half
    private static final int[] C = { 3020, Y, 3000 }; // placed by a (fake) player: never loot
    private static final int[] E = { 3030, Y, 3000 }; // empty: not loot
    private static final int[] H = { 3040, Y, 3000 }; // loot chest that gets a hopper placed next to it
    private static final int[] K = { 3050, Y, 3000 }; // loot chest broken in creative
    private static final int[] L = { 3060, Y, 3000 }; // loot chest nobody opens: locked anyway
    private static final int[] M1 = { 3070, Y, 3000 }; // double chest whose west half has loot...
    private static final int[] M2 = { 3071, Y, 3000 }; // ...and whose east half is empty
    private static final int[] Q = { 3080, Y, 3000 }; // loot chest that a full chest is pushed next to...
    private static final int[] Q_DOLLY = { 3081, Y, 3000 }; // ...without any event, as a dolly would
    private static final int[] S = { 3090, Y, 3000 }; // loot chest a player breaks by sneaking
    private static final int[] T = { 3100, Y, 3000 }; // loot chest a Matter Manipulator removes
    private static final int[] R1 = { 3110, Y, 3000 }; // random loot, bonus mode
    private static final int[] R2 = { 3120, Y, 3000 }; // random loot, replace mode
    private static final int[] D = { 3200, Y, 3000 }; // in a chunk players have spent time near: not loot
    // Chests from "before the mod" in D's visited chunk, for /lootchest import:
    private static final int[] I1 = { 3203, Y, 3000 }; // partly looted since the backup: restored
    private static final int[] I2 = { 3206, Y, 3000 }; // has an item that wasn't in the loot: skipped
    private static final int[] I3 = { 3209, Y, 3000 }; // untouched: restored
    private static final int[] I4 = { 3203, Y, 3005 }; // placed after the backup: skipped
    private static final int[] J1 = { 3212, Y, 3000 }; // double chest with loot...
    private static final int[] J2 = { 3213, Y, 3000 }; // ...and an empty half: restored together
    private static final String IMPORT_DIR = "valierloot-test-backup";
    private static final int GEN_CHUNK = 400; // the test world generator puts a chest in this chunk
    private static final int[] G = { GEN_CHUNK * 16 + 8, Y, GEN_CHUNK * 16 + 8 };
    private static final int POP_CHUNK = 300; // the populate check below works around this chunk
    private static final int[] P_OLD = { POP_CHUNK * 16 + 3, Y, POP_CHUNK * 16 + 3 };
    private static final int[] P_NEW = { POP_CHUNK * 16 + 20, Y, POP_CHUNK * 16 + 5 }; // in the next chunk east

    private static final UUID U1 = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID U2 = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID BUILDER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID CREATIVE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID WALKER = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {}

    /** A world generator that puts one chest of loot in chunk (GEN_CHUNK, GEN_CHUNK), like a structure would. */
    public static void registerWorldGenerator() {
        GameRegistry.registerWorldGenerator((random, chunkX, chunkZ, world, generator, provider) -> {
            if (world.provider.dimensionId != 0 || chunkX != GEN_CHUNK || chunkZ != GEN_CHUNK) return;
            world.setBlock(G[0], G[1], G[2], Blocks.chest, 0, 2);
            TileEntity te = world.getTileEntity(G[0], G[1], G[2]);
            if (te instanceof TileEntityChest) {
                ((TileEntityChest) te).setInventorySlotContents(0, new ItemStack(Items.gold_nugget, 11));
            }
        }, 0);
    }

    public static void run(ICommandSender sender, String[] args) {
        passed = 0;
        failed = 0;
        failures.clear();
        WorldServer world = DimensionManager.getWorld(0);
        String step = args.length > 1 ? args[1] : "";
        LootConfig.randomMode = LootConfig.RandomMode.OFF;
        try {
            switch (step) {
                case "prepare":
                    prepare(world);
                    break;
                case "setup":
                    setup(world);
                    break;
                case "gen":
                    loadChunks(world, GEN_CHUNK - 1, GEN_CHUNK + 2, GEN_CHUNK - 1, GEN_CHUNK + 2);
                    log("generated the chunks around (" + GEN_CHUNK + ", " + GEN_CHUNK + ")");
                    return;
                case "tables":
                    // The real tables the default pool rules use roll items in this pack.
                    for (String table : new String[] { "chest1", "chest2", "chest3", "chest4", "dungeonChest" }) {
                        List<ItemStack> roll = RandomLoot.roll(
                            LootConfig.parseRules(new String[] { "*;0;" + table })
                                .get(0),
                            new Random());
                        StringBuilder items = new StringBuilder();
                        for (ItemStack s : roll) items.append(s.stackSize)
                            .append("x ")
                            .append(s.getDisplayName())
                            .append("; ");
                        check(!roll.isEmpty(), "table " + table + " rolls " + roll.size() + " stacks: " + items);
                    }
                    break;
                case "run":
                    runChecks(world, sender);
                    break;
                case "run2": {
                    LootData data = LootData.get(world);
                    load(world, T);
                    check(data.getOpened(pos(T)) != null, "T is still loot until the Manipulator removes it");
                    world.setBlockToAir(T[0], T[1], T[2]); // as the Manipulator does, with no event
                    break;
                }
                case "verify":
                    verify(world);
                    break;
                default:
                    log("usage: lootchest selftest <prepare|setup|gen|run|verify>");
                    return;
            }
        } catch (Throwable t) {
            ValierLoot.LOG.error("[ValierLoot SelfTest] step " + step + " threw", t);
            check(false, "step " + step + " finished without an exception (" + t + ")");
        }
        log(
            "SUMMARY " + step
                + " "
                + (failed == 0 ? "PASSED" : "FAILED")
                + " ("
                + passed
                + " passed, "
                + failed
                + " failed)"
                + (failures.isEmpty() ? "" : ": " + failures));
    }

    // ---- Boot 1 ----

    private static void prepare(WorldServer world) {
        // Generate and populate the area first, so nothing populates while the test chests are being placed.
        loadChunks(world, 185, 196, 185, 189);
        loadChunks(world, 198, 202, 185, 189);
        log("prepared the test area");
    }

    private static void setup(WorldServer world) {
        LootData data = LootData.get(world);
        for (int[] p : new int[][] { A, B1, C, E, H, K, L, M1, Q, S, T, R1, R2, D }) load(world, p);
        placeChest(world, A, new ItemStack(Items.diamond, 5), new ItemStack(Items.bread, 3));
        placeChest(world, B1, new ItemStack(Items.emerald, 1));
        placeChest(world, B2);
        chest(world, B2).setInventorySlotContents(26, new ItemStack(Items.gold_ingot, 2));
        placeChest(world, E);
        placeChest(world, H, new ItemStack(Items.coal, 7));
        placeChest(world, K, new ItemStack(Items.apple, 1));
        placeChest(world, L, new ItemStack(Items.glowstone_dust, 6));
        placeChest(world, M1, new ItemStack(Items.ender_pearl, 2));
        placeChest(world, M2);
        placeChest(world, Q, new ItemStack(Items.slime_ball, 4));
        placeChest(world, S, new ItemStack(Items.iron_ingot, 5), new ItemStack(Items.gold_ingot, 3));
        placeChest(world, T, new ItemStack(Items.feather, 7));
        placeChest(world, R1, new ItemStack(Items.stick, 1));
        placeChest(world, R2, new ItemStack(Items.stick, 1));
        placeChest(world, D, new ItemStack(Items.redstone, 9));
        placeChest(world, I1, new ItemStack(Items.diamond, 4), new ItemStack(Items.coal, 6));
        placeChest(world, I2, new ItemStack(Items.emerald, 3));
        placeChest(world, I3, new ItemStack(Items.iron_ingot, 2));
        placeChest(world, J1, new ItemStack(Items.gold_ingot, 5));
        placeChest(world, J2);
        Chunk dChunk = load(world, D);
        dChunk.inhabitedTime = 100000L;
        dChunk.setChunkModified();

        // C is placed the way a player places a chest, so it fires the place event.
        world.setBlock(C[0], C[1] - 1, C[2], Blocks.stone);
        FakePlayer builder = fake(world, BUILDER, "ValierLootBuilder", false);
        boolean placed = new ItemStack(Blocks.chest)
            .tryPlaceItemIntoWorld(builder, world, C[0], C[1] - 1, C[2], 1, 0.5F, 1.0F, 0.5F);
        check(placed && world.getBlock(C[0], C[1], C[2]) == Blocks.chest, "player-style placement made chest C");
        if (chest(world, C) != null) chest(world, C).setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 4));

        check(data.isIgnored(pos(C)), "C (placed by a player) is recorded as a player's chest");
        for (int[] p : new int[][] { A, B1, B2, E, H, K, L, M1, M2, Q, S, T, R1, R2, D }) {
            check(!data.isLoot(pos(p)), "chest at " + str(p) + " is not loot yet (its chunk has not reloaded)");
        }
    }

    // ---- Boot 2 ----

    private static void runChecks(WorldServer world, ICommandSender sender) {
        LootData data = LootData.get(world);
        check(
            LootData.unpackX(LootData.pack(-30000, 5, -7)) == -30000 && LootData.unpackY(LootData.pack(-30000, 5, -7)) == 5
                && LootData.unpackZ(LootData.pack(-30000, 5, -7)) == -7,
            "positions pack and unpack (negative coordinates too)");

        // Loading the chunks from disk is what registers worldgen chests.
        for (int[] p : new int[][] { A, B1, C, E, H, K, L, M1, Q, S, T, R1, R2, D }) load(world, p);

        check(data.isPending(pos(A)), "A registered as loot when its untouched chunk loaded");
        check(data.isPending(pos(B1)) && data.isPending(pos(B2)), "both halves of double chest B registered");
        check(!data.isLoot(pos(C)), "C (placed by a player) did not become loot");
        check(!data.isLoot(pos(E)), "E (empty) did not become loot");
        check(!data.isLoot(pos(D)), "D (in a chunk players have been near) did not become loot");
        check(data.isPending(pos(H)) && data.isPending(pos(K)) && data.isPending(pos(L)), "H, K and L registered");
        check(data.isPending(pos(M1)) && !data.isLoot(pos(M2)), "M1 registered; empty M2 not on its own");
        load(world, G);
        check(data.getOpened(pos(G)) != null && isEmpty(chest(world, G)),
            "G (made by a structure generator in a new chunk) became loot, locked away at once");

        // Player 1 opens A and takes the diamonds; player 2 still gets them.
        IInventory a1 = LootEvents.buildView(U1, world, A[0], A[1], A[2], data);
        check(a1.getSizeInventory() == 27, "A opens as a 27-slot chest");
        check(is(a1.getStackInSlot(0), Items.diamond, 5) && is(a1.getStackInSlot(1), Items.bread, 3), "player 1 sees A's loot");
        check(isEmpty(chest(world, A)) && !data.isPending(pos(A)) && data.getOpened(pos(A)) != null,
            "after the first open, A's loot moved out of the block");
        ItemStack taken = a1.decrStackSize(0, 5);
        check(is(taken, Items.diamond, 5) && a1.getStackInSlot(0) == null, "player 1 takes the diamonds");
        IInventory a2 = LootEvents.buildView(U2, world, A[0], A[1], A[2], data);
        check(is(a2.getStackInSlot(0), Items.diamond, 5), "player 2 still gets the diamonds");
        IInventory a1again = LootEvents.buildView(U1, world, A[0], A[1], A[2], data);
        check(a1again.getStackInSlot(0) == null && is(a1again.getStackInSlot(1), Items.bread, 3),
            "player 1's copy remembers what they took");

        // Double chest: west half first, as vanilla shows it.
        IInventory b = LootEvents.buildView(U1, world, B2[0], B2[1], B2[2], data);
        check(b.getSizeInventory() == 54, "B opens as a 54-slot double chest");
        check(is(b.getStackInSlot(0), Items.emerald, 1) && is(b.getStackInSlot(53), Items.gold_ingot, 2),
            "B shows the west half first");
        check(data.getOpened(pos(B1)) != null && data.getOpened(pos(B2)) != null, "opening B opened both halves");

        // Right-clicks: a machine (fake player) is refused on a loot chest; ordinary chests are left alone.
        FakePlayer machine = fake(world, U2, "ValierLootMachine", false);
        PlayerInteractEvent click = ForgeEventFactory
            .onPlayerInteract(machine, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, A[0], A[1], A[2], 1, world);
        check(click.isCanceled(), "a machine right-clicking loot chest A is refused");
        click = ForgeEventFactory
            .onPlayerInteract(machine, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, C[0], C[1], C[2], 1, world);
        check(!click.isCanceled(), "right-clicking ordinary chest C is left to vanilla");
        machine.setSneaking(true);
        machine.inventory.currentItem = 0;
        machine.inventory.setInventorySlotContents(0, new ItemStack(Items.stick));
        click = ForgeEventFactory
            .onPlayerInteract(machine, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, A[0], A[1], A[2], 1, world);
        check(click.isCanceled(), "sneak-clicking loot chest A with a tool (dolly, wrench) is refused");
        machine.inventory.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone));
        click = ForgeEventFactory
            .onPlayerInteract(machine, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, A[0], A[1], A[2], 1, world);
        check(!click.isCanceled() && click.useBlock == Event.Result.DENY,
            "sneak-clicking A with a block may place it, but never opens the chest");
        machine.setSneaking(false);
        machine.inventory.setInventorySlotContents(0, null);

        // Breaking: refused in survival, allowed in creative.
        check(breakAttempt(world, A, machine).isCanceled(), "survival can't break loot chest A");
        FakePlayer creative = fake(world, CREATIVE, "ValierLootCreative", true);
        check(!breakAttempt(world, K, creative).isCanceled(), "creative can break loot chest K");
        world.setBlockToAir(K[0], K[1], K[2]); // finish the break the event only announced
        machine.setSneaking(true);
        check(breakAttempt(world, A, machine).isCanceled(), "a machine can't break a loot chest even sneaking");
        machine.setSneaking(false);

        // A player (not a machine) can break one by sneaking; their share of the loot drops (checked in verify).
        EntityPlayerMP walker = new EntityPlayerMP(
            MinecraftServer.getServer(),
            world,
            new GameProfile(WALKER, "ValierLootWalker"),
            new ItemInWorldManager(world));
        IInventory s1 = LootEvents.buildView(WALKER, world, S[0], S[1], S[2], data);
        check(is(s1.decrStackSize(0, 5), Items.iron_ingot, 5), "the walker takes the iron from their copy of S");
        check(breakAttempt(world, S, walker).isCanceled(), "a player who isn't sneaking can't break loot chest S");
        walker.setSneaking(true);
        check(!breakAttempt(world, S, walker).isCanceled(), "a sneaking player can break loot chest S");
        world.setBlockToAir(S[0], S[1], S[2]); // finish the break the event only announced
        walker.setSneaking(false);
        Item manipulator = GameRegistry.findItem("matter-manipulator", "itemMatterManipulator3");
        check(manipulator != null, "the Matter Manipulator item is there to test with");
        if (manipulator != null) {
            walker.inventory.currentItem = 0;
            walker.inventory.setInventorySlotContents(0, new ItemStack(manipulator));
            check(!breakAttempt(world, T, walker).isCanceled(),
                "a player's Matter Manipulator may remove loot chest T (removed for real in run2)");
            walker.inventory.setInventorySlotContents(0, null);
        }
        check(!breakAttempt(world, C, machine).isCanceled() && data.isIgnored(pos(C)),
            "breaking ordinary chest C is allowed (and C stays a player's chest)");

        // Building around loot chests.
        FakePlayer builder = fake(world, BUILDER, "ValierLootBuilder", false);
        new ItemStack(Blocks.chest).tryPlaceItemIntoWorld(builder, world, A[0], A[1], A[2], 5, 1.0F, 0.5F, 0.5F);
        check(world.getBlock(A[0] + 1, A[1], A[2]) == Blocks.air, "a chest can't be placed next to loot chest A");
        boolean hopper = new ItemStack(Blocks.hopper)
            .tryPlaceItemIntoWorld(builder, world, H[0], H[1], H[2], 5, 1.0F, 0.5F, 0.5F);
        check(hopper && world.getBlock(H[0] + 1, H[1], H[2]) == Blocks.hopper, "a hopper can be placed next to H");
        check(!data.isPending(pos(H)) && data.getOpened(pos(H)) != null && isEmpty(chest(world, H)),
            "placing the hopper locked H's loot away from it");
        IInventory h = LootEvents.buildView(U1, world, H[0], H[1], H[2], data);
        check(is(h.getStackInSlot(0), Items.coal, 7), "players still get H's loot");

        // The precise path for new chunks (the flat test world never posts PopulateChunkEvent.Pre, so post it here):
        // a chest that was already there is left alone, one that appears during population becomes loot.
        loadChunks(world, POP_CHUNK - 1, POP_CHUNK + 2, POP_CHUNK - 1, POP_CHUNK + 1);
        placeChest(world, P_OLD, new ItemStack(Items.flint, 2));
        MinecraftForge.EVENT_BUS.post(
            new PopulateChunkEvent.Pre(world.theChunkProviderServer, world, new Random(1), POP_CHUNK, POP_CHUNK, false));
        placeChest(world, P_NEW, new ItemStack(Items.blaze_rod, 3));
        ValierLoot.events.generator
            .generate(new Random(1), POP_CHUNK, POP_CHUNK, world, world.theChunkProviderServer, world.theChunkProviderServer);
        check(data.getOpened(pos(P_NEW)) != null && isEmpty(chest(world, P_NEW)),
            "a chest that appears while a chunk populates becomes loot, locked away at once");
        check(!data.isLoot(pos(P_OLD)), "a chest that was already there before population is left alone");

        // A full chest pushed next to a loot chest without any event (a dolly) is never adopted as its other half.
        placeChest(world, Q_DOLLY, new ItemStack(Items.diamond, 64));
        LootEvents.lock(world, data, Q[0], Q[1], Q[2]);
        check(data.getOpened(pos(Q)) != null && !data.isLoot(pos(Q_DOLLY))
            && is(chest(world, Q_DOLLY).getStackInSlot(0), Items.diamond, 64),
            "a full chest that was never found as loot is not adopted (its items stay its owner's)");
        world.setBlockToAir(Q_DOLLY[0], Q_DOLLY[1], Q_DOLLY[2]);

        // Restoring chests from before the mod, using the copy of their region file made after boot 1.
        load(world, I1);
        chest(world, I1).setInventorySlotContents(0, null); // a player took the diamonds
        chest(world, I2).setInventorySlotContents(5, new ItemStack(Blocks.dirt, 1)); // someone's storage
        placeChest(world, I4, new ItemStack(Items.apple, 2));
        check(!data.isLoot(pos(I1)) && !data.isLoot(pos(I3)), "chests in a visited chunk are not loot by themselves");
        java.io.File importDir = new java.io.File(IMPORT_DIR);
        java.io.File candidates = new java.io.File(importDir, "candidates.txt");
        try {
            java.nio.file.Files.write(candidates.toPath(), java.util.Arrays.asList(
                "0 " + str(I1), "0 " + str(I2), "0 " + str(I3), "0 " + str(I4), "0 " + str(J1), "0 " + str(J2),
                "0 " + str(A)));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        BackupImport.run(sender, candidates.getPath(), importDir.getPath(), false);
        check(!data.isLoot(pos(I1)) && !data.isLoot(pos(I3)) && is(chest(world, I1).getStackInSlot(1), Items.coal, 6),
            "an import dry run changes nothing");
        BackupImport.run(sender, candidates.getPath(), importDir.getPath(), true);
        IInventory i1 = LootEvents.buildView(U1, world, I1[0], I1[1], I1[2], data);
        check(data.getOpened(pos(I1)) != null && isEmpty(chest(world, I1)) && is(i1.getStackInSlot(0), Items.diamond, 4)
            && is(i1.getStackInSlot(1), Items.coal, 6), "a partly looted chest is restored with its full original loot");
        check(!data.isLoot(pos(I2)) && is(chest(world, I2).getStackInSlot(5), Item.getItemFromBlock(Blocks.dirt), 1),
            "a chest holding items that weren't in the loot is left alone");
        check(data.getOpened(pos(I3)) != null, "an untouched chest is restored");
        check(!data.isLoot(pos(I4)) && is(chest(world, I4).getStackInSlot(0), Items.apple, 2),
            "a chest that isn't in the backup is left alone");
        IInventory j = LootEvents.buildView(U1, world, J2[0], J2[1], J2[2], data);
        check(data.getOpened(pos(J1)) != null && data.getOpened(pos(J2)) != null && j.getSizeInventory() == 54
            && is(j.getStackInSlot(0), Items.gold_ingot, 5), "a double chest is restored as one, empty half included");

        // Pool rules: the largest distance reached, own dimension first, "*" for dimensions without rules.
        List<LootConfig.PoolRule> configured = LootConfig.poolRules;
        LootConfig.poolRules = LootConfig.parseRules(new String[] { "0;0;a", "0;1000;b", "*;0;c", "7;500;d;2-4" });
        check("a".equals(LootConfig.poolFor(0, 500).category) && "b".equals(LootConfig.poolFor(0, 1500).category)
            && "c".equals(LootConfig.poolFor(5, 0).category) && LootConfig.poolFor(7, 100) == null
            && "d".equals(LootConfig.poolFor(7, 600).category) && LootConfig.poolFor(7, 600).maxItems == 4,
            "pool rules pick the right table");

        // Rolls from a known weighted table: 3 picks of 2 emeralds each.
        ChestGenHooks.addItem("valierloot_test", new WeightedRandomChestContent(new ItemStack(Items.emerald), 2, 2, 1));
        LootConfig.poolRules = LootConfig.parseRules(new String[] { "*;0;valierloot_test;3-3" });
        LootConfig.randomMode = LootConfig.RandomMode.BONUS;
        IInventory r1 = LootEvents.buildView(U1, world, R1[0], R1[1], R1[2], data);
        check(count(r1, Items.stick) == 1 && count(r1, Items.emerald) == 6,
            "bonus mode: the chest's own loot plus the player's roll (found " + count(r1, Items.stick) + " stick, "
                + count(r1, Items.emerald) + " emeralds)");
        LootConfig.randomMode = LootConfig.RandomMode.REPLACE;
        IInventory r2 = LootEvents.buildView(U2, world, R2[0], R2[1], R2[2], data);
        check(count(r2, Items.stick) == 0 && count(r2, Items.emerald) == 6,
            "replace mode: only the player's roll (found " + count(r2, Items.stick) + " stick, "
                + count(r2, Items.emerald) + " emeralds)");
        LootConfig.randomMode = LootConfig.RandomMode.OFF;
        LootConfig.poolRules = configured;
    }

    // ---- Boot 3 ----

    private static void verify(WorldServer world) {
        LootData data = LootData.get(world);
        for (int[] p : new int[][] { A, B1, C, E, H, K, L, M1, Q, S, T, D }) load(world, p);
        load(world, G);
        check(!new java.io.File(world.getSaveHandler().getWorldDirectory(), "data/valierloot.dat").exists(),
            "loot is kept in the chunks themselves (no separate data file)");
        check(data.getOpened(pos(A)) != null && isEmpty(chest(world, A)), "A is still an opened loot chest, block empty");
        IInventory a1 = LootEvents.buildView(U1, world, A[0], A[1], A[2], data);
        IInventory a2 = LootEvents.buildView(U2, world, A[0], A[1], A[2], data);
        check(a1.getStackInSlot(0) == null && is(a1.getStackInSlot(1), Items.bread, 3),
            "player 1's copy of A was saved without the diamonds");
        check(is(a2.getStackInSlot(0), Items.diamond, 5), "player 2's copy of A was saved with the diamonds");
        check(data.getOpened(pos(B1)) != null && data.getOpened(pos(B2)) != null, "B is still opened");
        check(!data.isLoot(pos(C)) && !data.isLoot(pos(E)) && !data.isLoot(pos(D)) && !data.isLoot(pos(K)),
            "C, D, E and K are still not loot (K forgotten once its block was really gone)");
        check(data.getOpened(pos(H)) != null && isEmpty(chest(world, H)) && world.getBlock(H[0] + 1, H[1], H[2]) == Blocks.hopper,
            "H is still opened, its block empty, its hopper there");
        check(data.getOpened(pos(G)) != null, "G is still a loot chest");
        check(data.getOpened(pos(I1)) != null && data.getOpened(pos(I3)) != null && data.getOpened(pos(J2)) != null
            && !data.isLoot(pos(I2)) && is(chest(world, I2).getStackInSlot(5), Item.getItemFromBlock(Blocks.dirt), 1),
            "restored chests are still loot after a restart; the skipped one is untouched");
        int gold = 0, iron = 0;
        for (Object o : world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(S[0] - 3, 0, S[2] - 3, S[0] + 4, S[1] + 3, S[2] + 4))) {
            ItemStack stack = ((EntityItem) o).getEntityItem();
            if (stack.getItem() == Items.gold_ingot) gold += stack.stackSize;
            if (stack.getItem() == Items.iron_ingot) iron += stack.stackSize;
        }
        int feathers = 0;
        for (Object o : world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(T[0] - 3, 0, T[2] - 3, T[0] + 4, T[1] + 3, T[2] + 4))) {
            ItemStack stack = ((EntityItem) o).getEntityItem();
            if (stack.getItem() == Items.feather) feathers += stack.stackSize;
        }
        check(!data.isLoot(pos(T)) && feathers == 7,
            "after the Manipulator removed T, the player's full share dropped (found " + feathers + " feathers)");
        check(!data.isLoot(pos(S)) && gold == 3 && iron == 0,
            "breaking S dropped the breaker's share (3 gold, not the 5 iron they already took) and S is gone"
                + " (found " + gold + " gold, " + iron + " iron)");
        check(data.getOpened(pos(L)) != null && isEmpty(chest(world, L)),
            "L, which nobody opened, had its loot locked away at the end of the tick its chunk loaded");
        check(is(LootEvents.buildView(U2, world, L[0], L[1], L[2], data).getStackInSlot(0), Items.glowstone_dust, 6),
            "players get L's loot");
        check(data.getOpened(pos(M2)) != null, "empty half M2 became loot together with M1");
        IInventory m = LootEvents.buildView(U1, world, M2[0], M2[1], M2[2], data);
        check(m.getSizeInventory() == 54 && is(m.getStackInSlot(0), Items.ender_pearl, 2),
            "M opens as one double loot chest from either half");
        // A chest that joins a loot chest later (a dolly fires no events) is refused, never adopted.
        data.forget(pos(M2));
        FakePlayer machine = fake(world, U2, "ValierLootMachine", false);
        PlayerInteractEvent click = ForgeEventFactory
            .onPlayerInteract(machine, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, M2[0], M2[1], M2[2], 1, world);
        check(click.isCanceled() && !data.isLoot(pos(M2)), "a chest joined to a loot chest later is refused, not adopted");
    }

    // ---- Helpers ----

    private static void loadChunks(WorldServer world, int x0, int x1, int z0, int z1) {
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) world.theChunkProviderServer.loadChunk(cx, cz);
    }

    /**
     * Loads (or generates) the chunk holding p. Plain chunk lookups can't be used here: with no player near, they
     * may hand back an empty placeholder chunk instead of loading the real one.
     */
    private static Chunk load(WorldServer world, int[] p) {
        return world.theChunkProviderServer.loadChunk(p[0] >> 4, p[2] >> 4);
    }

    private static void placeChest(World world, int[] p, ItemStack... loot) {
        boolean set = world.setBlock(p[0], p[1], p[2], Blocks.chest, 0, 2);
        TileEntityChest chest = chest(world, p);
        if (chest == null) {
            TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
            throw new IllegalStateException(
                "no vanilla chest at " + str(p)
                    + ": setBlock returned "
                    + set
                    + ", block is "
                    + Block.blockRegistry.getNameForObject(world.getBlock(p[0], p[1], p[2]))
                    + ", tile entity is "
                    + (te == null ? "null" : te.getClass()
                        .getName()));
        }
        for (int i = 0; i < loot.length; i++) chest.setInventorySlotContents(i, loot[i]);
    }

    private static TileEntityChest chest(World world, int[] p) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        return LootEvents.isVanillaChest(te) ? (TileEntityChest) te : null;
    }

    private static BlockEvent.BreakEvent breakAttempt(World world, int[] p, EntityPlayer player) {
        Block block = world.getBlock(p[0], p[1], p[2]);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
            p[0],
            p[1],
            p[2],
            world,
            block,
            world.getBlockMetadata(p[0], p[1], p[2]),
            player);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    private static FakePlayer fake(WorldServer world, UUID id, String name, boolean creative) {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(id, name));
        player.capabilities.isCreativeMode = creative;
        return player;
    }

    private static boolean isEmpty(TileEntityChest chest) {
        if (chest == null) return false;
        for (int i = 0; i < chest.getSizeInventory(); i++) if (chest.getStackInSlot(i) != null) return false;
        return true;
    }

    private static int count(IInventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    private static boolean is(ItemStack stack, Item item, int count) {
        return stack != null && stack.getItem() == item && stack.stackSize == count;
    }

    private static long pos(int[] p) {
        return LootData.pack(p[0], p[1], p[2]);
    }

    private static String str(int[] p) {
        return p[0] + " " + p[1] + " " + p[2];
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            failures.add(what);
        }
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void log(String text) {
        ValierLoot.LOG.info("[ValierLoot SelfTest] " + text);
    }
}
