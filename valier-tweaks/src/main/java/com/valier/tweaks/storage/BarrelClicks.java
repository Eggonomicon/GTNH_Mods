package com.valier.tweaks.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.valier.tweaks.LootSpawners;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** What players do to barrels (server side): put in, take out, upgrade, open the menu, pick up with a wrench. */
public final class BarrelClicks {

    /** The tag in a barrel item holding what it held and its channel. */
    public static final String ITEM_TAG = "ValierBarrel";
    private static final Map<UUID, long[]> lastClick = new HashMap<>();

    private BarrelClicks() {}

    /** Right-click: an upgrade, the menu (sneaking, empty hand), or put the held stack in (twice quickly: everything). */
    static void rightClick(EntityPlayer p, TileBarrel t) {
        ItemStack held = p.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBarrelUpgrade) {
            upgrade(p, t, held);
            return;
        }
        if (p.isSneaking() && held == null) {
            if (may(p, t)) p.openGui(ValierTweaks.instance, ValierGui.BARREL, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord);
            else tell(p, EnumChatFormatting.RED, "You can't change this barrel: it's protected.");
            return;
        }
        BarrelStore st = t.store();
        if (st.creative) { // 2.3.1: the held item becomes what it gives (and stays in the hand); nothing goes in by hand
            if (held == null || st.matches(held)) return;
            if (!may(p, t)) {
                tell(p, EnumChatFormatting.RED, "You can't change this barrel: it's protected.");
                return;
            }
            st.set(held);
            t.markDirty();
            ValierTweaks.LOG.info("{} set the Creative barrel at {} {} {} to {}", p.getCommandSenderName(), t.xCoord, t.yCoord,
                t.zCoord, held);
            ChatComponentText msg = new ChatComponentText("This Creative barrel gives ");
            msg.getChatStyle()
                .setColor(EnumChatFormatting.GREEN);
            msg.appendSibling(new net.minecraft.util.ChatComponentTranslation(held.getUnlocalizedName() + ".name"))
                .appendSibling(new ChatComponentText(" now, without end."));
            p.addChatMessage(msg);
            return;
        }
        boolean twice = twice(p, t);
        if (held != null && st.takes(held)) {
            long n = st.insert(held, held.stackSize, false);
            held.stackSize -= (int) n;
            if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        }
        if (twice) insertAll(p, st);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** Every stack of the barrel's item the player carries goes in (as much as fits). */
    static void insertAll(EntityPlayer p, BarrelStore st) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || !st.matches(s)) continue;
            long n = st.insert(s, s.stackSize, false);
            if (n <= 0) break;
            s.stackSize -= (int) n;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
    }

    private static boolean twice(EntityPlayer p, TileBarrel t) {
        long now = t.getWorldObj()
            .getTotalWorldTime();
        long at = ((long) t.xCoord & 0x3FFFFFF) << 38 | ((long) t.zCoord & 0x3FFFFFF) << 12 | (t.yCoord & 0xFFF);
        long[] last = lastClick.get(p.getUniqueID());
        lastClick.put(p.getUniqueID(), new long[] { at, now });
        return last != null && last[0] == at && now - last[1] <= 10;
    }

    /** Left-click: a stack (sneaking: one item) into the player's inventory, or at their feet. */
    static void take(EntityPlayer p, TileBarrel t, boolean one) {
        BarrelStore st = t.store();
        if (st.item == null) return;
        ItemStack out = st.extract(one ? 1 : st.item.getMaxStackSize(), false, false);
        if (out == null) return;
        if (!p.inventory.addItemStackToInventory(out) || out.stackSize > 0) {
            if (out.stackSize > 0) {
                EntityItem e = new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, out);
                e.delayBeforeCanPickup = 0;
                p.worldObj.spawnEntityInWorld(e);
            }
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    static void upgrade(EntityPlayer p, TileBarrel t, ItemStack held) {
        int to = held.getItemDamage();
        if (to != t.tier + 1) {
            tell(p, EnumChatFormatting.RED, "This is " + an(BarrelTier.NAMES[t.tier]) + " barrel: it takes "
                + (t.tier + 1 < BarrelTier.COUNT ? an(BarrelTier.NAMES[t.tier + 1]) + " upgrade." : "no more upgrades."));
            return;
        }
        if (!may(p, t)) {
            tell(p, EnumChatFormatting.RED, "You can't change this barrel: it's protected.");
            return;
        }
        World w = t.getWorldObj();
        w.setBlockMetadataWithNotify(t.xCoord, t.yCoord, t.zCoord, to, 3);
        t.tier = to;
        if (!t.channel().isEmpty()) {
            BarrelChannels d = BarrelChannels.get();
            if (d != null) d.retier(t);
        } else {
            t.own.stacks = BarrelTier.stacks(to);
            t.own.changed();
        }
        t.markDirty();
        if (!p.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        }
        ValierTweaks.LOG.info("{} upgraded the barrel at {} {} {} to {}", p.getCommandSenderName(), t.xCoord, t.yCoord, t.zCoord,
            BarrelTier.NAMES[to]);
        tell(p, EnumChatFormatting.GREEN, "It is " + an(BarrelTier.NAMES[to]) + " barrel now: " + String.format("%,d",
            BarrelTier.stacks(to)) + " stacks.");
    }

    static String an(String tier) {
        return (tier.equals("Basic") || tier.equals("Creative") ? "a " : "an ") + tier; // "an LV", "an MV", as spoken
    }

    /** Sneak + right-click with a wrench: the barrel comes off as an item holding what it held, and its channel. */
    static void pickUp(EntityPlayer p, TileBarrel t, ItemStack wrench) {
        if (!may(p, t)) {
            tell(p, EnumChatFormatting.RED, "You can't change this barrel: it's protected.");
            return;
        }
        World w = t.getWorldObj();
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        ItemStack item = toItem(t);
        t.pickedUp = true;
        w.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(ModBlocks.barrel) + (t.tier << 12));
        w.setBlockToAir(x, y, z);
        LootSpawners.give(p, item);
        LootSpawners.wrenchUsed(wrench, p, x, y, z);
    }

    /**
     * The barrel as an item: it leaves its channel first (keeping what the others can't hold) and the item remembers the
     * channel, so placed again it joins it again. The tile is emptied.
     */
    static ItemStack toItem(TileBarrel t) {
        String scope = t.scope, channel = t.channel();
        if (!channel.isEmpty()) {
            BarrelChannels d = BarrelChannels.get();
            if (d != null) d.leave(t);
        }
        NBTTagCompound tag = t.saveForItem();
        if (!channel.isEmpty()) {
            tag.setString("Scope", scope);
            tag.setString("Channel", channel);
        }
        ItemStack s = new ItemStack(ModBlocks.barrel, 1, t.tier);
        if (!tag.hasNoTags()) {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag(ITEM_TAG, tag);
            s.setTagCompound(root);
        }
        t.own.item = null;
        t.own.count = 0;
        return s;
    }

    static boolean may(EntityPlayer p, TileBarrel t) {
        return LootSpawners.mayChange(p, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord);
    }

    static void tell(EntityPlayer p, EnumChatFormatting colour, String text) {
        ChatComponentText c = new ChatComponentText(text);
        c.getChatStyle()
            .setColor(colour);
        p.addChatMessage(c);
    }

    /** Sneak + right-click with a wrench (Forge doesn't always pass sneak-clicks with an item to the block). */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onClick(PlayerInteractEvent event) {
            if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote) return;
            EntityPlayer p = event.entityPlayer;
            if (!p.isSneaking() || event.world.getBlock(event.x, event.y, event.z) != ModBlocks.barrel) return;
            ItemStack held = p.getHeldItem();
            if (!LootSpawners.isWrench(held, p, event.x, event.y, event.z)) return;
            Object te = event.world.getTileEntity(event.x, event.y, event.z);
            if (!(te instanceof TileBarrel)) return;
            event.setCanceled(true);
            pickUp(p, (TileBarrel) te, held);
        }
    }
}
