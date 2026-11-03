package gtnhserver.mmfreepower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.oredict.OreDictionary;

import com.mojang.authlib.GameProfile;
import com.recursive_pineapple.matter_manipulator.common.building.MMInventory;
import com.recursive_pineapple.matter_manipulator.common.building.MMItemConsumer;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
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

    private void run() {
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
        player.inventory.setInventorySlotContents(0, null);
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
