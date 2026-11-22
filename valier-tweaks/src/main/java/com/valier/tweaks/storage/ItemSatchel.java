package com.valier.tweaks.storage;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.IFluidHandler;

import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Valier Satchel (items) and Valier Flask (fluids), metadata the tier ({@link SatchelTier}); what they hold is in
 * the item ({@link SatchelInventory}).
 * <ul>
 * <li>Satchel: picks up what it already holds (and, if set, new items) before they reach the inventory; right-click a
 * block to place or use the selected item; right-click the air for its menu; sneak + mouse wheel picks the slot;
 * 2.3.3: sneak + right-click a container to put the selected item in, put everything in or take out, by its container
 * mode ({@link SatchelBoxes}).</li>
 * <li>Flask: right-click a fluid source to pick it up, a block to place the selected fluid there; right-click a tank or
 * machine (Railcraft's tanks too, any block of them) holding fluid to take it, an empty one to put the selected fluid in
 * (sneaking: put in always); right-click the air for its menu.</li>
 * </ul>
 */
public class ItemSatchel extends Item {

    public final boolean fluid;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemSatchel(boolean fluid) {
        this.fluid = fluid;
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName(ValierTweaks.MODID + (fluid ? ".flask" : ".satchel"));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + SatchelTier.clamp(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < SatchelTier.COUNT; t++) list.add(new ItemStack(item, 1, t));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[SatchelTier.COUNT];
        for (int t = 0; t < SatchelTier.COUNT; t++) icons[t] = r.registerIcon(ValierTweaks.MODID + ":" + (fluid ? "flask_" : "satchel_") + t);
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[SatchelTier.clamp(meta)];
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return stack.getItemDamage() == SatchelTier.CREATIVE;
    }

    // ---- in hand -----------------------------------------------------------------------------------------------------

    /**
     * A Flask on a tank or machine (2.3.2), before the block's own use (its menu: Railcraft's and GregTech's tanks open
     * one), and a Satchel sneak-clicked on a container (2.3.3): the server answers the click here; the client passes it on
     * (true there would keep it from the server).
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy,
        float hz) {
        if (world.isRemote) return false;
        if (!fluid) return player.isSneaking() && SatchelBoxes.click(stack, player, world, x, y, z, side);
        IFluidHandler h = tank(world.getTileEntity(x, y, z), side);
        SatchelInventory inv = SatchelInventory.of(stack);
        if (h == null || inv == null) return false;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, x, y, z)) return false; // a claim: the block decides
        tankClick(inv, h, ForgeDirection.getOrientation(side), player.isSneaking(), player);
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy,
        float hz) {
        SatchelInventory inv = SatchelInventory.of(stack);
        if (inv == null) return false;
        // A Flask's tank clicks are done in onItemUseFirst; here (the client, or a claimed tank) it only keeps the click
        // from going on to place the selected fluid against the tank.
        if (fluid) return tankish(world.getTileEntity(x, y, z));
        // A container click is done in onItemUseFirst too; here (the client) it only keeps the click from placing a block.
        if (player.isSneaking() && SatchelBoxes.applies(inv, world.getTileEntity(x, y, z))) return true;
        int i = inv.selected;
        if (inv.available(i, true) <= 0) return false;
        ItemStack one = inv.items[i].copy();
        one.stackSize = 1;
        ItemStack before = one.copy();
        boolean used = one.tryPlaceItemIntoWorld(player, world, x, y, z, side, hx, hy, hz);
        if (!used || world.isRemote) return used;
        if (one.stackSize <= 0) inv.extract(i, 1, true, false);
        else if (!ItemStack.areItemStacksEqual(one, before) && inv.extract(i, 1, true, false) != null) {
            // Worn or changed by its use (a tool, flint and steel): that one comes out of the Satchel, into the inventory.
            if (!player.inventory.addItemStackToInventory(one)) player.dropPlayerItemWithRandomChoice(one, false);
        }
        return used;
    }

    private static final boolean RAILCRAFT = cpw.mods.fml.common.Loader.isModLoaded("Railcraft");

    /**
     * The tank a click on te reaches: a fluid handler with tanks on that side, or any block of a formed Railcraft tank
     * (only its valves are handlers). Null for anything else, a GregTech machine without tanks among them, so its menu
     * still opens.
     */
    static IFluidHandler tank(TileEntity te, int side) {
        if (te == null) return null;
        if (RAILCRAFT) {
            IFluidHandler rc = RailTanks.of(te);
            if (rc != null) return rc;
        }
        if (!(te instanceof IFluidHandler)) return null;
        IFluidHandler h = (IFluidHandler) te;
        FluidTankInfo[] info = h.getTankInfo(ForgeDirection.getOrientation(side));
        return info == null || info.length == 0 ? null : h;
    }

    /** Any fluid handler or Railcraft tank block (formed or not): a Flask never places fluid against one. */
    static boolean tankish(TileEntity te) {
        return te instanceof IFluidHandler || RAILCRAFT && RailTanks.is(te);
    }

    /**
     * A Flask clicked on a tank (2.3.2): one holding fluid gives it to the Flask (into the tank holding that fluid, else
     * an empty one); an empty one (or anything, sneaking) gets the selected fluid. Tells the player when nothing moved.
     * Returns the mB moved: positive taken, negative put in.
     */
    static int tankClick(SatchelInventory inv, IFluidHandler h, ForgeDirection from, boolean put, EntityPlayer player) {
        if (!put) {
            FluidStack offer = h.drain(from, Integer.MAX_VALUE / 2, false);
            if (offer != null && offer.amount > 0 && offer.getFluid() != null) {
                int moved = 0;
                for (int pass = 0; pass < 4; pass++) { // a handler may give one of its tanks at a time
                    FluidStack o = pass == 0 ? offer : h.drain(from, Integer.MAX_VALUE / 2, false);
                    if (o == null || o.amount <= 0) break;
                    int room = inv.roomFor(o); // never more: a voiding tank of the Flask's mustn't destroy the tank's fluid
                    if (room <= 0) break;
                    FluidStack got = h.drain(from, Math.min(room, o.amount), true);
                    if (got == null || got.amount <= 0) break;
                    moved += inv.fill(got, true);
                }
                if (moved > 0) selectIfEmpty(inv, offer);
                else tell(player, "The Flask has no room for " + offer.getLocalizedName() + ".");
                return moved;
            }
        }
        FluidStack sel = inv.drain(inv.selected, Integer.MAX_VALUE, true, false);
        if (sel == null) {
            tell(player, inv.isEmpty(inv.selected) ? "The Flask's selected tank is empty: pick one in its menu (right-click the air)."
                : "The Flask's selected tank keeps what is left in it.");
            return 0;
        }
        int n = h.fill(from, sel, true);
        if (n <= 0) {
            tell(player, "That doesn't take " + sel.getLocalizedName() + (put ? "." : " (or is full)."));
            return 0;
        }
        inv.drain(inv.selected, n, true, true);
        return -n;
    }

    /** With the selected tank empty, the tank now holding f becomes the selected one (so a click puts it in again). */
    static void selectIfEmpty(SatchelInventory inv, FluidStack f) {
        if (!inv.isEmpty(inv.selected)) return;
        int at = inv.findFluid(f);
        if (at < 0 || at == inv.selected) return;
        inv.selected = at;
        inv.save();
    }

    static void tell(EntityPlayer player, String text) {
        if (player != null) player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + text));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (fluid) {
            MovingObjectPosition mop = getMovingObjectPositionFromPlayer(world, player, true);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (!world.isRemote) fluidClick(stack, world, player, mop);
                return stack;
            }
        }
        if (!world.isRemote) player.openGui(ValierTweaks.instance, ValierGui.SATCHEL, world, player.inventory.currentItem, 0, 0);
        return stack;
    }

    /** Picks up the fluid source looked at, or places the selected fluid against the block looked at. */
    void fluidClick(ItemStack stack, World world, EntityPlayer player, MovingObjectPosition mop) {
        SatchelInventory inv = SatchelInventory.of(stack);
        int x = mop.blockX, y = mop.blockY, z = mop.blockZ;
        if (!world.canMineBlock(player, x, y, z)) return;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, x, y, z)) return; // claims
        if (tankish(world.getTileEntity(x, y, z))) return; // tanks are clicked in onItemUseFirst
        Block b = world.getBlock(x, y, z);
        Fluid f = FluidRegistry.lookupFluidForBlock(b);
        boolean source = f != null && (b instanceof IFluidBlock ? ((IFluidBlock) b).canDrain(world, x, y, z)
            : world.getBlockMetadata(x, y, z) == 0);
        if (source && !player.isSneaking()) {
            FluidStack got = new FluidStack(f, 1000);
            if (inv.fill(got, false) >= 1000) {
                inv.fill(got, true);
                selectIfEmpty(inv, got);
                world.setBlockToAir(x, y, z);
            }
            return;
        }
        ForgeDirection d = ForgeDirection.getOrientation(mop.sideHit);
        int px = x + d.offsetX, py = y + d.offsetY, pz = z + d.offsetZ;
        if (!player.canPlayerEdit(px, py, pz, mop.sideHit, stack)) return;
        if (!com.valier.tweaks.LootSpawners.mayChange(player, world, px, py, pz)) return; // claims
        FluidStack sel = inv.drain(inv.selected, 1000, true, false);
        if (sel == null || sel.amount < 1000 || sel.getFluid() == null || sel.getFluid()
            .getBlock() == null) return;
        Block target = world.getBlock(px, py, pz);
        if (!world.isAirBlock(px, py, pz) && !target.isReplaceable(world, px, py, pz)) return;
        if (world.provider.isHellWorld && sel.getFluid() == FluidRegistry.WATER) {
            inv.drain(inv.selected, 1000, true, true); // evaporates, as a bucket of water does there
            return;
        }
        world.setBlock(px, py, pz, sel.getFluid()
            .getBlock(), 0, 3);
        inv.drain(inv.selected, 1000, true, true);
    }

    // ---- tooltip -----------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = SatchelTier.clamp(stack.getItemDamage());
        boolean creative = t == SatchelTier.CREATIVE;
        String each = creative ? "endless" : String.format("%,d", SatchelTier.capacity(t, fluid)) + (fluid ? " mB" : "");
        list.add(EnumChatFormatting.GRAY + "" + SatchelTier.slots(t, fluid) + (fluid ? " tanks of " : " slots of ") + each + " each");
        SatchelInventory inv = SatchelInventory.of(stack);
        if (inv != null) {
            int used = 0;
            for (int i = 0; i < inv.size; i++) if (!inv.isEmpty(i)) used++;
            if (used > 0) list.add(EnumChatFormatting.YELLOW + "" + used + " in use; selected: " + selectedText(inv));
            if (!fluid) list.add(EnumChatFormatting.DARK_GRAY + "Pickup " + (inv.pickup ? "on" : "off") + (inv.pickNew ? ", new items too" : "")
                + (inv.restock ? "; hotbar restock" : ""));
        }
        if (fluid) {
            list.add(EnumChatFormatting.DARK_GRAY + "Right-click a source: pick it up; a block: place.");
            list.add(EnumChatFormatting.DARK_GRAY + "A tank with fluid: take it; an empty tank");
            list.add(EnumChatFormatting.DARK_GRAY + "(or sneaking): put the selected fluid in.");
            list.add(EnumChatFormatting.DARK_GRAY + "In the air: its menu.");
        } else {
            list.add(EnumChatFormatting.DARK_GRAY + "Right-click a block: place the selected item.");
            list.add(EnumChatFormatting.DARK_GRAY + "In the air: its menu. Sneak + wheel: pick a slot.");
            list.add(EnumChatFormatting.DARK_GRAY + "Sneak + right-click a chest: " + boxText(inv == null ? 0 : inv.box) + ".");
        }
    }

    /** What a sneak + right-click on a container does in each container mode. */
    public static String boxText(int box) {
        switch (box) {
            case SatchelInventory.BOX_ALL:
                return "put everything in";
            case SatchelInventory.BOX_TAKE:
                return "take out what it holds";
            case SatchelInventory.BOX_OFF:
                return "nothing (places the item)";
            default:
                return "put the selected item in";
        }
    }

    static String selectedText(SatchelInventory inv) {
        int i = inv.selected;
        if (inv.isEmpty(i)) return "slot " + (i + 1) + " (empty)";
        if (inv.fluid) return inv.fluids[i].getLocalizedName() + ", " + (inv.creative ? "endless" : String.format("%,d mB", inv.fluids[i].amount));
        return inv.items[i].getDisplayName() + " × " + (inv.creative ? "endless" : String.format("%,d", inv.counts[i]));
    }
}
