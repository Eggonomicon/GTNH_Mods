package com.valier.tweaks.storage;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import com.valier.tweaks.pipe.PipeActionMessage;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Satchels picking things up (before they reach the inventory's own stacks; a slot sending home first,
 * {@link SatchelHome}), and sneak + mouse wheel choosing a Satchel's or Flask's slot ({@link Scroll}).
 */
public final class SatchelEvents {

    /**
     * An item is picked up: every Satchel in the player's inventory with pickup on takes what it holds (or new items,
     * if set) first. Items the player threw in the last 10 seconds are left alone, so they don't come straight back.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPickup(EntityItemPickupEvent event) {
        EntityPlayer p = event.entityPlayer;
        EntityItem e = event.item;
        if (p == null || p.worldObj.isRemote || e == null || e.isDead) return;
        long t0 = com.valier.tweaks.ValierCost.start();
        try {
            pickup(event, p, e);
        } finally {
            com.valier.tweaks.ValierCost.add(com.valier.tweaks.ValierCost.Part.SATCHELS, t0);
        }
    }

    private static void pickup(EntityItemPickupEvent event, EntityPlayer p, EntityItem e) {
        ItemStack s = e.getEntityItem();
        if (s == null || s.stackSize <= 0 || s.getItem() instanceof ItemSatchel) return;
        String thrower = e.func_145800_j();
        if (thrower != null && thrower.equals(p.getCommandSenderName()) && e.age < 200) return;
        int before = s.stackSize;
        // 2.3.3: a slot sending home first (filling to its keep amount, the rest to its barrel channel), before a full
        // slot's void overflow could destroy what the channel has room for.
        for (ItemStack c : p.inventory.mainInventory) {
            SatchelInventory inv = SatchelRestock.carried(c);
            if (inv == null || !inv.pickup) continue;
            s.stackSize -= SatchelHome.pickup(p, inv, s);
            if (s.stackSize <= 0) break;
        }
        if (s.stackSize > 0) for (ItemStack c : p.inventory.mainInventory) {
            SatchelInventory inv = SatchelRestock.carried(c);
            if (inv == null || !inv.pickup) continue;
            int took = inv.insert(s, s.stackSize, true, false);
            if (took > 0) s.stackSize -= took;
            if (s.stackSize <= 0) break;
        }
        int took = before - s.stackSize;
        if (took <= 0) return;
        p.onItemPickup(e, took);
        p.worldObj.playSoundAtEntity(p, "random.pop", 0.2F,
            ((p.worldObj.rand.nextFloat() - p.worldObj.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        if (s.stackSize <= 0) {
            e.setDead();
            event.setCanceled(true);
        } else e.setEntityItemStack(s);
    }

    /** Sneak + mouse wheel on a held Satchel or Flask: the next or previous slot holding something (or any, if none). */
    static void scroll(EntityPlayer p, int dir) {
        ItemStack held = p.getHeldItem();
        SatchelInventory inv = SatchelInventory.of(held);
        if (inv == null) return;
        int i = inv.selected;
        for (int k = 0; k < inv.size; k++) {
            i = Math.floorMod(i + (dir < 0 ? -1 : 1), inv.size);
            if (!inv.isEmpty(i)) break;
        }
        inv.selected = i;
        inv.save();
        if (p instanceof EntityPlayerMP) ((EntityPlayerMP) p).inventoryContainer.detectAndSendChanges();
    }

    /** Player to server: sneak + mouse wheel with a Satchel or Flask in hand. */
    public static final class Scroll implements IMessage {

        int dir;

        public Scroll() {}

        public Scroll(int dir) {
            this.dir = dir;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            dir = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(dir);
        }

        public static final class Handler implements IMessageHandler<Scroll, IMessage> {

            @Override
            public IMessage onMessage(Scroll m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().playerEntity;
                PipeActionMessage.TASKS.add(() -> scroll(p, m.dir));
                return null;
            }
        }
    }
}
