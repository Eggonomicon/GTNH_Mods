package com.valier.tweaks;

import static com.valier.tweaks.SelfTest.check;
import static com.valier.tweaks.SelfTest.log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code valiertweaks selftest spectate} (2.3.9), the server's side of spectator mode: a player without the permission is
 * refused; on, the player flies, can't be hurt, has noClip each tick, night vision and (if ServerUtilities takes a fake
 * player) vanish; clicks, breaking, picking up and attacking are cancelled and a zombie drops them as a target; it is
 * saved with the player and comes back on login; off inside stone, they're lifted onto the stone with flying, damage
 * and night vision as before. The client's part (passing through blocks on their own computer) needs a real game.
 */
public final class SpectateSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int cx = SelfTest.CX - 26, cz = SelfTest.CZ - 30;
    final int x = (cx << 4) + 8, z = (cz << 4) + 8;
    int y, ticks;
    final List<String> said = new ArrayList<>();
    FakePlayer p;
    EntityZombie zombie;

    void start() {
        world.theChunkProviderServer.loadChunk(cx, cz);
        SelfTest.keepLoaded(world, cx, cz, cx, cz);
        y = world.getHeightValue(x, z);
        p = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000d07"), "SpecTester")) {

            @Override
            public void addChatMessage(IChatComponent message) {
                said.add(message.getUnformattedText());
            }
        };
        p.setPosition(x + 0.5, y, z + 0.5);
        // A connection that goes nowhere, so the abilities and effect packets spectator mode sends have somewhere to go.
        new net.minecraft.network.NetHandlerPlayServer(net.minecraft.server.MinecraftServer.getServer(), new net.minecraft.network.NetworkManager(false), p);

        said.clear();
        new Spectators.Command().processCommand(p, new String[0]);
        check(String.join(" | ", said).contains("can't use spectator mode") && !Spectators.isSpectating(p),
            "a player without the permission is refused (" + String.join(" | ", said) + ")");

        said.clear();
        Spectators.enable(p);
        check(Spectators.isSpectating(p), "on: the player is spectating (" + String.join(" | ", said) + ")");
        check(Spectators.persisted(p).getCompoundTag(Spectators.TAG).getBoolean("On"), "it's saved with the player");
        check(p.capabilities.allowFlying && p.capabilities.isFlying && p.capabilities.disableDamage && p.noClip,
            "they fly, can't be hurt and pass through blocks (noClip)");
        log("ServerUtilities vanish for a fake player: " + (Spectators.persisted(p).getBoolean("vanish") ? "on" : "not taken"));

        // Hands off.
        PlayerInteractEvent click = new PlayerInteractEvent(p, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y - 1, z, 1, world);
        MinecraftForge.EVENT_BUS.post(click);
        BlockEvent.BreakEvent dig = new BlockEvent.BreakEvent(x, y - 1, z, world, world.getBlock(x, y - 1, z), 0, p);
        MinecraftForge.EVENT_BUS.post(dig);
        EntityItem item = new EntityItem(world, x + 0.5, y, z + 0.5, new ItemStack(Items.diamond));
        EntityItemPickupEvent pick = new EntityItemPickupEvent(p, item);
        MinecraftForge.EVENT_BUS.post(pick);
        zombie = new EntityZombie(world);
        zombie.setLocationAndAngles(x + 2.5, y, z + 0.5, 0, 0);
        AttackEntityEvent hit = new AttackEntityEvent(p, zombie);
        MinecraftForge.EVENT_BUS.post(hit);
        check(click.isCanceled() && dig.isCanceled() && pick.isCanceled() && hit.isCanceled(),
            "clicking, breaking, picking up and attacking are all stopped");
        zombie.setAttackTarget(p);
        check(zombie.getAttackTarget() == null, "a zombie drops the spectator as its target");

        p.noClip = false;
        p.capabilities.isFlying = false;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        log("waiting a tick for spectator mode to put itself back");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 2) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            // FakePlayers aren't ticked by the world, so run the player tick the way FML would.
            new Spectators().onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, p));
            check(p.noClip && p.capabilities.isFlying, "each tick puts noClip and flying back");
            check(p.getActivePotionEffect(Potion.nightVision) != null, "with night vision, to see inside rock");

            // Logging out and in.
            Spectators.active.remove(p.getUniqueID());
            new Spectators().onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            check(Spectators.isSpectating(p), "after logging out and in, they're still spectating");

            // Off, inside stone.
            for (int k = 0; k < 2; k++) world.setBlock(x, y + k, z, Blocks.stone);
            world.setBlock(x, y + 2, z, Blocks.stone);
            said.clear();
            Spectators.disable(p);
            String r = String.join(" | ", said);
            check(!Spectators.isSpectating(p) && !Spectators.persisted(p).hasKey(Spectators.TAG), "off: no longer spectating, nothing saved (" + r + ")");
            check(!p.noClip && !p.capabilities.allowFlying && !p.capabilities.isFlying && !p.capabilities.disableDamage,
                "flying and damage are back as they were (a survival player)");
            check(p.getActivePotionEffect(Potion.nightVision) == null, "night vision is gone");
            check(r.contains("lifted") && Math.abs(p.posY - (y + 3)) < 0.01, "stopped inside stone, they're lifted onto it (y " + p.posY + ", stone top " + (y + 2) + ")");
            check(!Spectators.persisted(p).getBoolean("vanish"), "and not vanished");
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] spectate threw", t);
            check(false, "spectate checks ran without an exception (" + t + ")");
        } finally {
            for (int k = 0; k < 3; k++) world.setBlockToAir(x, y + k, z);
            Spectators.active.remove(p.getUniqueID());
            SelfTest.summary();
        }
    }
}
