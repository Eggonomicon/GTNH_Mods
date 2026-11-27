package com.valier.tweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.valier.tweaks.SpectatorMessage;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Spectator mode on a player's own computer (2.3.9, see {@link com.valier.tweaks.Spectators}): tells a server with
 * Valier Tweaks 2.3.9 or newer, on joining each world, that this game can pass through blocks; while the server says
 * spectator mode is on, keeps the player's {@code noClip} and flying on before each tick (so 1.7.10's own movement code
 * lets them through blocks) and hides the "inside a block" overlay.
 */
public final class ClientSpectator {

    static volatile boolean on;

    public static void set(boolean value) {
        on = value;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.noClip = value;
            if (value) {
                mc.thePlayer.capabilities.allowFlying = true;
                mc.thePlayer.capabilities.isFlying = true;
            }
        }
    }

    public static boolean isOn() {
        return on;
    }

    /** Forge's bus: joining a world, and the overlay. */
    public static final class Events {

        @SubscribeEvent
        public void onJoin(EntityJoinWorldEvent event) {
            if (!event.world.isRemote || event.entity != Minecraft.getMinecraft().thePlayer) return;
            if (ValierTweaks.serverHasSpectator()) ValierTweaks.NET.sendToServer(new SpectatorMessage.Hello());
        }

        @SubscribeEvent
        public void onOverlay(RenderBlockOverlayEvent event) {
            if (on) event.setCanceled(true);
        }
    }

    /** FML's bus: the player's tick, and leaving the server. */
    public static final class Ticks {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (!on || event.phase != TickEvent.Phase.START || event.side != Side.CLIENT || event.player != Minecraft.getMinecraft().thePlayer) return;
            event.player.noClip = true;
            event.player.capabilities.allowFlying = true;
            event.player.capabilities.isFlying = true;
            event.player.fallDistance = 0;
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            on = false;
        }
    }
}
