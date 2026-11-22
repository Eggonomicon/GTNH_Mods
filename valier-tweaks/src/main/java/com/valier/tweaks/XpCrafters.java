package com.valier.tweaks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.covers.CoverRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.util.GTUtility;

/**
 * XP crafters: three GregTech tanks (GT++'s Ultra Low Voltage Fluid Tank by default), two LV pumps, an LV conveyor, an
 * LV robot arm, a crafting table and an LV circuit craft a "Valier XP Crafter" ({@link Recipe}). Placed, it is still
 * the tank to clients and pipes: it fills with liquid XP the usual way. Once a second it takes the liquid XP it needs and
 * makes one of the things GTNH's XP bucket crafting recipes make (a tiny pile of gallium dust from 8 buckets, and so
 * on), as many as it has XP for, into its store of 9 stacks, and hands its store to the chests, inventories and pipes
 * touching it. When its tank hasn't enough, it takes solid XP (Tinkers' essence berries, from CropsNH's berry farms;
 * {@link #solidXp}) from the chests and inventories touching it.
 * <ul>
 * <li>Right-click it (with an empty hand, or anything but a GregTech tool or cover, which GregTech uses on its
 * machines): its menu ({@link XpMenu}), a chest window any client shows: its liquid XP, everything it can make (click
 * one), its store (take from it), full dust.</li>
 * <li>Sneak-right-click it with what it should make to choose (the item isn't used up); with an empty hand for its
 * status in chat.</li>
 * <li>Sneak-right-click it with a block of emerald (or use its menu): it makes the whole dust instead of a tiny or small
 * pile, for 9 or 4 times the XP (the emerald block stays in it; again, or a sneaking empty-hand hit, gives it back).</li>
 * <li>Broken, it drops as the XP crafter item again, with its recipe, emerald block and the XP it was holding; its
 * store drops as items.</li>
 * </ul>
 * Server side only: clients don't know the recipe, so the crafting grid's output is sent to them ({@link CraftSync}).
 * The crafters of a world are kept in its {@code data/valiertweaks_xpcrafters.dat}, by position; one removed without
 * dropping (a Matter Manipulator, a frame mover) is forgotten and stays a plain tank. With WAILA on the server, what a
 * crafter makes and is doing goes along with the block data WAILA sends a player looking at it ({@link XpCrafterWaila});
 * the optional Valier Client mod shows it, other clients ignore it.
 */
public final class XpCrafters {

    /** The tag in a Valier XP Crafter item holding what the crafter knows (see {@link Crafter#write}). */
    static final String ITEM_TAG = "ValierXpCrafter";
    static final String NAME = "Valier XP Crafter";
    /** At most this many crafts per crafter per second. */
    static final int MAX_PER_SECOND = 64;

    enum Status {
        NO_RECIPE("nothing to make yet"),
        WORKING("working"),
        NEEDS_XP("waiting for liquid XP (or essence berries)"),
        WRONG_FLUID("waiting: its tank holds something other than liquid XP"),
        NO_OUTPUT("waiting: its store is full, and nothing touching it takes items"),
        FULL("waiting: its store and everything touching it are full"),
        OFF("XP crafters are switched off on this server");

        final String words;

        Status(String words) {
            this.words = words;
        }
    }

    private int tick;

    static void register() {
        XpCrafters crafters = new XpCrafters();
        MinecraftForge.EVENT_BUS.register(crafters); // clicks, placing, drops
        FMLCommonHandler.instance()
            .bus()
            .register(crafters); // the crafting, on server ticks
        FMLCommonHandler.instance()
            .bus()
            .register(new CraftSync());
        RecipeSorter.register(ValierTweaks.MODID + ":xpcrafter", Recipe.class, RecipeSorter.Category.SHAPED,
            "after:forge:shapedore");
        Recipe recipe = Recipe.make();
        if (recipe != null) { // the shaped recipe always (it checks the setting); the assembler one only when switched on
            GameRegistry.addRecipe(recipe);
            // The assembler's: one tank and one pump instead of three and two.
            Item tank = LootSpawners.itemOf(TweaksConfig.xpCrafterTank);
            if (TweaksConfig.xpCrafter && tank != null) ValierRecipes.assembler(recipe.getRecipeOutput(), 10, ValierRecipes.LV,
                new ItemStack(tank, 1, Math.max(0, LootSpawners.metaOf(TweaksConfig.xpCrafterTank))), ItemList.Electric_Pump_LV.get(1),
                ItemList.Conveyor_Module_LV.get(1), ItemList.Robot_Arm_LV.get(1), new ItemStack(Blocks.crafting_table),
                ValierRecipes.ore(ValierRecipes.circuit(1), 1));
        }
        hud = Loader.isModLoaded("Waila");
        if (hud) {
            FMLInterModComms.sendMessage("Waila", "register", "com.valier.tweaks.XpCrafterWaila.callbackRegister");
            // What players read (WAILA runs it on both sides; only clients draw it).
            FMLInterModComms.sendMessage("Waila", "register", "com.valier.tweaks.client.XpCrafterHud.callbackRegister");
        }
    }

    /** At server start: the recipes are looked up again, and the saved crafters read again. */
    static void forget() {
        recipes = null;
        loaded.clear();
        HUD.clear();
    }

    // ---- what a crafter knows ---------------------------------------------------------------------------------------

    static final class Crafter {

        /** Stacks its store holds. */
        static final int STORE = 9;
        final int x, y, z;
        /** One of the XP recipes' outputs (a single item), or null. */
        ItemStack makes;
        /** Makes the whole dust of a tiny or small pile. */
        boolean full;
        /** The full-dust item paid into it (none in creative). */
        ItemStack fullPaid;
        /** Liquid XP (mB) already taken out of its tank towards the next craft. */
        int bank;
        /** Made but not taken by anything touching it yet: a player takes these in its menu. */
        final ItemStack[] store = new ItemStack[STORE];
        long made;
        Status status = Status.NO_RECIPE;

        Crafter(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** For the world's save, or (forItem) for the item it drops as: no position, no store (that drops). */
        NBTTagCompound write(boolean forItem) {
            NBTTagCompound t = new NBTTagCompound();
            if (!forItem) {
                t.setInteger("x", x);
                t.setInteger("y", y);
                t.setInteger("z", z);
                NBTTagList list = new NBTTagList();
                for (int i = 0; i < STORE; i++) {
                    if (store[i] == null) continue;
                    NBTTagCompound s = store[i].writeToNBT(new NBTTagCompound());
                    s.setByte("Slot", (byte) i);
                    list.appendTag(s);
                }
                if (list.tagCount() > 0) t.setTag("Store", list);
            }
            if (makes != null) t.setTag("Makes", makes.writeToNBT(new NBTTagCompound()));
            t.setBoolean("Full", full);
            if (fullPaid != null) t.setTag("FullPaid", fullPaid.writeToNBT(new NBTTagCompound()));
            t.setInteger("Bank", bank);
            t.setLong("Made", made);
            return t;
        }

        static Crafter read(int x, int y, int z, NBTTagCompound t) {
            Crafter c = new Crafter(x, y, z);
            c.makes = t.hasKey("Makes", 10) ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Makes")) : null;
            c.full = t.getBoolean("Full");
            c.fullPaid = t.hasKey("FullPaid", 10) ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("FullPaid")) : null;
            c.bank = Math.max(0, t.getInteger("Bank"));
            NBTTagList list = t.getTagList("Store", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound s = list.getCompoundTagAt(i);
                int slot = s.getByte("Slot");
                if (slot >= 0 && slot < STORE) c.store[slot] = ItemStack.loadItemStackFromNBT(s);
            }
            if (t.hasKey("Pending", 10)) { // 1.10.0 and before: one made but not handed over
                ItemStack pending = ItemStack.loadItemStackFromNBT(t.getCompoundTag("Pending"));
                if (pending != null && c.store[0] == null) c.store[0] = pending;
            }
            c.made = t.getLong("Made");
            return c;
        }

        /** How many items its store holds. */
        int stored() {
            int n = 0;
            for (ItemStack s : store) if (s != null) n += s.stackSize;
            return n;
        }

        String where() {
            return x + " " + y + " " + z;
        }
    }

    /** The crafters of one world (dimension), saved with it. */
    public static final class Data extends WorldSavedData {

        static final String NAME = "valiertweaks_xpcrafters";
        final Map<Long, Crafter> crafters = new LinkedHashMap<>();
        /** The dimension it belongs to, once {@link #data} has it (for what WAILA is told). */
        Integer dim;

        public Data(String name) {
            super(name);
        }

        Crafter at(int x, int y, int z) {
            return crafters.get(key(x, y, z));
        }

        void put(Crafter c) {
            crafters.put(key(c.x, c.y, c.z), c);
            markDirty();
        }

        void remove(Crafter c) {
            crafters.remove(key(c.x, c.y, c.z));
            markDirty();
            if (dim != null) hudRemove(dim, c);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            crafters.clear();
            NBTTagList list = tag.getTagList("Crafters", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                Crafter c = Crafter.read(t.getInteger("x"), t.getInteger("y"), t.getInteger("z"), t);
                crafters.put(key(c.x, c.y, c.z), c);
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList list = new NBTTagList();
            for (Crafter c : crafters.values()) list.appendTag(c.write(false));
            tag.setTag("Crafters", list);
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Worlds whose crafters are read, and those that have none saved (so the file isn't looked for every second). */
    private static final Map<World, Data> loaded = new WeakHashMap<>();
    private static final Data NONE = new Data("none");

    static Data data(World world, boolean create) {
        Data d = loaded.get(world);
        if (d == null) {
            d = (Data) world.perWorldStorage.loadData(Data.class, Data.NAME);
            if (d == null) d = NONE;
            else d.dim = world.provider.dimensionId;
            loaded.put(world, d);
        }
        if (d != NONE) return d;
        if (!create) return null;
        d = new Data(Data.NAME);
        d.dim = world.provider.dimensionId;
        world.perWorldStorage.setData(Data.NAME, d);
        loaded.put(world, d);
        return d;
    }

    static Crafter crafterAt(World world, int x, int y, int z) {
        Data d = data(world, false);
        return d == null ? null : d.at(x, y, z);
    }

    // ---- the XP recipes -------------------------------------------------------------------------------------------

    /** One crafting recipe made only of liquid XP containers: what it makes and how much liquid XP that is. */
    static final class XpRecipe {

        final ItemStack output;
        final int cost;

        XpRecipe(ItemStack output, int cost) {
            this.output = output;
            this.cost = cost;
        }
    }

    /** For a tiny or small pile: the whole dust, and how many times the XP it takes. */
    static final class Full {

        final ItemStack dust;
        final int times;

        Full(ItemStack dust, int times) {
            this.dust = dust;
            this.times = times;
        }
    }

    private static Map<String, XpRecipe> recipes;

    /** The XP recipes by output (item and damage), cheapest first per output; read from the crafting recipes once. */
    static Map<String, XpRecipe> recipes() {
        if (recipes != null) return recipes;
        Map<String, XpRecipe> map = new LinkedHashMap<>();
        for (Object o : CraftingManager.getInstance()
            .getRecipeList()) {
            if (!(o instanceof IRecipe)) continue;
            IRecipe r = (IRecipe) o;
            List<?> in = inputs(r);
            ItemStack out = r.getRecipeOutput();
            if (in == null || out == null || out.getItem() == null || out.stackSize <= 0) continue;
            int cost = 0;
            boolean xpOnly = true;
            for (Object i : in) {
                if (i == null) continue;
                int mb = xpIn(i);
                if (mb <= 0) {
                    xpOnly = false;
                    break;
                }
                cost += mb;
            }
            if (!xpOnly || cost <= 0) continue;
            String key = itemKey(out);
            XpRecipe old = map.get(key);
            if (old == null || (double) cost / out.stackSize < (double) old.cost / old.output.stackSize)
                map.put(key, new XpRecipe(out.copy(), cost));
        }
        List<XpRecipe> sorted = new ArrayList<>();
        for (XpRecipe r : map.values()) sorted.add(new XpRecipe(r.output, configuredCost(r.output, r.cost)));
        sorted.sort((a, b) -> Integer.compare(a.cost, b.cost));
        recipes = new LinkedHashMap<>();
        for (XpRecipe r : sorted) recipes.put(itemKey(r.output), r);
        ValierTweaks.LOG.info("XP crafters: {} liquid XP recipes (costs at {}%, {} set by item)", recipes.size(),
            TweaksConfig.xpCrafterCostPercent, TweaksConfig.xpCrafterCosts.length);
        for (String entry : TweaksConfig.xpCrafterCosts) {
            boolean used = false;
            for (XpRecipe r : recipes.values()) used |= costEntry(entry, r.output) > 0;
            if (!used) ValierTweaks.LOG.warn("XP crafters: xpCrafterCosts entry \"{}\" sets no recipe's cost (it should be "
                + "item=mB, the item a liquid XP recipe makes, by name or ore dictionary name)", entry);
        }
        return recipes;
    }

    /** Built again on next use (the self-test, after changing the cost settings). */
    static void forgetRecipes() {
        recipes = null;
    }

    /**
     * The liquid XP one craft takes on this server: an {@code xpCrafterCosts} entry for what it makes, else GTNH's cost
     * at {@code xpCrafterCostPercent} (at least 1 mB).
     */
    static int configuredCost(ItemStack out, int gtnh) {
        for (String entry : TweaksConfig.xpCrafterCosts) {
            int mb = costEntry(entry, out);
            if (mb > 0) return mb;
        }
        long c = Math.round(gtnh * (TweaksConfig.xpCrafterCostPercent / 100.0));
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, c));
    }

    /** An {@code xpCrafterCosts} entry's mB if it is for this item (mod:item[:meta] or an ore dictionary name), else 0. */
    static int costEntry(String entry, ItemStack out) {
        int eq = entry.lastIndexOf('=');
        if (eq <= 0 || out == null || out.getItem() == null) return 0;
        int mb;
        try {
            mb = Integer.parseInt(entry.substring(eq + 1)
                .trim());
        } catch (NumberFormatException e) {
            return 0;
        }
        if (mb <= 0) return 0;
        String key = entry.substring(0, eq)
            .trim();
        if (key.indexOf(':') >= 0) return LootSpawners.matches(key, out) ? mb : 0;
        for (int id : OreDictionary.getOreIDs(out)) if (OreDictionary.getOreName(id)
            .equalsIgnoreCase(key)) return mb;
        return 0;
    }

    static XpRecipe recipeFor(ItemStack stack) {
        return stack == null || stack.getItem() == null ? null : recipes().get(itemKey(stack));
    }

    static String itemKey(ItemStack s) {
        return Item.itemRegistry.getNameForObject(s.getItem()) + ":" + s.getItemDamage();
    }

    /** A recipe's ingredients, for the kinds of recipe whose ingredients can be read; else null. */
    static List<?> inputs(IRecipe r) {
        try {
            if (r instanceof ShapedOreRecipe) return Arrays.asList(((ShapedOreRecipe) r).getInput());
            if (r instanceof ShapelessOreRecipe) return ((ShapelessOreRecipe) r).getInput();
            if (r instanceof ShapedRecipes) return Arrays.asList(((ShapedRecipes) r).recipeItems);
            if (r instanceof ShapelessRecipes) return ((ShapelessRecipes) r).recipeItems;
        } catch (Throwable e) {
            ValierTweaks.LOG.debug("XP crafters: couldn't read {}: {}", r.getClass()
                .getName(), e);
        }
        return null;
    }

    /** The liquid XP in an ingredient (an item, or an ore dictionary list where every choice holds liquid XP), or 0. */
    static int xpIn(Object ingredient) {
        if (ingredient instanceof ItemStack) return xpAmount((ItemStack) ingredient);
        if (!(ingredient instanceof List) || ((List<?>) ingredient).isEmpty()) return 0;
        int least = 0;
        for (Object o : (List<?>) ingredient) {
            int mb = o instanceof ItemStack ? xpAmount((ItemStack) o) : 0;
            if (mb <= 0) return 0;
            least = least == 0 ? mb : Math.min(least, mb);
        }
        return least;
    }

    static int xpAmount(ItemStack s) {
        if (s == null || s.getItem() == null) return 0;
        FluidStack f = null;
        try {
            f = FluidContainerRegistry.getFluidForFilledItem(s);
            if (f == null && s.getItem() instanceof IFluidContainerItem) f = ((IFluidContainerItem) s.getItem()).getFluid(s);
        } catch (Throwable e) {
            return 0;
        }
        return f != null && isXp(f.getFluid()) ? f.amount : 0;
    }

    static boolean isXp(Fluid fluid) {
        if (fluid == null) return false;
        for (String name : TweaksConfig.xpCrafterFluids) if (name.trim()
            .equalsIgnoreCase(fluid.getName())) return true;
        return false;
    }

    /** For a tiny or small pile of dust: the whole dust (as many as the piles) and the XP multiplier; else null. */
    static Full full(ItemStack out) {
        if (out == null || out.getItem() == null) return null;
        for (int id : OreDictionary.getOreIDs(out)) {
            String name = OreDictionary.getOreName(id);
            String material;
            int times;
            if (name.startsWith("dustTiny") && name.length() > 8) {
                material = name.substring(8);
                times = TweaksConfig.xpCrafterTinyTimes;
            } else if (name.startsWith("dustSmall") && name.length() > 9) {
                material = name.substring(9);
                times = TweaksConfig.xpCrafterSmallTimes;
            } else continue;
            ItemStack dust = firstOre("dust" + material);
            if (dust == null) continue;
            dust.stackSize = out.stackSize;
            return new Full(dust, times);
        }
        return null;
    }

    /** An ore dictionary entry's item, GregTech's if it has one. */
    static ItemStack firstOre(String name) {
        ItemStack pick = null;
        boolean pickGt = false;
        for (ItemStack s : OreDictionary.getOres(name)) {
            if (s == null || s.getItem() == null || s.getItemDamage() == OreDictionary.WILDCARD_VALUE) continue;
            String reg = Item.itemRegistry.getNameForObject(s.getItem());
            boolean gt = reg != null && reg.startsWith("gregtech:");
            if (pick == null || (gt && !pickGt)) {
                pick = s;
                pickGt = gt;
            }
        }
        return pick == null ? null : pick.copy();
    }

    /** What one craft makes now. */
    static ItemStack product(Crafter c, XpRecipe r) {
        Full f = c.full ? full(r.output) : null;
        return f != null ? f.dust.copy() : r.output.copy();
    }

    /** The liquid XP one craft takes now. */
    static int cost(Crafter c, XpRecipe r) {
        Full f = c.full ? full(r.output) : null;
        return f != null ? (int) Math.min(Integer.MAX_VALUE, (long) r.cost * Math.max(1, f.times)) : r.cost;
    }

    // ---- solid XP -------------------------------------------------------------------------------------------------

    /** The liquid XP (mB) one of this item counts as ({@link TweaksConfig#xpCrafterSolidXp}), or 0 if it isn't solid XP. */
    static int solidXp(ItemStack s) {
        if (s == null || s.getItem() == null) return 0;
        for (String entry : TweaksConfig.xpCrafterSolidXp) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            try {
                int mb = Integer.parseInt(entry.substring(eq + 1)
                    .trim());
                if (mb > 0 && LootSpawners.matches(entry.substring(0, eq)
                    .trim(), s)) return mb;
            } catch (NumberFormatException e) {
                // not a number: not an entry
            }
        }
        return 0;
    }

    /**
     * Takes solid XP from the chests and inventories touching the crafter (by their rules for that side, like a hopper),
     * as few as make {@code need} mB, or all there are. Returns the mB taken.
     */
    static int takeSolidXp(World w, Crafter c, int need) {
        int got = 0;
        // Its own tank first (1.13.1): pipes and conveyors put berries straight into a GregTech tank's input slot.
        TileEntity own = w.blockExists(c.x, c.y, c.z) ? w.getTileEntity(c.x, c.y, c.z) : null;
        if (own instanceof IInventory && !own.isInvalid()) got += takeSolidXp((IInventory) own, -1, need);
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (got >= need) break;
            int x = c.x + dir.offsetX, y = c.y + dir.offsetY, z = c.z + dir.offsetZ;
            if (y < 0 || y >= w.getHeight() || !w.blockExists(x, y, z)) continue; // never load a chunk
            TileEntity te = w.getTileEntity(x, y, z);
            if (!(te instanceof IInventory) || te.isInvalid()) continue;
            got += takeSolidXp((IInventory) te, dir.getOpposite()
                .ordinal(), need - got);
        }
        return got;
    }

    /**
     * Solid XP items worth at least {@code need} mB (whole items, as few as that takes, or all there are) out of one
     * inventory; what they were worth. {@code side}: the side it is reached from, whose slot rules apply; -1 (the
     * crafter's own tank): every slot.
     */
    static int takeSolidXp(IInventory inv, int side, int need) {
        int got = 0;
        boolean sided = side >= 0 && inv instanceof ISidedInventory;
        int[] slots = sided ? ((ISidedInventory) inv).getAccessibleSlotsFromSide(side) : null;
        int n = slots == null ? inv.getSizeInventory() : slots.length;
        boolean changed = false;
        for (int k = 0; k < n && got < need; k++) {
            int slot = slots == null ? k : slots[k];
            ItemStack s = inv.getStackInSlot(slot);
            int mb = solidXp(s);
            if (mb <= 0 || (sided && !((ISidedInventory) inv).canExtractItem(slot, s, side))) continue;
            int take = Math.min(s.stackSize, (need - got + mb - 1) / mb);
            if (take <= 0) continue;
            ItemStack out = inv.decrStackSize(slot, take);
            if (out == null || out.stackSize <= 0) continue;
            got += out.stackSize * mb;
            changed = true;
        }
        if (changed) inv.markDirty();
        return got;
    }

    // ---- the tank ---------------------------------------------------------------------------------------------------

    /**
     * Whether a GregTech tank may take this item into its slots (1.14.0, from {@code MixinBasicTank}): an XP crafter's
     * tank only takes liquid XP containers and solid XP, so pipes, hoppers and other crafters can't block its input slot
     * with dust. Other tanks, and every tank with XP crafters off, are left to GregTech.
     */
    public static boolean tankMayTake(gregtech.api.interfaces.tileentity.IGregTechTileEntity base, ItemStack stack) {
        if (!TweaksConfig.xpCrafter || base == null || stack == null) return true;
        World w = base.getWorld();
        if (w == null || w.isRemote) return true;
        Data d = data(w, false);
        if (d == null || d.at(base.getXCoord(), base.getYCoord(), base.getZCoord()) == null) return true;
        return xpAmount(stack) > 0 || solidXp(stack) > 0;
    }

    static boolean isTankItem(ItemStack s) {
        return LootSpawners.matches(TweaksConfig.xpCrafterTank, s);
    }

    /** A Valier XP Crafter item: this mod's (since 2.0.0), or a GregTech tank item of the old kind. */
    static boolean isCrafterItem(ItemStack s) {
        return s != null && s.getItem() != null && s.getItem() == Item.getItemFromBlock(ModBlocks.xpCrafter) || isOldCrafterItem(s);
    }

    /** A Valier XP Crafter item from before 2.0.0: a GregTech tank item carrying the crafter tag. */
    static boolean isOldCrafterItem(ItemStack s) {
        return isTankItem(s) && s.hasTagCompound()
            && s.getTagCompound()
                .hasKey(ITEM_TAG, 10);
    }

    static boolean isFullItem(ItemStack s) {
        return LootSpawners.matches(TweaksConfig.xpCrafterFullItem, s);
    }

    /**
     * 1: the crafter block is there; 2: a GregTech tank of the old kind is (turned into the block by {@link #convert});
     * 0: neither is; -1: can't tell yet.
     */
    static int tankState(World w, int x, int y, int z) {
        if (w.getBlock(x, y, z) == ModBlocks.xpCrafter) return w.getTileEntity(x, y, z) instanceof TileXpCrafter ? 1 : -1;
        int gt = gtTankState(w, x, y, z);
        return gt == 1 ? 2 : gt;
    }

    /** The crafter block's tile there, or null. */
    static TileXpCrafter tankAt(World w, int x, int y, int z) {
        if (w.getBlock(x, y, z) != ModBlocks.xpCrafter) return null;
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileXpCrafter ? (TileXpCrafter) te : null;
    }

    /** For a GregTech tank: 1 it is there; 0 it isn't; -1 can't tell yet (a GregTech machine whose machine isn't loaded). */
    static int gtTankState(World w, int x, int y, int z) {
        Item item = LootSpawners.itemOf(TweaksConfig.xpCrafterTank);
        Block block = item == null ? null : Block.getBlockFromItem(item);
        if (block == null || block == Blocks.air) return -1; // the setting is broken: forget nothing
        if (w.getBlock(x, y, z) != block) return 0;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity)) return te == null ? -1 : 0;
        IGregTechTileEntity gt = (IGregTechTileEntity) te;
        int meta = LootSpawners.metaOf(TweaksConfig.xpCrafterTank);
        if (meta >= 0 && gt.getMetaTileID() != meta) return 0;
        IMetaTileEntity mte = gt.getMetaTileEntity();
        if (mte == null) return -1;
        return mte instanceof MTEBasicTank ? 1 : 0;
    }

    static MTEBasicTank gtTankAt(World w, int x, int y, int z) {
        if (gtTankState(w, x, y, z) != 1) return null;
        return (MTEBasicTank) ((IGregTechTileEntity) w.getTileEntity(x, y, z)).getMetaTileEntity();
    }

    /**
     * A crafter of the old kind (a GregTech tank) becomes the Valier XP Crafter block where it stands: its covers drop,
     * its fluid (whatever and however much) and its input and output slots go into the block, its record stays as it
     * is. The tank is emptied first, so GregTech spills nothing as it goes. Null if there was no tank or the block
     * didn't take.
     */
    static TileXpCrafter convert(World w, Data d, Crafter c) {
        MTEBasicTank gt = gtTankAt(w, c.x, c.y, c.z);
        if (gt == null) return null;
        IGregTechTileEntity base = gt.getBaseMetaTileEntity();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) if (base.hasCoverAtSide(side)) base.dropCover(side, side);
        FluidStack fluid = gt.mFluid == null || gt.mFluid.amount <= 0 ? null : gt.mFluid.copy();
        ItemStack in = gt.mInventory[gt.getInputSlot()], out = gt.mInventory[gt.getOutputSlot()];
        in = in == null ? null : in.copy();
        out = out == null ? null : out.copy();
        gt.mFluid = null;
        for (int i = 0; i < gt.mInventory.length; i++) gt.mInventory[i] = null;
        w.setBlock(c.x, c.y, c.z, ModBlocks.xpCrafter, 0, 3);
        TileXpCrafter t = tankAt(w, c.x, c.y, c.z);
        if (t == null) {
            ValierTweaks.LOG.error("The XP crafter at {} couldn't become a Valier XP Crafter block; its slots drop", c.where());
            if (in != null) LootSpawners.drop(w, c.x, c.y, c.z, in);
            if (out != null) LootSpawners.drop(w, c.x, c.y, c.z, out);
            return null;
        }
        t.setContents(fluid, in, out);
        hudUpdate(w, c);
        ValierTweaks.LOG.info("The XP crafter at {} is a Valier XP Crafter block now (tank: {})", c.where(),
            fluid == null ? "empty" : fluid.amount + " mB of " + fluid.getFluid()
                .getName());
        return t;
    }

    // ---- the item ---------------------------------------------------------------------------------------------------

    /** A new Valier XP Crafter item (the creative tab's, pick-block's). {@code tank} isn't used (from before 2.0.0). */
    public static ItemStack newItem(ItemStack tank) {
        return toItem(new Crafter(0, 0, 0), null);
    }

    /**
     * A Valier XP Crafter item that knows what {@code c} knows, for a dropped GregTech tank item of the old kind (its
     * liquid XP, GregTech's "mFluid", goes along).
     */
    static ItemStack toItem(ItemStack tankDrop, Crafter c) {
        return toItem(c, fluidOf(tankDrop));
    }

    /** A Valier XP Crafter item (since 2.0.0 this mod's block item) knowing what {@code c} knows, with its tank's fluid. */
    static ItemStack toItem(Crafter c, FluidStack fluid) {
        ItemStack s = new ItemStack(ModBlocks.xpCrafter);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(ITEM_TAG, c.write(true));
        if (fluid != null && fluid.amount > 0 && fluid.getFluid() != null)
            tag.setTag("Fluid", fluid.writeToNBT(new NBTTagCompound()));
        s.setTagCompound(tag);
        label(s, c);
        return s;
    }

    /** The fluid a crafter item carries: this mod's "Fluid", or GregTech's "mFluid" (a tank item of the old kind). */
    static FluidStack fluidOf(ItemStack s) {
        if (s == null || !s.hasTagCompound()) return null;
        NBTTagCompound tag = s.getTagCompound();
        String key = tag.hasKey("Fluid", 10) ? "Fluid" : tag.hasKey("mFluid", 10) ? "mFluid" : null;
        FluidStack f = key == null ? null : FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(key));
        return f == null || f.amount <= 0 ? null : f;
    }

    /** Its name and the tooltip lines saying what it makes. */
    static void label(ItemStack s, Crafter c) {
        NBTTagCompound tag = s.getTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        display.setString("Name", EnumChatFormatting.RESET + NAME);
        NBTTagList lore = new NBTTagList();
        for (String line : loreLines(c, s)) lore.appendTag(new NBTTagString(EnumChatFormatting.GRAY + line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
    }

    static List<String> loreLines(Crafter c) {
        return loreLines(c, null);
    }

    static List<String> loreLines(Crafter c, ItemStack s) {
        List<String> lines = new ArrayList<>();
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        if (r == null) {
            lines.add("Makes: nothing yet");
            lines.add("Place it, then right-click it");
            lines.add("to choose what it makes");
        } else {
            lines.add("Makes: " + countName(product(c, r)));
            lines.add("Uses " + mb(cost(c, r)) + " mB of liquid XP each");
        }
        if (c.full) lines.add("Full dust: on (" + (c.fullPaid != null ? c.fullPaid.getDisplayName() + " in it" : "creative") + ")");
        if (c.bank > 0) lines.add("Holding " + mb(c.bank) + " mB of liquid XP");
        if (fluidOf(s) != null) lines.add("Tank: " + mb(fluidOf(s).amount) + " mB of " + fluidOf(s).getLocalizedName());
        lines.add("Fill it with liquid XP (or essence berries");
        lines.add("in a chest touching it); what it makes");
        lines.add("goes into chests and pipes touching it,");
        lines.add("or waits in its store");
        return lines;
    }

    // ---- clicks, placing and breaking --------------------------------------------------------------------------------

    /**
     * A right-click on an XP crafter opens its menu, unless the hand holds a GregTech tool or cover ({@link #menuItem}).
     * Sneak-right-click: with an empty hand its status,
     * with the full-dust item that switched on or off, with something an XP recipe makes that becomes what it makes. A
     * sneaking empty-hand hit gives the full-dust item back. Low priority, and not for cancelled clicks, so claim
     * protection that stops the click wins.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEvent event) {
        boolean hit = event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
        if (!hit && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        World world = event.world;
        ItemStack held = player.getHeldItem();
        boolean menu = !hit && !player.isSneaking() && menuItem(held) && opensMenus(player);
        if (world.isRemote || !(player.isSneaking() || menu)) return;
        int x = event.x, y = event.y, z = event.z;
        Data d = data(world, false);
        Crafter c = d == null ? null : d.at(x, y, z);
        int state = c == null ? 0 : tankState(world, x, y, z);
        if (c != null && state == 0) {
            forget(world, d, c); // removed without dropping
            c = null;
        } else if (state == 2 && !RevertBlocks.reverting) convert(world, d, c); // the old kind: the block now
        if (menu) {
            if (c != null) {
                event.setCanceled(true);
                XpMenu.open(player, world, d, c);
            }
            return;
        }
        if (c == null) {
            if (!hit && held != null && recipeFor(held) != null && gtTankAt(world, x, y, z) != null) {
                event.setCanceled(true);
                tell(player, text(EnumChatFormatting.GRAY, "This is a plain tank. A " + NAME + " is crafted from three of "
                    + "these, two LV pumps, an LV conveyor, an LV robot arm, a crafting table and an LV circuit. Place "
                    + "that and right-click it to choose what it makes."));
            }
            return;
        }
        if (hit) {
            if (held == null) {
                event.setCanceled(true);
                takeBack(player, world, d, c);
            }
            return;
        }
        if (held == null) {
            event.setCanceled(true);
            tell(player, status(world, c));
        } else if (isFullItem(held)) {
            event.setCanceled(true); // don't place the emerald block
            toggleFull(player, world, d, c, held);
        } else if (recipeFor(held) != null) {
            event.setCanceled(true);
            choose(player, world, d, c, recipeFor(held));
        } else if (isDust(held)) {
            event.setCanceled(true);
            tell(player, text(EnumChatFormatting.RED, "No liquid XP recipe makes " + held.getDisplayName() + ". It can make: "
                + canMake() + "."));
        }
    }

    /**
     * Whether a right-click holding this opens the menu: an empty hand or anything at all, except what GregTech uses on
     * its machines (its wrenches, screwdrivers, mallets, hammers, crowbars, wire cutters, soldering irons, jackhammers,
     * and covers), which go to the tank as before.
     */
    static boolean menuItem(ItemStack held) {
        if (held == null) return true;
        try {
            return !(GTUtility.isStackInList(held, GregTechAPI.sWrenchList)
                || GTUtility.isStackInList(held, GregTechAPI.sScrewdriverList)
                || GTUtility.isStackInList(held, GregTechAPI.sSoftMalletList)
                || GTUtility.isStackInList(held, GregTechAPI.sHardHammerList)
                || GTUtility.isStackInList(held, GregTechAPI.sCrowbarList)
                || GTUtility.isStackInList(held, GregTechAPI.sWireCutterList)
                || GTUtility.isStackInList(held, GregTechAPI.sSolderingToolList)
                || GTUtility.isStackInList(held, GregTechAPI.sJackhammerList) || CoverRegistry.isCover(held));
        } catch (Throwable e) {
            return false;
        }
    }

    /** Players open menus; other fake players (machines that click) don't, except the self-test's. */
    static boolean opensMenus(EntityPlayer player) {
        return player instanceof EntityPlayerMP && (!(player instanceof FakePlayer) || player == XpMenu.testPlayer);
    }

    static void choose(EntityPlayer player, World world, Data d, Crafter c, XpRecipe r) {
        if (!LootSpawners.mayChange(player, world, c.x, c.y, c.z)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this XP crafter: it's protected."));
            return;
        }
        c.makes = LootSpawners.copyOne(r.output);
        d.markDirty();
        hudUpdate(world, c);
        ValierTweaks.LOG.info("{} set the XP crafter at {} to make {}", player.getCommandSenderName(), c.where(),
            r.output.getDisplayName());
        IChatComponent msg = text(EnumChatFormatting.GREEN, "This XP crafter makes " + countName(product(c, r)) + " now, from "
            + mb(cost(c, r)) + " mB of liquid XP each.");
        Full f = full(r.output);
        if (f != null && !c.full && LootSpawners.itemOf(TweaksConfig.xpCrafterFullItem) != null)
            msg.appendSibling(new ChatComponentText(" With "))
                .appendSibling(LootSpawners.costText(TweaksConfig.xpCrafterFullItem, 1))
                .appendSibling(new ChatComponentText(" in it (full dust, in its menu) it makes " + countName(f.dust)
                    + " instead (" + mb(r.cost * f.times) + " mB each)."));
        tell(player, msg);
    }

    /** It stops making anything (its menu: the chosen one clicked again). */
    static void stop(EntityPlayer player, World world, Data d, Crafter c) {
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        c.makes = null;
        c.status = Status.NO_RECIPE;
        d.markDirty();
        hudUpdate(world, c);
        ValierTweaks.LOG.info("{} stopped the XP crafter at {}", player.getCommandSenderName(), c.where());
        tell(player, text(EnumChatFormatting.YELLOW, "This XP crafter stopped making " + (r == null ? "anything" : countName(r.output))
            + ". Its liquid XP stays in it."));
    }

    static void toggleFull(EntityPlayer player, World world, Data d, Crafter c, ItemStack held) {
        if (!LootSpawners.mayChange(player, world, c.x, c.y, c.z)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this XP crafter: it's protected."));
            return;
        }
        if (c.full) {
            switchFullOff(player, d, c, "Full dust off");
            return;
        }
        ItemStack paid = null;
        if (!player.capabilities.isCreativeMode) {
            paid = held.splitStack(1);
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        fullOn(player, world, d, c, paid);
    }

    /** Full dust on, the item paid for it kept in it (none in creative). */
    static void fullOn(EntityPlayer player, World world, Data d, Crafter c, ItemStack paid) {
        c.fullPaid = paid;
        c.full = true;
        d.markDirty();
        hudUpdate(world, c);
        ValierTweaks.LOG.info("{} switched full dust on for the XP crafter at {}", player.getCommandSenderName(), c.where());
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        Full f = r == null ? null : full(r.output);
        String what = r == null ? "Once it makes a tiny or small pile of dust, it will make the whole dust instead, for "
            + TweaksConfig.xpCrafterTinyTimes + " or " + TweaksConfig.xpCrafterSmallTimes + " times the liquid XP."
            : f == null ? countName(r.output) + " is already whole, so nothing changes until it makes a tiny or small pile."
                : "It makes " + countName(f.dust) + " now, from " + mb(cost(c, r)) + " mB of liquid XP each.";
        tell(player, text(EnumChatFormatting.GREEN, "Full dust on. " + what
            + " Do it again, click it in its menu, or sneak and hit it with an empty hand, to switch that off and get the "
            + "block back."));
    }

    /** A sneaking empty-hand hit: the full-dust item comes back out. */
    static void takeBack(EntityPlayer player, World world, Data d, Crafter c) {
        if (!c.full && c.fullPaid == null) {
            tell(player, text(EnumChatFormatting.GRAY, "There is nothing in this XP crafter to take back."));
            return;
        }
        if (!LootSpawners.mayChange(player, world, c.x, c.y, c.z)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this XP crafter: it's protected."));
            return;
        }
        switchFullOff(player, d, c, "Taken back: full dust is off");
    }

    static void switchFullOff(EntityPlayer player, Data d, Crafter c, String said) {
        c.full = false;
        ItemStack back = c.fullPaid;
        c.fullPaid = null;
        d.markDirty();
        hudUpdate(player.worldObj, c);
        if (back != null) LootSpawners.give(player, back);
        ValierTweaks.LOG.info("{} switched full dust off for the XP crafter at {}", player.getCommandSenderName(), c.where());
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        tell(player, text(EnumChatFormatting.GREEN, said + (r == null ? "." : "; it makes " + countName(r.output) + " again.")
            + (back != null ? " Here is your " + back.getDisplayName() + " back." : "")));
    }

    /**
     * A Valier XP Crafter item from before 2.0.0 (a GregTech tank item) placed: the tank GregTech just made is a
     * crafter, knowing what the item knew, and becomes the block. Lowest priority and not for cancelled placements, so
     * claims that stop it win. This mod's own item does it in {@link ItemBlockXpCrafter} ({@link #placed}).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || !isOldCrafterItem(event.itemInHand)
            || gtTankAt(event.world, event.x, event.y, event.z) == null) return;
        Crafter c = Crafter.read(event.x, event.y, event.z, event.itemInHand.getTagCompound()
            .getCompoundTag(ITEM_TAG));
        Data d = data(event.world, true);
        d.put(c);
        if (!RevertBlocks.reverting) convert(event.world, d, c);
        ValierTweaks.LOG.info("{} placed an XP crafter (an item from before 2.0.0) at {}",
            event.player == null ? "Something" : event.player.getCommandSenderName(), c.where());
    }

    /** This mod's crafter item placed: a crafter knowing what the item knew, its liquid XP in its tank. */
    static void placed(World world, TileXpCrafter t, ItemStack stack, EntityPlayer player) {
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        Data d = data(world, true);
        Crafter old = d.at(x, y, z);
        if (old != null) forget(world, d, old); // a record left where something removed a crafter without breaking it
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : null;
        Crafter c = tag != null && tag.hasKey(ITEM_TAG, 10) ? Crafter.read(x, y, z, tag.getCompoundTag(ITEM_TAG)) : new Crafter(x, y, z);
        FluidStack f = fluidOf(stack);
        if (f != null) t.setContents(f, null, null);
        d.put(c);
        hudUpdate(world, c);
        ValierTweaks.LOG.info("{} placed an XP crafter at {}", player == null ? "Something" : player.getCommandSenderName(),
            c.where());
    }

    /**
     * The crafter block going, however (see {@link BlockXpCrafter#breakBlock}): it drops as the crafter item, knowing
     * what it knew and holding its tank's fluid; its store and slots drop too, and its record is gone.
     */
    static void broken(World w, TileXpCrafter t) {
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        Data d = data(w, false);
        Crafter c = d == null ? null : d.at(x, y, z);
        LootSpawners.drop(w, x, y, z, toItem(c != null ? c : new Crafter(x, y, z), t.getFluid()));
        for (int i = 0; i < t.slots.length; i++) {
            if (t.slots[i] != null) LootSpawners.drop(w, x, y, z, t.slots[i]);
            t.slots[i] = null;
        }
        t.fluid = null;
        if (c == null) return;
        for (int i = 0; i < Crafter.STORE; i++) {
            if (c.store[i] != null) LootSpawners.drop(w, x, y, z, c.store[i]);
            c.store[i] = null;
        }
        d.remove(c);
        ValierTweaks.LOG.info("The XP crafter at {} was broken and dropped as an item", c.where());
    }

    /**
     * An XP crafter broken with drops (by a player, an explosion, a mod that breaks blocks with drops): the tank it drops
     * becomes the XP crafter item, knowing what it knew; its store drops too.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        if (event.world.isRemote || event.block == ModBlocks.xpCrafter) return; // the block drops it itself
        Data d = data(event.world, false);
        Crafter c = d == null ? null : d.at(event.x, event.y, event.z);
        if (c == null) return;
        d.remove(c);
        ItemStack tank = null;
        for (ItemStack s : event.drops) {
            if (isTankItem(s) && !isCrafterItem(s)) {
                tank = s;
                break;
            }
        }
        if (tank != null) {
            event.drops.set(event.drops.indexOf(tank), toItem(tank, c));
        } else if (c.fullPaid != null) { // the tank itself didn't drop: the full-dust item paid into it does
            event.drops.add(c.fullPaid);
        }
        for (int i = 0; i < Crafter.STORE; i++) {
            if (c.store[i] != null) event.drops.add(c.store[i]);
            c.store[i] = null;
        }
        ValierTweaks.LOG.info("The XP crafter at {} was broken{}", c.where(), tank != null ? " and dropped as an item" : "");
    }

    // ---- crafting -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0) return;
        long t0 = ValierCost.start();
        for (WorldServer w : DimensionManager.getWorlds()) {
            Data d = data(w, false);
            if (d == null || d.crafters.isEmpty()) continue;
            for (Crafter c : new ArrayList<>(d.crafters.values())) run(w, d, c);
        }
        ValierCost.add(ValierCost.Part.XP, t0);
    }

    /** One second of one crafter: only while its chunk is loaded; forgotten if its tank is gone. */
    static void run(World w, Data d, Crafter c) {
        if (!w.blockExists(c.x, c.y, c.z)) return; // never load a chunk
        int state = tankState(w, c.x, c.y, c.z);
        if (state == 0) {
            forget(w, d, c);
            return;
        }
        if (state < 0) return;
        if (state == 2 && (RevertBlocks.reverting || convert(w, d, c) == null)) return; // the old kind: the block now
        TileXpCrafter t = tankAt(w, c.x, c.y, c.z);
        if (t == null) return;
        if (!TweaksConfig.xpCrafter) c.status = Status.OFF;
        else {
            t.emptyContainers();
            cycle(w, d, c, t);
        }
        hudUpdate(w, c);
    }

    /** Removed without dropping (a Matter Manipulator, a frame mover): its store drops where it was, and it's forgotten. */
    static void forget(World w, Data d, Crafter c) {
        for (int i = 0; i < Crafter.STORE; i++) {
            if (c.store[i] == null) continue;
            EntityItem e = new EntityItem(w, c.x + 0.5, c.y + 0.5, c.z + 0.5, c.store[i]);
            e.delayBeforeCanPickup = 10;
            w.spawnEntityInWorld(e);
            c.store[i] = null;
        }
        d.remove(c);
        ValierTweaks.LOG.info("The XP crafter at {} is gone (removed without dropping); forgotten", c.where());
    }

    /**
     * Hands its store to what touches it, crafts into its store as long as there is liquid XP for it and room, then hands
     * that over too. XP is taken from the tank only as needed, so with nothing to craft (or no room) it stays in the tank;
     * when the tank hasn't enough (or holds another fluid), solid XP touching it makes up the rest.
     */
    static void cycle(World w, Data d, Crafter c, TileXpCrafter tank) {
        handOver(w, d, c);
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        if (r == null) {
            c.status = Status.NO_RECIPE;
            return;
        }
        int cost = cost(c, r);
        ItemStack each = product(c, r);
        int made = 0;
        Status waiting = Status.NEEDS_XP;
        for (int i = 0; i < MAX_PER_SECOND; i++) {
            if (!fits(c.store, each)) {
                waiting = anyOutput(w, c) ? Status.FULL : Status.NO_OUTPUT;
                break;
            }
            if (c.bank < cost) {
                FluidStack f = tank.getDrainableStack();
                boolean wrong = f != null && f.amount > 0 && !isXp(f.getFluid());
                int take = wrong ? 0 : Math.min(cost - c.bank, f == null ? 0 : f.amount);
                if (take > 0) {
                    FluidStack got = tank.drain(take, true);
                    if (got != null && got.amount > 0) {
                        c.bank += got.amount;
                        d.markDirty();
                    }
                }
                if (c.bank < cost) {
                    int solid = takeSolidXp(w, c, cost - c.bank);
                    if (solid > 0) {
                        c.bank += solid;
                        d.markDirty();
                    }
                }
                if (c.bank < cost) {
                    waiting = wrong ? Status.WRONG_FLUID : Status.NEEDS_XP;
                    break;
                }
            }
            c.bank -= cost;
            c.made++;
            made++;
            add(c.store, each.copy());
            d.markDirty();
        }
        c.status = made > 0 ? Status.WORKING : waiting;
        if (made > 0) handOver(w, d, c);
    }

    /** Its store into the chests, inventories and pipes touching it. */
    static void handOver(World w, Data d, Crafter c) {
        for (int i = 0; i < Crafter.STORE; i++) {
            if (c.store[i] == null) continue;
            int before = c.store[i].stackSize; // what takes it may shrink this very stack
            ItemStack left = LootSpawners.insertAround(w, c.x, c.y, c.z, c.store[i]);
            if (left == null || left.stackSize != before) d.markDirty();
            c.store[i] = left;
        }
    }

    /** All of {@code s} fits in the store. */
    static boolean fits(ItemStack[] store, ItemStack s) {
        int room = 0, max = Math.min(64, s.getMaxStackSize());
        for (ItemStack in : store) {
            if (in == null) room += max;
            else if (stackable(in, s)) room += Math.max(0, max - in.stackSize);
            if (room >= s.stackSize) return true;
        }
        return false;
    }

    /** Into the store, onto stacks of the same item first; call only when it {@link #fits}. */
    static void add(ItemStack[] store, ItemStack s) {
        int max = Math.min(64, s.getMaxStackSize());
        for (int i = 0; i < store.length && s.stackSize > 0; i++) {
            if (store[i] == null || !stackable(store[i], s)) continue;
            int move = Math.min(s.stackSize, max - store[i].stackSize);
            if (move <= 0) continue;
            store[i].stackSize += move;
            s.stackSize -= move;
        }
        for (int i = 0; i < store.length && s.stackSize > 0; i++) {
            if (store[i] != null) continue;
            store[i] = s.splitStack(Math.min(s.stackSize, max));
        }
    }

    static boolean stackable(ItemStack a, ItemStack b) {
        return a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    static boolean anyOutput(World w, Crafter c) {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (LootSpawners.accepts(w, c.x + dir.offsetX, c.y + dir.offsetY, c.z + dir.offsetZ, dir.getOpposite())) return true;
        }
        return false;
    }

    // ---- the crafting recipe -------------------------------------------------------------------------------------

    /**
     * The Valier XP Crafter's crafting recipe (mirrored works too):
     *
     * <pre>
     * tank  LV pump         LV conveyor
     * tank  crafting table  LV circuit
     * tank  LV pump         LV robot arm
     * </pre>
     *
     * The tanks must be empty and not crafters already; the crafter is the first of them, made one.
     */
    public static final class Recipe extends ShapedOreRecipe {

        Recipe(ItemStack output, Object... shape) {
            super(output, shape);
        }

        /** The recipe, or null if the tank setting names no item. */
        static Recipe make() {
            Item tank = LootSpawners.itemOf(TweaksConfig.xpCrafterTank);
            if (tank == null) {
                ValierTweaks.LOG.warn("XP crafters: xpCrafterTank names no item; none can be crafted");
                return null;
            }
            ItemStack t = new ItemStack(tank, 1, Math.max(0, LootSpawners.metaOf(TweaksConfig.xpCrafterTank)));
            return new Recipe(toItem(t, new Crafter(0, 0, 0)), "TPC", "TWc", "TPA", 'T', t, 'P',
                ItemList.Electric_Pump_LV.get(1), 'C', ItemList.Conveyor_Module_LV.get(1), 'A', ItemList.Robot_Arm_LV.get(1),
                'W', new ItemStack(Blocks.crafting_table), 'c', OrePrefixes.circuit.get(Materials.LV)
                    .toString());
        }

        @Override
        public boolean matches(InventoryCrafting grid, World world) {
            return TweaksConfig.xpCrafter && super.matches(grid, world) && tank(grid) != null;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting grid) {
            ItemStack tank = tank(grid);
            return tank == null ? null : toItem(tank, new Crafter(0, 0, 0));
        }

        /** The grid's first tank, if none of its tanks is a crafter already or holds fluid; else null. */
        static ItemStack tank(InventoryCrafting grid) {
            ItemStack first = null;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                ItemStack s = grid.getStackInSlot(i);
                if (s == null || !isTankItem(s)) continue;
                if (isCrafterItem(s) || holdsFluid(s)) return null;
                if (first == null) first = s;
            }
            return first;
        }

        /** A GregTech tank item with fluid in it (its "mFluid"). */
        static boolean holdsFluid(ItemStack s) {
            return s.hasTagCompound() && s.getTagCompound()
                .hasKey("mFluid");
        }
    }

    /**
     * Shows a Valier XP Crafter in a crafting output slot. Minecraft 1.7.10 never sends crafting output slots to the
     * client (the client works the output out itself), and clients don't have this recipe, so without this the output
     * would look empty though the server has the crafter ready there.
     */
    public static final class CraftSync {

        private final Map<EntityPlayerMP, Object[]> sent = new WeakHashMap<>();

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player instanceof FakePlayer) return;
            long t0 = ValierCost.start();
            try {
                sync((EntityPlayerMP) event.player);
            } finally {
                ValierCost.add(ValierCost.Part.XP, t0);
            }
        }

        private void sync(EntityPlayerMP player) {
            if (player.playerNetServerHandler == null) return;
            Container container = player.openContainer;
            Slot result = resultSlot(container);
            if (result == null) {
                sent.remove(player);
                return;
            }
            ItemStack stack = result.getStack();
            Object[] last = sent.get(player);
            if (last != null && (Integer) last[0] == container.windowId && (Integer) last[1] == result.slotNumber
                && ItemStack.areItemStacksEqual((ItemStack) last[2], stack)) return;
            player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(container.windowId, result.slotNumber, stack));
            sent.put(player, new Object[] { container.windowId, result.slotNumber, stack.copy() });
        }

        /** The container's crafting output slot if it holds an XP crafter, otherwise null. */
        static Slot resultSlot(Container container) {
            if (container == null) return null;
            for (Object o : container.inventorySlots) {
                if (o instanceof SlotCrafting && isCrafterItem(((Slot) o).getStack())) return (Slot) o;
            }
            return null;
        }
    }

    // ---- what WAILA is told ---------------------------------------------------------------------------------------

    /**
     * What each crafter tells a player looking at it with WAILA ({@link #hudInfo}), by dimension and position. WAILA asks
     * on its network thread, so this is made ready on the server thread (every second, and whenever a crafter changes)
     * and never changed once made: only replaced.
     */
    static final Map<Integer, Map<Long, NBTTagCompound>> HUD = new ConcurrentHashMap<>();
    /** WAILA is there to ask. */
    static boolean hud;

    static void hudUpdate(World w, Crafter c) {
        if (!hud || w == null) return;
        HUD.computeIfAbsent(w.provider.dimensionId, k -> new ConcurrentHashMap<>())
            .put(key(c.x, c.y, c.z), hudInfo(w, c));
    }

    static void hudRemove(int dim, Crafter c) {
        Map<Long, NBTTagCompound> m = HUD.get(dim);
        if (m != null) m.remove(key(c.x, c.y, c.z));
    }

    /** A copy of what the crafter there tells WAILA, or null if there is none. Any thread. */
    static NBTTagCompound hudAt(int dim, int x, int y, int z) {
        Map<Long, NBTTagCompound> m = HUD.get(dim);
        NBTTagCompound t = m == null ? null : m.get(key(x, y, z));
        return t == null ? null : (NBTTagCompound) t.copy();
    }

    /**
     * What a crafter tells WAILA (format 1): {@code Makes} (the item one craft makes now, as an item stack) with
     * {@code MakesName} (its English name, should the client not know the item), {@code Cost} (mB each) and
     * {@code Whole} (full dust is on and changes it); {@code Full} (full dust is on); {@code Status} (a {@link Status}
     * name) with {@code StatusText} (English); {@code Bank} (mB already taken), {@code Made}, {@code Stored} (items in
     * its store, 1.11.0 on); {@code Tank} and {@code TankFluid} (what is in its tank, if anything).
     */
    static NBTTagCompound hudInfo(World w, Crafter c) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Format", 1);
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        if (r != null) {
            ItemStack made = product(c, r);
            t.setTag("Makes", made.writeToNBT(new NBTTagCompound()));
            t.setString("MakesName", countName(made));
            t.setInteger("Cost", cost(c, r));
            t.setBoolean("Whole", c.full && full(r.output) != null);
        }
        t.setBoolean("Full", c.full);
        t.setString("Status", c.status.name());
        t.setString("StatusText", c.status.words);
        t.setInteger("Bank", c.bank);
        t.setLong("Made", c.made);
        t.setInteger("Stored", c.stored());
        TileXpCrafter tank = tankAt(w, c.x, c.y, c.z);
        FluidStack f = tank == null ? null : tank.getFluid();
        if (f != null && f.amount > 0 && f.getFluid() != null) {
            t.setInteger("Tank", f.amount);
            t.setString("TankFluid", f.getFluid()
                .getName());
        }
        return t;
    }

    // ---- messages -------------------------------------------------------------------------------------------------

    static IChatComponent status(World w, Crafter c) {
        IChatComponent msg = text(EnumChatFormatting.GOLD, "XP crafter: ");
        XpRecipe r = c.makes == null ? null : recipeFor(c.makes);
        if (r == null) msg.appendSibling(new ChatComponentText("makes nothing yet. Right-click it to choose in its menu, "
            + "or sneak-right-click it with what it should make: " + canMake() + "."));
        else msg.appendSibling(new ChatComponentText("makes " + countName(product(c, r)) + " from " + mb(cost(c, r))
            + " mB of liquid XP each, into the chests, inventories and pipes touching it, or its store. Now: "
            + c.status.words + "."));
        TileXpCrafter tank = tankAt(w, c.x, c.y, c.z);
        FluidStack f = tank == null ? null : tank.getFluid();
        msg.appendSibling(new ChatComponentText(" Its tank: " + (f == null || f.amount <= 0 ? "empty"
            : mb(f.amount) + " mB of " + f.getFluid()
                .getLocalizedName(f))
            + (c.bank > 0 ? ", and " + mb(c.bank) + " mB already taken towards the next" : "") + "."));
        if (c.made > 0) msg.appendSibling(new ChatComponentText(" Made " + c.made + " so far."));
        if (c.stored() > 0) msg.appendSibling(new ChatComponentText(" Its store holds " + mb(c.stored()) + " (right-click "
            + "it to take them)."));
        Full full = r == null ? null : full(r.output);
        if (c.full) msg.appendSibling(new ChatComponentText(full != null ? " Full dust is on." : " Full dust is on (for tiny and small piles)."));
        else if (full != null && LootSpawners.itemOf(TweaksConfig.xpCrafterFullItem) != null)
            msg.appendSibling(new ChatComponentText(" Sneak-right-click it (or click full dust in its menu) with "))
                .appendSibling(LootSpawners.costText(TweaksConfig.xpCrafterFullItem, 1))
                .appendSibling(new ChatComponentText(" to make " + countName(full.dust) + " instead (" + mb(r.cost * full.times)
                    + " mB each)."));
        if (c.full || c.fullPaid != null) msg.appendSibling(new ChatComponentText(" Sneak and hit it with an empty hand to take "
            + "the block back."));
        if (r != null) msg.appendSibling(new ChatComponentText(" Sneak-right-click it with something else an XP recipe makes "
            + "to switch."));
        msg.appendSibling(new ChatComponentText(" Broken, it keeps all this in the item."));
        return msg;
    }

    /** Everything an XP crafter can make, cheapest first. */
    static String canMake() {
        StringBuilder sb = new StringBuilder();
        for (XpRecipe r : recipes().values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.output.getDisplayName());
        }
        return sb.length() == 0 ? "nothing (no liquid XP recipes found)" : sb.toString();
    }

    static boolean isDust(ItemStack s) {
        for (int id : OreDictionary.getOreIDs(s)) if (OreDictionary.getOreName(id)
            .startsWith("dust")) return true;
        return false;
    }

    static String countName(ItemStack s) {
        return (s.stackSize > 1 ? s.stackSize + " × " : "") + s.getDisplayName();
    }

    static String mb(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private static IChatComponent text(EnumChatFormatting colour, String s) {
        IChatComponent c = new ChatComponentText(s);
        c.getChatStyle()
            .setColor(colour);
        return c;
    }

    private static void tell(EntityPlayer player, IChatComponent msg) {
        player.addChatMessage(msg);
    }
}
