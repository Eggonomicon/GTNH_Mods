package com.valier.tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.valier.tweaks.pipe.PipeActionMessage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Spectator mode's two messages (2.3.9, see {@link Spectators}). {@link Hello}: a player's Valier Tweaks tells the
 * server, as they join a world, that it can let them pass through blocks (only to a server with 2.3.9 or newer, which
 * knows this message). {@link State}: the server tells such a player's game that spectator mode is on or off. Older
 * games never get it, so they never see a message they don't know.
 */
public final class SpectatorMessage {

    private SpectatorMessage() {}

    static void send(EntityPlayerMP p, boolean on) {
        if (p.playerNetServerHandler != null) ValierTweaks.NET.sendTo(new State(on), p);
    }

    public static final class Hello implements IMessage {

        int version = 1;

        public Hello() {}

        @Override
        public void fromBytes(ByteBuf buf) {
            version = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(version);
        }
    }

    public static final class HelloHandler implements IMessageHandler<Hello, IMessage> {

        @Override
        public IMessage onMessage(Hello m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            Spectators.capable.add(p.getUniqueID());
            PipeActionMessage.TASKS.add(() -> Spectators.helloArrived(p)); // netty's thread: hand it to the server's
            return null;
        }
    }

    public static final class State implements IMessage {

        boolean on;

        public State() {}

        State(boolean on) {
            this.on = on;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {

        @Override
        public IMessage onMessage(State m, MessageContext ctx) {
            Client.receive(m.on);
            return null;
        }
    }

    /** Only loaded on a player's computer. */
    static final class Client {

        static void receive(boolean on) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.func_152344_a(() -> com.valier.tweaks.client.ClientSpectator.set(on)); // netty's thread: hand it to the game's
        }
    }
}
