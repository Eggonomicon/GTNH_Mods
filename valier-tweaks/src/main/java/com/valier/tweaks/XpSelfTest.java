package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/**
 * {@code valiertweaks selftest xpcrafter}, test server only. In an empty chunk of the flat test world:
 * <ul>
 * <li>recipes: GTNH's XP bucket recipes are found, with their liquid XP costs (a tiny pile of gallium 8,000 mB, of
 * arsenic 7,000, a small pile of brick 4,000, iron dust 2,000, charcoal 1,000); the full dust of a tiny pile is 9
 * times the XP, of a small pile 4 times; whole dusts have none;</li>
 * <li>crafting: a ULV fluid tank and a block of diamond make a Valier XP Crafter (name and tooltip); not the tank
 * alone, an LV tank, a crafter again, or with anything extra; in a crafting table it is in the output slot, the slot
 * sent to players;</li>
 * <li>the crafter: placed as a player places it, it is a crafter; sneak-right-click with an empty hand shows its status,
 * with a tiny pile of gallium chooses that (not without sneaking, not where protected; a dust no recipe makes is
 * refused); 5,000 mB makes nothing yet, 3,000 more make one into the chest, 20,000 make two more; an emerald block
 * makes gallium dust from 72,000 mB instead; broken, it drops as the item with all it knew and placed again carries
 * on; a sneaking empty-hand hit gives the emerald block back; with nothing touching it no XP is taken, with the chest
 * full the one it made waits; its record saves; removed without dropping, it is forgotten;</li>
 * <li>a plain ULV tank only gets a hint;</li>
 * <li>with WAILA: what it makes and is doing goes along with WAILA's block data, and with Valier Client loaded too, a
 * player reads it ({@link XpHudSelfTest});</li>
 * <li>chain armour: GregTech's macerator recipes give whole steel dust (helmet 1, leggings 2, boots 1, chestplate 2),
 * also as a macerator looks them up.</li>
 * </ul>
 */
final class XpSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int bx = (SelfTest.CX + 4) << 4, bz = (SelfTest.CZ + 6) << 4;
    int y;
    final String[] said = new String[1];
    FakePlayer player;
    final List<int[]> placed = new ArrayList<>();

    final ItemStack tinyGallium = GTOreDictUnificator.get(OrePrefixes.dustTiny, Materials.Gallium, 1);
    final ItemStack tinyArsenic = GTOreDictUnificator.get(OrePrefixes.dustTiny, Materials.Arsenic, 1);
    final ItemStack smallBrick = GTOreDictUnificator.get(OrePrefixes.dustSmall, Materials.Brick, 1);
    final ItemStack ironDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Iron, 1);
    final ItemStack galliumDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Gallium, 1);
    final ItemStack brickDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Brick, 1);
    final ItemStack titaniumDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 1);
    final Fluid xp = FluidRegistry.getFluid("xpjuice");
    ItemStack crafterItem;
    /** The WAILA checks, when WAILA is loaded. */
    XpHudSelfTest hud;

    void start() {
        try {
            world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
            y = world.getHeightValue(bx + 8, bz + 8);
            clear();
            player = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000d03"), "XpTester")) {

                @Override
                public void addChatMessage(IChatComponent message) {
                    said[0] = message.getUnformattedText();
                }
            };
            player.setPosition(bx + 8.5, y, bz + 0.5);
            if (Loader.isModLoaded("Waila")) hud = new XpHudSelfTest(world, player);
            recipes();
            craftingRecipe();
            if (crafterItem != null) crafter();
            plainTank();
            chainArmour();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] xpcrafter threw", t);
            SelfTest.check(false, "the XP crafter checks ran without an exception (" + t + ")");
        } finally {
            clear();
            SelfTest.summary();
        }
    }

    // ---- recipes --------------------------------------------------------------------------------------------------

    void recipes() {
        Map<String, XpCrafters.XpRecipe> all = XpCrafters.recipes();
        SelfTest.check(all.size() >= 20, "GTNH's liquid XP recipes are found (" + all.size() + ": " + XpCrafters.canMake() + ")");
        int g = cost(tinyGallium), a = cost(tinyArsenic), b = cost(smallBrick), i = cost(ironDust),
            ch = cost(new ItemStack(Items.coal, 1, 1));
        SelfTest.check(g == 8000 && a == 7000 && b == 4000 && i == 2000 && ch == 1000, "their costs are the XP buckets' "
            + "liquid XP: tiny gallium " + g + ", tiny arsenic " + a + ", small brick " + b + ", iron dust " + i + ", charcoal "
            + ch + " mB");
        XpCrafters.Full fg = XpCrafters.full(tinyGallium), fb = XpCrafters.full(smallBrick), fi = XpCrafters.full(ironDust);
        SelfTest.check(fg != null && fg.dust.isItemEqual(galliumDust) && fg.times == 9 && fb != null
            && fb.dust.isItemEqual(brickDust) && fb.times == 4 && fi == null, "full dust: a tiny pile of gallium becomes "
                + "gallium dust for 9 times the XP, a small pile of brick dust brick dust for 4 times, iron dust has none ("
                + (fg == null ? null : fg.dust.getDisplayName() + " x" + fg.times) + ", "
                + (fb == null ? null : fb.dust.getDisplayName() + " x" + fb.times) + ", " + fi + ")");
        SelfTest.check(XpCrafters.recipeFor(new ItemStack(Blocks.dirt)) == null && XpCrafters.recipeFor(titaniumDust) == null,
            "dirt and titanium dust have no liquid XP recipe");
    }

    int cost(ItemStack s) {
        XpCrafters.XpRecipe r = XpCrafters.recipeFor(s);
        return r == null ? -1 : r.cost;
    }

    // ---- the crafting recipe ------------------------------------------------------------------------------------------

    void craftingRecipe() {
        ItemStack tank = new ItemStack(GregTechAPI.sBlockMachines, 1, 817);
        ItemStack lv = new ItemStack(GregTechAPI.sBlockMachines, 1, 818);
        ItemStack diamond = new ItemStack(Blocks.diamond_block);
        ItemStack made = craft(tank, diamond);
        crafterItem = XpCrafters.isCrafterItem(made) ? made : null;
        SelfTest.check(crafterItem != null && made.getDisplayName()
            .contains("Valier XP Crafter") && lore(made).contains("Makes: nothing yet"), "a ULV fluid tank and a block of "
                + "diamond craft a Valier XP Crafter (" + (made == null ? null : made.getDisplayName() + ": " + lore(made)) + ")");
        ItemStack alone = craft(tank), wrongTank = craft(lv, diamond), again = craft(made == null ? tank : made, diamond),
            extra = craft(tank, diamond, new ItemStack(Blocks.dirt));
        SelfTest.check(!XpCrafters.isCrafterItem(alone) && !XpCrafters.isCrafterItem(wrongTank) && !XpCrafters.isCrafterItem(again)
            && !XpCrafters.isCrafterItem(extra), "not the tank alone, an LV tank, a crafter again, or with dirt as well");
        ContainerWorkbench bench = new ContainerWorkbench(player.inventory, world, bx, y, bz);
        bench.craftMatrix.setInventorySlotContents(0, tank.copy());
        bench.craftMatrix.setInventorySlotContents(4, diamond.copy());
        Slot slot = XpCrafters.CraftSync.resultSlot(bench);
        SelfTest.check(slot != null && slot.slotNumber == 0 && XpCrafters.isCrafterItem(slot.getStack()),
            "in a crafting table the crafter is in the output slot, the slot sent to the player (" + slot + ")");
    }

    ItemStack craft(ItemStack... stacks) {
        InventoryCrafting grid = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer p) {
                return true;
            }
        }, 3, 3);
        for (int i = 0; i < stacks.length; i++) grid.setInventorySlotContents(i * 2, stacks[i].copy());
        return CraftingManager.getInstance()
            .findMatchingRecipe(grid, world);
    }

    // ---- the crafter ------------------------------------------------------------------------------------------------

    void crafter() {
        int[] p = { bx + 4, y, bz + 4 };
        int[] out = { bx + 5, y, bz + 4 };
        XpCrafters.Crafter c = place(crafterItem.copy(), p);
        MTEBasicTank tank = XpCrafters.tankAt(world, p[0], p[1], p[2]);
        SelfTest.check(c != null && tank != null && c.makes == null && !c.full,
            "placed as a player places it, it is a ULV tank that is an XP crafter, making nothing yet (" + tank + ")");
        if (c == null || tank == null) return;
        XpCrafters.Data d = XpCrafters.data(world, false);
        world.setBlock(out[0], out[1], out[2], Blocks.chest);
        placed.add(out);
        IInventory chest = (IInventory) world.getTileEntity(out[0], out[1], out[2]);

        hold(null);
        player.setSneaking(true);
        PlayerInteractEvent ev = click(p);
        SelfTest.check(ev.isCanceled() && said[0] != null && said[0].startsWith("XP crafter: makes nothing yet")
            && said[0].contains(tinyGallium.getDisplayName()), "sneak-right-click with an empty hand shows its status and "
                + "what it can make (" + said[0] + ")");

        hold(copy(tinyGallium, 3));
        player.setSneaking(false);
        ev = click(p);
        SelfTest.check(!ev.isCanceled() && c.makes == null, "without sneaking, a tiny pile of gallium dust chooses nothing");
        player.setSneaking(true);
        ev = click(p);
        SelfTest.check(ev.isCanceled() && c.makes != null && c.makes.isItemEqual(tinyGallium) && held() == 3 && said[0] != null
            && said[0].contains("8,000 mB"), "sneak-right-click with a tiny pile of gallium dust makes it make those, 8,000 mB "
                + "each, and the pile isn't used (" + said[0] + ")");
        if (hud != null) hud.chosen(p, tinyGallium);

        hold(copy(tinyArsenic, 1));
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        try {
            ev = click(p);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(c.makes.isItemEqual(tinyGallium) && said[0] != null && said[0].contains("protected"),
            "where protected, what it makes can't be changed (" + said[0] + ")");
        hold(copy(titaniumDust, 1));
        ev = click(p);
        SelfTest.check(ev.isCanceled() && c.makes.isItemEqual(tinyGallium) && said[0] != null
            && said[0].startsWith("No liquid XP recipe makes"), "titanium dust is refused: no XP recipe makes it");

        tank.fill(new FluidStack(xp, 5000), true);
        XpCrafters.run(world, d, c);
        SelfTest.check(count(chest, tinyGallium) == 0 && c.bank == 5000 && amount(tank) == 0
            && c.status == XpCrafters.Status.NEEDS_XP, "with 5,000 mB of liquid XP it takes it and waits for more (" + state(c, tank, chest) + ")");
        tank.fill(new FluidStack(xp, 3000), true);
        XpCrafters.run(world, d, c);
        SelfTest.check(count(chest, tinyGallium) == 1 && c.bank == 0 && amount(tank) == 0 && c.made == 1,
            "3,000 mB more: a tiny pile of gallium dust goes into the chest (" + state(c, tank, chest) + ")");
        tank.fill(new FluidStack(xp, 20000), true);
        XpCrafters.run(world, d, c);
        SelfTest.check(count(chest, tinyGallium) == 3 && c.bank == 4000 && amount(tank) == 0,
            "20,000 mB: two more at once, and 4,000 mB taken towards the next (" + state(c, tank, chest) + ")");
        if (hud != null) hud.working(p, tinyGallium);

        hold(new ItemStack(Blocks.emerald_block, 2));
        ev = click(p);
        SelfTest.check(ev.isCanceled() && c.full && c.fullPaid != null && held() == 1 && said[0] != null
            && said[0].contains(galliumDust.getDisplayName()) && said[0].contains("72,000 mB"), "an emerald block switches full "
                + "dust on (one used): gallium dust from 72,000 mB each (" + said[0] + ")");
        if (hud != null) hud.full(p, galliumDust);
        tank.fill(new FluidStack(xp, 32000), true);
        XpCrafters.run(world, d, c);
        boolean waited = c.bank == 36000 && count(chest, galliumDust) == 0;
        tank.fill(new FluidStack(xp, 32000), true);
        XpCrafters.run(world, d, c);
        tank.fill(new FluidStack(xp, 8000), true);
        XpCrafters.run(world, d, c);
        SelfTest.check(waited && count(chest, galliumDust) == 1 && c.bank == 4000 && amount(tank) == 0,
            "a tank at a time it takes all 72,000 mB, then one gallium dust comes out (" + state(c, tank, chest) + ")");
        hold(null);
        ev = click(p);
        SelfTest.check(said[0] != null && said[0].contains("makes " + galliumDust.getDisplayName()) && said[0].contains("Full dust is on")
            && said[0].contains("4,000 mB already taken"), "its status says so (" + said[0] + ")");

        world.func_147480_a(p[0], p[1], p[2], true); // broken with drops
        ItemStack dropped = null;
        for (EntityItem e : itemsNear(p)) {
            if (XpCrafters.isCrafterItem(e.getEntityItem())) dropped = e.getEntityItem();
            e.setDead();
        }
        XpCrafters.Crafter kept = dropped == null ? null
            : XpCrafters.Crafter.read(0, 0, 0, dropped.getTagCompound()
                .getCompoundTag(XpCrafters.ITEM_TAG));
        SelfTest.check(dropped != null && XpCrafters.crafterAt(world, p[0], p[1], p[2]) == null && kept.makes != null
            && kept.makes.isItemEqual(tinyGallium) && kept.full && kept.fullPaid != null && kept.fullPaid.getItem() == Item.getItemFromBlock(Blocks.emerald_block)
            && kept.bank == 4000 && lore(dropped).contains("Makes: " + galliumDust.getDisplayName())
            && lore(dropped).contains("Holding 4,000 mB"), "broken, it drops as the XP crafter item, with what it makes, the "
                + "emerald block and the 4,000 mB (" + (dropped == null ? null : lore(dropped)) + ")");
        if (dropped == null) return;
        c = place(dropped, p);
        tank = XpCrafters.tankAt(world, p[0], p[1], p[2]);
        SelfTest.check(c != null && tank != null && c.full && c.bank == 4000 && c.makes != null && c.makes.isItemEqual(tinyGallium),
            "placed again, it carries on where it was");
        if (c == null || tank == null) return;

        hold(null);
        player.setSneaking(true);
        ev = hit(p);
        SelfTest.check(ev.isCanceled() && !c.full && c.fullPaid == null && inInventory(Blocks.emerald_block) == 1,
            "a sneaking empty-hand hit gives the emerald block back and switches full dust off (" + said[0] + ")");

        for (int i = 0; i < chest.getSizeInventory(); i++) chest.setInventorySlotContents(i, null);
        world.setBlockToAir(out[0], out[1], out[2]);
        tank.fill(new FluidStack(xp, 8000), true);
        XpCrafters.run(world, d, c);
        SelfTest.check(c.status == XpCrafters.Status.NO_OUTPUT && amount(tank) == 8000 && c.bank == 4000,
            "with nothing touching it, it takes no liquid XP and says why (" + state(c, tank, null) + ")");
        if (hud != null) hud.noOutput(p, xp);
        world.setBlock(out[0], out[1], out[2], Blocks.chest);
        chest = (IInventory) world.getTileEntity(out[0], out[1], out[2]);
        for (int i = 0; i < chest.getSizeInventory(); i++) chest.setInventorySlotContents(i, new ItemStack(Blocks.stone, 64));
        XpCrafters.run(world, d, c);
        SelfTest.check(c.status == XpCrafters.Status.FULL && c.pending != null && c.pending.isItemEqual(tinyGallium)
            && amount(tank) == 4000 && c.bank == 0, "with the chest full, it keeps the one it made and waits (" + state(c, tank, chest) + ")");
        chest.setInventorySlotContents(0, null);
        XpCrafters.run(world, d, c);
        SelfTest.check(c.pending == null && count(chest, tinyGallium) == 1, "with room again, the waiting one goes in");

        NBTTagCompound saved = new NBTTagCompound();
        d.writeToNBT(saved);
        XpCrafters.Data loaded = new XpCrafters.Data(XpCrafters.Data.NAME);
        loaded.readFromNBT(saved);
        XpCrafters.Crafter back = loaded.at(p[0], p[1], p[2]);
        SelfTest.check(back != null && back.makes != null && back.makes.isItemEqual(tinyGallium) && back.bank == c.bank
            && back.made == c.made && !back.full, "its record saves and loads");

        world.setBlockToAir(p[0], p[1], p[2]); // no drops: a Matter Manipulator or a frame mover
        XpCrafters.run(world, d, c);
        SelfTest.check(XpCrafters.crafterAt(world, p[0], p[1], p[2]) == null, "removed without dropping, it is forgotten");
        if (hud != null) hud.forgotten(p);
    }

    void plainTank() {
        int[] q = { bx + 10, y, bz + 4 };
        hold(new ItemStack(GregTechAPI.sBlockMachines, 1, 817));
        player.setSneaking(false);
        boolean ok = player.getHeldItem()
            .tryPlaceItemIntoWorld(player, world, q[0], q[1] - 1, q[2], 1, 0.5F, 1F, 0.5F);
        placed.add(q);
        hold(copy(tinyGallium, 1));
        player.setSneaking(true);
        PlayerInteractEvent ev = click(q);
        SelfTest.check(ok && XpCrafters.tankAt(world, q[0], q[1], q[2]) != null && ev.isCanceled()
            && XpCrafters.crafterAt(world, q[0], q[1], q[2]) == null && said[0] != null && said[0].contains("plain tank")
            && said[0].contains("Block of Diamond"), "on a plain ULV tank a tiny pile of gallium dust only gets a hint (" + said[0] + ")");
        if (hud != null) hud.plainTank(q);
    }

    // ---- chain armour ------------------------------------------------------------------------------------------------

    void chainArmour() {
        int helmet = steel(Items.chainmail_helmet), legs = steel(Items.chainmail_leggings), boots = steel(Items.chainmail_boots),
            chestplate = steel(Items.chainmail_chestplate);
        SelfTest.check(helmet == 1 && legs == 2 && boots == 1 && chestplate == 2, "chain armour macerates into whole steel "
            + "dust: helmet " + helmet + ", leggings " + legs + ", boots " + boots + ", chestplate " + chestplate);
        GTRecipe h = RecipeMaps.maceratorRecipes.findRecipeQuery()
            .items(new ItemStack(Items.chainmail_helmet))
            .voltage(30)
            .find();
        GTRecipe l = RecipeMaps.maceratorRecipes.findRecipeQuery()
            .items(new ItemStack(Items.chainmail_leggings))
            .voltage(30)
            .find();
        SelfTest.check(h != null && l != null && h.mOutputs[0].stackSize == 1 && l.mOutputs[0].stackSize == 2
            && h.mOutputs[0].isItemEqual(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Steel, 1)),
            "a macerator looking the helmet and leggings up gets 1 and 2 steel dust");
    }

    /** Steel dust from macerating the item: -1 if there is no recipe, -2 if one gives something else. */
    int steel(Item item) {
        ItemStack steel = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Steel, 1);
        int found = -1;
        for (GTRecipe r : RecipeMaps.maceratorRecipes.getAllRecipes()) {
            if (r.mInputs == null || r.mInputs.length != 1 || r.mInputs[0] == null || r.mInputs[0].getItem() != item) continue;
            if (r.mOutputs.length != 1 || r.mOutputs[0] == null || !r.mOutputs[0].isItemEqual(steel)
                || (r.mOutputChances != null && r.mOutputChances[0] != 10000)) return -2;
            found = r.mOutputs[0].stackSize;
        }
        return found;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** Places the item on the ground as a player does (Forge's place event fires); the crafter there, or null. */
    XpCrafters.Crafter place(ItemStack item, int[] p) {
        hold(item);
        player.setSneaking(false);
        item.tryPlaceItemIntoWorld(player, world, p[0], p[1] - 1, p[2], 1, 0.5F, 1F, 0.5F);
        placed.add(p);
        return XpCrafters.crafterAt(world, p[0], p[1], p[2]);
    }

    void hold(ItemStack stack) {
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, stack);
    }

    int held() {
        ItemStack s = player.getHeldItem();
        return s == null ? 0 : s.stackSize;
    }

    int inInventory(net.minecraft.block.Block block) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s != null && s.getItem() == Item.getItemFromBlock(block)) n += s.stackSize;
        return n;
    }

    static ItemStack copy(ItemStack s, int n) {
        ItemStack c = s.copy();
        c.stackSize = n;
        return c;
    }

    PlayerInteractEvent click(int[] p) {
        said[0] = null;
        return ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, p[0], p[1], p[2], 1,
            world);
    }

    PlayerInteractEvent hit(int[] p) {
        said[0] = null;
        return ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, p[0], p[1], p[2], 1,
            world);
    }

    static int amount(MTEBasicTank tank) {
        FluidStack f = tank.getFluid();
        return f == null ? 0 : f.amount;
    }

    static int count(IInventory inv, ItemStack what) {
        int n = 0;
        if (inv == null) return 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.isItemEqual(what)) n += s.stackSize;
        }
        return n;
    }

    String state(XpCrafters.Crafter c, MTEBasicTank tank, IInventory chest) {
        return "bank " + c.bank + ", tank " + amount(tank) + ", made " + c.made + ", " + c.status + (chest == null ? ""
            : ", chest: " + count(chest, tinyGallium) + " tiny gallium, " + count(chest, galliumDust) + " gallium dust");
    }

    static String lore(ItemStack s) {
        if (s == null || !s.hasTagCompound()) return "";
        NBTTagList lore = s.getTagCompound()
            .getCompoundTag("display")
            .getTagList("Lore", 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lore.tagCount(); i++) sb.append(i > 0 ? " / " : "")
            .append(lore.getStringTagAt(i)
                .replaceAll("§.", ""));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    List<EntityItem> itemsNear(int[] p) {
        return world.getEntitiesWithinAABB(EntityItem.class,
            AxisAlignedBB.getBoundingBox(p[0] - 2, p[1] - 2, p[2] - 2, p[0] + 3, p[1] + 3, p[2] + 3));
    }

    /** Empties and removes everything in the test chunk above the ground, its crafter records and dropped items. */
    void clear() {
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                for (int yy = Math.max(1, y); yy < y + 4; yy++) {
                    TileEntity te = world.getTileEntity(x, yy, z);
                    if (te instanceof IInventory) for (int i = 0; i < ((IInventory) te).getSizeInventory(); i++)
                        ((IInventory) te).setInventorySlotContents(i, null);
                    if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
                }
            }
        }
        XpCrafters.Data d = XpCrafters.data(world, false);
        if (d != null) {
            for (XpCrafters.Crafter c : new ArrayList<>(d.crafters.values())) {
                if (c.x >= bx && c.x < bx + 16 && c.z >= bz && c.z < bz + 16) d.remove(c);
            }
        }
        for (Object o : world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(bx, 0, bz, bx + 16, 256, bz + 16)))
            ((EntityItem) o).setDead();
    }
}
