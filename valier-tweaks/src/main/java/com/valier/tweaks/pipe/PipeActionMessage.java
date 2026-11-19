package com.valier.tweaks.pipe;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Player to server: something done in a pipe face's menu (a button, the mouse wheel on a filter slot, an item dragged
 * in from NEI, the tag typed). The server only takes it for the menu that player has open (same window id, still in
 * reach, allowed by claims) and checks every value ({@link ContainerPipeFace#apply}).
 */
public class PipeActionMessage implements IMessage {

    public static final int MODE = 0, REDSTONE = 1, AMOUNT = 2, TICKS = 3, PRIORITY = 4, OVERFLOW = 5, KIND = 6, BLACKLIST = 7,
        DAMAGE = 8, NBT = 9, ORE = 10, SNAPSHOT = 11, CLEAR = 12, COUNT = 13, SET_SLOT = 14, TAG = 15, FACE = 16;

    /** Work handed from netty's thread to the server thread ({@link PipeNetworks} runs it each server tick). */
    static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    int window, action, a, b;
    ItemStack stack;
    String text;

    public PipeActionMessage() {}

    public PipeActionMessage(int window, int action, int a, int b, ItemStack stack, String text) {
        this.window = window;
        this.action = action;
        this.a = a;
        this.b = b;
        this.stack = stack;
        this.text = text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        window = buf.readInt();
        action = buf.readByte();
        a = buf.readInt();
        b = buf.readInt();
        stack = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        text = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
        if (text != null && text.length() > 64) text = text.substring(0, 64);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(window);
        buf.writeByte(action);
        buf.writeInt(a);
        buf.writeInt(b);
        buf.writeBoolean(stack != null);
        if (stack != null) ByteBufUtils.writeItemStack(buf, stack);
        buf.writeBoolean(text != null);
        if (text != null) ByteBufUtils.writeUTF8String(buf, text);
    }

    public static class Handler implements IMessageHandler<PipeActionMessage, IMessage> {

        @Override
        public IMessage onMessage(PipeActionMessage m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            TASKS.add(() -> {
                if (p.openContainer instanceof ContainerPipeFace && p.openContainer.windowId == m.window
                    && p.openContainer.canInteractWith(p)) ((ContainerPipeFace) p.openContainer).apply(p, m.action, m.a, m.b, m.stack, m.text);
            });
            return null;
        }
    }
}
