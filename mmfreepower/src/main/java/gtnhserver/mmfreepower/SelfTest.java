package gtnhserver.mmfreepower;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.mojang.authlib.GameProfile;
import com.recursive_pineapple.matter_manipulator.common.building.AbstractBuildable;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.ImmutableBlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.MMInventory;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBuild;
import com.recursive_pineapple.matter_manipulator.common.building.MMItemConsumer;
import com.recursive_pineapple.matter_manipulator.common.entities.EntityItemLarge;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

import com.sinthoras.visualprospecting.Config;
import com.sinthoras.visualprospecting.Utils;
import com.sinthoras.visualprospecting.VisualProspecting_API;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sinthoras.visualprospecting.database.veintypes.VeinType;
import com.sinthoras.visualprospecting.database.veintypes.VeinTypeCaching;
import com.sinthoras.visualprospecting.network.ProspectingNotification;
import com.sinthoras.visualprospecting.network.ProspectingRequest;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.enums.StoneType;
import gregtech.api.events.OreInteractEvent;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.common.ores.IOreAdapter;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;

/**
 * Test-server-only checks ({@code -Dmmfreepower.selftest=true}), run from the console with
 * {@code /mmfreepower selftest}: the Mk IV recipe, keeping its marker through the manipulator's own NBT rewrites,
 * which blocks are free, and taking items through the manipulator's real inventory code with a fake player. Each
 * check logs one {@code [MMFreePower SelfTest] PASS/FAIL} line, then a SUMMARY line.
 */
public class SelfTest extends CommandBase {

    public static final boolean ENABLED = Boolean.getBoolean("mmfreepower.selftest");

    private int passed, failed;
    private final List<String> failures = new ArrayList<>();

    @Override
    public String getCommandName() {
        return "mmfreepower";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mmfreepower selftest";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && "dump".equals(args[0])) {
            dump();
            return;
        }
        passed = 0;
        failed = 0;
        failures.clear();
        try {
            run();
        } catch (Throwable t) {
            MMFreePower.LOG.error("[MMFreePower SelfTest] threw", t);
            check(false, "finished without an exception (" + t + ")");
        }
        log(
            "SUMMARY " + (failed == 0 ? "PASSED" : "FAILED")
                + " ("
                + passed
                + " passed, "
                + failed
                + " failed)"
                + (failures.isEmpty() ? "" : ": " + failures));
    }

    private void run() throws Exception {
        WorldServer world = DimensionManager.getWorld(0);
        Item mk3 = GameRegistry.findItem("matter-manipulator", "itemMatterManipulator3");
        check(mk3 != null, "the MK III item exists");

        // A real MK III, with its state and some charge.
        ItemStack manipulator = new ItemStack(mk3);
        ItemMatterManipulator.setState(manipulator, ItemMatterManipulator.getState(manipulator));
        manipulator.getTagCompound()
            .setDouble("charge", 12345.0D);

        // The recipe: an MK III alone in the grid.
        InventoryCrafting grid = grid();
        grid.setInventorySlotContents(4, manipulator.copy());
        ItemStack mk4 = CraftingManager.getInstance()
            .findMatchingRecipe(grid, world);
        check(
            MkIV.isMkIV(mk4) && MkIV.NAME.equals(mk4.getDisplayName()) && mk4.isItemEnchanted(),
            "an MK III alone in a crafting grid makes the Valier MM Mk IV (" + (mk4 == null ? "nothing" : mk4.getDisplayName()) + ")");
        check(
            mk4 != null && mk4.getTagCompound()
                .getDouble("charge") == 12345.0D,
            "the Mk IV keeps the MK III's charge and settings");
        grid.setInventorySlotContents(0, new ItemStack(Items.stick));
        check(
            CraftingManager.getInstance()
                .findMatchingRecipe(grid, world) == null,
            "an MK III with anything else in the grid makes nothing");
        InventoryCrafting again = grid();
        again.setInventorySlotContents(4, mk4);
        check(
            CraftingManager.getInstance()
                .findMatchingRecipe(again, world) == null,
            "a Mk IV can't be crafted again");

        // The manipulator rebuilds its NBT whenever its settings change; the Mk IV marker must survive that.
        ItemMatterManipulator.setState(mk4, ItemMatterManipulator.getState(mk4));
        check(
            MkIV.isMkIV(mk4) && MkIV.NAME.equals(mk4.getDisplayName()) && mk4.isItemEnchanted(),
            "the Mk IV marker, name and glint survive the manipulator saving its state");

        // The real crafting containers, as a player uses them: the MK III goes in the grid, the Mk IV comes out.
        FakePlayer crafter = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f5"), "MkIVCrafter"));
        for (int i = 0; i < crafter.inventory.getSizeInventory(); i++) crafter.inventory.setInventorySlotContents(i, null);
        crafter.inventory.setItemStack(null);
        net.minecraft.inventory.ContainerWorkbench bench = new net.minecraft.inventory.ContainerWorkbench(
            crafter.inventory, world, 0, 0, 0);
        bench.putStackInSlot(5, manipulator.copy()); // slot 0 is the output, 1-9 the grid
        net.minecraft.inventory.Slot shown = MkIVCraftSync.mkIVResult(bench);
        check(shown != null && shown.slotNumber == 0,
            "a crafting table's output slot holds the Mk IV (the slot pushed to the player's screen)");
        bench.slotClick(0, 0, 0, crafter);
        check(MkIV.isMkIV(crafter.inventory.getItemStack()) && bench.getSlot(5).getStack() == null,
            "taking the output of a crafting table gives the Mk IV and uses up the MK III");
        crafter.inventory.setItemStack(null);
        net.minecraft.inventory.Container grid2x2 = crafter.inventoryContainer;
        grid2x2.putStackInSlot(1, manipulator.copy()); // the inventory's own 2x2 grid: output 0, grid 1-4
        check(MkIVCraftSync.mkIVResult(grid2x2) != null, "the inventory's 2x2 grid makes the Mk IV too");
        grid2x2.slotClick(0, 0, 0, crafter);
        check(MkIV.isMkIV(crafter.inventory.getItemStack()) && grid2x2.getSlot(1).getStack() == null,
            "taking it from the 2x2 grid gives the Mk IV and uses up the MK III");
        crafter.inventory.setItemStack(null);

        // Which blocks are free. null expected: just report the verdict.
        Object[][] blocks = { { "minecraft", "stone", true }, { "minecraft", "cobblestone", true },
            { "minecraft", "glass", true }, { "minecraft", "wool", true }, { "minecraft", "planks", true },
            { "minecraft", "log", true }, { "minecraft", "sandstone", true }, { "minecraft", "stonebrick", true },
            { "minecraft", "brick_block", true }, { "minecraft", "stained_glass", true },
            { "minecraft", "diamond_block", false }, { "minecraft", "iron_block", false },
            { "minecraft", "gold_ore", false }, { "minecraft", "chest", false }, { "minecraft", "furnace", false },
            { "minecraft", "glowstone", false }, { "minecraft", "anvil", false },
            { "gregtech", "gt.blockcasings", false }, { "gregtech", "gt.blockmachines", false },
            { "chisel", "marble", true }, { "chisel", "diamond_block", false }, { "BiomesOPlenty", "planks", true },
            { "etfuturum", "deepslate", true }, { "minecraft", "obsidian", true }, { "minecraft", "quartz_block", false },
            { "minecraft", "wool", true }, { "minecraft", "stained_hardened_clay", true },
            { "minecraft", "lapis_block", false }, { "minecraft", "coal_block", false },
            { "minecraft", "emerald_ore", false }, { "minecraft", "tnt", false }, { "minecraft", "hopper", false },
            { "Ztones", "tile.mystBlock", true }, { "etfuturum", "concrete", true }, { "chisel", "valentines", true },
            { "chisel", "granite", true }, { "chisel", "hexPlating", true }, { "chisel", "silverwood_planks", true },
            { "chisel", "iron_block", false }, { "Botania", "livingrock", true }, { "Botania", "storage", false },
            { "modernmarkings", "tile.wall_marking_symbol_storage", true }, { "etfuturum", "chiseled_copper", true },
            { "etfuturum", "copper_block", false }, { "minecraft", "torch", true }, { "TConstruct", "MetalBlock", false } };
        for (Object[] b : blocks) {
            Block block = GameRegistry.findBlock((String) b[0], (String) b[1]);
            if (block == null || Item.getItemFromBlock(block) == null) {
                log("(no block " + b[0] + ":" + b[1] + " in this pack)");
                continue;
            }
            ItemStack stack = new ItemStack(Item.getItemFromBlock(block), 1, 0);
            boolean free = MkIV.isFree(stack);
            List<String> ores = new ArrayList<>();
            for (int id : OreDictionary.getOreIDs(stack)) ores.add(OreDictionary.getOreName(id));
            String what = b[0] + ":" + b[1] + " is " + (free ? "free" : "not free") + " (ore names " + ores + ")";
            if (b[2] == null) log("verdict: " + what);
            else check(free == (Boolean) b[2], what);
        }

        // Taking items through the manipulator's real inventory code, as a player holding the Mk IV.
        FakePlayer player = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f4"), "MkIVTester"));
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) player.inventory.setInventorySlotContents(i, null);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, mk4);
        MMInventory inv = new MMInventory(
            player,
            ItemMatterManipulator.getState(mk4),
            ItemMatterManipulator.ManipulatorTier.Tier3);
        BigItemStack stone = MMItemConsumer.consume(inv, BigItemStack.create(new ItemStack(Blocks.stone, 64)));
        check(
            stone != null && stone.getStackSize() == 64 && count(player, Item.getItemFromBlock(Blocks.stone)) == 0,
            "the Mk IV places 64 stone with none in the inventory (got " + (stone == null ? 0 : stone.getStackSize()) + ")");
        BigItemStack diamonds = MMItemConsumer.consume(inv, BigItemStack.create(new ItemStack(Blocks.diamond_block, 1)));
        check(
            diamonds == null || diamonds.getStackSize() == 0,
            "the Mk IV can't place a diamond block it doesn't have");
        player.inventory.setInventorySlotContents(5, new ItemStack(Blocks.diamond_block, 2));
        diamonds = MMItemConsumer.consume(inv, BigItemStack.create(new ItemStack(Blocks.diamond_block, 1)));
        check(
            diamonds != null && diamonds.getStackSize() == 1 && count(player, Item.getItemFromBlock(Blocks.diamond_block)) == 1,
            "a diamond block it does have is taken from the inventory as usual");
        BooleanObjectImmutablePair<List<BigItemStack>> mixed = inv.tryConsumeItems(
            Arrays.asList(
                BigItemStack.create(new ItemStack(Blocks.glass, 10)),
                BigItemStack.create(new ItemStack(Blocks.diamond_block, 1))),
            0);
        check(
            mixed.leftBoolean() && mixed.right() != null
                && mixed.right()
                    .size() >= 2
                && count(player, Item.getItemFromBlock(Blocks.diamond_block)) == 0,
            "a mixed request takes the free glass for free and the diamond block from the inventory");

        // The void list: what the Mk IV hands back (removed blocks' drops) is deleted when the holder's list names it.
        MkIVVoid.clear(player);
        MkIVVoid.setOn(player, true);
        check(
            MkIVVoid.toggle(player, MkIVVoid.COBBLE) && MkIVVoid.entries(player)
                .contains(MkIVVoid.COBBLE),
            "/mmvoid cobble puts cobblestone on the void list");
        inv.givePlayerItems(
            Arrays.asList(
                BigItemStack.create(new ItemStack(Blocks.cobblestone, 40)),
                BigItemStack.create(new ItemStack(Blocks.dirt, 3))));
        check(
            pending(inv, Blocks.cobblestone, 0) == 0 && pending(inv, Blocks.dirt, 0) == 3,
            "removed cobblestone is deleted and removed dirt is handed back (cobblestone "
                + pending(inv, Blocks.cobblestone, 0) + ", dirt " + pending(inv, Blocks.dirt, 0) + ")");
        check(MkIVVoid.add(player, MkIVVoid.parse("minecraft:dirt")), "dirt can be added by name");
        inv.givePlayerItems(Arrays.asList(BigItemStack.create(new ItemStack(Blocks.dirt, 5))));
        check(pending(inv, Blocks.dirt, 0) == 3, "then removed dirt is deleted too (dirt " + pending(inv, Blocks.dirt, 0) + ")");
        String red = MkIVVoid.entryFor(new ItemStack(Blocks.wool, 1, 14));
        check(
            "minecraft:wool:14".equals(red) && MkIVVoid.add(player, red),
            "a held item with kinds is added as modid:name:meta (" + red + ")");
        inv.givePlayerItems(
            Arrays.asList(
                BigItemStack.create(new ItemStack(Blocks.wool, 2, 14)),
                BigItemStack.create(new ItemStack(Blocks.wool, 2, 3))));
        check(
            pending(inv, Blocks.wool, 14) == 0 && pending(inv, Blocks.wool, 3) == 2,
            "only that kind of wool is deleted");
        MkIVVoid.setOn(player, false);
        inv.givePlayerItems(Arrays.asList(BigItemStack.create(new ItemStack(Blocks.cobblestone, 7))));
        check(pending(inv, Blocks.cobblestone, 0) == 7, "with voiding paused, cobblestone is handed back");
        MkIVVoid.setOn(player, true);
        check(
            MkIVVoid.parse("ore:cobblestone") != null && MkIVVoid.parse("minecraft:dirt:0") != null
                && MkIVVoid.parse("nosuchmod:thing") == null
                && MkIVVoid.parse("cobblestone") == null,
            "typed entries are checked (ore:name, modid:name[:meta], no unknown items)");
        check(
            player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getCompoundTag("ValierMMVoid")
                .getTagList("Items", 8)
                .tagCount() == 3,
            "the list is kept in the player's persistent data");

        // An ordinary MK III still needs the blocks.
        ItemStack plain = manipulator.copy();
        player.inventory.setInventorySlotContents(0, plain);
        MMInventory plainInv = new MMInventory(
            player,
            ItemMatterManipulator.getState(plain),
            ItemMatterManipulator.ManipulatorTier.Tier3);
        BigItemStack plainStone = MMItemConsumer.consume(plainInv, BigItemStack.create(new ItemStack(Blocks.stone, 64)));
        check(
            plainStone == null || plainStone.getStackSize() == 0,
            "an ordinary MK III still needs the stone in the inventory");
        plainInv.givePlayerItems(Arrays.asList(BigItemStack.create(new ItemStack(Blocks.cobblestone, 9))));
        check(pending(plainInv, Blocks.cobblestone, 0) == 9, "an ordinary MK III voids nothing, whatever the holder's list");
        player.inventory.setInventorySlotContents(0, null);
        MkIVVoid.clear(player);

        // /mmvoid toggle and /mmvoid remove work on the item in hand, as the player types them.
        VoidCommand command = new VoidCommand();
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.wool, 1, 14));
        command.processCommand(player, new String[] { "toggle" });
        check(
            MkIVVoid.entries(player)
                .equals(Arrays.asList("minecraft:wool:14")),
            "/mmvoid toggle voids the held item (" + MkIVVoid.entries(player) + ")");
        command.processCommand(player, new String[] { "toggle" });
        check(
            MkIVVoid.entries(player)
                .isEmpty(),
            "/mmvoid toggle again stops voiding it");
        command.processCommand(player, new String[] { "cobble" });
        command.processCommand(player, new String[] { "add", "minecraft:dirt" });
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 1));
        command.processCommand(player, new String[] { "remove" });
        check(
            MkIVVoid.entries(player)
                .equals(Arrays.asList("minecraft:dirt")),
            "/mmvoid remove with cobblestone in hand takes off ore:cobblestone and keeps dirt (" + MkIVVoid.entries(player)
                + ")");
        command.processCommand(player, new String[] { "toggle" });
        check(
            MkIVVoid.entries(player)
                .equals(Arrays.asList("minecraft:dirt", "minecraft:cobblestone")),
            "/mmvoid toggle with cobblestone in hand puts it back (" + MkIVVoid.entries(player) + ")");
        player.inventory.setInventorySlotContents(0, null);
        MkIVVoid.clear(player);

        // /mmvoid purge: only matching drops on the ground go, the manipulator's large drops included.
        ChunkCoordinates spawn = world.getSpawnPoint();
        double x = spawn.posX + 0.5, y = world.getTopSolidOrLiquidBlock(spawn.posX, spawn.posZ) + 1, z = spawn.posZ + 0.5;
        EntityItem cobble = new EntityItem(world, x, y, z, new ItemStack(Blocks.cobblestone, 12));
        EntityItem dirt = new EntityItem(world, x, y, z, new ItemStack(Blocks.dirt, 4));
        EntityItem large = new EntityItemLarge(world, x, y, z, new ItemStack(Blocks.cobblestone, 500));
        for (EntityItem drop : new EntityItem[] { cobble, dirt, large }) world.spawnEntityInWorld(drop);
        long[] removed = MkIVVoid.purge(Arrays.asList(MkIVVoid.COBBLE));
        check(
            cobble.isDead && large.isDead && !dirt.isDead,
            "purge deletes dropped cobblestone, large drops too, and leaves the dirt (" + removed[0] + " stacks, "
                + removed[1] + " items)");
        dirt.setDead();

        // Ores: on its own the manipulator deletes them; with mining/dropOres their drops come back like any block's.
        // Removed through the manipulator's own removeBlock, with the block spec it builds from the world.
        ItemStack mk3InHand = manipulator.copy();
        player.inventory.setInventorySlotContents(0, mk3InHand);
        PendingBuild build = new PendingBuild(
            player,
            ItemMatterManipulator.getState(mk3InHand),
            ItemMatterManipulator.ManipulatorTier.Tier3,
            new ArrayList<>());
        int ox = spawn.posX + 3, oy = 200, oz = spawn.posZ + 3;
        MkIVConfig.dropOres = true;
        check(
            removeWithManipulator(build, world, ox, oy, oz, Blocks.iron_ore, 0) && pending(build, Blocks.iron_ore, 0) == 1,
            "removing vanilla iron ore hands back the iron ore (" + pending(build, Blocks.iron_ore, 0) + ")");
        check(
            removeWithManipulator(build, world, ox + 1, oy, oz, Blocks.diamond_ore, 0)
                && pending(build, new ItemStack(Items.diamond)) >= 1,
            "removing diamond ore hands back a diamond (" + pending(build, new ItemStack(Items.diamond)) + ")");
        // GregTech 5.09.54 ore blocks: gt.blockores2 holds stone (variant 0), netherrack, end stone, granites, marble,
        // basalt and moon rock; plain gt.blockores is the old pre-rework block, kept only to convert old worlds.
        Block gtOres = GameRegistry.findBlock("gregtech", "gt.blockores2");
        int naturalIron = 8000 + 32; // natural (8000+) ore of iron (GT material 32) in stone (stone type 0)
        int smallIron = 16000 + naturalIron; // the same as a small ore
        check(
            gtOres != null && gtIsOre(gtOres, naturalIron) && gtIsOre(gtOres, smallIron),
            "GregTech counts natural iron ore and small iron ore as ores (the kind the manipulator deleted)");
        ItemStack rawIron = firstOre("rawOreIron");
        IOreMaterial ironOre = Materials.Iron;
        check(ironOre != null && placeOre(world, ox + 11, oy, oz, ironOre, false), "the test's ore placer places a natural iron ore");
        world.setBlockToAir(ox + 11, oy, oz);
        world.setBlock(ox + 2, oy, oz, gtOres, naturalIron, 2);
        log(
            "The manipulator's block spec of a natural iron ore (world metadata " + world.getBlockMetadata(ox + 2, oy, oz)
                + ") has metadata " + BlockSpec.fromBlock(new BlockSpec(), world, ox + 2, oy, oz)
                    .getBlockMeta());
        long before = total(build);
        boolean gone = removeWithManipulator(build, world, ox + 2, oy, oz, gtOres, naturalIron);
        log("GregTech iron ore gave: " + build.pendingItems);
        check(
            gone && total(build) > before && (rawIron == null || pending(build, rawIron) >= 1),
            "removing a natural GregTech iron ore hands back its drops (raw iron ore: "
                + (rawIron == null ? "no rawOreIron item" : pending(build, rawIron)) + ", " + (total(build) - before)
                + " items in all)");
        before = total(build);
        gone = removeWithManipulator(build, world, ox + 3, oy, oz, gtOres, smallIron);
        log("After the small iron ore: " + build.pendingItems);
        check(gone && total(build) > before, "removing a small GregTech iron ore hands back its drops (" + (total(build) - before) + " items)");
        MkIVConfig.dropOres = false;
        before = total(build);
        check(
            removeWithManipulator(build, world, ox + 4, oy, oz, Blocks.iron_ore, 0) && total(build) == before,
            "with mining/dropOres off, the manipulator deletes ores as it does on its own");
        before = total(build);
        removeWithManipulator(build, world, ox + 5, oy, oz, Blocks.stone, 0);
        check(total(build) == before + 1, "with mining/dropOres off, other blocks still come back (stone gives cobblestone)");
        MkIVConfig.dropOres = true;

        // Thaumcraft's infused stone (blockCustomOre 1 to 6) drops 1 + random(2 + Fortune) shards of its aspect, as with
        // a pickaxe; 7 is amber bearing stone, 0 cinnabar ore (which drops itself).
        Block tcOre = GameRegistry.findBlock("Thaumcraft", "blockCustomOre");
        Item shard = GameRegistry.findItem("Thaumcraft", "ItemShard");
        check(tcOre != null && shard != null, "Thaumcraft's infused stone and shard item exist");
        String[] aspects = { "air", "fire", "water", "earth", "order", "entropy" };
        for (int m = 1; tcOre != null && shard != null && m <= 6; m++) {
            long had = pending(build, new ItemStack(shard, 1, m - 1));
            boolean taken = removeWithManipulator(build, world, ox + 9, oy, oz, tcOre, m);
            long got = pending(build, new ItemStack(shard, 1, m - 1)) - had;
            check(
                taken && got >= 1 && got <= 2,
                "removing " + aspects[m - 1] + " infused stone hands back 1 or 2 " + aspects[m - 1] + " shards (" + got + ")");
        }
        if (tcOre != null) {
            Item resource = GameRegistry.findItem("Thaumcraft", "ItemResource");
            long amber = resource == null ? 0 : pending(build, new ItemStack(resource, 1, 6));
            check(
                removeWithManipulator(build, world, ox + 9, oy, oz, tcOre, 7) && resource != null
                    && pending(build, new ItemStack(resource, 1, 6)) > amber,
                "removing amber bearing stone hands back amber");
            check(
                removeWithManipulator(build, world, ox + 9, oy, oz, tcOre, 0) && pending(build, tcOre, 0) >= 1,
                "removing cinnabar ore hands back the cinnabar ore block");
        }
        // GregTech's own shard veins (Terra/Aer, Aqua/Ignis, Perditio/Ordo) are GregTech ores: with the server's
        // FortuneItem drop setting they drop raw ore, the same as with a pickaxe, which processing turns into shards.
        IOreMaterial infusedAir = Materials.InfusedAir;
        check(infusedAir != null, "GregTech has an InfusedAir ore material");
        if (infusedAir != null) {
            ItemStack rawAir = firstOre("rawOreInfusedAir");
            check(placeOre(world, ox + 10, oy, oz, infusedAir, false), "GregTech places a natural infused air ore");
            Block airOre = world.getBlock(ox + 10, oy, oz);
            List<ItemStack> pick = airOre.getDrops(world, ox + 10, oy, oz, world.getBlockMetadata(ox + 10, oy, oz), 0);
            placeOre(world, ox + 10, oy, oz, infusedAir, false);
            before = total(build);
            long rawBefore = rawAir == null ? 0 : pending(build, rawAir);
            boolean taken = removeInPlace(build, world, ox + 10, oy, oz);
            log("A natural GregTech infused air ore: a pickaxe gets " + pick + "; the manipulator handed back "
                + (total(build) - before) + " items (raw infused air ore: "
                + (rawAir == null ? "no rawOreInfusedAir item" : pending(build, rawAir) - rawBefore) + ")");
            check(
                taken && rawAir != null && pending(build, rawAir) - rawBefore >= 1,
                "removing a natural GregTech infused air ore hands back raw infused air ore, as a pickaxe gets");
        }

        // Fortune: crafted on with a book, kept through the manipulator's state saves, and used for drops.
        ItemStack fortuneBook = Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.fortune, 3));
        InventoryCrafting fg = grid();
        fg.setInventorySlotContents(0, manipulator.copy());
        fg.setInventorySlotContents(4, fortuneBook.copy());
        ItemStack lucky = CraftingManager.getInstance()
            .findMatchingRecipe(fg, world);
        check(
            MMFortune.isManipulator(lucky) && MMFortune.onItem(lucky) == 3
                && lucky.getTagCompound()
                    .getDouble("charge") == 12345.0D
                && !MkIV.isMkIV(lucky),
            "an MK III crafted with a Fortune III book gets Fortune III and keeps its charge ("
                + (lucky == null ? "no result" : "Fortune " + MMFortune.onItem(lucky)) + ")");
        InventoryCrafting fg4 = grid();
        fg4.setInventorySlotContents(2, mk4.copy());
        fg4.setInventorySlotContents(7, fortuneBook.copy());
        ItemStack luckyMk4 = CraftingManager.getInstance()
            .findMatchingRecipe(fg4, world);
        check(
            MkIV.isMkIV(luckyMk4) && MMFortune.onItem(luckyMk4) == 3,
            "a Mk IV crafted with the book stays a Mk IV and gets Fortune III");
        InventoryCrafting weaker = grid();
        weaker.setInventorySlotContents(0, lucky == null ? manipulator.copy() : lucky.copy());
        weaker.setInventorySlotContents(1, Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.fortune, 2)));
        InventoryCrafting noBook = grid();
        noBook.setInventorySlotContents(0, manipulator.copy());
        noBook.setInventorySlotContents(1, Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.unbreaking, 3)));
        FortuneRecipe fortuneRecipe = new FortuneRecipe(mk3);
        check(
            !fortuneRecipe.matches(weaker, world) && !fortuneRecipe.matches(noBook, world),
            "a lower Fortune book or a book without Fortune doesn't match");
        if (lucky != null) {
            ItemMatterManipulator.setState(lucky, ItemMatterManipulator.getState(lucky));
            check(MMFortune.onItem(lucky) == 3, "Fortune survives the manipulator saving its state");
        }
        if (luckyMk4 != null) {
            ItemMatterManipulator.setState(luckyMk4, ItemMatterManipulator.getState(luckyMk4));
            check(MkIV.isMkIV(luckyMk4) && MMFortune.onItem(luckyMk4) == 3, "so do the Mk IV marker and Fortune together");
        }

        if (lucky != null) {
            // GregTech gives no Fortune to fake players (a class name with "Fake"), so this one is a plain
            // EntityPlayerMP that is never added to the world.
            EntityPlayerMP miner = new EntityPlayerMP(
                MinecraftServer.getServer(),
                world,
                new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f5"), "MkIVMiner"),
                new ItemInWorldManager(world));
            miner.inventory.currentItem = 0;
            miner.inventory.setInventorySlotContents(0, lucky);
            check(MMFortune.miningLevel(miner) == 3, "holding it, the manipulator mines with Fortune III");
            PendingBuild luckyBuild = new PendingBuild(
                miner,
                ItemMatterManipulator.getState(lucky),
                ItemMatterManipulator.ManipulatorTier.Tier3,
                new ArrayList<>());
            for (int i = 0; i < 20; i++) removeWithManipulator(luckyBuild, world, ox + 6, oy, oz, Blocks.diamond_ore, 0);
            long luckyDiamonds = pending(luckyBuild, new ItemStack(Items.diamond));
            check(
                luckyDiamonds > 20,
                "20 diamond ores removed with Fortune III give more than 20 diamonds (" + luckyDiamonds + ")");
            int withHarvester = 0, without = 0;
            for (int i = 0; i < 50; i++) {
                world.setBlock(ox + 8, oy, oz, gtOres, naturalIron, 2);
                for (ItemStack s : gtOres.getDrops(world, ox + 8, oy, oz, naturalIron, 3)) without += s.stackSize;
                Object previous = MMFortune.beginHarvest(gtOres, miner);
                try {
                    for (ItemStack s : gtOres.getDrops(world, ox + 8, oy, oz, naturalIron, 3)) withHarvester += s.stackSize;
                } finally {
                    MMFortune.endHarvest(gtOres, previous);
                }
            }
            world.setBlockToAir(ox + 8, oy, oz);
            log(
                "GregTech iron ore getDrops with Fortune 3, 50 times: " + withHarvester + " items with the miner as harvester, "
                    + without + " without; isRealPlayer(miner) = " + gtIsRealPlayer(miner) + "; drop system = "
                    + gtDropSystem());
            long rawBefore = rawIron == null ? 0 : pending(luckyBuild, rawIron);
            long allBefore = total(luckyBuild);
            for (int i = 0; i < 20; i++) removeWithManipulator(luckyBuild, world, ox + 7, oy, oz, gtOres, naturalIron);
            long raw = rawIron == null ? total(luckyBuild) - allBefore : pending(luckyBuild, rawIron) - rawBefore;
            log("20 GregTech iron ores with Fortune III gave: " + luckyBuild.pendingItems);
            check(raw > 20, "20 natural GregTech iron ores removed with Fortune III give more than 20 raw ores (" + raw + ")");
            if (tcOre != null && shard != null) {
                long plainBefore = pending(build, new ItemStack(shard, 1, 0));
                long luckyBefore = pending(luckyBuild, new ItemStack(shard, 1, 0));
                for (int i = 0; i < 20; i++) {
                    removeWithManipulator(build, world, ox + 9, oy, oz, tcOre, 1);
                    removeWithManipulator(luckyBuild, world, ox + 9, oy, oz, tcOre, 1);
                }
                long plainShards = pending(build, new ItemStack(shard, 1, 0)) - plainBefore;
                long luckyShards = pending(luckyBuild, new ItemStack(shard, 1, 0)) - luckyBefore;
                check(
                    plainShards >= 20 && plainShards <= 40 && luckyShards > 40,
                    "20 air infused stones: " + plainShards + " shards without Fortune (20 to 40), " + luckyShards
                        + " with Fortune III (more than 40)");
            }
        }
        player.inventory.setInventorySlotContents(0, manipulator.copy());
        check(MMFortune.miningLevel(player) == 0, "an unenchanted manipulator mines with Fortune 0");
        MkIVConfig.baseFortune = 3;
        check(MMFortune.miningLevel(player) == 3, "mining/baseFortune 3 gives every manipulator Fortune III");
        MkIVConfig.baseFortune = 0;

        if (Loader.isModLoaded("visualprospecting")) veins(world, manipulator, build, spawn);
        else check(false, "VisualProspecting is loaded (for the ore vein checks)");
        player.inventory.setInventorySlotContents(0, null);
    }

    /** Records GregTech's ore events, as VisualProspecting receives them. Public: FML's event bus calls it directly. */
    public static class OreEvents {

        public final List<OreInteractEvent> events = new ArrayList<>();

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onOre(OreInteractEvent event) {
            events.add(event);
        }
    }

    /**
     * Ore veins on the map: a manipulator removing a natural GregTech ore posts GregTech's OreInteractEvent (what a
     * pickaxe's harvest posts) once per chunk and ore, and VisualProspecting answers that ore with its vein.
     */
    private void veins(WorldServer world, ItemStack manipulator, PendingBuild fakeBuild, ChunkCoordinates spawn)
        throws Exception {
        EntityPlayerMP miner = new EntityPlayerMP(
            MinecraftServer.getServer(),
            world,
            new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f6"), "VeinMiner"),
            new ItemInWorldManager(world));
        miner.inventory.currentItem = 0;
        miner.inventory.setInventorySlotContents(0, manipulator.copy());
        PendingBuild build = new PendingBuild(
            miner,
            ItemMatterManipulator.getState(manipulator),
            ItemMatterManipulator.ManipulatorTier.Tier3,
            new ArrayList<>());

        List<OreVeinPosition> known = VisualProspecting_API.LogicalServer
            .prospectOreVeinsWithinRadius(0, spawn.posX, spawn.posZ, 2048);
        OreVeinPosition vein = null;
        for (OreVeinPosition v : known) {
            if (v.veinType != VeinType.NO_VEIN && v.veinType.primaryOre != null) {
                vein = v;
                break;
            }
        }
        if (vein == null) {
            // A world VisualProspecting hasn't watched being generated: tell it about a vein, as GregTech's ore
            // generator does (VisualProspecting_API.LogicalServer.notifyOreGeneration).
            VeinType type = null;
            for (VeinType t : VeinTypeCaching.getVeinTypes()) {
                if (t != VeinType.NO_VEIN && t.primaryOre != null && t.getAllowedDimensions()
                    .contains("Overworld")) {
                    type = t;
                    break;
                }
            }
            if (type == null) for (VeinType t : VeinTypeCaching.getVeinTypes()) {
                if (t != VeinType.NO_VEIN && t.primaryOre != null) {
                    type = t;
                    break;
                }
            }
            // Next to spawn, in chunks that are generated and loaded.
            int cx = Utils.mapToCenterOreChunkCoord((spawn.posX >> 4) + 3);
            int cz = Utils.mapToCenterOreChunkCoord((spawn.posZ >> 4) + 3);
            if (type != null) {
                VisualProspecting_API.LogicalServer.notifyOreGeneration(0, cx, cz, type.name);
                OreVeinPosition v = VisualProspecting_API.LogicalServer.getOreVein(0, cx, cz);
                if (v != null && v.veinType == type) vein = v;
            }
            log("VisualProspecting knew no vein near spawn (" + known.size() + "); registered "
                + (type == null ? "nothing (no vein type)" : "a " + type.name + " vein at chunk " + cx + " " + cz)
                + (vein == null ? ", which it didn't keep" : ""));
        }
        check(vein != null, "an ore vein VisualProspecting knows, for the vein checks");
        if (vein == null) return;
        IOreMaterial ore = vein.veinType.primaryOre;
        int vx = vein.getBlockX(), vy = 250, vz = vein.getBlockZ();
        log("Vein checks use the " + vein.veinType.name + " vein at chunk " + vein.chunkX + " " + vein.chunkZ + " (primary ore "
            + ore.getInternalName() + ", block " + vx + " " + vz + ")");

        OreEvents seen = new OreEvents();
        boolean prospecting = Config.enableProspecting;
        // VisualProspecting's own answer is sent over the player's connection, which this miner doesn't have; it is
        // checked directly below.
        Config.enableProspecting = false;
        MinecraftForge.EVENT_BUS.register(seen);
        try {
            check(placeOre(world, vx, vy, vz, ore, false), "a natural " + ore.getInternalName() + " ore is placed in the vein's chunk");
            Block block = world.getBlock(vx, vy, vz);
            int meta = world.getBlockMetadata(vx, vy, vz);
            removeInPlace(build, world, vx, vy, vz);
            OreInteractEvent e = seen.events.isEmpty() ? null : seen.events.get(0);
            check(
                world.isAirBlock(vx, vy, vz) && seen.events.size() == 1 && e.world == world && e.x == vx && e.y == vy
                    && e.z == vz && e.block == block && e.meta == meta && e.player == miner,
                "removing it posts GregTech's ore event for that block and the holder, as a pickaxe's harvest does ("
                    + seen.events.size() + " events)");
            placeOre(world, vx + 1, vy, vz, ore, false);
            removeInPlace(build, world, vx + 1, vy, vz);
            check(seen.events.size() == 1, "a second " + ore.getInternalName() + " ore in the same chunk doesn't post it again");
            placeOre(world, vx + 2, vy, vz, ore, true);
            removeInPlace(build, world, vx + 2, vy, vz);
            check(seen.events.size() == 1, "a small ore posts nothing (VisualProspecting ignores small ores)");
            world.setBlock(vx + 3, vy, vz, Blocks.stone, 0, 2);
            removeInPlace(build, world, vx + 3, vy, vz);
            check(seen.events.size() == 1, "stone posts nothing");
            placeOre(world, vx + 16, vy, vz, ore, false);
            removeInPlace(fakeBuild, world, vx + 16, vy, vz);
            check(seen.events.size() == 1, "a fake player's manipulator posts nothing");
            MkIVConfig.markVeins = false;
            placeOre(world, vx + 16, vy, vz, ore, false);
            removeInPlace(build, world, vx + 16, vy, vz);
            check(seen.events.size() == 1, "with mining/markVeins off, nothing is posted");
            MkIVConfig.markVeins = true;
            placeOre(world, vx + 16, vy, vz, ore, false);
            removeInPlace(build, world, vx + 16, vy, vz);
            check(seen.events.size() == 2, "the same ore in the next chunk posts it again (" + seen.events.size() + " events)");
        } finally {
            MkIVConfig.markVeins = true;
            MinecraftForge.EVENT_BUS.unregister(seen);
            Config.enableProspecting = prospecting;
            VeinReveal.forget(miner);
        }

        // What VisualProspecting does with that event on the server (ServerCache.onOreClicked): the vein for the map.
        placeOre(world, vx, vy, vz, ore, false);
        ProspectingNotification answer = ProspectingRequest.prospect(new ProspectingRequest(0, vx, vy, vz, ore), world);
        world.setBlockToAir(vx, vy, vz);
        boolean found = false;
        if (answer != null) for (OreVeinPosition v : answer.getOreVeins()) {
            found |= v.chunkX == vein.chunkX && v.chunkZ == vein.chunkZ && v.veinType == vein.veinType;
        }
        check(found, "VisualProspecting answers that ore with the " + vein.veinType.name + " vein, the one a pickaxe shows");
    }

    /** A world-generated (natural) GregTech ore in stone: the block and metadata GregTech's own ore adapter gives. */
    private static boolean placeOre(WorldServer world, int x, int y, int z, IOreMaterial ore, boolean small) {
        try (OreInfo<IOreMaterial> want = OreInfo.getNewInfo()) {
            want.material = ore;
            want.stoneType = StoneType.Stone;
            want.isNatural = true;
            want.isSmall = small;
            IOreAdapter<?> adapter = OreManager.getAdapter(want);
            ImmutableBlockMeta block = adapter == null ? null : adapter.getBlock(want);
            if (block == null) {
                log("placeOre: no ore block for " + ore.getInternalName() + " (adapter " + adapter + ")");
                return false;
            }
            // An unloaded chunk would take the block into the server's empty placeholder chunk.
            world.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
            boolean set = world.setBlock(x, y, z, block.getBlock(), block.getBlockMeta(), 2);
            try (OreInfo<IOreMaterial> info = OreManager.getOreInfo(world, x, y, z)) {
                boolean ok = info != null && info.material == ore && info.isNatural && info.isSmall == small;
                if (!ok) log("placeOre: " + ore.getInternalName() + (small ? " (small)" : "") + " -> " + block.getBlock()
                    .getUnlocalizedName() + ":" + block.getBlockMeta() + ", setBlock " + set + ", world has "
                    + world.getBlock(x, y, z)
                        .getUnlocalizedName()
                    + ":" + world.getBlockMetadata(x, y, z) + ", ore info " + info);
                return ok;
            }
        }
    }

    /**
     * Places the block, then removes it as a manipulator does: {@code AbstractBuildable.removeBlock} with the spec
     * {@code BlockSpec.fromBlock} reads from the world (what PendingBuild does). True if it was placed and is gone.
     */
    private static boolean removeWithManipulator(AbstractBuildable build, WorldServer world, int x, int y, int z, Block block,
        int meta) throws Exception {
        world.setBlock(x, y, z, block, meta, 2);
        if (world.getBlock(x, y, z) != block || world.getBlockMetadata(x, y, z) != meta) return false;
        return removeInPlace(build, world, x, y, z);
    }

    /** Removes the block already there as a manipulator does. True if it is gone. */
    private static boolean removeInPlace(AbstractBuildable build, WorldServer world, int x, int y, int z) throws Exception {
        BlockSpec spec = BlockSpec.fromBlock(new BlockSpec(), world, x, y, z);
        Method remove = AbstractBuildable.class.getDeclaredMethod(
            "removeBlock",
            World.class,
            int.class,
            int.class,
            int.class,
            ImmutableBlockSpec.class,
            boolean.class);
        remove.setAccessible(true);
        remove.invoke(build, world, x, y, z, spec, true);
        return world.isAirBlock(x, y, z);
    }

    private static boolean gtIsOre(Block block, int meta) throws Exception {
        return (Boolean) Class.forName("gregtech.api.util.GTUtility")
            .getMethod("isOre", Block.class, int.class)
            .invoke(null, block, meta);
    }

    private static boolean gtIsRealPlayer(EntityPlayer player) throws Exception {
        return (Boolean) Class.forName("gregtech.api.util.GTUtility")
            .getMethod("isRealPlayer", net.minecraft.entity.EntityLivingBase.class)
            .invoke(null, player);
    }

    /** GregTech's ore drop setting as loaded (GTMod.proxy's OreDropSystem field). */
    private static String gtDropSystem() {
        try {
            Object proxy = Class.forName("gregtech.GTMod")
                .getField("proxy")
                .get(null);
            for (Class<?> c = proxy.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType()
                        .getName()
                        .endsWith("OreDropSystem")) {
                        f.setAccessible(true);
                        return f.getName() + "=" + f.get(proxy);
                    }
                }
            }
            return "not found";
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static ItemStack firstOre(String name) {
        List<ItemStack> ores = OreDictionary.getOres(name);
        return ores.isEmpty() ? null : ores.get(0);
    }

    private static long pending(MMInventory inv, ItemStack stack) {
        return inv.pendingItems.getLong(
            BigItemStack.create(stack)
                .getId());
    }

    private static long total(MMInventory inv) {
        long sum = 0;
        for (long n : inv.pendingItems.values()) sum += n;
        return sum;
    }

    /** How many of this item the manipulator is holding back to hand to the player. */
    private static long pending(MMInventory inv, Block block, int meta) {
        return inv.pendingItems.getLong(
            BigItemStack.create(new ItemStack(block, 1, meta))
                .getId());
    }

    /**
     * {@code /mmfreepower dump}: every block item's verdict (metadata 0 to 15 where they differ), written to
     * mmfreepower-verdicts.tsv as: free/cost, modid:name, metadata, display name, reason.
     */
    private void dump() {
        java.util.List<String> lines = new ArrayList<>();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            if (!(item instanceof net.minecraft.item.ItemBlock)) continue;
            String regName = Item.itemRegistry.getNameForObject(item);
            String last = null;
            for (int meta = 0; meta < (item.getHasSubtypes() ? 16 : 1); meta++) {
                ItemStack stack = new ItemStack(item, 1, meta);
                String reason, display;
                try {
                    reason = MkIV.whyNotFree(stack);
                    display = stack.getDisplayName();
                } catch (Throwable t) {
                    continue; // metadata the block doesn't have
                }
                String line = (reason == null ? "free" : "cost") + "\t" + regName + "\t" + meta + "\t" + display + "\t"
                    + (reason == null ? "" : reason);
                String key = (reason == null ? "free" : reason) + display;
                if (key.equals(last)) continue;
                last = key;
                lines.add(line);
            }
        }
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("mmfreepower-verdicts.tsv"), lines);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        log("dumped " + lines.size() + " block verdicts to mmfreepower-verdicts.tsv");
    }

    private static InventoryCrafting grid() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }

    private static int count(EntityPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    private void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            failures.add(what);
        }
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void log(String text) {
        MMFreePower.LOG.info("[MMFreePower SelfTest] " + text);
    }
}
