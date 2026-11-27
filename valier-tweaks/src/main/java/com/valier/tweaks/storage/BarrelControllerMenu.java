package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.TweaksConfig;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeFaceMessage;
import com.valier.tweaks.pipe.ValierMenu;

/**
 * A Barrel Controller's menu (right-click with an empty hand): how many barrels it reaches, what they hold, and the
 * "fill empty barrels" switch. The state goes out as one compound ({@link PipeFaceMessage}) at most twice a second while
 * it is open; the switch comes back as a {@link com.valier.tweaks.pipe.PipeActionMessage}.
 */
public class BarrelControllerMenu extends Container implements ValierMenu {

    public static final int FILL = 40;
    /** Kinds of item the menu lists (the most first). */
    public static final int SHOWN = 8;

    final TileBarrelController tile;
    final World world;
    public final int x, y, z;
    // What the screen shows (from the server).
    public int barrels, stores, kinds, empties, max, range;
    public boolean fillEmpty, capped;
    public final List<ItemStack> top = new ArrayList<>();
    public final List<Long> topCount = new ArrayList<>();
    private String lastSent;
    private int ticks;

    public BarrelControllerMenu(TileBarrelController tile, World world, int x, int y, int z) {
        this.tile = tile;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    boolean server() {
        return tile != null && !world.isRemote;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false;
        return tile == null || !tile.isInvalid() && world.getTileEntity(x, y, z) == tile;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }

    @Override
    public void apply(EntityPlayer player, int action, int a, int b, ItemStack stack, String text) {
        if (!server() || !LootSpawners.mayChange(player, world, x, y, z)) return;
        if (action != FILL) return;
        tile.fillEmpty = !tile.fillEmpty;
        tile.markDirty();
        ValierTweaks.LOG.info("{} turned fill empty barrels {} on the Barrel Controller at {} {} {}", player.getCommandSenderName(),
            tile.fillEmpty ? "on" : "off", x, y, z);
        ticks = 0;
        lastSent = null;
        detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!server() || ticks++ % 10 != 0) return;
        NBTTagCompound t = state();
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
        ticks = 0;
        detectAndSendChanges();
    }

    /** What the screen is sent. */
    NBTTagCompound state() {
        int[] n = tile.counts();
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Barrels", n[0]);
        t.setInteger("Stores", n[1]);
        t.setInteger("Kinds", n[2]);
        t.setInteger("Empties", n[3]);
        t.setInteger("Max", TweaksConfig.barrelControllerMax);
        t.setInteger("Range", TweaksConfig.barrelControllerRange);
        t.setBoolean("Fill", tile.fillEmpty);
        t.setBoolean("Capped", tile.capped);
        // The kinds held most of (summed over barrels holding the same item).
        Map<TileBarrelController.Key, long[]> sum = new HashMap<>();
        Map<TileBarrelController.Key, ItemStack> item = new HashMap<>();
        for (BarrelStore st : tile.stores) {
            if (st.item == null) continue;
            TileBarrelController.Key k = new TileBarrelController.Key(st.item);
            item.putIfAbsent(k, st.item);
            sum.computeIfAbsent(k, q -> new long[1])[0] += st.creative ? 0 : st.count;
        }
        List<Map.Entry<TileBarrelController.Key, long[]>> all = new ArrayList<>(sum.entrySet());
        all.sort((p, q) -> Long.compare(q.getValue()[0], p.getValue()[0]));
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < Math.min(SHOWN, all.size()); i++) {
            NBTTagCompound e = item.get(all.get(i)
                .getKey())
                .writeToNBT(new NBTTagCompound());
            e.setLong("Amount", all.get(i)
                .getValue()[0]);
            list.appendTag(e);
        }
        t.setTag("Top", list);
        return t;
    }

    @Override
    public void receive(NBTTagCompound t) {
        barrels = t.getInteger("Barrels");
        stores = t.getInteger("Stores");
        kinds = t.getInteger("Kinds");
        empties = t.getInteger("Empties");
        max = t.getInteger("Max");
        range = t.getInteger("Range");
        fillEmpty = t.getBoolean("Fill");
        capped = t.getBoolean("Capped");
        top.clear();
        topCount.clear();
        NBTTagList list = t.getTagList("Top", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            ItemStack s = ItemStack.loadItemStackFromNBT(e);
            if (s == null) continue;
            top.add(s);
            topCount.add(e.getLong("Amount"));
        }
    }

    public static Object server(EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileBarrelController) || !LootSpawners.mayChange(player, world, x, y, z)) return null;
        return new BarrelControllerMenu((TileBarrelController) te, world, x, y, z);
    }

    public static BarrelControllerMenu client(EntityPlayer player, World world, int x, int y, int z) {
        return new BarrelControllerMenu(null, world, x, y, z);
    }
}
