package com.valier.tweaks.pipe;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server to player: the state of the Valier menu that is open (window id; {@link ValierMenu}), all at once. */
public class PipeFaceMessage implements IMessage {

    int window;
    NBTTagCompound tag;

    public PipeFaceMessage() {}

    public PipeFaceMessage(int window, NBTTagCompound tag) {
        this.window = window;
        this.tag = tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        window = buf.readInt();
        tag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(window);
        ByteBufUtils.writeTag(buf, tag);
    }

    /** Players' computers only. */
    public static class Handler implements IMessageHandler<PipeFaceMessage, IMessage> {

        @Override
        public IMessage onMessage(PipeFaceMessage m, MessageContext ctx) {
            Client.receive(m);
            return null;
        }
    }

    /** Kept apart so a server never loads Minecraft's client class. */
    static final class Client {

        static void receive(PipeFaceMessage m) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.func_152344_a(() -> { // netty's thread: hand it to the game's
                if (mc.thePlayer == null || m.tag == null) return;
                if (mc.thePlayer.openContainer instanceof ValierMenu && mc.thePlayer.openContainer.windowId == m.window)
                    ((ValierMenu) mc.thePlayer.openContainer).receive(m.tag);
            });
        }
    }
}
