package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.valier.tweaks.ValierTweaks;

/**
 * The menu of one pipe face (both sides). Its 18 filter slots are ghosts: clicking one with an item sets a copy (the item
 * stays on the cursor; a stack of more than one also sets the amount), right-clicking clears it, and nothing can be
 * taken out of them or collected from them. Everything else comes as {@link PipeActionMessage}s, checked here. The
 * server sends the face's settings to the player as one compound ({@link PipeFaceMessage}) whenever they change.
 */
public class ContainerPipeFace extends Container {

    /** Where the GUI draws things; the slots must match it. */
    public static final int FILTER_X = 8, FILTER_Y = 105, INV_Y = 159, HOTBAR_Y = 217;

    public final TilePipe pipe;
    public final int side;
    /** The face's settings: the pipe's own on the server, a copy kept up to date on the client. */
    public FaceConfig cfg;
    /** From the server, for the client's screen: the tier's most per transfer and shortest interval, the network. */
    public int max, minTicks;
    public String netState = "";
    private String lastSent;
    final IInventory filterView;

    public ContainerPipeFace(InventoryPlayer inv, TilePipe pipe, int side, FaceConfig cfg) {
        this.pipe = pipe;
        this.side = side;
        this.cfg = cfg;
        this.max = PipeTier.max(pipe.kind(), pipe.tier());
        this.minTicks = PipeTier.interval(pipe.tier());
        filterView = new FilterView(this);
        for (int i = 0; i < FaceConfig.FILTER; i++)
            addSlotToContainer(new Ghost(filterView, i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c, 8 + c * 18, HOTBAR_Y));
    }

    public boolean fluid() {
        return pipe.kind() == PipeKind.FLUID;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !pipe.isInvalid() && pipe.getWorldObj()
            .getTileEntity(pipe.xCoord, pipe.yCoord, pipe.zCoord) == pipe && pipe.blockOn(side)
            && player.getDistanceSq(pipe.xCoord + 0.5, pipe.yCoord + 0.5, pipe.zCoord + 0.5) <= 64.0;
    }

    /** Ghost filter slots: left-click with an item sets it, right-click clears; never take. */
    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        if (slot >= 0 && slot < FaceConfig.FILTER) {
            if (mode == 0 && !pipe.getWorldObj().isRemote && PipeClicks.may(pipe, player)) {
                ItemStack cursor = player.inventory.getItemStack();
                if (button == 1) {
                    cfg.filter[slot] = null;
                    cfg.count[slot] = 0;
                    changed();
                } else if (cursor != null) {
                    setSlot(slot, cursor, cursor.stackSize > 1 ? cursor.stackSize : -1);
                    changed();
                }
            }
            return null;
        }
        if (mode == 6) return null; // double-click collect would reach into the ghosts
        return super.slotClick(slot, button, mode, player);
    }

    @Override
    public boolean func_94530_a(ItemStack stack, Slot slot) {
        return !(slot instanceof Ghost);
    }

    /** Shift-click from the inventory: into the first empty filter slot, as a ghost. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || slot instanceof Ghost || !slot.getHasStack() || pipe.getWorldObj().isRemote) return null;
        if (!PipeClicks.may(pipe, player)) return null;
        ItemStack s = slot.getStack();
        for (int i = 0; i < FaceConfig.FILTER; i++) if (cfg.filter[i] != null && ItemStack.areItemStacksEqual(one(cfg.filter[i]), one(s))) return null;
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            if (cfg.filter[i] == null) {
                setSlot(i, s, -1);
                changed();
                break;
            }
        }
        return null;
    }

    static ItemStack one(ItemStack s) {
        ItemStack c = s.copy();
        c.stackSize = 1;
        return c;
    }

    /** Sets a filter slot to a ghost of s; amount -1 keeps the slot's amount (fluids: amount buckets' worth). */
    void setSlot(int i, ItemStack s, int amount) {
        cfg.filter[i] = one(s);
        if (amount >= 0) {
            FluidStack f = fluid() ? Fluids.of(s) : null;
            cfg.count[i] = f != null ? f.amount * Math.max(1, amount) : amount;
        }
    }

    /** One action from the menu (server side; every value checked). */
    public void apply(EntityPlayer player, int action, int a, int b, ItemStack stack, String text) {
        if (!PipeClicks.may(pipe, player)) return;
        switch (action) {
            case PipeActionMessage.MODE:
                cfg.mode = cfg.mode.next(a < 0 ? -1 : 1);
                break;
            case PipeActionMessage.REDSTONE:
                cfg.redstone = Math.floorMod(cfg.redstone + (a < 0 ? -1 : 1), 3);
                break;
            case PipeActionMessage.AMOUNT: {
                int now = cfg.amount <= 0 ? max : cfg.amount;
                int next = Math.max(1, Math.min(max, now + clampStep(a)));
                cfg.amount = next >= max ? 0 : next; // 0: the tier's most
                break;
            }
            case PipeActionMessage.TICKS: {
                int now = cfg.interval(pipe.tier());
                int next = Math.max(minTicks, Math.min(1200, now + clampStep(a)));
                cfg.ticks = next <= minTicks ? 0 : next;
                break;
            }
            case PipeActionMessage.PRIORITY:
                cfg.priority = Math.max(-99, Math.min(99, cfg.priority + Math.max(-10, Math.min(10, a))));
                break;
            case PipeActionMessage.OVERFLOW:
                cfg.acceptOverflow = !cfg.acceptOverflow;
                break;
            case PipeActionMessage.KIND:
                if (a >= 0 && a < FaceConfig.Kind.values().length) cfg.kind = FaceConfig.Kind.values()[a];
                break;
            case PipeActionMessage.BLACKLIST:
                cfg.blacklist = !cfg.blacklist;
                break;
            case PipeActionMessage.DAMAGE: {
                FaceConfig.Damage[] all = FaceConfig.Damage.values();
                cfg.damage = all[Math.floorMod(cfg.damage.ordinal() + (a < 0 ? -1 : 1), all.length)];
                break;
            }
            case PipeActionMessage.NBT:
                cfg.matchNbt = !cfg.matchNbt;
                break;
            case PipeActionMessage.ORE:
                cfg.oreDict = !cfg.oreDict;
                break;
            case PipeActionMessage.SNAPSHOT:
                snapshot();
                break;
            case PipeActionMessage.CLEAR:
                for (int i = 0; i < FaceConfig.FILTER; i++) {
                    cfg.filter[i] = null;
                    cfg.count[i] = 0;
                }
                break;
            case PipeActionMessage.COUNT:
                if (a < 0 || a >= FaceConfig.FILTER || cfg.filter[a] == null) return;
                cfg.count[a] = Math.max(0, Math.min(fluid() ? 10_000_000 : 1_000_000, cfg.count[a] + clampStep(b)));
                break;
            case PipeActionMessage.SET_SLOT:
                if (a < 0 || a >= FaceConfig.FILTER || stack == null || stack.getItem() == null) return;
                setSlot(a, stack, -1);
                break;
            case PipeActionMessage.TAG:
                cfg.tag = FaceConfig.cleanTag(text);
                break;
            case PipeActionMessage.FACE:
                if (a >= 0 && a < 6 && a != side && pipe.blockOn(a)) PipeGui.open(player, pipe, a);
                return;
            default:
                return;
        }
        changed();
    }

    static int clampStep(int step) {
        return Math.max(-100_000, Math.min(100_000, step));
    }

    /** The filter becomes a list of what the block on this face holds now (each item once, up to 18). */
    void snapshot() {
        List<ItemStack> found = new ArrayList<>();
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int x = pipe.xCoord + d.offsetX, y = pipe.yCoord + d.offsetY, z = pipe.zCoord + d.offsetZ;
        if (fluid()) {
            net.minecraftforge.fluids.IFluidHandler t = FluidMover.tank(pipe.getWorldObj(), x, y, z);
            if (t != null) for (FluidStack f : FluidMover.contents(t, d.getOpposite())) {
                ItemStack filled = net.minecraftforge.fluids.FluidContainerRegistry.fillFluidContainer(f.copy(),
                    new ItemStack(net.minecraft.init.Items.bucket));
                if (filled != null && found.size() < FaceConfig.FILTER) found.add(filled);
            }
        } else {
            IInventory inv = ItemMover.inventory(pipe.getWorldObj(), x, y, z);
            if (inv != null) {
                for (int i = 0; i < inv.getSizeInventory() && found.size() < FaceConfig.FILTER; i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s == null) continue;
                    boolean seen = false;
                    for (ItemStack f : found) seen |= ItemMover.stackable(f, s);
                    if (!seen) found.add(one(s));
                }
            }
        }
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            cfg.filter[i] = i < found.size() ? found.get(i) : null;
            cfg.count[i] = 0;
        }
        cfg.kind = FaceConfig.Kind.ITEMS;
    }

    void changed() {
        if (pipe.getWorldObj().isRemote) return;
        pipe.faceChanged();
        detectAndSendChanges();
    }

    /** Sends the settings (as one compound) to the player when they changed. */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (pipe.getWorldObj().isRemote) return;
        NBTTagCompound t = cfg.write();
        t.setInteger("Max", max);
        t.setInteger("MinTicks", minTicks);
        PipeNetwork net = PipeNetworks.networkAt(pipe.getWorldObj(), pipe.xCoord, pipe.yCoord, pipe.zCoord);
        t.setString("Net", net == null ? "" : net.tooBig ? "network too big: it does nothing" : net.pipes.size() + " pipes, "
            + net.all.size() + " faces on this network");
        String s = t.toString();
        if (s.equals(lastSent)) return;
        lastSent = s;
        for (Object o : crafters) {
            if (o instanceof EntityPlayerMP) ValierTweaks.NET.sendTo(new PipeFaceMessage(windowId, t), (EntityPlayerMP) o);
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        lastSent = null;
        detectAndSendChanges();
    }

    /** The client's copy, from the server. */
    public void receive(NBTTagCompound t) {
        cfg = FaceConfig.read(t);
        max = t.getInteger("Max");
        minTicks = t.getInteger("MinTicks");
        netState = t.getString("Net");
    }

    /** A ghost slot: shows the filter item, takes nothing, gives nothing. */
    public static final class Ghost extends Slot {

        Ghost(IInventory inv, int i, int x, int y) {
            super(inv, i, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return false;
        }
    }

    /** The filter as an inventory, for the ghost slots (vanilla syncs their contents). */
    static final class FilterView implements IInventory {

        final ContainerPipeFace c;

        FilterView(ContainerPipeFace c) {
            this.c = c;
        }

        @Override
        public int getSizeInventory() {
            return FaceConfig.FILTER;
        }

        @Override
        public ItemStack getStackInSlot(int i) {
            return c.cfg.filter[i];
        }

        @Override
        public ItemStack decrStackSize(int i, int n) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int i) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int i, ItemStack s) {
            c.cfg.filter[i] = s == null ? null : one(s);
        }

        @Override
        public String getInventoryName() {
            return "Filter";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer p) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int i, ItemStack s) {
            return false;
        }
    }
}
