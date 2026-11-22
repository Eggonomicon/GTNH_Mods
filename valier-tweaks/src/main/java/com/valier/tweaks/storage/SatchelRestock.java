package com.valier.tweaks.storage;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Satchels keeping the player stocked (2.3.3):
 * <ul>
 * <li>Hotbar restock (a Satchel switch, on by default): the stack in the hand used up (the last block placed, the last
 * pearl thrown, the last food eaten, a tool broken) is replaced, the next tick, by a stack of the same item from a
 * carried Satchel holding it. A stack moved away (into a chest, up into the inventory) is used up by nothing, so it
 * isn't replaced: only the game's "used up" events start it.</li>
 * <li>Refill (per slot): keeps that many of the slot's item in the inventory, topped up twice a second from the Satchel
 * (over its keep amount), onto stacks of it first, then empty slots, the hotbar first. Paused while a menu other than
 * the player's own inventory is open, so items put into a chest or the Satchel don't come straight back.</li>
 * </ul>
 * Cost: a restock is one map check a tick while nothing is pending (nearly always); a refill looks at the player's 36
 * slots every 10 ticks, and only does more for a Satchel with a refill slot.
 */
public final class SatchelRestock {

    /** A used-up stack waiting for the next tick: the hotbar slot and the item it held. */
    static final class Pending {

        final int slot;
        final ItemStack item;

        Pending(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }
    }

    private static final Map<EntityPlayer, Pending> PENDING = new WeakHashMap<>();

    /** The stack in the hand is used up (placed, thrown, broken: its size 0 now, or the hand already empty). */
    @SubscribeEvent
    public void onDestroyed(PlayerDestroyItemEvent e) {
        used(e.entityPlayer, e.original);
    }

    /** Eaten or drunk: the last one leaves the hand empty. */
    @SubscribeEvent
    public void onFinished(PlayerUseItemEvent.Finish e) {
        if (e.item != null && e.item.stackSize <= 0) used(e.entityPlayer, e.item);
    }

    static void used(EntityPlayer p, ItemStack s) {
        if (p == null || s == null || s.getItem() == null || p.worldObj.isRemote) return;
        ItemStack hand = p.inventory.getCurrentItem();
        if (hand != null && hand != s) return; // not the stack in the hand (armour wearing out, say)
        ItemStack key = s.copy();
        key.stackSize = 1;
        if (key.isItemStackDamageable()) key.setItemDamage(0); // a broken tool: a new one of it
        PENDING.put(p, new Pending(p.inventory.currentItem, key));
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        long t0 = com.valier.tweaks.ValierCost.start();
        EntityPlayer p = e.player;
        if (!PENDING.isEmpty()) {
            Pending w = PENDING.remove(p);
            if (w != null) restock(p, w.slot, w.item);
        }
        if (p.ticksExisted % 10 == 0 && !(p instanceof FakePlayer)) refill(p);
        com.valier.tweaks.ValierCost.add(com.valier.tweaks.ValierCost.Part.SATCHELS, t0);
    }

    /** A carried Satchel (not a Flask). */
    static SatchelInventory carried(ItemStack c) {
        if (c == null || !(c.getItem() instanceof ItemSatchel) || ((ItemSatchel) c.getItem()).fluid) return null;
        return SatchelInventory.of(c);
    }

    /** Hotbar slot (still the hand, still empty) gets a stack of item from the first Satchel with restock on holding it. */
    static boolean restock(EntityPlayer p, int slot, ItemStack item) {
        ItemStack[] main = p.inventory.mainInventory;
        if (p.inventory.currentItem != slot || slot < 0 || slot >= main.length || main[slot] != null) return false;
        for (ItemStack c : main) {
            SatchelInventory inv = carried(c);
            if (inv == null || !inv.restock) continue;
            int i = inv.find(item);
            if (i < 0) continue;
            ItemStack out = inv.extract(i, Math.max(1, inv.items[i].getMaxStackSize()), true, false);
            if (out == null) continue;
            main[slot] = out;
            return true;
        }
        return false;
    }

    /** Tops up every refill slot's item in the inventory; returns how many came out. */
    static int refill(EntityPlayer p) {
        if (p.openContainer != null && p.openContainer != p.inventoryContainer) return 0;
        ItemStack[] main = p.inventory.mainInventory;
        int moved = 0;
        for (int b = 0; b < main.length; b++) {
            SatchelInventory inv = carried(main[b]);
            if (inv == null || !inv.refills) continue;
            for (int i = 0; i < inv.size; i++) {
                if (inv.refill[i] <= 0 || inv.items[i] == null) continue;
                int want = inv.refill[i] - count(main, inv.items[i]);
                if (want <= 0) continue;
                ItemStack offer = inv.extract(i, want, true, true);
                if (offer == null) continue;
                int given = give(p, offer);
                if (given > 0) {
                    inv.extract(i, given, true, false);
                    moved += given;
                }
            }
        }
        return moved;
    }

    static int count(ItemStack[] main, ItemStack item) {
        int n = 0;
        for (ItemStack s : main) if (s != null && SatchelInventory.same(s, item)) n += s.stackSize;
        return n;
    }

    /** Puts offer (any size) into the inventory in stacks; returns how many went in. */
    static int give(EntityPlayer p, ItemStack offer) {
        int left = offer.stackSize, max = Math.max(1, offer.getMaxStackSize()), given = 0;
        while (left > 0) {
            ItemStack piece = offer.copy();
            piece.stackSize = Math.min(left, max);
            int size = piece.stackSize;
            p.inventory.addItemStackToInventory(piece);
            int in = size - Math.max(0, piece.stackSize);
            given += in;
            left -= in;
            if (in < size) break; // full
        }
        return given;
    }
}
